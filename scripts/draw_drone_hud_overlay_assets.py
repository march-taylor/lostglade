#!/usr/bin/env python3
"""Draw the static, hand-authored pieces used by the drone attitude HUD."""
from __future__ import annotations

from pathlib import Path

from PIL import Image, ImageDraw

ASSET_DIR = Path(__file__).resolve().parent / "assets"


def main() -> None:
    ASSET_DIR.mkdir(parents=True, exist_ok=True)

    # Fixed reticle brackets: the moving horizon is composited around this gap.
    reticle = Image.new("RGBA", (48, 28), (0, 0, 0, 0))
    draw = ImageDraw.Draw(reticle)
    dim = (255, 255, 255, 130)
    bright = (255, 255, 255, 235)
    draw.line((18, 12, 21, 12), fill=dim)
    draw.line((18, 12, 18, 16), fill=dim)
    draw.line((18, 16, 21, 16), fill=dim)
    draw.line((30, 12, 27, 12), fill=dim)
    draw.line((30, 12, 30, 16), fill=dim)
    draw.line((30, 16, 27, 16), fill=dim)
    draw.rectangle((23, 13, 25, 15), outline=bright)
    reticle.save(ASSET_DIR / "drone_hud_attitude_reticle.png")

    # Compass pointer is static; the tape and its numeric heading are frame-specific.
    pointer = Image.new("RGBA", (48, 12), (0, 0, 0, 0))
    draw = ImageDraw.Draw(pointer)
    bright = (255, 255, 255, 245)
    draw.polygon(((24, 7), (21, 11), (27, 11)), fill=bright)
    draw.line((24, 7, 24, 11), fill=(255, 255, 255, 110))
    pointer.save(ASSET_DIR / "drone_hud_heading_pointer.png")

    print("wrote drone HUD overlay source textures")


if __name__ == "__main__":
    main()
