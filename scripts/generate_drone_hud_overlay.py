#!/usr/bin/env python3
"""Build the animated drone-HUD bitmap-font atlases.

The compass tape repeats every ten degrees, with 40 sub-degree frames. Its
three digits are a separate ten-glyph font, so the tape can animate smoothly
without duplicating the same artwork for every cardinal direction. Keep all
ranges in sync with DroneSystem and both minecraft/font/default.json files.
"""
from __future__ import annotations

import json
import math
from pathlib import Path

from PIL import Image, ImageDraw

HEADING_BASE = 0xE7A0
HEADING_FRAMES = 10
HEADING_COLUMNS = 5
HEADING_DRAW_SIZE = (48, 12)
HEADING_DIGIT_BASE = 0xE8D0
HEADING_DIGIT_COUNT = 10
HEADING_DIGIT_CELL = (4, 144)
HEADING_PIXELS_PER_DEGREE = 0.75
# Bossbar titles start at the top of the GUI. The attitude artwork is placed
# lower in each cell so it reaches the crosshair instead of remaining in the
# bossbar area. The compass intentionally remains at the bossbar height.
HUD_CELL = (48, 144)
ATTITUDE_ANCHOR_Y = 130
ATTITUDE_LADDER_TOP_Y = 98
ATTITUDE_LADDER_HEIGHT = 65
ATTITUDE_RUNG_SPACING = 5
ATTITUDE_RUNG_COUNT = 13
ATTITUDE_LABEL_X = 2
ATTITUDE_LABEL_Y = 86
HEADING_TOP_Y = 0
HEADING_CELL = HUD_CELL
ATTITUDE_BASE = 0xE7F0
ATTITUDE_FRAMES = 5
ATTITUDE_LABEL_BASE = 0xEAB0
ATTITUDE_LABEL_FRAMES = 39
ATTITUDE_LABEL_COLUMNS = 3
ATTITUDE_DRAW_SIZE = (48, 28)
ATTITUDE_CELL = (48, 176)
BANK_BASE = 0xE800
BANK_FRAMES = 13
BANK_COLUMNS = 1
BANK_CELL = HUD_CELL

PIXEL_FONT = {
    "0": ("111", "101", "101", "101", "111"),
    "1": ("010", "110", "010", "010", "111"),
    "2": ("111", "001", "111", "100", "111"),
    "3": ("111", "001", "111", "001", "111"),
    "4": ("101", "101", "111", "001", "001"),
    "5": ("111", "100", "111", "001", "111"),
    "6": ("111", "100", "111", "101", "111"),
    "7": ("111", "001", "010", "010", "010"),
    "8": ("111", "101", "111", "101", "111"),
    "9": ("111", "101", "111", "001", "111"),
    "D": ("110", "101", "101", "101", "110"),
    "L": ("100", "100", "100", "100", "111"),
    "N": ("101", "111", "111", "111", "101"),
    "P": ("110", "101", "110", "100", "100"),
    "U": ("101", "101", "101", "101", "111"),
    "V": ("101", "101", "101", "101", "010"),
}


def draw_pixel_text(draw: ImageDraw.ImageDraw, x: int, y: int, text: str, color: tuple[int, int, int, int]) -> None:
    cursor = x
    for char in text:
        glyph = PIXEL_FONT[char]
        for row, pixels in enumerate(glyph):
            for column, pixel in enumerate(pixels):
                if pixel == "1":
                    draw.point((cursor + column, y + row), fill=color)
        cursor += 4


def fix_hud_glyph_advance(cell: Image.Image) -> None:
    """Make every HUD glyph exactly 49 px wide in Minecraft's bitmap font."""
    cell.putpixel((cell.width - 1, cell.height - 1), (255, 255, 255, 1))


