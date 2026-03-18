from __future__ import annotations

import math
import random
from pathlib import Path
from typing import Iterable

from PIL import Image, ImageChops, ImageDraw

ROOT = Path('/home/mart/Desktop/lostglade')
SOURCE_LOGO = Path('/home/mart/Pictures/logos/lostglade_px.png')
OUTPUT_SHEET = ROOT / 'mods/lg2-0.1.0/src/main/resources/assets/minecraft/textures/font/main_menu_logo_anim.png'
PREVIEW = Path('/tmp/main_menu_logo_preview.png')

CANVAS_W = 176
CANVAS_H = 204
SCREEN_X = 19
SCREEN_Y = 82
SCREEN_W = 101
SCREEN_H = 51
INNER_X = SCREEN_X + 1
INNER_Y = SCREEN_Y + 1
INNER_W = SCREEN_W - 2
INNER_H = SCREEN_H - 2
FRAME_COUNT = 16
GRID_COLS = 4
GRID_ROWS = 4
GLYPH_W = CANVAS_W
GLYPH_H = CANVAS_H
SHEET_W = GLYPH_W * GRID_COLS
SHEET_H = GLYPH_H * GRID_ROWS

# Pixel-art friendly target that still fills the screen nicely.
LOGO_W = 54
LOGO_H = 36
SCREEN_BG = (0x18, 0x15, 0x14)
SCREEN_FRAME = (0x29, 0x2F, 0x2F)


def lerp(a: float, b: float, t: float) -> float:
    return a + (b - a) * t


def clamp(value: int, low: int, high: int) -> int:
    return max(low, min(high, value))


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


def make_gradient_background(frame: int) -> Image.Image:
    img = Image.new('RGBA', (CANVAS_W, CANVAS_H), (0, 0, 0, 0))
    px = img.load()
    sweep = (frame / FRAME_COUNT) * (INNER_H + 14) - 7
    phase = frame / FRAME_COUNT * math.tau

    for y in range(SCREEN_Y, SCREEN_Y + SCREEN_H):
        for x in range(SCREEN_X, SCREEN_X + SCREEN_W):
            if not inside_screen_mask(x, y):
                continue
            border = x in (SCREEN_X, SCREEN_X + SCREEN_W - 1) or y in (SCREEN_Y, SCREEN_Y + SCREEN_H - 1)
            if border:
                px[x, y] = (*SCREEN_FRAME, 255)
                continue

            r, g, b = SCREEN_BG

            # Very light scanline movement like unstable signal.
            if ((y + frame) % 4) in (0, 1):
                r -= 1
                g -= 1
                b -= 1

            # Soft horizontal sweep without leaving the original palette family.
            distance = abs((y - INNER_Y) - sweep)
            if distance < 3.5:
                glow = 1.0 - (distance / 3.5)
                r += int(7 * glow)
                g += int(6 * glow)
                b += int(6 * glow)

            # Weak signal shimmer.
            signal = math.sin((x * 0.19) + phase * 1.1) + math.sin((y * 0.37) + phase * 0.8)
            signal += math.sin(((x + y) * 0.11) - phase * 0.6)
            signal = signal / 3.0
            signal_delta = int(round(signal * 2.0))
            r += signal_delta
            g += signal_delta
            b += signal_delta

            px[x, y] = (clamp(r, 0, 255), clamp(g, 0, 255), clamp(b, 0, 255), 255)

    return img


def dilate_alpha(alpha: Image.Image, offsets: Iterable[tuple[int, int]]) -> Image.Image:
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


def bend_logo(logo: Image.Image, phase: float) -> Image.Image:
    out = Image.new('RGBA', logo.size, (0, 0, 0, 0))
    for y in range(logo.height):
        wave = math.sin((y / max(1, logo.height - 1)) * math.tau * 0.9 + phase)
        drift = math.sin((y / max(1, logo.height - 1)) * math.tau * 2.1 + phase * 1.2)
        shift = int(round(wave * 0.8 + drift * 0.35))
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


