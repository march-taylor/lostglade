#!/usr/bin/env python3

from __future__ import annotations

import base64
import copy
import json
from collections import Counter
from io import BytesIO
from pathlib import Path
from typing import Any

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
SOURCE_DIR = Path("/home/mart/Desktop/models/drone")
BBMODEL_PATH = SOURCE_DIR / "model.bbmodel"
AUTO_AIM_MODEL_PATH = SOURCE_DIR / "model.json"
BASE_TEXTURE_PATH = SOURCE_DIR / "base_colored+propellers.png"
FRAME_TEXTURE_PATH = SOURCE_DIR / "frame+camera.png"
AUTO_AIM_TEXTURE_PATH = SOURCE_DIR / "auto_aim.png"

ITEM_MODELS_DIR = ROOT / "src/main/resources/assets/lg2/models/item"
ITEM_DEFS_DIR = ROOT / "src/main/resources/assets/lg2/items"
TEXTURES_DIR = ROOT / "src/main/resources/assets/lg2/textures/item"

FRAME_MODEL_ID = "drone_display"
FRAME_TEXTURE_ID = "drone_frame_camera"
KAMIKAZE_TEXTURE_ID = "drone_module_kamikaze"
TURRET_TEXTURE_ID = "drone_module_turret"
AUTO_AIM_TEXTURE_ID = "drone_module_auto_aim"
BASE_TEXTURE_ID_PREFIX = "drone_base_"
BODY_MODEL_ID_PREFIX = "drone_body_"
CAMERA_MODEL_ID_PREFIX = "drone_camera_pitch_"
PROPELLER_MODEL_ID_PREFIX = "drone_propeller_"
PROPELLER_TEXTURE_ID_PREFIX = "drone_propeller_"
KAMIKAZE_MODEL_ID_PREFIX = "drone_module_kamikaze_"
TURRET_MODEL_ID_PREFIX = "drone_module_turret_pitch_"
AUTO_AIM_MODEL_ID = "drone_module_auto_aim"
AUTO_AIM_MODEL_ID_PREFIX = "drone_module_auto_aim_"
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

PROPELLER_MODEL_UV = [0.0, 0.0, 5.5, 5.5]
PROPELLER_FRAME_BOXES = (
	(0, 0, 11, 11),
	(0, 11, 11, 22),
	(11, 16, 22, 27),
)
PROPELLER_ACTIVE_FRAME_ORDER_BY_NAME = {
	"right_front": (0, 1, 2),
	"left_back": (0, 1, 2),
	"right_back": (0, 2, 1),
	"left_front": (0, 2, 1),
}
AUTO_AIM_TENTACLE_NAMES = (
	"right_front",
	"right_bottom",
	"left_front",
	"left_bottom",
)
AUTO_AIM_FRAME_COUNT = 8


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


def decode_data_url_image(data_url: str) -> Image.Image:
	if not data_url or "," not in data_url:
		raise ValueError("Expected an inline data URL")
	raw = base64.b64decode(data_url.split(",", 1)[1])
	return Image.open(BytesIO(raw)).convert("RGBA")


def texture_image_from_bbmodel(bbmodel: dict[str, Any], texture_id: str) -> Image.Image:
	for texture in bbmodel.get("textures", []):
		if texture.get("id") != texture_id:
			continue
		source = texture.get("source")
		if source:
			return decode_data_url_image(source)
		relative_path = texture.get("relative_path")
		if relative_path:
			return Image.open(SOURCE_DIR / relative_path).convert("RGBA")
		break
	raise ValueError(f"Could not resolve texture id {texture_id!r} from source bbmodel")


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


def propeller_animation_frame_order(propeller_name: str) -> tuple[int, int, int]:
	order = PROPELLER_ACTIVE_FRAME_ORDER_BY_NAME.get(propeller_name)
	if order is None:
		raise ValueError(f"Unknown propeller name: {propeller_name}")
	return order


def propeller_frame_canvas(source: Image.Image, frame_box: tuple[int, int, int, int]) -> Image.Image:
	canvas = Image.new("RGBA", source.size)
	# Vanilla animated textures only cycle the sprite frame; the content has to stay
	# anchored in the same UV area for every frame.
	canvas.paste(source.crop(frame_box), (0, 0))
	return canvas


