from __future__ import annotations

import json
import math
import shutil
import subprocess
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageEnhance

VIDEO_SOURCE = Path('/home/mart/Videos/recording_2026-03-18_21.13.17.mp4')
ROOT = Path('/home/mart/Desktop/lostglade')
OUTPUT_DIRS = [
    ROOT / 'mods/lg2-0.1.0/src/main/resources/assets/minecraft/textures/font',
    ROOT / 'polymer/source_assets/assets/minecraft/textures/font',
]
ITEM_OUTPUT_DIRS = [
    ROOT / 'mods/lg2-0.1.0/src/main/resources/assets/lg2/textures/item/gui/main_logo',
    ROOT / 'polymer/source_assets/assets/lg2/textures/item/gui/main_logo',
]
TEMP_DIR = ROOT / '.tmp' / 'backrooms_archive_frames'
PREVIEW_PATH = ROOT / '.tmp' / 'main_menu_logo_archive_preview.png'
OUTPUT_NAME = 'main_menu_logo_archive_anim.png'
ITEM_OUTPUT_NAME = 'archive_anim.png'

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
FRAME_COUNT = 24
GRID_COLS = 6
GRID_ROWS = 4
SEGMENT_START = 108.0
SEGMENT_DURATION = 4.0
FPS = FRAME_COUNT / SEGMENT_DURATION
FRAME_TIME = 2

SCREEN_BG = (0x18, 0x15, 0x14)
SCREEN_FRAME = (0x29, 0x2F, 0x2F)
STATIC_WHITE = (236, 232, 220)
GLOW = (255, 232, 182, 10)


def inside_screen_mask(x: int, y: int) -> bool:
    sx0 = SCREEN_X
    sy0 = SCREEN_Y
    sx1 = SCREEN_X + SCREEN_W - 1
    sy1 = SCREEN_Y + SCREEN_H - 1
    if x < sx0 or x > sx1 or y < sy0 or y > sy1:
        return False
    return (x, y) not in {(sx0, sy0), (sx1, sy0), (sx0, sy1), (sx1, sy1)}


def make_screen_base(frame: int) -> Image.Image:
    img = Image.new('RGBA', (CANVAS_W, CANVAS_H), (0, 0, 0, 0))
    px = img.load()
    phase = frame / FRAME_COUNT * math.tau

    for y in range(SCREEN_Y, SCREEN_Y + SCREEN_H):
        for x in range(SCREEN_X, SCREEN_X + SCREEN_W):
            if not inside_screen_mask(x, y):
                continue
            if x in (SCREEN_X, SCREEN_X + SCREEN_W - 1) or y in (SCREEN_Y, SCREEN_Y + SCREEN_H - 1):
                px[x, y] = (*SCREEN_FRAME, 255)
                continue
            r, g, b = SCREEN_BG
            band = math.sin((y * 0.55) + phase * 1.7)
            delta = int(round(band * 2.0))
            px[x, y] = (max(0, min(255, r + delta)), max(0, min(255, g + delta)), max(0, min(255, b + delta)), 255)
    return img


