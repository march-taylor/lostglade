#!/usr/bin/env python3

from __future__ import annotations

import copy
import json
from collections import Counter
from pathlib import Path
from typing import Any

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
SOURCE_DIR = Path("/home/mart/Desktop/models/drone")
BBMODEL_PATH = SOURCE_DIR / "model.bbmodel"
BASE_TEXTURE_PATH = SOURCE_DIR / "base_colored+propellers.png"
FRAME_TEXTURE_PATH = SOURCE_DIR / "frame+camera.png"

ITEM_MODELS_DIR = ROOT / "src/main/resources/assets/lg2/models/item"
ITEM_DEFS_DIR = ROOT / "src/main/resources/assets/lg2/items"
TEXTURES_DIR = ROOT / "src/main/resources/assets/lg2/textures/item"

FRAME_MODEL_ID = "drone_display"
FRAME_TEXTURE_ID = "drone_frame_camera"
BASE_TEXTURE_ID_PREFIX = "drone_base_"
BODY_MODEL_ID_PREFIX = "drone_body_"
CAMERA_MODEL_ID_PREFIX = "drone_camera_pitch_"
PROPELLER_MODEL_ID_PREFIX = "drone_propeller_"
COLOR_LIFT_FLOOR = 120

COLOR_NAMES = (
	"white",
	"orange",
	"magenta",
	"light_blue",
	"yellow",
	"lime",
	"pink",
	"gray",
	"light_gray",
	"cyan",
	"purple",
	"blue",
	"brown",
	"green",
	"red",
	"black",
)

PROPELLER_FRAME_UVS = (
	[0.0, 0.0, 5.5, 5.5],
	[0.0, 5.5, 5.5, 11.0],
	[5.5, 8.0, 11.0, 13.5],
)


def load_json(path: Path) -> dict[str, Any]:
	return json.loads(path.read_text(encoding="utf-8"))


def write_json(path: Path, payload: dict[str, Any]) -> None:
	path.parent.mkdir(parents=True, exist_ok=True)
	path.write_text(json.dumps(payload, indent=2, ensure_ascii=True) + "\n", encoding="utf-8")


def model_definition(model_id: str) -> dict[str, Any]:
	return {
		"model": {
			"type": "minecraft:model",
			"model": f"lg2:item/{model_id}",
		}
	}


def representative_color(path: Path) -> tuple[int, int, int]:
	image = Image.open(path).convert("RGBA")
	counter: Counter[tuple[int, int, int]] = Counter()
	for x in range(image.width):
		for y in range(image.height):
			red, green, blue, alpha = image.getpixel((x, y))
			if alpha <= 0:
				continue
			counter[(red, green, blue)] += 1
	if not counter:
		raise ValueError(f"No opaque pixels in {path}")
	return counter.most_common(1)[0][0]


def source_intensity_bounds(source: Image.Image) -> tuple[int, int]:
	min_intensity = 255
	max_intensity = 0
	for x in range(source.width):
		for y in range(source.height):
			red, green, blue, alpha = source.getpixel((x, y))
			if alpha <= 0:
				continue
			intensity = max(red, green, blue)
			min_intensity = min(min_intensity, intensity)
			max_intensity = max(max_intensity, intensity)
	if min_intensity > max_intensity:
		raise ValueError("No opaque pixels in source texture")
	return min_intensity, max_intensity


def remap_intensity(intensity: int, source_min: int, source_max: int, lifted_min: int) -> int:
	if source_max <= source_min:
		return max(lifted_min, min(source_max, intensity))
	clamped = max(source_min, min(source_max, intensity))
	return round(
		lifted_min
		+ (source_max - lifted_min) * (clamped - source_min) / (source_max - source_min)
	)


def lift_source_texture(source: Image.Image, source_min: int, source_max: int) -> Image.Image:
	out = Image.new("RGBA", source.size)
	pixels: list[tuple[int, int, int, int]] = []
	for y in range(source.height):
		for x in range(source.width):
			red, green, blue, alpha = source.getpixel((x, y))
			if alpha <= 0:
				pixels.append((0, 0, 0, 0))
				continue
			intensity = max(red, green, blue)
			mapped = remap_intensity(intensity, source_min, source_max, COLOR_LIFT_FLOOR)
			if intensity <= 0:
				pixels.append((0, 0, 0, alpha))
				continue
			scale = mapped / intensity
			pixels.append((
				min(255, round(red * scale)),
				min(255, round(green * scale)),
				min(255, round(blue * scale)),
				alpha,
			))
	out.putdata(pixels)
	return out


