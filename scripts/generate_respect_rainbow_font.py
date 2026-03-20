from __future__ import annotations

import json
from pathlib import Path
from zipfile import ZipFile

from PIL import Image, ImageDraw, ImageFont


ROOT = Path(__file__).resolve().parents[1]
CLIENT_JAR = Path.home() / ".gradle/caches/fabric-loom/1.21.11/minecraft-client.jar"
LANG_DIR = ROOT / "mods/lg2-0.1.0/src/main/resources/assets/lg2/lang"
OUTPUT_DIRS = [
    ROOT / "mods/lg2-0.1.0/src/main/resources/assets/minecraft/textures/font",
    ROOT / "polymer/source_assets/assets/minecraft/textures/font",
]
JAPANESE_FONT = Path("/usr/share/fonts/noto-cjk/NotoSansCJK-Regular.ttc")

LOCALE_OUTPUTS = {
    "en_us": "respect_rainbow_en_us.png",
    "ru_ru": "respect_rainbow_ru_ru.png",
    "uk_ua": "respect_rainbow_uk_ua.png",
    "rpr": "respect_rainbow_rpr.png",
    "ja_jp": "respect_rainbow_ja_jp.png",
}
CELL_WIDTH = 8
CELL_HEIGHT = 8
DIGIT_CHARS = [str(value) for value in range(10)]
RAINBOW = [
    (255, 42, 64, 255),
    (255, 42, 64, 255),
    (255, 124, 0, 255),
    (255, 211, 0, 255),
    (74, 214, 67, 255),
    (0, 140, 255, 255),
    (104, 92, 255, 255),
    (203, 71, 255, 255),
]
SHADOW = (22, 10, 34, 170)


def read_jar_json(path: str) -> dict:
    with ZipFile(CLIENT_JAR) as jar:
        with jar.open(path) as handle:
            return json.load(handle)


def read_jar_image(path: str) -> Image.Image:
    with ZipFile(CLIENT_JAR) as jar:
        with jar.open(path) as handle:
            return Image.open(handle).convert("RGBA")


def load_provider_data() -> list[dict]:
    font_json = read_jar_json("assets/minecraft/font/include/default.json")
    providers = []
    for provider in font_json["providers"]:
        if provider.get("type") != "bitmap":
            continue
        namespace, path = provider["file"].split(":", 1)
        image = read_jar_image(f"assets/{namespace}/textures/{path}")
        chars = provider["chars"]
        columns = max(len(row) for row in chars)
        providers.append({
            "image": image,
            "chars": chars,
            "cell_width": image.width // columns,
            "cell_height": image.height // len(chars),
        })
    return providers


def load_localized_actionbar(locale: str) -> str:
    with (LANG_DIR / f"{locale}.json").open("r", encoding="utf-8-sig") as handle:
        values = json.load(handle)
    value = values["lg2.respect.actionbar"]
    if not value:
        raise ValueError(f"Missing lg2.respect.actionbar in {locale}")
    return value


def find_glyph_cell(providers: list[dict], target: str) -> Image.Image | None:
    for provider in providers:
        for row_index, row in enumerate(provider["chars"]):
            for column_index, value in enumerate(row):
                if value != target:
                    continue
                left = column_index * provider["cell_width"]
                top = row_index * provider["cell_height"]
                return provider["image"].crop((
                    left,
                    top,
                    left + provider["cell_width"],
                    top + provider["cell_height"],
                ))
    return None


def render_japanese_glyph(char: str) -> Image.Image:
    font = ImageFont.truetype(str(JAPANESE_FONT), 26)
    canvas = Image.new("L", (32, 32), 0)
    draw = ImageDraw.Draw(canvas)
    bbox = draw.textbbox((0, 0), char, font=font)
    text_x = (32 - (bbox[2] - bbox[0])) // 2 - bbox[0]
    text_y = (32 - (bbox[3] - bbox[1])) // 2 - bbox[1] - 1
    draw.text((text_x, text_y), char, fill=255, font=font)
    cropped = canvas.crop(canvas.getbbox() or (0, 0, 1, 1))
    scale = min((CELL_WIDTH - 1) / max(1, cropped.width), (CELL_HEIGHT - 1) / max(1, cropped.height))
    resized = cropped.resize(
        (max(1, round(cropped.width * scale)), max(1, round(cropped.height * scale))),
        Image.Resampling.LANCZOS,
    )
    binary = Image.new("L", resized.size, 0)
    for y in range(resized.height):
        for x in range(resized.width):
            binary.putpixel((x, y), 255 if resized.getpixel((x, y)) >= 96 else 0)
    cell = Image.new("RGBA", (CELL_WIDTH, CELL_HEIGHT), (0, 0, 0, 0))
    offset_x = (CELL_WIDTH - binary.width) // 2
    offset_y = CELL_HEIGHT - binary.height
    for y in range(binary.height):
        for x in range(binary.width):
            if binary.getpixel((x, y)) > 0:
                cell.putpixel((offset_x + x, offset_y + y), (255, 255, 255, 255))
    return cell