def cover_resize(image: Image.Image, width: int, height: int) -> Image.Image:
    scale = max(width / image.width, height / image.height)
    resized = image.resize((max(1, round(image.width * scale)), max(1, round(image.height * scale))), Image.Resampling.BICUBIC)
    left = max(0, (resized.width - width) // 2)
    top = max(0, (resized.height - height) // 2)
    return resized.crop((left, top, left + width, top + height))


def stylize_frame(frame: Image.Image, index: int) -> Image.Image:
    frame = cover_resize(frame.convert('RGB'), INNER_W, INNER_H)
    frame = ImageEnhance.Contrast(frame).enhance(1.12)
    frame = ImageEnhance.Brightness(frame).enhance(0.92)
    frame = ImageEnhance.Color(frame).enhance(0.92)
    rgba = frame.convert('RGBA')
    draw = ImageDraw.Draw(rgba, 'RGBA')

    # Warm archival tint, but keep the original Backrooms palette readable.
    warm = Image.new('RGBA', rgba.size, (232, 216, 168, 22))
    rgba = Image.alpha_composite(rgba, warm)
    draw = ImageDraw.Draw(rgba, 'RGBA')

    # Scanlines and CRT roll.
    for y in range(0, INNER_H, 2):
        draw.line((0, y, INNER_W - 1, y), fill=(0, 0, 0, 22), width=1)
    sweep_y = (index * 5) % (INNER_H + 10) - 5
    for y in range(max(0, sweep_y - 1), min(INNER_H, sweep_y + 2)):
        alpha = 16 if y == sweep_y else 8
        draw.line((0, y, INNER_W - 1, y), fill=(*STATIC_WHITE, alpha), width=1)

    # Light horizontal interference bands.
    for band_index in range(1):
        band_y = (index * (7 + band_index) + band_index * 13) % max(1, INNER_H - 3)
        band_h = 1 + ((index + band_index) % 3)
        offset = ((index * (band_index + 3)) % 3) - 1
        band = rgba.crop((0, band_y, INNER_W, min(INNER_H, band_y + band_h)))
        rgba.paste((0, 0, 0, 0), (0, band_y, INNER_W, min(INNER_H, band_y + band_h)))
        rgba.alpha_composite(band, (offset, band_y))
        draw.rectangle((0, band_y, INNER_W - 1, min(INNER_H - 1, band_y + band_h - 1)), outline=(0, 0, 0, 8))

    # Random-looking static points, deterministic from frame index.
    for seed in range(16):
        x = (index * 19 + seed * 17) % INNER_W
        y = (index * 11 + seed * 23) % INNER_H
        if (seed + index) % 5 == 0:
            rgba.putpixel((x, y), (*STATIC_WHITE, 96))
        elif (seed + index) % 3 == 0:
            rgba.putpixel((x, y), (18, 18, 18, 52))

    # Vignette and edge burn.
    vignette = Image.new('RGBA', rgba.size, (0, 0, 0, 0))
    vdraw = ImageDraw.Draw(vignette, 'RGBA')
    for inset in range(8):
        alpha = 12 + inset * 4
        vdraw.rectangle((inset, inset, INNER_W - 1 - inset, INNER_H - 1 - inset), outline=(0, 0, 0, alpha))
    rgba = Image.alpha_composite(rgba, vignette)

    # Occasional full-screen signal tear.
    if index in {8, 19}:
        tear = Image.new('RGBA', rgba.size, (0, 0, 0, 0))
        tdraw = ImageDraw.Draw(tear, 'RGBA')
        tear_y = (index * 9) % INNER_H
        tdraw.rectangle((0, max(0, tear_y - 1), INNER_W - 1, min(INNER_H - 1, tear_y + 1)), fill=(*STATIC_WHITE, 56))
        tdraw.rectangle((0, max(0, tear_y + 2), INNER_W - 1, min(INNER_H - 1, tear_y + 4)), fill=(0, 0, 0, 40))
        rgba = Image.alpha_composite(rgba, tear)

    return rgba


def compose_frame(video_frame: Image.Image, index: int) -> Image.Image:
    canvas = make_screen_base(index)
    content = stylize_frame(video_frame, index)
    glow = Image.new('RGBA', (INNER_W, INNER_H), GLOW)
    content = Image.alpha_composite(content, glow)
    canvas.alpha_composite(content, (INNER_X, INNER_Y))
    return canvas


def extract_frames() -> list[Path]:
    if not VIDEO_SOURCE.exists():
        raise FileNotFoundError(f'Missing video source: {VIDEO_SOURCE}')
    if TEMP_DIR.exists():
        shutil.rmtree(TEMP_DIR)
    TEMP_DIR.mkdir(parents=True, exist_ok=True)
    pattern = TEMP_DIR / 'frame_%03d.png'
    subprocess.run([
        'ffmpeg', '-y', '-v', 'error', '-ss', f'{SEGMENT_START:.2f}', '-t', f'{SEGMENT_DURATION:.2f}',
        '-i', str(VIDEO_SOURCE), '-vf', f'fps={FPS:.6f}', '-frames:v', str(FRAME_COUNT), str(pattern)
    ], check=True)
    frames = sorted(TEMP_DIR.glob('frame_*.png'))
    if len(frames) != FRAME_COUNT:
        raise RuntimeError(f'Expected {FRAME_COUNT} frames, got {len(frames)}')
    return frames


def save_sheet(sheet: Image.Image) -> None:
    for directory in OUTPUT_DIRS:
        directory.mkdir(parents=True, exist_ok=True)
        sheet.save(directory / OUTPUT_NAME)


def sheet_to_vertical_strip(sheet: Image.Image) -> Image.Image:
    strip = Image.new('RGBA', (CANVAS_W, CANVAS_H * FRAME_COUNT), (0, 0, 0, 0))
    for index in range(FRAME_COUNT):
        col = index % GRID_COLS
        row = index // GRID_COLS
        frame = sheet.crop((
            col * CANVAS_W,
            row * CANVAS_H,
            (col + 1) * CANVAS_W,
            (row + 1) * CANVAS_H,
        ))
        strip.alpha_composite(frame, (0, index * CANVAS_H))
    return strip


def save_animation_metadata(path: Path) -> None:
    metadata = {
        'animation': {
            'interpolate': False,
            'frametime': FRAME_TIME,
            'width': CANVAS_W,
            'height': CANVAS_H,
        }
    }
    path.with_suffix(path.suffix + '.mcmeta').write_text(
        json.dumps(metadata, ensure_ascii=False, indent=2) + '\n',
        encoding='utf-8',
    )


def save_item_animation(strip: Image.Image) -> None:
    for directory in ITEM_OUTPUT_DIRS:
        directory.mkdir(parents=True, exist_ok=True)
        target = directory / ITEM_OUTPUT_NAME
        strip.save(target)
        save_animation_metadata(target)


def main() -> None:
    frame_paths = extract_frames()
    images = [Image.open(path).convert('RGBA') for path in frame_paths]

    sheet = Image.new('RGBA', (CANVAS_W * GRID_COLS, CANVAS_H * GRID_ROWS), (0, 0, 0, 0))
    for index, image in enumerate(images):
        composed = compose_frame(image, index)
        x = (index % GRID_COLS) * CANVAS_W
        y = (index // GRID_COLS) * CANVAS_H
        sheet.alpha_composite(composed, (x, y))

    save_sheet(sheet)
    save_item_animation(sheet_to_vertical_strip(sheet))
    PREVIEW_PATH.parent.mkdir(parents=True, exist_ok=True)
    preview = Image.new('RGBA', sheet.size, (8, 8, 8, 255))
    preview.alpha_composite(sheet)
    preview.save(PREVIEW_PATH)
    print(f'generated {OUTPUT_NAME}')
    print(f'source {VIDEO_SOURCE}')
    print(f'preview {PREVIEW_PATH}')


if __name__ == '__main__':
    main()