def recolor_base_texture(source: Image.Image, target: tuple[int, int, int], source_min: int, source_max: int) -> Image.Image:
	out = Image.new("RGBA", source.size)
	pixels: list[tuple[int, int, int, int]] = []
	for y in range(source.height):
		for x in range(source.width):
			red, green, blue, alpha = source.getpixel((x, y))
			if alpha <= 0:
				pixels.append((0, 0, 0, 0))
				continue
			intensity = max(red, green, blue)
			mapped = remap_intensity(intensity, source_min, source_max, COLOR_LIFT_FLOOR)
			pixels.append((
				target[0] * mapped // 255,
				target[1] * mapped // 255,
				target[2] * mapped // 255,
				alpha,
			))
	out.putdata(pixels)
	return out


def resolve_texture_index_map(bbmodel: dict[str, Any]) -> dict[int, str]:
	textures = bbmodel["textures"]
	return {index: textures[index]["id"] for index in range(len(textures))}


def resolve_group_uuids(bbmodel: dict[str, Any]) -> dict[str, str]:
	return {group["name"]: group["uuid"] for group in bbmodel["groups"]}


def find_outliner_node(nodes: list[Any], target_uuid: str) -> dict[str, Any] | None:
	for node in nodes:
		if isinstance(node, str):
			continue
		if node.get("uuid") == target_uuid:
			return node
		found = find_outliner_node(node.get("children", []), target_uuid)
		if found is not None:
			return found
	return None


def collect_element_uuids(node: dict[str, Any]) -> list[str]:
	collected: list[str] = []
	for child in node.get("children", []):
		if isinstance(child, str):
			collected.append(child)
			continue
		collected.extend(collect_element_uuids(child))
	return collected


def bb_element_to_model_element(
	element: dict[str, Any],
	texture_resolver: dict[int, str],
) -> dict[str, Any]:
	exported: dict[str, Any] = {
		"name": element["name"],
		"from": element["from"],
		"to": element["to"],
	}
	rotation = element.get("rotation") or [0.0, 0.0, 0.0]
	if any(abs(value) > 1.0e-6 for value in rotation):
		exported["rotation"] = {
			"x": rotation[0],
			"y": rotation[1],
			"z": rotation[2],
			"origin": element.get("origin", [0.0, 0.0, 0.0]),
		}
	faces: dict[str, Any] = {}
	for direction, face in element.get("faces", {}).items():
		texture_index = face["texture"]
		faces[direction] = {
			"uv": face["uv"],
			"texture": f"#{texture_resolver[texture_index]}",
		}
	exported["faces"] = faces
	return exported


def build_model(
	credit: str,
	texture_id: str,
	elements: list[dict[str, Any]],
) -> dict[str, Any]:
	return {
		"format_version": "1.21.11",
		"credit": credit,
		"textures": {
			"0": texture_id,
			"particle": texture_id,
		},
		"elements": elements,
	}


