#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path
from PIL import Image

CELL_WIDTH = 32
CELL_HEIGHT = 32
GRID_SIZE = 11
STATE_POSITIONS = [5, 7, 9, 12, 14, 16, 18, 21, 23, 25, 27]


def main() -> None:
    script_dir = Path(__file__).resolve().parent
    root_dir = script_dir.parent
    assets_dir = script_dir / "assets"
    background_path = assets_dir / "drone_hud_stick_background.png"
    knob_path = assets_dir / "drone_hud_stick_knob.png"
    speed_bar_path = assets_dir / "drone_hud_speed_bar.png"

    background = Image.open(background_path).convert("RGBA")
    knob = Image.open(knob_path).convert("RGBA")
    speed_bar = Image.open(speed_bar_path).convert("RGBA")
    if background.size != (CELL_WIDTH, CELL_WIDTH):
        raise SystemExit(f"background sprite must be {CELL_WIDTH}x{CELL_WIDTH}, got {background.size}")
    if speed_bar.size != (CELL_WIDTH * 9, CELL_WIDTH):
        raise SystemExit(f"speed bar source must be {CELL_WIDTH * 9}x{CELL_WIDTH}, got {speed_bar.size}")

    atlas = Image.new("RGBA", (CELL_WIDTH * GRID_SIZE, CELL_HEIGHT * GRID_SIZE), (0, 0, 0, 0))
    speed_atlas = Image.new("RGBA", (CELL_WIDTH * 9, CELL_HEIGHT), (0, 0, 0, 0))
    speed_atlas.alpha_composite(speed_bar)

    knob_half_x = knob.width // 2
    knob_half_y = knob.height // 2

    for gy, knob_y in enumerate(STATE_POSITIONS):
        for gx, knob_x in enumerate(STATE_POSITIONS):
            ox = gx * CELL_WIDTH
            oy = gy * CELL_HEIGHT
            atlas.alpha_composite(background, (ox, oy))
            atlas.alpha_composite(knob, (ox + knob_x - knob_half_x, oy + knob_y - knob_half_y))

    outputs = {
        "drone_hud_stick.png": atlas,
        "drone_hud_speed_bar.png": speed_atlas,
    }
    for filename, image in outputs.items():
        paths = (
            root_dir / "mods/lg2-0.1.0/src/main/resources/assets/lg2/textures/font" / filename,
            root_dir / "polymer/source_assets/assets/lg2/textures/font" / filename,
        )
        for path in paths:
            path.parent.mkdir(parents=True, exist_ok=True)
            image.save(path)
            print(f"wrote {path}")


if __name__ == "__main__":
    main()
