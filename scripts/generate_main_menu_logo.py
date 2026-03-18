from __future__ import annotations

import math
import random
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
SCRIPT_DIR = Path(__file__).resolve().parent
ASSETS_DIR = SCRIPT_DIR / 'assets'
SOURCE_LOGO = ASSETS_DIR / 'lostglade_px.png'
OUTPUT_DIRS = [
    ROOT / 'mods/lg2-0.1.0/src/main/resources/assets/minecraft/textures/font',
    ROOT / 'polymer/source_assets/assets/minecraft/textures/font',
]
PREVIEW_DIR = ROOT / '.tmp'
GLITCH_NAME = 'main_menu_logo_anim.png'
DVD_NAME = 'main_menu_logo_dvd_anim.png'
PREVIEW_GLITCH = PREVIEW_DIR / 'main_menu_logo_preview_glitch.png'
PREVIEW_DVD = PREVIEW_DIR / 'main_menu_logo_preview_dvd.png'

CANVAS_W = 176
CANVAS_H = 204
SCREEN_X = 19
SCREEN_Y = 82
SCREEN_W = 100
SCREEN_H = 51
INNER_X = SCREEN_X + 1
INNER_Y = SCREEN_Y + 1
INNER_W = SCREEN_W - 2
INNER_H = SCREEN_H - 2
GLYPH_W = CANVAS_W
GLYPH_H = CANVAS_H

GLITCH_FRAME_COUNT = 16
GLITCH_GRID_COLS = 4
GLITCH_GRID_ROWS = 4
DVD_FRAME_COUNT = 130
DVD_GRID_COLS = 10
DVD_GRID_ROWS = 13

GLITCH_LOGO_W = 48
GLITCH_LOGO_H = 32
DVD_MARGIN_X = 0
DVD_MARGIN_Y = 0
DVD_SPEED_X = 6
DVD_SPEED_Y = 7

SCREEN_BG = (0x18, 0x15, 0x14)
SCREEN_FRAME = (0x29, 0x2F, 0x2F)
SIGNAL_BRIGHT = (0x22, 0x1B, 0x14)
SIGNAL_DARK = (0x15, 0x12, 0x11)
REFLECTION = (0x5C, 0x5E, 0x5C, 14)
AMBER_GLOW = (255, 154, 44)
CYAN_GLOW = (68, 255, 234)
CORE_GLOW = (110, 255, 240)
LOGO_PALETTE = [
    (0, 0, 0, 0),
    (0, 0, 0, 255),
    (0, 210, 137, 255),
    (0, 199, 241, 255),
]


def clamp(value: int, low: int, high: int) -> int:
    return max(low, min(high, value))


def quantize_to_palette(image: Image.Image, palette: list[tuple[int, int, int, int]]) -> Image.Image:
    output = Image.new('RGBA', image.size)
    pixels = []
    for pixel in image.getdata():
        best = min(
            palette,
            key=lambda color: (color[3] - pixel[3]) ** 2 + sum((color[index] - pixel[index]) ** 2 for index in range(3)),
        )
        pixels.append(best)
    output.putdata(pixels)
    return output


def upscale_logo_clean(base_logo: Image.Image, size: tuple[int, int]) -> Image.Image:
    resized = base_logo.resize(size, Image.Resampling.BICUBIC)
    return quantize_to_palette(resized, LOGO_PALETTE)


def inside_screen_mask(x: int, y: int) -> bool:
    sx0 = SCREEN_X
    sy0 = SCREEN_Y
    sx1 = SCREEN_X + SCREEN_W - 1
    sy1 = SCREEN_Y + SCREEN_H - 1
    if x < sx0 or x > sx1 or y < sy0 or y > sy1:
        return False
    return (x, y) not in {
        (sx0, sy0),
        (sx1, sy0),
        (sx0, sy1),
        (sx1, sy1),
    }


