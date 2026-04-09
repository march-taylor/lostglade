#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path
from PIL import Image

CELL_SIZE = 32
GRID_SIZE = 11
STATE_POSITIONS = [5, 7, 9, 12, 14, 16, 18, 21, 23, 25, 27]


def main() -> None:
    script_dir = Path(__file__).resolve().parent
    root_dir = script_dir.parent
    assets_dir = script_dir / "assets"
    background_path = assets_dir / "drone_hud_stick_background.png"
    knob_path = assets_dir / "drone_hud_stick_knob.png"

    background = Image.open(background_path).convert("RGBA")
    knob = Image.open(knob_path).convert("RGBA")
    if background.size != (CELL_SIZE, CELL_SIZE):
        raise SystemExit(f"background sprite must be {CELL_SIZE}x{CELL_SIZE}, got {background.size}")

    atlas = Image.new("RGBA", (CELL_SIZE * GRID_SIZE, CELL_SIZE * GRID_SIZE), (0, 0, 0, 0))

    knob_half_x = knob.width // 2
    knob_half_y = knob.height // 2

    for gy, knob_y in enumerate(STATE_POSITIONS):
        for gx, knob_x in enumerate(STATE_POSITIONS):
            ox = gx * CELL_SIZE
            oy = gy * CELL_SIZE
            atlas.alpha_composite(background, (ox, oy))
            atlas.alpha_composite(knob, (ox + knob_x - knob_half_x, oy + knob_y - knob_half_y))

    outputs = [
        root_dir / "mods/lg2-0.1.0/src/main/resources/assets/minecraft/textures/font/drone_hud_stick.png",
        root_dir / "polymer/source_assets/assets/minecraft/textures/font/drone_hud_stick.png",
    ]
    for path in outputs:
        path.parent.mkdir(parents=True, exist_ok=True)
        atlas.save(path)
        print(f"wrote {path}")


if __name__ == "__main__":
    main()
