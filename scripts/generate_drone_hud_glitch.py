#!/usr/bin/env python3
"""Build wide transparent drone-HUD glitch masks from animation.gif.mp4.

The source video is sampled into small fragments, then each output frame is
assembled by deterministic random copies with soft alpha overlap. The result
is a mask (white RGB + alpha), not an opaque video layer.
"""
from __future__ import annotations

import json
import random
import subprocess
import tempfile
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageFilter, ImageOps


SOURCE_VIDEO = Path("/home/mart/Documents/animation.gif.mp4")
TARGET_SIZE = (640, 360)
TILE_SIZE = (160, 180)
IDLE_FRAMES = 32
BURST_FRAMES = 6
IDLE_GLYPH_BASE = 0xE600
BURST_GLYPH_BASE = 0xE860
TILES_PER_FRAME = 8


def read_source_frames() -> list[Image.Image]:
    if not SOURCE_VIDEO.is_file():
        raise SystemExit(f"missing glitch source video: {SOURCE_VIDEO}")
    with tempfile.TemporaryDirectory(prefix="lg2-glitch-") as temp_dir:
        output_pattern = Path(temp_dir) / "frame_%02d.png"
        subprocess.run(
            ["ffmpeg", "-v", "error", "-i", str(SOURCE_VIDEO), "-vf", "fps=10", str(output_pattern)],
            check=True,
        )
        frames = [Image.open(path).convert("L").copy() for path in sorted(Path(temp_dir).glob("frame_*.png"))]
    if not frames:
        raise SystemExit("the glitch source video contains no decodable frames")
    return frames