def normalize_cell(source: Image.Image) -> Image.Image:
    if source.size == (CELL_WIDTH, CELL_HEIGHT):
        return source.convert("RGBA")
    bbox = source.getbbox()
    if bbox is None:
        return Image.new("RGBA", (CELL_WIDTH, CELL_HEIGHT), (0, 0, 0, 0))
    cropped = source.crop(bbox).convert("RGBA")
    scale = min(CELL_WIDTH / max(1, cropped.width), CELL_HEIGHT / max(1, cropped.height))
    resized = cropped.resize(
        (max(1, round(cropped.width * scale)), max(1, round(cropped.height * scale))),
        Image.Resampling.NEAREST,
    )
    cell = Image.new("RGBA", (CELL_WIDTH, CELL_HEIGHT), (0, 0, 0, 0))
    offset_x = (CELL_WIDTH - resized.width) // 2
    offset_y = CELL_HEIGHT - resized.height
    cell.alpha_composite(resized, (offset_x, offset_y))
    return cell


def build_glyph_cell(providers: list[dict], char: str) -> Image.Image:
    source = find_glyph_cell(providers, char)
    if source is None:
        source = render_japanese_glyph(char)
    else:
        source = normalize_cell(source)
    return recolor_cell(source)


def recolor_cell(source: Image.Image) -> Image.Image:
    image = Image.new("RGBA", source.size, (0, 0, 0, 0))
    source_rgba = source.convert("RGBA")

    for y in range(source_rgba.height):
        for x in range(source_rgba.width):
            _, _, _, a = source_rgba.getpixel((x, y))
            if a == 0:
                continue
            shadow_x = x + 1
            shadow_y = y + 1
            if shadow_x >= source_rgba.width or shadow_y >= source_rgba.height:
                continue
            _, _, _, shadow_alpha = source_rgba.getpixel((shadow_x, shadow_y))
            if shadow_alpha != 0:
                continue
            image.putpixel((shadow_x, shadow_y), SHADOW)

    for y in range(source_rgba.height):
        for x in range(source_rgba.width):
            _, _, _, a = source_rgba.getpixel((x, y))
            if a == 0:
                continue
            band = min(len(RAINBOW) - 1, int(y * len(RAINBOW) / max(1, source_rgba.height)))
            color = RAINBOW[band]
            image.putpixel((x, y), (color[0], color[1], color[2], a))
    return image


def build_sheet(cells: list[Image.Image]) -> Image.Image:
    sheet = Image.new("RGBA", (CELL_WIDTH * len(cells), CELL_HEIGHT), (0, 0, 0, 0))
    for index, cell in enumerate(cells):
        sheet.alpha_composite(cell, (index * CELL_WIDTH, 0))
    return sheet


def save_to_outputs(name: str, image: Image.Image) -> None:
    for directory in OUTPUT_DIRS:
        directory.mkdir(parents=True, exist_ok=True)
        image.save(directory / name)


def main() -> None:
    if not CLIENT_JAR.exists():
        raise SystemExit(f"Missing minecraft client jar: {CLIENT_JAR}")

    providers = load_provider_data()

    for locale, output_name in LOCALE_OUTPUTS.items():
        text = load_localized_actionbar(locale)
        cells = [build_glyph_cell(providers, char) for char in text]
        save_to_outputs(output_name, build_sheet(cells))

    digit_cells = [build_glyph_cell(providers, char) for char in DIGIT_CHARS]
    save_to_outputs("respect_rainbow_digits.png", build_sheet(digit_cells))


if __name__ == "__main__":
    main()