def main() -> None:
	bbmodel = load_json(BBMODEL_PATH)
	texture_index_map = resolve_texture_index_map(bbmodel)
	group_uuids = resolve_group_uuids(bbmodel)

	drone_node = find_outliner_node(bbmodel["outliner"], group_uuids["drone"])
	frame_node = find_outliner_node(bbmodel["outliner"], group_uuids["frame"])
	propellers_node = find_outliner_node(bbmodel["outliner"], group_uuids["propellers"])
	if drone_node is None or frame_node is None or propellers_node is None:
		raise ValueError("Could not resolve drone outliner groups from source bbmodel")

	drone_element_uuids = collect_element_uuids(drone_node)
	frame_element_uuids = collect_element_uuids(frame_node)
	propeller_element_uuids = collect_element_uuids(propellers_node)

	elements_by_uuid = {element["uuid"]: element for element in bbmodel["elements"]}
	drone_elements = [elements_by_uuid[uuid] for uuid in drone_element_uuids]

	base_colored = next(element for element in drone_elements if element["name"] == "base_colored")
	base_black = next(element for element in drone_elements if element["name"] == "base_black")
	camera = next(element for element in drone_elements if element["name"] == "camera")

	frame_elements = [elements_by_uuid[uuid] for uuid in frame_element_uuids]
	propeller_elements = [elements_by_uuid[uuid] for uuid in propeller_element_uuids]
	propeller_by_name = {element["name"]: element for element in propeller_elements}

	frame_texture_source = texture_index_map[frame_elements[0]["faces"]["north"]["texture"]]
	body_texture_source = texture_index_map[base_colored["faces"]["north"]["texture"]]
	if frame_texture_source != "5" or body_texture_source != "4":
		raise ValueError("Unexpected texture mapping in source drone bbmodel")

	source_image = Image.open(BASE_TEXTURE_PATH).convert("RGBA")
	frame_image = Image.open(FRAME_TEXTURE_PATH).convert("RGBA")

	frame_model_elements = [
		bb_element_to_model_element(
			base_black,
			{texture_index: "0" for texture_index in range(len(texture_index_map))},
		)
	]
	for element in frame_elements:
		frame_model_elements.append(
			bb_element_to_model_element(
				element,
				{texture_index: "0" for texture_index in range(len(texture_index_map))},
			)
		)

	write_json(
		ITEM_MODELS_DIR / f"{FRAME_MODEL_ID}.json",
		build_model(
			"Lostglade drone frame extracted from new base model",
			f"lg2:item/{FRAME_TEXTURE_ID}",
			frame_model_elements,
		),
	)
	write_json(ITEM_DEFS_DIR / f"{FRAME_MODEL_ID}.json", model_definition(FRAME_MODEL_ID))

	for angle in range(91):
		camera_element = copy.deepcopy(camera)
		camera_element["rotation"] = [float(angle), 0.0, 0.0]
		model_id = f"{CAMERA_MODEL_ID_PREFIX}{angle}"
		write_json(
			ITEM_MODELS_DIR / f"{model_id}.json",
			build_model(
				"Lostglade drone camera extracted from new base model",
				f"lg2:item/{FRAME_TEXTURE_ID}",
				[
					bb_element_to_model_element(
						camera_element,
						{texture_index: "0" for texture_index in range(len(texture_index_map))},
					)
				],
			),
		)
		write_json(ITEM_DEFS_DIR / f"{model_id}.json", model_definition(model_id))

	source_min_intensity, source_max_intensity = source_intensity_bounds(source_image)
	TEXTURES_DIR.mkdir(parents=True, exist_ok=True)
	frame_image.save(TEXTURES_DIR / f"{FRAME_TEXTURE_ID}.png")

	color_targets = {
		color_name: representative_color(TEXTURES_DIR / f"drone_paint_{color_name}.png")
		for color_name in COLOR_NAMES
	}

	for color_name in COLOR_NAMES:
		texture_id = f"{BASE_TEXTURE_ID_PREFIX}{color_name}"
		texture_path = TEXTURES_DIR / f"{texture_id}.png"
		if color_name == "red":
			lift_source_texture(source_image, source_min_intensity, source_max_intensity).save(texture_path)
		else:
			recolor_base_texture(source_image, color_targets[color_name], source_min_intensity, source_max_intensity).save(texture_path)

		body_model_id = f"{BODY_MODEL_ID_PREFIX}{color_name}"
		write_json(
			ITEM_MODELS_DIR / f"{body_model_id}.json",
			build_model(
				"Lostglade drone colored shell extracted from new base model",
				f"lg2:item/{texture_id}",
				[
					bb_element_to_model_element(
						base_colored,
						{texture_index: "0" for texture_index in range(len(texture_index_map))},
					)
				],
			),
		)
		write_json(ITEM_DEFS_DIR / f"{body_model_id}.json", model_definition(body_model_id))

		for propeller_name, propeller in propeller_by_name.items():
			for frame_index, propeller_uv in enumerate(PROPELLER_FRAME_UVS):
				propeller_element = copy.deepcopy(propeller)
				propeller_element["faces"]["up"]["uv"] = [
					propeller_uv[2],
					propeller_uv[3],
					propeller_uv[0],
					propeller_uv[1],
				]
				propeller_element["faces"]["down"]["uv"] = list(propeller_uv)
				model_id = f"{PROPELLER_MODEL_ID_PREFIX}{propeller_name}_{color_name}_{frame_index}"
				write_json(
					ITEM_MODELS_DIR / f"{model_id}.json",
					build_model(
						"Lostglade drone propeller frame extracted from new base model",
						f"lg2:item/{texture_id}",
						[
							bb_element_to_model_element(
								propeller_element,
								{texture_index: "0" for texture_index in range(len(texture_index_map))},
							)
						],
					),
				)
				write_json(ITEM_DEFS_DIR / f"{model_id}.json", model_definition(model_id))


if __name__ == "__main__":
	main()
