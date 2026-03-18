#!/usr/bin/env python3
from __future__ import annotations

import hashlib
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
FONT_DIR = ROOT / "mods/lg2-0.1.0/src/main/resources/assets/minecraft/textures/font"
CANVAS_SIZE = (176, 204)

SOURCE_FILES = {
    "empty": FONT_DIR / "eras_bar_empty.png",
    "unlocked": FONT_DIR / "eras_bar_unlocked.png",
    "filled": FONT_DIR / "eras_bar_filled.png",
}

OUTPUT_AVAILABLE = FONT_DIR / "eras_available_{stage}.png"
OUTPUT_PROGRESS = FONT_DIR / "eras_progress_{frame:02d}.png"

MARKER_COLORS = {
    (255, 59, 48),
    (52, 199, 89),
    (255, 255, 255),
}

REAL_ENDS = [-1, -1, 41, 77, 113, 149]
POTENTIAL_ENDS = [-1, 41, 77, 113, 149, 149]

FRAMES_PER_STAGE = 7
TOTAL_STAGES = 5
TOTAL_FRAMES = FRAMES_PER_STAGE * TOTAL_STAGES


def load_layer(path: Path) -> Image.Image:
    image = Image.open(path).convert("RGBA")
    pixels = image.load()
    for y in range(image.height):
        for x in range(image.width):
            r, g, b, a = pixels[x, y]
            if a and (r, g, b) in MARKER_COLORS:
                pixels[x, y] = (0, 0, 0, 0)
    return image


def visible_columns(*images: Image.Image) -> list[int]:
    visible: set[int] = set()
    for image in images:
        alpha = image.getchannel("A")
        for x in range(image.width):
            for y in range(image.height):
                if alpha.getpixel((x, y)):
                    visible.add(x)
                    break
    return sorted(visible)


def make_mask(end_x: int) -> Image.Image:
    mask = Image.new("L", CANVAS_SIZE, 0)
    if end_x < 0:
        return mask
    pixels = mask.load()
    end_x = min(CANVAS_SIZE[0] - 1, end_x)
    for y in range(mask.height):
        for x in range(end_x + 1):
            pixels[x, y] = 255
    return mask


def compose_state(
    empty: Image.Image,
    unlocked: Image.Image,
    filled: Image.Image,
    filled_end: int,
    potential_end: int,
) -> Image.Image:
    image = empty.copy()
    if potential_end >= 0:
        image = Image.composite(unlocked, image, make_mask(potential_end))
    if filled_end >= 0:
        image = Image.composite(filled, image, make_mask(filled_end))
    return image


def ease_out_quart(progress: float) -> float:
    progress = max(0.0, min(1.0, progress))
    return 1.0 - pow(1.0 - progress, 4)


def ease_out_cubic(progress: float) -> float:
    progress = max(0.0, min(1.0, progress))
    return 1.0 - pow(1.0 - progress, 3)


def ease_in_out_cubic(progress: float) -> float:
    progress = max(0.0, min(1.0, progress))
    if progress < 0.5:
        return 4.0 * progress * progress * progress
    return 1.0 - pow(-2.0 * progress + 2.0, 3) / 2.0


def delayed(progress: float, delay: float, easing) -> float:
    progress = max(0.0, min(1.0, progress))
    if progress <= delay:
        return 0.0
    if delay >= 1.0:
        return 1.0
    return easing((progress - delay) / (1.0 - delay))


def reveal_path(start: int, end: int, columns: list[int]) -> list[int]:
    if start == end:
        return [start]
    if start < end:
        return [start] + [column for column in columns if start < column <= end]
    return [start] + [column for column in reversed(columns) if end <= column < start]


def path_value(path: list[int], progress: float) -> int:
    if not path:
        return -1
    if len(path) == 1:
        return path[0]
    max_index = len(path) - 1
    progress = max(0.0, min(1.0, progress))
    index = round(progress * max_index)
    index = max(0, min(max_index, index))
    return path[index]


def frame_signature(image: Image.Image) -> str:
    return hashlib.sha256(image.tobytes()).hexdigest()