def make_ragged_ribbon_mask(width: int, height: int, randomizer: random.Random) -> Image.Image:
    """Return a horizontally stretched, irregular alpha silhouette.

    The sampled video provides texture, while this mask removes the rectangular
    crop boundaries: a random walk forms the upper/lower edges and a few holes
    chew into the ribbon before its soft final blur.
    """
    mask = Image.new("L", (width, height), 0)
    drawer = ImageDraw.Draw(mask)
    step = max(4, width // 24)
    start_x = randomizer.randint(0, max(1, width // 7))
    end_x = randomizer.randint(max(start_x + 1, width * 5 // 6), width)
    center = height * 0.5 + randomizer.uniform(-height * 0.12, height * 0.12)
    thickness = max(1.2, height * randomizer.uniform(0.48, 0.96))
    top_points: list[tuple[int, float]] = []
    bottom_points: list[tuple[int, float]] = []
    for x in range(start_x, end_x + step, step):
        center = max(0.0, min(float(height), center + randomizer.uniform(-height * 0.18, height * 0.18)))
        thickness = max(1.0, min(float(height), thickness + randomizer.uniform(-height * 0.16, height * 0.16)))
        top_points.append((min(x, end_x), max(0.0, center - thickness * 0.5)))
        bottom_points.append((min(x, end_x), min(float(height), center + thickness * 0.5)))
    drawer.polygon(top_points + list(reversed(bottom_points)), fill=255)
    for _ in range(randomizer.randint(1, 4)):
        hole_width = randomizer.randint(max(2, width // 32), max(3, width // 9))
        hole_height = randomizer.randint(1, max(2, height // 2))
        hole_x = randomizer.randint(start_x, max(start_x, end_x - hole_width))
        hole_y = randomizer.randint(0, max(0, height - hole_height))
        drawer.ellipse((hole_x, hole_y, hole_x + hole_width, hole_y + hole_height), fill=0)
    return mask.filter(ImageFilter.GaussianBlur(radius=randomizer.uniform(0.45, 1.2)))


def make_mask(source_frames: list[Image.Image], seed: int, patches: int, strength: float) -> Image.Image:
    randomizer = random.Random(seed)
    canvas = Image.new("L", TARGET_SIZE, 0)
    for _ in range(patches):
        source = randomizer.choice(source_frames)
        # Thin, wide strips read as a signal fault rather than square mosaic
        # blocks. The original clip still supplies each strip's noisy detail.
        crop_width = randomizer.randint(48, 180)
        crop_height = randomizer.randint(4, 28)
        left = randomizer.randint(0, source.width - crop_width)
        top = randomizer.randint(0, source.height - crop_height)
        fragment = source.crop((left, top, left + crop_width, top + crop_height))
        fragment = ImageOps.autocontrast(fragment)
        fragment = fragment.resize(
            (randomizer.randint(112, 520), randomizer.randint(2, 16)),
            Image.Resampling.BILINEAR,
        )
        alpha = fragment.point(lambda value: int(value * strength))
        alpha = ImageChops.multiply(alpha, make_ragged_ribbon_mask(alpha.width, alpha.height, randomizer))
        alpha = alpha.filter(ImageFilter.GaussianBlur(radius=randomizer.uniform(0.45, 1.6)))
        positioned = Image.new("L", TARGET_SIZE, 0)
        positioned.paste(
            alpha,
            (randomizer.randint(-40, TARGET_SIZE[0] - 10), randomizer.randint(0, TARGET_SIZE[1] - alpha.height)),
        )
        canvas = ImageChops.lighter(canvas, positioned)
    rgba = Image.new("RGBA", TARGET_SIZE, (255, 255, 255, 0))
    rgba.putalpha(canvas)
    # Keep the font glyph's advance fixed at the intended full-screen width.
    # This virtually invisible pixel is outside the useful mask area.
    rgba.putpixel((TARGET_SIZE[0] - 1, TARGET_SIZE[1] - 1), (255, 255, 255, 1))
    return rgba


def make_tiled_atlases(source_frames: list[Image.Image], frame_count: int, seed: int, patches: int, strengths: list[float]) -> tuple[Image.Image, Image.Image]:
    """Split every wide frame into atlas-safe 160x180 font glyphs.

    Minecraft's font stitcher only accepts glyph bitmaps up to 256x256. The
    top and bottom rows are separate atlases so their font providers can use
    different ascents and reconstruct a full 640x360 screen mask.
    """
    top_atlas = Image.new("RGBA", (TARGET_SIZE[0], TILE_SIZE[1] * frame_count), (0, 0, 0, 0))
    bottom_atlas = Image.new("RGBA", top_atlas.size, (0, 0, 0, 0))
    for frame in range(frame_count):
        mask = make_mask(source_frames, seed + frame, patches, strengths[frame])
        for column in range(TARGET_SIZE[0] // TILE_SIZE[0]):
            left = column * TILE_SIZE[0]
            top_tile = mask.crop((left, 0, left + TILE_SIZE[0], TILE_SIZE[1]))
            bottom_tile = mask.crop((left, TILE_SIZE[1], left + TILE_SIZE[0], TARGET_SIZE[1]))
            # The almost-transparent edge pixel makes each tile's text advance
            # deterministic (160 image pixels + Minecraft's one-pixel advance).
            top_tile.putpixel((TILE_SIZE[0] - 1, TILE_SIZE[1] - 1), (255, 255, 255, 1))
            bottom_tile.putpixel((TILE_SIZE[0] - 1, TILE_SIZE[1] - 1), (255, 255, 255, 1))
            top_atlas.alpha_composite(top_tile, (left, frame * TILE_SIZE[1]))
            bottom_atlas.alpha_composite(bottom_tile, (left, frame * TILE_SIZE[1]))
    return top_atlas, bottom_atlas


def write_outputs(root_dir: Path, filename: str, image: Image.Image) -> None:
    for resource_root in (root_dir / "mods/lg2-0.1.0/src/main/resources", root_dir / "polymer/source_assets"):
        path = resource_root / "assets/lg2/textures/font" / filename
        path.parent.mkdir(parents=True, exist_ok=True)
        image.save(path)
        print(f"wrote {path}")


def tile_rows(glyph_base: int, frame_count: int, first_tile: int) -> list[str]:
    return [
        "".join(chr(glyph_base + frame * TILES_PER_FRAME + first_tile + column) for column in range(4))
        for frame in range(frame_count)
    ]


def write_font_definition(root_dir: Path) -> None:
    providers = [
        {
            "type": "bitmap",
            "file": "lg2:font/drone_hud_glitch_idle.png",
            "ascent": -22,
            "height": TILE_SIZE[1],
            "chars": tile_rows(IDLE_GLYPH_BASE, IDLE_FRAMES, 0),
        },
        {
            "type": "bitmap",
            "file": "lg2:font/drone_hud_glitch_idle_bottom.png",
            "ascent": 158,
            "height": TILE_SIZE[1],
            "chars": tile_rows(IDLE_GLYPH_BASE, IDLE_FRAMES, 4),
        },
        {
            "type": "bitmap",
            "file": "lg2:font/drone_hud_glitch_burst.png",
            "ascent": -3,
            "height": TILE_SIZE[1],
            "chars": tile_rows(BURST_GLYPH_BASE, BURST_FRAMES, 0),
        },
        {
            "type": "bitmap",
            "file": "lg2:font/drone_hud_glitch_burst_bottom.png",
            "ascent": 177,
            "height": TILE_SIZE[1],
            "chars": tile_rows(BURST_GLYPH_BASE, BURST_FRAMES, 4),
        },
        {"type": "space", "advances": {"\ue890": -644}},
    ]
    definition = {"providers": providers}
    for resource_root in (root_dir / "mods/lg2-0.1.0/src/main/resources", root_dir / "polymer/source_assets"):
        path = resource_root / "assets/lg2/font/drone_glitch.json"
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(definition, ensure_ascii=True, indent=2) + "\n", encoding="utf-8")
        print(f"wrote {path}")


def main() -> None:
    root_dir = Path(__file__).resolve().parent.parent
    source_frames = read_source_frames()
    idle_top, idle_bottom = make_tiled_atlases(source_frames, IDLE_FRAMES, 0x1D1E, 20, [0.13] * IDLE_FRAMES)
    burst_top, burst_bottom = make_tiled_atlases(source_frames, BURST_FRAMES, 0xB0057, 76, [1.0, 0.76, 0.52, 0.30, 0.12, 0.0])
    write_outputs(root_dir, "drone_hud_glitch_idle.png", idle_top)
    write_outputs(root_dir, "drone_hud_glitch_idle_bottom.png", idle_bottom)
    write_outputs(root_dir, "drone_hud_glitch_burst.png", burst_top)
    write_outputs(root_dir, "drone_hud_glitch_burst_bottom.png", burst_bottom)
    write_font_definition(root_dir)


if __name__ == "__main__":
    main()
