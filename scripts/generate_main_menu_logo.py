from __future__ import annotations

import json
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
ITEM_OUTPUT_DIRS = [
    ROOT / 'mods/lg2-0.1.0/src/main/resources/assets/lg2/textures/item/gui/main_logo',
    ROOT / 'polymer/source_assets/assets/lg2/textures/item/gui/main_logo',
]
PREVIEW_DIR = ROOT / '.tmp'
GLITCH_NAME = 'main_menu_logo_anim.png'
DVD_NAME = 'main_menu_logo_dvd_anim.png'
SPIN_NAME = 'main_menu_logo_spin_anim.png'
ITEM_GLITCH_NAME = 'glitch_anim.png'
ITEM_DVD_NAME = 'dvd_anim.png'
ITEM_SPIN_NAME = 'spin_anim.png'
ITEM_SCREEN_GLITCH_NAME = 'glitch_screen_anim.png'
ITEM_SCREEN_DVD_NAME = 'dvd_screen_anim.png'
ITEM_SCREEN_SPIN_NAME = 'spin_screen_anim.png'
PREVIEW_GLITCH = PREVIEW_DIR / 'main_menu_logo_preview_glitch.png'
PREVIEW_DVD = PREVIEW_DIR / 'main_menu_logo_preview_dvd.png'
PREVIEW_SPIN = PREVIEW_DIR / 'main_menu_logo_preview_spin.png'

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
SPIN_FRAME_COUNT = 48
SPIN_GRID_COLS = 8
SPIN_GRID_ROWS = 6
GLITCH_FRAME_TIME = 2
DVD_FRAME_TIME = 4
SPIN_FRAME_TIME = 2

GLITCH_LOGO_W = 48
GLITCH_LOGO_H = 32
DVD_MARGIN_X = 0
DVD_MARGIN_Y = 0
DVD_SPEED_X = 6
DVD_SPEED_Y = 7
SPIN_RHOMBUS_HALF_W = 6.0
SPIN_RHOMBUS_HALF_H = 4.4
SPIN_MODEL_DEPTH = 8.4
SPIN_MODEL_SCALE = 1.08
SPIN_CAMERA_DISTANCE = 96.0
SPIN_CENTER_X = SCREEN_X + SCREEN_W / 2 - 1.0
SPIN_CENTER_Y = SCREEN_Y + SCREEN_H / 2 - 0.5

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
SPIN_CELL_COLORS = {
    (0, 2): (0, 210, 137),
    (1, 2): (0, 210, 137),
    (2, 2): (0, 210, 137),
    (0, 1): (0, 210, 137),
    (1, 1): (0, 199, 241),
    (0, 0): (0, 210, 137),
}
SPIN_LIGHT_DIR = (-0.35, 0.55, 0.92)
SPIN_VIEW_DIR = (0.0, 0.0, 1.0)


def clamp(value: int, low: int, high: int) -> int:
    return max(low, min(high, value))


def normalize3(vector: tuple[float, float, float]) -> tuple[float, float, float]:
    x, y, z = vector
    length = math.sqrt((x * x) + (y * y) + (z * z))
    if length == 0:
        return 0.0, 0.0, 0.0
    return x / length, y / length, z / length


def sub3(a: tuple[float, float, float], b: tuple[float, float, float]) -> tuple[float, float, float]:
    return a[0] - b[0], a[1] - b[1], a[2] - b[2]


def dot3(a: tuple[float, float, float], b: tuple[float, float, float]) -> float:
    return (a[0] * b[0]) + (a[1] * b[1]) + (a[2] * b[2])


def cross3(a: tuple[float, float, float], b: tuple[float, float, float]) -> tuple[float, float, float]:
    return (
        (a[1] * b[2]) - (a[2] * b[1]),
        (a[2] * b[0]) - (a[0] * b[2]),
        (a[0] * b[1]) - (a[1] * b[0]),
    )


def scale_rgb(color: tuple[int, int, int], factor: float) -> tuple[int, int, int]:
    return tuple(clamp(int(round(channel * factor)), 0, 255) for channel in color)


def diamond_grid_to_xy(u: float, v: float) -> tuple[float, float]:
    return (
        (u - v) * SPIN_RHOMBUS_HALF_W,
        (u + v) * SPIN_RHOMBUS_HALF_H,
    )