def make_screen_base(frame: int, total_frames: int) -> Image.Image:
    img = Image.new('RGBA', (CANVAS_W, CANVAS_H), (0, 0, 0, 0))
    px = img.load()
    phase = frame / total_frames * math.tau
    sweep = (frame / total_frames) * (INNER_H + 12) - 6

    for y in range(SCREEN_Y, SCREEN_Y + SCREEN_H):
        for x in range(SCREEN_X, SCREEN_X + SCREEN_W):
            if not inside_screen_mask(x, y):
                continue

            if x in (SCREEN_X, SCREEN_X + SCREEN_W - 1) or y in (SCREEN_Y, SCREEN_Y + SCREEN_H - 1):
                px[x, y] = (*SCREEN_FRAME, 255)
                continue

            r, g, b = SCREEN_BG
            if ((y + frame) % 4) in (0, 1):
                r, g, b = SIGNAL_DARK

            distance = abs((y - INNER_Y) - sweep)
            if distance < 3.2:
                glow = 1.0 - (distance / 3.2)
                r = clamp(r + int(4 * glow), 0, 255)
                g = clamp(g + int(4 * glow), 0, 255)
                b = clamp(b + int(3 * glow), 0, 255)

            signal = math.sin((x * 0.17) + phase * 1.1)
            signal += math.sin((y * 0.31) + phase * 0.8)
            signal += math.sin(((x + y) * 0.09) - phase * 0.7)
            signal_delta = int(round((signal / 3.0) * 1.1))
            r = clamp(r + signal_delta, 0, 255)
            g = clamp(g + signal_delta, 0, 255)
            b = clamp(b + signal_delta, 0, 255)
            px[x, y] = (r, g, b, 255)

    return img


def dilate_alpha(alpha: Image.Image, offsets: list[tuple[int, int]]) -> Image.Image:
    result = Image.new('L', alpha.size, 0)
    for dx, dy in offsets:
        shifted = ImageChops.offset(alpha, dx, dy)
        if dx > 0:
            shifted.paste(0, (0, 0, dx, alpha.height))
        elif dx < 0:
            shifted.paste(0, (alpha.width + dx, 0, alpha.width, alpha.height))
        if dy > 0:
            shifted.paste(0, (0, 0, alpha.width, dy))
        elif dy < 0:
            shifted.paste(0, (0, alpha.height + dy, alpha.width, alpha.height))
        result = ImageChops.lighter(result, shifted)
    return result


def add_logo_glow(canvas: Image.Image, logo: Image.Image, x: int, y: int, glow_boost: float = 1.0) -> None:
    alpha = logo.getchannel('A')
    outer = dilate_alpha(alpha, [(-2, 0), (2, 0), (0, -2), (0, 2), (-1, -1), (1, 1), (-1, 1), (1, -1)])
    inner = dilate_alpha(alpha, [(-1, 0), (1, 0), (0, -1), (0, 1)])

    amber = Image.new('RGBA', logo.size, (*AMBER_GLOW, 0))
    amber.putalpha(outer.point(lambda v: int(v * 0.11 * glow_boost)))
    cyan = Image.new('RGBA', logo.size, (*CYAN_GLOW, 0))
    cyan.putalpha(outer.point(lambda v: int(v * 0.18 * glow_boost)))
    core = Image.new('RGBA', logo.size, (*CORE_GLOW, 0))
    core.putalpha(inner.point(lambda v: int(v * 0.14 * glow_boost)))

    canvas.alpha_composite(amber, (x + 1, y + 1))
    canvas.alpha_composite(cyan, (x, y))
    canvas.alpha_composite(core, (x, y))
    canvas.alpha_composite(logo, (x, y))