def select_evenly(items: list, count: int) -> list:
    if len(items) <= count:
        return items[:]
    if count <= 1:
        return [items[-1]]

    max_index = len(items) - 1
    indices = [round(index * max_index / (count - 1)) for index in range(count)]
    indices[0] = 0
    indices[-1] = max_index

    for index in range(1, count):
        minimum = indices[index - 1] + 1
        remaining_slots = count - 1 - index
        maximum = max_index - remaining_slots
        indices[index] = max(minimum, min(indices[index], maximum))

    return [items[index] for index in indices]


def build_unique_stage_frames(
    empty: Image.Image,
    unlocked: Image.Image,
    filled: Image.Image,
    real_path: list[int],
    potential_path: list[int],
    real_progress,
    potential_progress,
) -> list[Image.Image]:
    candidate_count = max(len(real_path), len(potential_path), FRAMES_PER_STAGE) * 8
    unique_frames: list[Image.Image] = []
    previous_signature = None

    for step in range(candidate_count):
        progress = 1.0 if candidate_count <= 1 else step / (candidate_count - 1)
        frame = compose_state(
            empty,
            unlocked,
            filled,
            path_value(real_path, real_progress(progress)),
            path_value(potential_path, potential_progress(progress)),
        )
        signature = frame_signature(frame)
        if signature == previous_signature:
            continue
        unique_frames.append(frame)
        previous_signature = signature

    if len(unique_frames) > FRAMES_PER_STAGE:
        unique_frames = unique_frames[1:]

    return select_evenly(unique_frames, FRAMES_PER_STAGE)


def generate_static_states(empty: Image.Image, unlocked: Image.Image, filled: Image.Image) -> None:
    for purchased in range(TOTAL_STAGES + 1):
        image = compose_state(
            empty,
            unlocked,
            filled,
            REAL_ENDS[purchased],
            POTENTIAL_ENDS[purchased],
        )
        image.save(OUTPUT_AVAILABLE.with_name(OUTPUT_AVAILABLE.name.format(stage=purchased)))


def generate_transition_frames(empty: Image.Image, unlocked: Image.Image, filled: Image.Image) -> None:
    reveal_columns = visible_columns(unlocked, filled)
    for stage in range(1, TOTAL_STAGES + 1):
        previous_real = REAL_ENDS[stage - 1]
        previous_potential = POTENTIAL_ENDS[stage - 1]
        next_real = REAL_ENDS[stage]
        next_potential = POTENTIAL_ENDS[stage]

        real_path = reveal_path(previous_real, next_real, reveal_columns)
        potential_path = reveal_path(previous_potential, next_potential, reveal_columns)
        real_moves = len(real_path) > 1
        potential_moves = len(potential_path) > 1

        if real_moves and potential_moves:
            real_progress = ease_out_cubic
            potential_progress = lambda progress: delayed(progress, 0.28, ease_out_quart)
        elif real_moves:
            real_progress = ease_out_cubic
            potential_progress = lambda progress: 1.0
        else:
            real_progress = lambda progress: 0.0
            potential_progress = ease_out_quart

        stage_frames = build_unique_stage_frames(
            empty,
            unlocked,
            filled,
            real_path,
            potential_path,
            real_progress,
            potential_progress,
        )

        base_frame = (stage - 1) * FRAMES_PER_STAGE
        for frame_in_stage, image in enumerate(stage_frames):
            image.save(OUTPUT_PROGRESS.with_name(OUTPUT_PROGRESS.name.format(frame=base_frame + frame_in_stage)))


def main() -> None:
    empty = load_layer(SOURCE_FILES["empty"])
    unlocked = load_layer(SOURCE_FILES["unlocked"])
    filled = load_layer(SOURCE_FILES["filled"])
    generate_static_states(empty, unlocked, filled)
    generate_transition_frames(empty, unlocked, filled)
    print(f"Generated {TOTAL_FRAMES} animated frames and {TOTAL_STAGES + 1} static states.")


if __name__ == "__main__":
    main()