def build_heading_atlas(pointer: Image.Image) -> Image.Image:
    cell_width, cell_height = HEADING_CELL
    rows = HEADING_FRAMES // HEADING_COLUMNS
    atlas = Image.new("RGBA", (cell_width * HEADING_COLUMNS, cell_height * rows), (0, 0, 0, 0))

    for frame in range(HEADING_FRAMES):
        # The visible pattern is periodic: a tick occurs every ten compass
        # degrees, while `phase` advances at 0.25 degree increments. As the
        # drone turns clockwise, ground-fixed marks travel left across tape.
        phase = frame * 10.0 / HEADING_FRAMES
        cell = Image.new("RGBA", HEADING_CELL, (0, 0, 0, 0))
        draw = ImageDraw.Draw(cell)
        line_y = HEADING_TOP_Y + 9
        draw.line((2, line_y, 45, line_y), fill=(255, 255, 255, 105))
        for tick in range(-4, 5):
            x = round(24 + (tick * 10.0 - phase) * HEADING_PIXELS_PER_DEGREE)
            if not 1 <= x < cell_width - 1:
                continue
            tick_height = 3
            draw.line((x, line_y, x, line_y - tick_height), fill=(255, 255, 255, 165))
        cell.alpha_composite(pointer, (0, HEADING_TOP_Y))
        fix_hud_glyph_advance(cell)
        atlas.alpha_composite(cell, ((frame % HEADING_COLUMNS) * cell_width, (frame // HEADING_COLUMNS) * cell_height))

    return atlas


def build_heading_digit_atlas() -> Image.Image:
    digit_width, digit_height = HEADING_DIGIT_CELL
    atlas = Image.new("RGBA", (digit_width * HEADING_DIGIT_COUNT, digit_height), (0, 0, 0, 0))
    for digit in range(HEADING_DIGIT_COUNT):
        cell = Image.new("RGBA", HEADING_DIGIT_CELL, (0, 0, 0, 0))
        draw_pixel_text(ImageDraw.Draw(cell), 0, HEADING_TOP_Y + 1, str(digit), (255, 255, 255, 215))
        # The three-pixel digit deliberately has no forced right edge: its
        # Minecraft advance is exactly four pixels, allowing a fixed 000-359
        # readout to be assembled from only ten reusable glyphs.
        atlas.alpha_composite(cell, (digit * digit_width, 0))
    return atlas


def build_attitude_atlas(reticle: Image.Image) -> Image.Image:
    cell_width, cell_height = ATTITUDE_CELL
    atlas = Image.new("RGBA", (cell_width, cell_height * ATTITUDE_FRAMES), (0, 0, 0, 0))

    for frame in range(ATTITUDE_FRAMES):
        cell = Image.new("RGBA", ATTITUDE_CELL, (0, 0, 0, 0))
        draw = ImageDraw.Draw(cell)
        # One five-frame pixel-scroll cycle. It repeats every 20 degrees of
        # pitch, while the separate label keeps the true UP/DN angle. Every
        # rung has the same treatment: there is no oversized "horizon" line.
        for rung in range(-(ATTITUDE_RUNG_COUNT // 2), ATTITUDE_RUNG_COUNT // 2 + 1):
            y = ATTITUDE_LADDER_TOP_Y + ((frame + rung * ATTITUDE_RUNG_SPACING) % ATTITUDE_LADDER_HEIGHT)
            # Short, inward side dashes leave the centre for roll wings and
            # the fixed reticle. Fade them at the ladder's top/bottom edge so
            # the scale visually dissolves instead of ending in a hard wall.
            if y <= ATTITUDE_ANCHOR_Y:
                edge_distance = ATTITUDE_ANCHOR_Y - y
                fade_distance = ATTITUDE_ANCHOR_Y - ATTITUDE_LADDER_TOP_Y
            else:
                edge_distance = y - ATTITUDE_ANCHOR_Y
                fade_distance = ATTITUDE_LADDER_TOP_Y + ATTITUDE_LADDER_HEIGHT - 1 - ATTITUDE_ANCHOR_Y
            # A two-pixel centre plateau means the nearest animated rung is
            # always at full opacity on the fixed reticle centre.
            alpha = round(105 * max(0.0, 1.0 - max(0.0, edge_distance - 2) / (fade_distance - 2)))
            if alpha <= 0:
                continue
            draw.line((11, y, 16, y), fill=(255, 255, 255, alpha))
            draw.line((31, y, 36, y), fill=(255, 255, 255, alpha))

        cell.alpha_composite(reticle, (0, ATTITUDE_ANCHOR_Y - ATTITUDE_DRAW_SIZE[1] // 2))
        fix_hud_glyph_advance(cell)
        atlas.alpha_composite(cell, (0, frame * cell_height))

    return atlas


def build_attitude_label_atlas() -> Image.Image:
    cell_width, cell_height = ATTITUDE_CELL
    rows = math.ceil(ATTITUDE_LABEL_FRAMES / ATTITUDE_LABEL_COLUMNS)
    atlas = Image.new("RGBA", (cell_width * ATTITUDE_LABEL_COLUMNS, cell_height * rows), (0, 0, 0, 0))
    for frame in range(ATTITUDE_LABEL_FRAMES):
        pitch = round(-90 + frame * (180 / (ATTITUDE_LABEL_FRAMES - 1)))
        cell = Image.new("RGBA", ATTITUDE_CELL, (0, 0, 0, 0))
        if pitch < 0:
            label = f"UP{abs(pitch):02d}"
        elif pitch > 0:
            label = f"DN{pitch:02d}"
        else:
            label = "LVL"
        draw_pixel_text(ImageDraw.Draw(cell), ATTITUDE_LABEL_X, ATTITUDE_LABEL_Y, label, (255, 255, 255, 145))
        fix_hud_glyph_advance(cell)
        atlas.alpha_composite(cell, ((frame % ATTITUDE_LABEL_COLUMNS) * cell_width, (frame // ATTITUDE_LABEL_COLUMNS) * cell_height))
    return atlas


def build_bank_atlas() -> Image.Image:
    """Draw the aircraft-reference wings: a separate roll line beside the ground horizon."""
    cell_width, cell_height = BANK_CELL
    rows = math.ceil(BANK_FRAMES / BANK_COLUMNS)
    atlas = Image.new("RGBA", (cell_width * BANK_COLUMNS, cell_height * rows), (0, 0, 0, 0))
    center_x = cell_width // 2
    center_y = ATTITUDE_ANCHOR_Y

    for frame in range(BANK_FRAMES):
        bank_degrees = -32 + frame * (64 / (BANK_FRAMES - 1))
        angle = math.radians(-bank_degrees)
        direction_x = math.cos(angle)
        direction_y = math.sin(angle)
        cell = Image.new("RGBA", BANK_CELL, (0, 0, 0, 0))
        # A native one-pixel Bresenham line is deliberate here. Any filtered
        # high-resolution variant bleeds alpha into a neighbouring pixel and
        # looks two pixels wide in Minecraft's nearest-neighbour GUI.
        draw = ImageDraw.Draw(cell)

        # Keep the central crosshair gap open. These are the drone's two wing
        # markers, while the other layer is the unrotated ground horizon.
        for start, end in ((-23, -8), (8, 23)):
            draw.line(
                (
                    round(center_x + direction_x * start),
                    round(center_y + direction_y * start),
                    round(center_x + direction_x * end),
                    round(center_y + direction_y * end),
                ),
                fill=(255, 255, 255, 210),
                width=1,
            )
        fix_hud_glyph_advance(cell)
        atlas.alpha_composite(cell, ((frame % BANK_COLUMNS) * cell_width, (frame // BANK_COLUMNS) * cell_height))

    return atlas


def save_to_resource_packs(root_dir: Path, filename: str, image: Image.Image) -> None:
    outputs = (
        root_dir / "mods/lg2-0.1.0/src/main/resources/assets/lg2/textures/font" / filename,
        root_dir / "polymer/source_assets/assets/lg2/textures/font" / filename,
    )
    for path in outputs:
        path.parent.mkdir(parents=True, exist_ok=True)
        image.save(path)
        print(f"wrote {path}")


def verify_font_registration(font_path: Path) -> None:
    providers = json.loads(font_path.read_text(encoding="utf-8"))["providers"]
    expected = {
        "lg2:font/drone_hud_heading.png": list(range(HEADING_BASE, HEADING_BASE + HEADING_FRAMES)),
        "lg2:font/drone_hud_heading_digits.png": list(range(HEADING_DIGIT_BASE, HEADING_DIGIT_BASE + HEADING_DIGIT_COUNT)),
        "lg2:font/drone_hud_attitude.png": list(range(ATTITUDE_BASE, ATTITUDE_BASE + ATTITUDE_FRAMES)),
        "lg2:font/drone_hud_attitude_labels.png": list(range(ATTITUDE_LABEL_BASE, ATTITUDE_LABEL_BASE + ATTITUDE_LABEL_FRAMES)),
        "lg2:font/drone_hud_bank.png": list(range(BANK_BASE, BANK_BASE + BANK_FRAMES)),
    }
    cell_heights = {
        "lg2:font/drone_hud_heading.png": HEADING_CELL[1],
        "lg2:font/drone_hud_heading_digits.png": HEADING_DIGIT_CELL[1],
        "lg2:font/drone_hud_attitude.png": ATTITUDE_CELL[1],
        "lg2:font/drone_hud_attitude_labels.png": ATTITUDE_CELL[1],
        "lg2:font/drone_hud_bank.png": BANK_CELL[1],
    }
    for filename, codepoints in expected.items():
        provider = next((item for item in providers if item.get("file") == filename), None)
        if provider is None:
            raise SystemExit(f"{font_path} does not register {filename}")
        registered = [ord(char) for row in provider["chars"] for char in row]
        if registered != codepoints:
            raise SystemExit(f"{font_path} has an invalid Unicode sequence for {filename}")
        if provider["ascent"] > provider["height"] or provider["height"] != cell_heights[filename]:
            raise SystemExit(f"{font_path} has invalid bitmap dimensions for {filename}")

    space = next((item for item in providers if item.get("type") == "space"), None)
    advances = {} if space is None else space.get("advances", {})
    if (advances.get(chr(0xE940)) != -64
            or advances.get(chr(0xE94A)) != 8
            or advances.get(chr(0xE94B)) != 16
            or advances.get(chr(0xE946)) != -1):
        raise SystemExit(f"{font_path} has an invalid drone HUD glyph rewind")


def main() -> None:
    script_dir = Path(__file__).resolve().parent
    root_dir = script_dir.parent
    assets_dir = script_dir / "assets"
    reticle = Image.open(assets_dir / "drone_hud_attitude_reticle.png").convert("RGBA")
    pointer = Image.open(assets_dir / "drone_hud_heading_pointer.png").convert("RGBA")
    if reticle.size != ATTITUDE_DRAW_SIZE or pointer.size != HEADING_DRAW_SIZE:
        raise SystemExit("drone HUD overlay source textures have unexpected dimensions")

    if (HEADING_BASE + HEADING_FRAMES - 1 != 0xE7A9
            or HEADING_DIGIT_BASE + HEADING_DIGIT_COUNT - 1 != 0xE8D9
            or ATTITUDE_BASE + ATTITUDE_FRAMES - 1 != 0xE7F4
            or ATTITUDE_LABEL_BASE + ATTITUDE_LABEL_FRAMES - 1 != 0xEAD6
            or BANK_BASE + BANK_FRAMES - 1 != 0xE80C):
        raise SystemExit("drone HUD glyph ranges do not match their registered font ranges")

    verify_font_registration(root_dir / "mods/lg2-0.1.0/src/main/resources/assets/minecraft/font/default.json")
    verify_font_registration(root_dir / "polymer/source_assets/assets/minecraft/font/default.json")

    save_to_resource_packs(root_dir, "drone_hud_heading.png", build_heading_atlas(pointer))
    save_to_resource_packs(root_dir, "drone_hud_heading_digits.png", build_heading_digit_atlas())
    save_to_resource_packs(root_dir, "drone_hud_attitude.png", build_attitude_atlas(reticle))
    save_to_resource_packs(root_dir, "drone_hud_attitude_labels.png", build_attitude_label_atlas())
    save_to_resource_packs(root_dir, "drone_hud_bank.png", build_bank_atlas())


if __name__ == "__main__":
    main()