def add_signal_overlay(
    frame_img: Image.Image,
    frame: int,
    total_frames: int,
    rows: int = 2,
    snow_points: int = 12,
) -> Image.Image:
    overlay = Image.new('RGBA', frame_img.size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(overlay, 'RGBA')

    for idx in range(rows):
        bar_y = INNER_Y + ((frame * 4 + idx * 15) % max(1, INNER_H - 2))
        alpha = 18 if idx == 0 else 12
        draw.line((INNER_X + 2, bar_y, INNER_X + INNER_W - 3, bar_y), fill=(*SIGNAL_BRIGHT, alpha), width=1)

    for seed in range(snow_points):
        x = INNER_X + 2 + ((frame * 11 + seed * 7) % max(1, INNER_W - 4))
        y = INNER_Y + 2 + ((frame * 5 + seed * 13) % max(1, INNER_H - 4))
        if inside_screen_mask(x, y):
            tone = 45 + ((seed * 9 + frame * 3) % 10)
            draw.point((x, y), fill=(tone, tone, tone, 16))

    reflection_y = INNER_Y + 5 + int((math.sin(frame / total_frames * math.tau) + 1) * 2)
    draw.line((INNER_X + 10, reflection_y, INNER_X + INNER_W - 14, reflection_y + 6), fill=REFLECTION, width=1)
    return Image.alpha_composite(frame_img, overlay)


def bend_logo(logo: Image.Image, phase: float) -> Image.Image:
    out = Image.new('RGBA', logo.size, (0, 0, 0, 0))
    for y in range(logo.height):
        # Softer than in 941a4bd: same character, lower amplitude.
        wave = math.sin((y / max(1, logo.height - 1)) * math.tau * 0.9 + phase)
        drift = math.sin((y / max(1, logo.height - 1)) * math.tau * 2.1 + phase * 1.2)
        shift = int(round(wave * 0.45 + drift * 0.18))
        row = logo.crop((0, y, logo.width, y + 1))
        out.alpha_composite(row, (shift, y))
    return out


def glitch_logo(logo: Image.Image, frame: int) -> Image.Image:
    glitched = logo.copy()
    if frame not in {5, 13}:
        return glitched

    y = 8 + ((frame * 5) % max(1, logo.height - 16))
    h = 3
    shift = -2 if frame == 5 else 2
    band = glitched.crop((0, y, logo.width, min(logo.height, y + h)))
    glitched.paste((0, 0, 0, 0), (0, y, logo.width, min(logo.height, y + h)))
    glitched.alpha_composite(band, (shift, y))
    return glitched


def make_logo_frame(base_logo: Image.Image, frame: int, total_frames: int) -> Image.Image:
    phase = frame / total_frames * math.tau
    logo = bend_logo(base_logo, phase)
    logo = glitch_logo(logo, frame)

    canvas = Image.new('RGBA', (CANVAS_W, CANVAS_H), (0, 0, 0, 0))
    x = SCREEN_X + (SCREEN_W - base_logo.width) // 2
    y = SCREEN_Y + (SCREEN_H - base_logo.height) // 2 + 1

    alpha = logo.getchannel('A')
    ghost_shift = 1 if frame in {5, 13} else 0
    if ghost_shift:
        red_ghost = Image.new('RGBA', logo.size, (255, 110, 90, 0))
        red_ghost.putalpha(alpha.point(lambda v: int(v * 0.12)))
        blue_ghost = Image.new('RGBA', logo.size, (70, 220, 255, 0))
        blue_ghost.putalpha(alpha.point(lambda v: int(v * 0.14)))
        canvas.alpha_composite(red_ghost, (x - ghost_shift, y))
        canvas.alpha_composite(blue_ghost, (x + ghost_shift, y))

    add_logo_glow(canvas, logo, x, y, glow_boost=1.0)
    return canvas


def add_screen_effects(frame_img: Image.Image, frame: int, total_frames: int) -> Image.Image:
    img = frame_img.copy()
    overlay = Image.new('RGBA', img.size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(overlay, 'RGBA')
    rng = random.Random(9000 + frame)

    for idx in range(2):
        bar_y = INNER_Y + ((frame * 4 + idx * 15) % max(1, INNER_H - 2))
        color = (255, 176, 64, 13) if idx == 0 else (90, 255, 235, 10)
        draw.line((INNER_X + 2, bar_y, INNER_X + INNER_W - 3, bar_y), fill=color, width=1)

    for _ in range(24):
        x = rng.randint(INNER_X + 1, INNER_X + INNER_W - 2)
        y = rng.randint(INNER_Y + 1, INNER_Y + INNER_H - 2)
        if not inside_screen_mask(x, y):
            continue
        if rng.random() < 0.86:
            continue
        color = rng.choice([
            (55, 57, 56, 22),
            (74, 77, 76, 18),
            (33, 35, 34, 18),
        ])
        draw.point((x, y), fill=color)

    reflection_y = INNER_Y + 5 + int((math.sin(frame / total_frames * math.tau) + 1) * 2)
    draw.line((INNER_X + 10, reflection_y, INNER_X + INNER_W - 14, reflection_y + 6), fill=REFLECTION, width=1)
    return Image.alpha_composite(img, overlay)


def build_glitch_frame(base_logo: Image.Image, frame: int, total_frames: int) -> Image.Image:
    frame_img = make_screen_base(frame, total_frames)
    frame_img.alpha_composite(make_logo_frame(base_logo, frame, total_frames))
    return add_screen_effects(frame_img, frame, total_frames)


def reflect_step(frame: int, speed: int, span: int) -> int:
    if span <= 0:
        return 0
    period = span * 2
    reflected = (frame * speed) % period
    return reflected if reflected <= span else period - reflected


def build_dvd_frame(base_logo: Image.Image, frame: int, total_frames: int) -> Image.Image:
    frame_img = make_screen_base(frame, total_frames)

    travel_x = max(0, INNER_W - base_logo.width - (DVD_MARGIN_X * 2))
    travel_y = max(0, INNER_H - base_logo.height - (DVD_MARGIN_Y * 2))
    x = INNER_X + DVD_MARGIN_X + reflect_step(frame, DVD_SPEED_X, travel_x)
    y = INNER_Y + DVD_MARGIN_Y + reflect_step(frame, DVD_SPEED_Y, travel_y)

    frame_img.alpha_composite(base_logo, (x, y))
    return add_signal_overlay(frame_img, frame, total_frames, rows=1, snow_points=8)


def build_sheet(base_logo: Image.Image, builder, frame_count: int, cols: int, rows: int) -> Image.Image:
    sheet_w = GLYPH_W * cols
    sheet_h = GLYPH_H * rows
    sheet = Image.new('RGBA', (sheet_w, sheet_h), (0, 0, 0, 0))
    for frame in range(frame_count):
        image = builder(base_logo, frame, frame_count)
        col = frame % cols
        row = frame // cols
        sheet.alpha_composite(image, (col * GLYPH_W, row * GLYPH_H))
    return sheet


def build_preview(sheet: Image.Image, output: Path) -> None:
    output.parent.mkdir(parents=True, exist_ok=True)
    preview = Image.new('RGBA', sheet.size, (8, 8, 8, 255))
    preview.alpha_composite(sheet)
    preview.save(output)


def save_outputs(filename: str, image: Image.Image) -> None:
    for directory in OUTPUT_DIRS:
        directory.mkdir(parents=True, exist_ok=True)
        image.save(directory / filename)


def main() -> None:
    if not SOURCE_LOGO.exists():
        raise FileNotFoundError(f'Missing source logo: {SOURCE_LOGO}')

    source_logo = Image.open(SOURCE_LOGO).convert('RGBA')
    glitch_logo = upscale_logo_clean(source_logo, (GLITCH_LOGO_W, GLITCH_LOGO_H))
    dvd_logo = source_logo.resize(
        (max(1, source_logo.width // 2), max(1, math.ceil(source_logo.height / 2))),
        Image.Resampling.NEAREST,
    )
    glitch_sheet = build_sheet(glitch_logo, build_glitch_frame, GLITCH_FRAME_COUNT, GLITCH_GRID_COLS, GLITCH_GRID_ROWS)
    dvd_sheet = build_sheet(dvd_logo, build_dvd_frame, DVD_FRAME_COUNT, DVD_GRID_COLS, DVD_GRID_ROWS)

    save_outputs(GLITCH_NAME, glitch_sheet)
    save_outputs(DVD_NAME, dvd_sheet)
    build_preview(glitch_sheet, PREVIEW_GLITCH)
    build_preview(dvd_sheet, PREVIEW_DVD)

    print(f'generated {GLITCH_NAME} and {DVD_NAME}')
    print(f'source {SOURCE_LOGO.relative_to(ROOT)}')
    print(f'previews {PREVIEW_GLITCH.relative_to(ROOT)} {PREVIEW_DVD.relative_to(ROOT)}')


if __name__ == '__main__':
    main()