def propeller_idle_texture(source: Image.Image) -> Image.Image:
	return propeller_frame_canvas(source, PROPELLER_FRAME_BOXES[0])


def propeller_active_texture_sheet(source: Image.Image) -> Image.Image:
	sheet = Image.new("RGBA", (source.width, source.height * len(PROPELLER_FRAME_BOXES)))
	for frame_index, frame_box in enumerate(PROPELLER_FRAME_BOXES):
		sheet.paste(propeller_frame_canvas(source, frame_box), (0, source.height * frame_index))
	return sheet


def write_animation_mcmeta(texture_path: Path, frame_order: tuple[int, int, int]) -> None:
	write_json(
		texture_path.with_suffix(texture_path.suffix + ".mcmeta"),
		{
			"animation": {
				"frametime": 1,
				"frames": list(frame_order),
			},
		},
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


def resolve_group_origin(bbmodel: dict[str, Any], group_name: str) -> list[float]:
	for group in bbmodel.get("groups", []):
		if group.get("name") != group_name:
			continue
		origin = group.get("origin")
		if origin is None:
			break
		return origin
	raise ValueError(f"Could not resolve origin for group {group_name!r} from source bbmodel")


def resolve_group_elements(bbmodel: dict[str, Any], group_name: str) -> list[dict[str, Any]]:
	group_uuids = resolve_group_uuids(bbmodel)
	group_uuid = group_uuids.get(group_name)
	if group_uuid is None:
		raise ValueError(f"Could not resolve group {group_name!r} from source bbmodel")
	group_node = find_outliner_node(bbmodel["outliner"], group_uuid)
	if group_node is None:
		raise ValueError(f"Could not resolve outliner node for group {group_name!r}")
	element_uuids = collect_element_uuids(group_node)
	elements_by_uuid = {element["uuid"]: element for element in bbmodel["elements"]}
	elements: list[dict[str, Any]] = []
	for element_uuid in element_uuids:
		element = elements_by_uuid.get(element_uuid)
		if element is None:
			raise ValueError(f"Missing element {element_uuid!r} for group {group_name!r}")
		elements.append(element)
	return elements


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


def find_named_outliner_node(nodes: list[Any], target_name: str) -> dict[str, Any] | None:
	for node in nodes:
		if isinstance(node, int):
			continue
		if node.get("name") == target_name:
			return node
		found = find_named_outliner_node(node.get("children", []), target_name)
		if found is not None:
			return found
	return None


def collect_exported_element_indices(node: dict[str, Any]) -> list[int]:
	collected: list[int] = []
	for child in node.get("children", []):
		if isinstance(child, int):
			collected.append(child)
			continue
		collected.extend(collect_exported_element_indices(child))
	return collected


def resolve_exported_model_group_elements(model: dict[str, Any], group_name: str) -> list[dict[str, Any]]:
	group_node = find_named_outliner_node(model.get("groups", []), group_name)
	if group_node is None:
		raise ValueError(f"Could not resolve exported model group {group_name!r}")
	elements = model.get("elements", [])
	return [copy.deepcopy(elements[index]) for index in collect_exported_element_indices(group_node)]


def normalize_exported_model_element(element: dict[str, Any]) -> dict[str, Any]:
	normalized = copy.deepcopy(element)
	for face in normalized.get("faces", {}).values():
		face["texture"] = "#0"
	return normalized


def apply_auto_aim_frame_uv(element: dict[str, Any], frame_index: int) -> dict[str, Any]:
	framed = copy.deepcopy(element)
	row = max(0, min(AUTO_AIM_FRAME_COUNT - 1, frame_index)) // 2
	mirrored = frame_index % 2 == 1
	y0 = float(row * 2)
	y1 = y0 + 2.0
	left_uv = [12.0, y0, 14.0, y1]
	right_uv = [14.0, y0, 16.0, y1]
	faces = framed.get("faces", {})
	if "east" in faces:
		faces["east"]["uv"] = right_uv if mirrored else left_uv
	if "west" in faces:
		faces["west"]["uv"] = left_uv if mirrored else right_uv
	return framed


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


def write_model_with_definition(model_id: str, credit: str, texture_id: str, elements: list[dict[str, Any]]) -> None:
	write_json(ITEM_MODELS_DIR / f"{model_id}.json", build_model(credit, texture_id, elements))
	write_json(ITEM_DEFS_DIR / f"{model_id}.json", model_definition(model_id))


def main() -> None:
	bbmodel = load_json(BBMODEL_PATH)
	auto_aim_model = load_json(AUTO_AIM_MODEL_PATH)
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
	auto_aim_texture_image = Image.open(AUTO_AIM_TEXTURE_PATH).convert("RGBA")
	kamikaze_texture_image = texture_image_from_bbmodel(bbmodel, "3")
	turret_texture_image = texture_image_from_bbmodel(bbmodel, "6")
	kamikaze_elements = resolve_group_elements(bbmodel, "kamikatze")
	combat_elements = resolve_group_elements(bbmodel, "combat")
	barrel_elements = resolve_group_elements(bbmodel, "barrel")
	auto_aim_elements = resolve_exported_model_group_elements(auto_aim_model, "auto_aim")
	barrel_element_uuids = {element["uuid"] for element in barrel_elements}
	combat_static_elements = [element for element in combat_elements if element["uuid"] not in barrel_element_uuids]
	auto_aim_elements_by_name = {element["name"]: element for element in auto_aim_elements}

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

	write_model_with_definition(
		f"{KAMIKAZE_MODEL_ID_PREFIX}0",
		"Lostglade drone kamikaze strap extracted from new base model",
		f"lg2:item/{KAMIKAZE_TEXTURE_ID}",
		[
			bb_element_to_model_element(
				element,
				{texture_index: "0" for texture_index in range(len(texture_index_map))},
			)
			for element in kamikaze_elements[:3]
		],
	)
	write_model_with_definition(
		f"{KAMIKAZE_MODEL_ID_PREFIX}1",
		"Lostglade drone kamikaze left payload extracted from new base model",
		f"lg2:item/{KAMIKAZE_TEXTURE_ID}",
		[
			bb_element_to_model_element(
				element,
				{texture_index: "0" for texture_index in range(len(texture_index_map))},
			)
			for element in kamikaze_elements[:4]
		],
	)
	write_model_with_definition(
		f"{KAMIKAZE_MODEL_ID_PREFIX}2",
		"Lostglade drone kamikaze left-right payload extracted from new base model",
		f"lg2:item/{KAMIKAZE_TEXTURE_ID}",
		[
			bb_element_to_model_element(
				element,
				{texture_index: "0" for texture_index in range(len(texture_index_map))},
			)
			for element in kamikaze_elements[:5]
		],
	)
	write_model_with_definition(
		f"{KAMIKAZE_MODEL_ID_PREFIX}3",
		"Lostglade drone kamikaze triad payload extracted from new base model",
		f"lg2:item/{KAMIKAZE_TEXTURE_ID}",
		[
			bb_element_to_model_element(
				element,
				{texture_index: "0" for texture_index in range(len(texture_index_map))},
			)
			for element in kamikaze_elements[:6]
		],
	)
	for angle in range(91):
		rotated_elements: list[dict[str, Any]] = [
			bb_element_to_model_element(
				element,
				{texture_index: "0" for texture_index in range(len(texture_index_map))},
			)
			for element in combat_static_elements
		]
		for element in barrel_elements:
			element_copy = copy.deepcopy(element)
			rotation = element_copy.get("rotation") or [0.0, 0.0, 0.0]
			element_copy["rotation"] = [float(angle) - 90.0, float(rotation[1]), float(rotation[2])]
			rotated_elements.append(
				bb_element_to_model_element(
					element_copy,
					{texture_index: "0" for texture_index in range(len(texture_index_map))},
				)
			)
		write_model_with_definition(
			f"{TURRET_MODEL_ID_PREFIX}{angle}",
			"Lostglade drone turret extracted from new base model",
			f"lg2:item/{TURRET_TEXTURE_ID}",
			rotated_elements,
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
	kamikaze_texture_image.save(TEXTURES_DIR / f"{KAMIKAZE_TEXTURE_ID}.png")
	turret_texture_image.save(TEXTURES_DIR / f"{TURRET_TEXTURE_ID}.png")
	frame_image.save(TEXTURES_DIR / f"{FRAME_TEXTURE_ID}.png")
	auto_aim_texture_image.save(TEXTURES_DIR / f"{AUTO_AIM_TEXTURE_ID}.png")

	auto_aim_base = auto_aim_elements_by_name.get("base")
	if auto_aim_base is None:
		raise ValueError("Missing auto_aim base element in exported drone model")
	write_model_with_definition(
		AUTO_AIM_MODEL_ID,
		"Lostglade drone auto-aim base extracted from top module source",
		f"lg2:item/{AUTO_AIM_TEXTURE_ID}",
		[
			normalize_exported_model_element(auto_aim_base)
		],
	)
	for tentacle_name in AUTO_AIM_TENTACLE_NAMES:
		tentacle = auto_aim_elements_by_name.get(tentacle_name)
		if tentacle is None:
			raise ValueError(f"Missing auto_aim tentacle element {tentacle_name!r} in exported drone model")
		for frame_index in range(AUTO_AIM_FRAME_COUNT):
			model_id = f"{AUTO_AIM_MODEL_ID_PREFIX}{tentacle_name}_{frame_index}"
			write_model_with_definition(
				model_id,
				"Lostglade drone auto-aim tentacle frame extracted from top module source",
				f"lg2:item/{AUTO_AIM_TEXTURE_ID}",
				[
					apply_auto_aim_frame_uv(
						normalize_exported_model_element(tentacle),
						frame_index,
					)
				],
			)

	color_targets = {
		color_name: representative_color(TEXTURES_DIR / f"drone_paint_{color_name}.png")
		for color_name in COLOR_NAMES
	}

	for color_name in COLOR_NAMES:
		texture_id = f"{BASE_TEXTURE_ID_PREFIX}{color_name}"
		texture_path = TEXTURES_DIR / f"{texture_id}.png"
		if color_name == "red":
			colored_source = lift_source_texture(source_image, source_min_intensity, source_max_intensity)
		else:
			colored_source = recolor_base_texture(source_image, color_targets[color_name], source_min_intensity, source_max_intensity)
		if color_name == "red":
			colored_source.save(texture_path)
		else:
			colored_source.save(texture_path)

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
			propeller_element = copy.deepcopy(propeller)
			propeller_element["faces"]["up"]["uv"] = [
				PROPELLER_MODEL_UV[2],
				PROPELLER_MODEL_UV[3],
				PROPELLER_MODEL_UV[0],
				PROPELLER_MODEL_UV[1],
			]
			propeller_element["faces"]["down"]["uv"] = list(PROPELLER_MODEL_UV)
			propeller_texture_base = f"{PROPELLER_TEXTURE_ID_PREFIX}{propeller_name}_{color_name}"
			idle_texture_id = f"{propeller_texture_base}_0"
			idle_texture_path = TEXTURES_DIR / f"{idle_texture_id}.png"
			propeller_idle_texture(colored_source).save(idle_texture_path)
			idle_model_id = f"{PROPELLER_MODEL_ID_PREFIX}{propeller_name}_{color_name}_0"
			write_json(
				ITEM_MODELS_DIR / f"{idle_model_id}.json",
				build_model(
					"Lostglade drone propeller idle extracted from new base model",
					f"lg2:item/{idle_texture_id}",
					[
						bb_element_to_model_element(
							propeller_element,
							{texture_index: "0" for texture_index in range(len(texture_index_map))},
						)
					],
				),
			)
			write_json(ITEM_DEFS_DIR / f"{idle_model_id}.json", model_definition(idle_model_id))

			active_texture_id = f"{propeller_texture_base}_1"
			active_texture_path = TEXTURES_DIR / f"{active_texture_id}.png"
			propeller_active_texture_sheet(colored_source).save(active_texture_path)
			write_animation_mcmeta(active_texture_path, propeller_animation_frame_order(propeller_name))
			active_model_id = f"{PROPELLER_MODEL_ID_PREFIX}{propeller_name}_{color_name}_1"
			write_json(
				ITEM_MODELS_DIR / f"{active_model_id}.json",
				build_model(
					"Lostglade drone propeller active extracted from new base model",
					f"lg2:item/{active_texture_id}",
					[
						bb_element_to_model_element(
							propeller_element,
							{texture_index: "0" for texture_index in range(len(texture_index_map))},
						)
					],
				),
			)
			write_json(ITEM_DEFS_DIR / f"{active_model_id}.json", model_definition(active_model_id))


if __name__ == "__main__":
	main()