def rotate_xy(point: tuple[float, float], angle: float) -> tuple[float, float]:
    x, y = point
    cos_a = math.cos(angle)
    sin_a = math.sin(angle)
    return (
        (x * cos_a) - (y * sin_a),
        (x * sin_a) + (y * cos_a),
    )


def spin_shape_center() -> tuple[float, float]:
    points: list[tuple[float, float]] = []
    for gx, gy in SPIN_CELL_COLORS:
        for u, v in ((gx, gy), (gx + 1, gy), (gx + 1, gy + 1), (gx, gy + 1)):
            points.append(rotate_xy(diamond_grid_to_xy(u, v), math.pi / 2))
    min_x = min(point[0] for point in points)
    max_x = max(point[0] for point in points)
    min_y = min(point[1] for point in points)
    max_y = max(point[1] for point in points)
    return (min_x + max_x) / 2.0, (min_y + max_y) / 2.0


SPIN_SHAPE_CENTER = spin_shape_center()


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


def downscale_pixel_art(image: Image.Image, divisor: int) -> Image.Image:
    if divisor <= 0:
        raise ValueError('divisor must be positive')

    padded_w = max(divisor, math.ceil(image.width / divisor) * divisor)
    padded_h = max(divisor, math.ceil(image.height / divisor) * divisor)
    if (padded_w, padded_h) == image.size:
        padded = image
    else:
        padded = Image.new('RGBA', (padded_w, padded_h), (0, 0, 0, 0))
        padded.alpha_composite(image, (0, 0))

    return padded.resize(
        (max(1, padded.width // divisor), max(1, padded.height // divisor)),
        Image.Resampling.NEAREST,
    )


def mirror_logo_horizontally(image: Image.Image, keep_side: str = 'left') -> Image.Image:
    if keep_side not in {'left', 'right'}:
        raise ValueError("keep_side must be 'left' or 'right'")

    mirrored = image.copy()
    width = mirrored.width
    half = width // 2

    for y in range(mirrored.height):
        for x in range(half):
            opposite_x = width - 1 - x
            source_x = x if keep_side == 'left' else opposite_x
            pixel = image.getpixel((source_x, y))
            mirrored.putpixel((x, y), pixel)
            mirrored.putpixel((opposite_x, y), pixel)

    return mirrored


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


def add_layer_glow(
    canvas: Image.Image,
    layer: Image.Image,
    amber_strength: float = 0.12,
    cyan_strength: float = 0.18,
    core_strength: float = 0.10,
) -> Image.Image:
    alpha = layer.getchannel('A')
    outer = dilate_alpha(alpha, [(-2, 0), (2, 0), (0, -2), (0, 2), (-1, -1), (1, 1), (-1, 1), (1, -1)])
    inner = dilate_alpha(alpha, [(-1, 0), (1, 0), (0, -1), (0, 1)])

    amber = Image.new('RGBA', canvas.size, (*AMBER_GLOW, 0))
    amber.putalpha(outer.point(lambda value: int(value * amber_strength)))
    cyan = Image.new('RGBA', canvas.size, (*CYAN_GLOW, 0))
    cyan.putalpha(outer.point(lambda value: int(value * cyan_strength)))
    core = Image.new('RGBA', canvas.size, (*CORE_GLOW, 0))
    core.putalpha(inner.point(lambda value: int(value * core_strength)))

    composed = Image.alpha_composite(canvas, amber)
    composed = Image.alpha_composite(composed, cyan)
    composed = Image.alpha_composite(composed, core)
    return Image.alpha_composite(composed, layer)


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


def rotate_point(
    point: tuple[float, float, float],
    yaw: float,
    pitch: float,
    roll: float,
) -> tuple[float, float, float]:
    x, y, z = point

    cos_yaw = math.cos(yaw)
    sin_yaw = math.sin(yaw)
    x, z = (x * cos_yaw) + (z * sin_yaw), (-x * sin_yaw) + (z * cos_yaw)

    cos_pitch = math.cos(pitch)
    sin_pitch = math.sin(pitch)
    y, z = (y * cos_pitch) - (z * sin_pitch), (y * sin_pitch) + (z * cos_pitch)

    cos_roll = math.cos(roll)
    sin_roll = math.sin(roll)
    x, y = (x * cos_roll) - (y * sin_roll), (x * sin_roll) + (y * cos_roll)
    return x, y, z


def project_point(point: tuple[float, float, float]) -> tuple[float, float]:
    x, y, z = point
    factor = SPIN_CAMERA_DISTANCE / max(8.0, SPIN_CAMERA_DISTANCE - z)
    return (
        SPIN_CENTER_X + (x * factor * SPIN_MODEL_SCALE),
        SPIN_CENTER_Y + (y * factor * SPIN_MODEL_SCALE),
    )


def cell_front_polygon(cell: tuple[int, int], z: float) -> list[tuple[float, float, float]]:
    gx, gy = cell
    points = []
    for u, v in ((gx, gy), (gx + 1, gy), (gx + 1, gy + 1), (gx, gy + 1)):
        x, y = rotate_xy(diamond_grid_to_xy(u, v), math.pi / 2)
        points.append((x - SPIN_SHAPE_CENTER[0], y - SPIN_SHAPE_CENTER[1], z))
    return points


def polygon_normal(points: list[tuple[float, float, float]]) -> tuple[float, float, float]:
    return cross3(sub3(points[1], points[0]), sub3(points[2], points[0]))


def orient_side_face(
    front_a: tuple[float, float, float],
    front_b: tuple[float, float, float],
    back_b: tuple[float, float, float],
    back_a: tuple[float, float, float],
) -> list[tuple[float, float, float]]:
    midpoint = (
        (front_a[0] + front_b[0]) / 2.0,
        (front_a[1] + front_b[1]) / 2.0,
        0.0,
    )
    outward_hint = normalize3(midpoint)
    quad = [front_a, front_b, back_b, back_a]
    normal = normalize3(polygon_normal(quad))
    if dot3(normal, outward_hint) < 0:
        return [front_b, front_a, back_a, back_b]
    return quad


def render_spin_model(frame: int, total_frames: int) -> Image.Image:
    layer = Image.new('RGBA', (CANVAS_W, CANVAS_H), (0, 0, 0, 0))
    draw = ImageDraw.Draw(layer, 'RGBA')

    phase = frame / total_frames
    yaw = phase * math.tau
    pitch = math.radians(14.0)
    roll = 0.0

    faces: list[tuple[float, list[tuple[float, float]], tuple[int, int, int, int], tuple[int, int, int, int], int]] = []
    light_dir = normalize3(SPIN_LIGHT_DIR)
    front_boundaries: list[list[tuple[float, float, float]]] = []
    back_boundaries: list[list[tuple[float, float, float]]] = []

    for cell, base_color in SPIN_CELL_COLORS.items():
        front = cell_front_polygon(cell, SPIN_MODEL_DEPTH / 2)
        back = cell_front_polygon(cell, -SPIN_MODEL_DEPTH / 2)
        back_reversed = [back[0], back[3], back[2], back[1]]
        side_faces = {
            'top': orient_side_face(front[0], front[1], back[1], back[0]),
            'right': orient_side_face(front[1], front[2], back[2], back[1]),
            'bottom': orient_side_face(front[2], front[3], back[3], back[2]),
            'left': orient_side_face(front[3], front[0], back[0], back[3]),
        }
        gx, gy = cell
        neighbors = {
            'top': (gx, gy - 1),
            'right': (gx + 1, gy),
            'bottom': (gx, gy + 1),
            'left': (gx - 1, gy),
        }
        exposed_side_faces = [face for side, face in side_faces.items() if neighbors[side] not in SPIN_CELL_COLORS]

        for points, base_factor, outline_alpha, priority in [
            (back_reversed, 0.40, 200, 0),
            *[(side, 0.52, 205, 1) for side in exposed_side_faces],
            (front, 1.0, 255, 2),
        ]:
            rotated = [rotate_point(point, yaw, pitch, roll) for point in points]
            normal = normalize3(polygon_normal(rotated))
            if dot3(normal, SPIN_VIEW_DIR) <= 0.05:
                continue

            shade = 0.50 + (max(0.0, dot3(normal, light_dir)) * 0.72)
            fill_rgb = scale_rgb(base_color, base_factor * shade)
            outline_rgb = scale_rgb((0, 0, 0), 1.0)
            projected = [project_point(point) for point in rotated]
            avg_z = sum(point[2] for point in rotated) / len(rotated)
            faces.append((
                avg_z,
                projected,
                (*fill_rgb, 255),
                (*outline_rgb, outline_alpha),
                priority,
            ))

        rotated_front = [rotate_point(point, yaw, pitch, roll) for point in front]
        front_normal = normalize3(polygon_normal(rotated_front))
        if dot3(front_normal, SPIN_VIEW_DIR) > 0.05:
            edges = {
                'top': [front[0], front[1]],
                'right': [front[1], front[2]],
                'bottom': [front[2], front[3]],
                'left': [front[3], front[0]],
            }
            for side, neighbor in neighbors.items():
                if neighbor not in SPIN_CELL_COLORS or SPIN_CELL_COLORS[neighbor] != base_color:
                    front_boundaries.append(edges[side])

        rotated_back = [rotate_point(point, yaw, pitch, roll) for point in back_reversed]
        back_normal = normalize3(polygon_normal(rotated_back))
        if dot3(back_normal, SPIN_VIEW_DIR) > 0.05:
            edges = {
                'top': [back[0], back[1]],
                'right': [back[1], back[2]],
                'bottom': [back[2], back[3]],
                'left': [back[3], back[0]],
            }
            for side, neighbor in neighbors.items():
                if neighbor not in SPIN_CELL_COLORS or SPIN_CELL_COLORS[neighbor] != base_color:
                    back_boundaries.append(edges[side])

    faces.sort(key=lambda face: (face[0], face[4]))

    for _, projected, fill, outline, _ in faces:
        draw.polygon(projected, fill=fill)

    for edge in front_boundaries:
        rotated_edge = [rotate_point(point, yaw, pitch, roll) for point in edge]
        projected_edge = [project_point(point) for point in rotated_edge]
        draw.line(projected_edge, fill=(0, 0, 0, 235), width=1)

    for edge in back_boundaries:
        rotated_edge = [rotate_point(point, yaw, pitch, roll) for point in edge]
        projected_edge = [project_point(point) for point in rotated_edge]
        draw.line(projected_edge, fill=(0, 0, 0, 235), width=1)

    shadow = Image.new('RGBA', layer.size, (0, 0, 0, 0))
    shadow_alpha = layer.getchannel('A').point(lambda value: int(value * 0.22))
    shadow.putalpha(shadow_alpha)
    shadow = ImageChops.offset(shadow, 0, 3)
    layer = Image.alpha_composite(shadow, layer)

    outline_alpha = dilate_alpha(layer.getchannel('A'), [(-1, 0), (1, 0), (0, -1), (0, 1)])
    outline_alpha = ImageChops.subtract(outline_alpha, layer.getchannel('A'))
    outline = Image.new('RGBA', layer.size, (0, 0, 0, 0))
    outline.putalpha(outline_alpha.point(lambda value: int(value * 0.95)))
    return Image.alpha_composite(outline, layer)


def build_spin_frame(_base_logo: Image.Image, frame: int, total_frames: int) -> Image.Image:
    frame_img = make_screen_base(frame, total_frames)
    model_layer = render_spin_model(frame, total_frames)
    frame_img = add_layer_glow(frame_img, model_layer, amber_strength=0.08, cyan_strength=0.14, core_strength=0.07)
    return add_signal_overlay(frame_img, frame, total_frames, rows=1, snow_points=9)


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


def sheet_to_vertical_strip(
    sheet: Image.Image,
    frame_count: int,
    cols: int,
    frame_width: int = GLYPH_W,
    frame_height: int = GLYPH_H,
    crop_box: tuple[int, int, int, int] | None = None,
) -> Image.Image:
    strip = Image.new('RGBA', (frame_width, frame_height * frame_count), (0, 0, 0, 0))
    for frame in range(frame_count):
        col = frame % cols
        row = frame // cols
        frame_img = sheet.crop((
            col * GLYPH_W,
            row * GLYPH_H,
            (col + 1) * GLYPH_W,
            (row + 1) * GLYPH_H,
        ))
        if crop_box is not None:
            frame_img = frame_img.crop(crop_box)
        strip.alpha_composite(frame_img, (0, frame * frame_height))
    return strip


def save_animation_metadata(path: Path, frame_time: int, frame_width: int, frame_height: int) -> None:
    metadata = {
        'animation': {
            'interpolate': False,
            'frametime': frame_time,
            'width': frame_width,
            'height': frame_height,
        }
    }
    path.with_suffix(path.suffix + '.mcmeta').write_text(
        json.dumps(metadata, ensure_ascii=False, indent=2) + '\n',
        encoding='utf-8',
    )


def save_item_animation_outputs(
    filename: str,
    image: Image.Image,
    frame_time: int,
    frame_width: int = GLYPH_W,
    frame_height: int = GLYPH_H,
) -> None:
    for directory in ITEM_OUTPUT_DIRS:
        directory.mkdir(parents=True, exist_ok=True)
        target = directory / filename
        image.save(target)
        save_animation_metadata(target, frame_time, frame_width, frame_height)


def main() -> None:
    if not SOURCE_LOGO.exists():
        raise FileNotFoundError(f'Missing source logo: {SOURCE_LOGO}')

    source_logo = Image.open(SOURCE_LOGO).convert('RGBA')
    glitch_logo = upscale_logo_clean(source_logo, (GLITCH_LOGO_W, GLITCH_LOGO_H))
    # Keep the tiny DVD logo perfectly mirrored after the final NEAREST downscale.
    # Doing this before resize is not enough, because center sampling can still skew
    # the 20x14 result by one pixel on one side.
    dvd_logo = mirror_logo_horizontally(
        downscale_pixel_art(quantize_to_palette(source_logo, LOGO_PALETTE), 2),
        keep_side='left',
    )
    glitch_sheet = build_sheet(glitch_logo, build_glitch_frame, GLITCH_FRAME_COUNT, GLITCH_GRID_COLS, GLITCH_GRID_ROWS)
    dvd_sheet = build_sheet(dvd_logo, build_dvd_frame, DVD_FRAME_COUNT, DVD_GRID_COLS, DVD_GRID_ROWS)
    spin_sheet = build_sheet(source_logo, build_spin_frame, SPIN_FRAME_COUNT, SPIN_GRID_COLS, SPIN_GRID_ROWS)

    save_outputs(GLITCH_NAME, glitch_sheet)
    save_outputs(DVD_NAME, dvd_sheet)
    save_outputs(SPIN_NAME, spin_sheet)
    save_item_animation_outputs(
        ITEM_GLITCH_NAME,
        sheet_to_vertical_strip(glitch_sheet, GLITCH_FRAME_COUNT, GLITCH_GRID_COLS),
        GLITCH_FRAME_TIME,
    )
    save_item_animation_outputs(
        ITEM_DVD_NAME,
        sheet_to_vertical_strip(dvd_sheet, DVD_FRAME_COUNT, DVD_GRID_COLS),
        DVD_FRAME_TIME,
    )
    save_item_animation_outputs(
        ITEM_SPIN_NAME,
        sheet_to_vertical_strip(spin_sheet, SPIN_FRAME_COUNT, SPIN_GRID_COLS),
        SPIN_FRAME_TIME,
    )
    screen_crop_box = (SCREEN_X, SCREEN_Y, SCREEN_X + SCREEN_W, SCREEN_Y + SCREEN_H)
    save_item_animation_outputs(
        ITEM_SCREEN_GLITCH_NAME,
        sheet_to_vertical_strip(
            glitch_sheet,
            GLITCH_FRAME_COUNT,
            GLITCH_GRID_COLS,
            SCREEN_W,
            SCREEN_H,
            screen_crop_box,
        ),
        GLITCH_FRAME_TIME,
        SCREEN_W,
        SCREEN_H,
    )
    save_item_animation_outputs(
        ITEM_SCREEN_DVD_NAME,
        sheet_to_vertical_strip(
            dvd_sheet,
            DVD_FRAME_COUNT,
            DVD_GRID_COLS,
            SCREEN_W,
            SCREEN_H,
            screen_crop_box,
        ),
        DVD_FRAME_TIME,
        SCREEN_W,
        SCREEN_H,
    )
    save_item_animation_outputs(
        ITEM_SCREEN_SPIN_NAME,
        sheet_to_vertical_strip(
            spin_sheet,
            SPIN_FRAME_COUNT,
            SPIN_GRID_COLS,
            SCREEN_W,
            SCREEN_H,
            screen_crop_box,
        ),
        SPIN_FRAME_TIME,
        SCREEN_W,
        SCREEN_H,
    )
    build_preview(glitch_sheet, PREVIEW_GLITCH)
    build_preview(dvd_sheet, PREVIEW_DVD)
    build_preview(spin_sheet, PREVIEW_SPIN)

    print(f'generated {GLITCH_NAME}, {DVD_NAME} and {SPIN_NAME}')
    print(f'source {SOURCE_LOGO.relative_to(ROOT)}')
    print(f'previews {PREVIEW_GLITCH.relative_to(ROOT)} {PREVIEW_DVD.relative_to(ROOT)} {PREVIEW_SPIN.relative_to(ROOT)}')


if __name__ == '__main__':
    main()