def make_logo_frame(base_logo: Image.Image, frame: int) -> Image.Image:
    phase = frame / FRAME_COUNT * math.tau
    logo = bend_logo(base_logo, phase)
    logo = glitch_logo(logo, frame)

    canvas = Image.new('RGBA', (CANVAS_W, CANVAS_H), (0, 0, 0, 0))
    cx = SCREEN_X + (SCREEN_W - LOGO_W) // 2
    cy = SCREEN_Y + (SCREEN_H - LOGO_H) // 2 + 1
    bob_x = 0
    bob_y = 0
    x = cx + bob_x
    y = cy + bob_y

    alpha = logo.getchannel('A')
    outer_glow = dilate_alpha(alpha, [(-2, 0), (2, 0), (0, -2), (0, 2), (-1, -1), (1, 1), (-1, 1), (1, -1)])
    inner_glow = dilate_alpha(alpha, [(-1, 0), (1, 0), (0, -1), (0, 1)])

    amber = Image.new('RGBA', logo.size, (255, 154, 44, 0))
    amber.putalpha(outer_glow.point(lambda v: int(v * 0.18)))
    cyan = Image.new('RGBA', logo.size, (68, 255, 234, 0))
    cyan.putalpha(outer_glow.point(lambda v: int(v * 0.35)))
    core_glow = Image.new('RGBA', logo.size, (110, 255, 240, 0))
    core_glow.putalpha(inner_glow.point(lambda v: int(v * 0.22)))

    # RGB split ghosts on stronger glitch frames.
    ghost_shift = 1 if frame in {5, 13} else 0
    if ghost_shift:
        red_ghost = Image.new('RGBA', logo.size, (255, 110, 90, 0))
        red_ghost.putalpha(alpha.point(lambda v: int(v * 0.12)))
        blue_ghost = Image.new('RGBA', logo.size, (70, 220, 255, 0))
        blue_ghost.putalpha(alpha.point(lambda v: int(v * 0.14)))
        canvas.alpha_composite(red_ghost, (x - ghost_shift, y))
        canvas.alpha_composite(blue_ghost, (x + ghost_shift, y))

    canvas.alpha_composite(amber, (x + 1, y + 1))
    canvas.alpha_composite(cyan, (x, y))
    canvas.alpha_composite(core_glow, (x, y))
    canvas.alpha_composite(logo, (x, y))

    return canvas


def add_screen_effects(frame_img: Image.Image, frame: int) -> Image.Image:
    img = frame_img.copy()
    overlay = Image.new('RGBA', img.size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(overlay, 'RGBA')
    rng = random.Random(9000 + frame)

    # Thin phosphor bars.
    for idx in range(2):
        bar_y = INNER_Y + ((frame * 4 + idx * 15) % max(1, INNER_H - 2))
        color = (255, 176, 64, 13) if idx == 0 else (90, 255, 235, 10)
        draw.line((INNER_X + 2, bar_y, INNER_X + INNER_W - 3, bar_y), fill=color, width=1)

    # Random tiny static.
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

    # Glass reflection.
    reflection_y = INNER_Y + 5 + int((math.sin(frame / FRAME_COUNT * math.tau) + 1) * 2)
    draw.line((INNER_X + 10, reflection_y, INNER_X + INNER_W - 14, reflection_y + 6), fill=(92, 94, 92, 14), width=1)
    return Image.alpha_composite(img, overlay)


def build_frame(base_logo: Image.Image, frame: int) -> Image.Image:
    frame_img = make_gradient_background(frame)
    frame_img.alpha_composite(make_logo_frame(base_logo, frame))
    frame_img = add_screen_effects(frame_img, frame)
    return frame_img


def main() -> None:
    OUTPUT_SHEET.parent.mkdir(parents=True, exist_ok=True)
    logo = Image.open(SOURCE_LOGO).convert('RGBA').resize((LOGO_W, LOGO_H), Image.Resampling.NEAREST)

    sheet = Image.new('RGBA', (SHEET_W, SHEET_H), (0, 0, 0, 0))
    preview = Image.new('RGBA', (SHEET_W, SHEET_H), (8, 8, 8, 255))

    for frame in range(FRAME_COUNT):
        image = build_frame(logo, frame)
        col = frame % GRID_COLS
        row = frame // GRID_COLS
        pos = (col * GLYPH_W, row * GLYPH_H)
        sheet.alpha_composite(image, pos)
        preview.alpha_composite(image, pos)

    sheet.save(OUTPUT_SHEET)
    preview.save(PREVIEW)
    print(f'generated {OUTPUT_SHEET}')
    print(f'preview {PREVIEW}')


if __name__ == '__main__':
    main()
