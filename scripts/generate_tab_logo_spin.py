#!/usr/bin/env python3
"""Build the compact TAB version of the main-menu 3D spinning logo."""
from __future__ import annotations

from pathlib import Path

from PIL import Image

import generate_main_menu_logo as main_menu_logo


ROOT = Path(__file__).resolve().parents[1]
OUTPUT_DIRS = [
    ROOT / "mods/lg2-0.1.0/src/main/resources/assets/lg2/textures/font",
    ROOT / "polymer/source_assets/assets/lg2/textures/font",
]
PREVIEW_PATH = ROOT / ".tmp/tab_lostglade_logo_spin_preview.png"
OUTPUT_NAME = "tab_lostglade_logo_spin.png"
FRAME_COUNT = main_menu_logo.SPIN_FRAME_COUNT
GRID_COLUMNS = 8
GRID_ROWS = FRAME_COUNT // GRID_COLUMNS
MARGIN = 1


def shared_crop_box(frames: list[Image.Image]) -> tuple[int, int, int, int]:
    boxes = [frame.getbbox() for frame in frames]
    if any(box is None for box in boxes):
        raise ValueError("A TAB logo frame is empty")
    left = max(0, min(box[0] for box in boxes if box is not None) - MARGIN)
    top = max(0, min(box[1] for box in boxes if box is not None) - MARGIN)
    right = min(frames[0].width, max(box[2] for box in boxes if box is not None) + MARGIN)
    bottom = min(frames[0].height, max(box[3] for box in boxes if box is not None) + MARGIN)
    return left, top, right, bottom


def stabilize_advance(frame: Image.Image) -> Image.Image:
    # Minecraft derives bitmap-glyph advance from the rightmost non-transparent
    # pixel.  This nearly invisible anchor keeps the TAB header centered even
    # when a rotated frame has a narrower visible silhouette.
    stabilized = frame.copy()
    stabilized.putpixel((stabilized.width - 1, 0), (0, 0, 0, 1))
    return stabilized


def make_sheet(frames: list[Image.Image]) -> Image.Image:
    width, height = frames[0].size
    sheet = Image.new("RGBA", (width * GRID_COLUMNS, height * GRID_ROWS))
    for index, frame in enumerate(frames):
        sheet.alpha_composite(frame, ((index % GRID_COLUMNS) * width, (index // GRID_COLUMNS) * height))
    return sheet


def main() -> None:
    rendered = [main_menu_logo.render_spin_model(frame, FRAME_COUNT) for frame in range(FRAME_COUNT)]
    crop_box = shared_crop_box(rendered)
    frames = [stabilize_advance(frame.crop(crop_box)) for frame in rendered]
    sheet = make_sheet(frames)

    for output_dir in OUTPUT_DIRS:
        output_dir.mkdir(parents=True, exist_ok=True)
        sheet.save(output_dir / OUTPUT_NAME)

    PREVIEW_PATH.parent.mkdir(parents=True, exist_ok=True)
    preview = Image.new("RGBA", sheet.size, (8, 8, 8, 255))
    preview.alpha_composite(sheet)
    preview.save(PREVIEW_PATH)
    print(f"Generated {FRAME_COUNT} TAB logo frames ({frames[0].width}x{frames[0].height}) in {OUTPUT_NAME}.")


if __name__ == "__main__":
    main()
