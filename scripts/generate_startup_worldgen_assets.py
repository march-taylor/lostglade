#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
from dataclasses import dataclass
from pathlib import Path

from PIL import Image, ImageDraw


DEFAULT_OUTPUT_ROOT = Path("mods/lg2-0.1.0/src/main/resources/assets/lg2")
POLYMER_OUTPUT_ROOT = Path("polymer/source_assets/assets/lg2")
FRAME_COUNT = 35
CELL_SIZE_PX = 2
MIN_CANVAS_SIZE = 128
CANVAS_PADDING = 12
FRONTIER_PROGRESS_BUDGET = 0.82
CELL_WINDOW_BASE = 0.07
CELL_WINDOW_RANGE = 0.26

# Mirrors the singleplayer loading square path in Minecraft#doWorldLoad:
# radius = 5 + ChunkLevel.RADIUS_AROUND_FULL_CHUNK + 1
PLAYER_FULL_RADIUS = 5
OUTER_EMPTY_RING = 1

# Vanilla LevelLoadingScreen.COLORS
COLORS = {
    "empty": 0x545454,
    "structure_starts": 0x999999,
    "structure_references": 0x5F6191,
    "biomes": 0x80B252,
    "noise": 0xD1D1D1,
    "surface": 0x72631D,
    "carvers": 0x303572,
    "features": 0x21C42,
    "initialize_light": 0xCCCCCC,
    "light": 0xFFD700,
    "spawn": 0xF26C40,
    "full": 0xFFFFFF,
}

# Generation pyramid order from ChunkPyramid.GENERATION_PYRAMID.
STATUSES = [
    "empty",
    "structure_starts",
    "structure_references",
    "biomes",
    "noise",
    "surface",
    "carvers",
    "features",
    "initialize_light",
    "light",
    "spawn",
    "full",
]
STATUS_INDEX = {name: index for index, name in enumerate(STATUSES)}
STATUS_DURATION_WEIGHTS = {
    "empty": 0.90,
    "structure_starts": 0.72,
    "structure_references": 0.42,
    "biomes": 0.92,
    "noise": 2.35,
    "surface": 0.86,
    "carvers": 1.08,
    "features": 2.55,
    "initialize_light": 0.05,
    "light": 0.004,
    "spawn": 0.003,
    "full": 0.60,
}

# Exact direct dependency rules extracted from ChunkPyramid.GENERATION_PYRAMID
# and ChunkStep.Builder#addRequirement.
GENERATION_REQUIREMENTS: dict[str, list[tuple[str, int]]] = {
    "structure_references": [("structure_starts", 8)],
    "biomes": [("structure_starts", 8)],
    "noise": [("structure_starts", 8), ("biomes", 1)],
    "surface": [("structure_starts", 8), ("biomes", 1)],
    "carvers": [("structure_starts", 8)],
    "features": [("structure_starts", 8), ("carvers", 1)],
    "light": [("initialize_light", 1)],
    "spawn": [("biomes", 1)],
}


@dataclass(frozen=True)
class Step:
    name: str
    direct_dependencies: tuple[str, ...]
    accumulated_dependencies: tuple[str, ...]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Generate vanilla world-loading square frames for the Lost Glade startup ceiling."
    )
    parser.add_argument(
        "--output-root",
        type=Path,
        default=DEFAULT_OUTPUT_ROOT,
        help=f"Root lg2 assets directory (default: {DEFAULT_OUTPUT_ROOT})",
    )
    parser.add_argument(
        "--seed",
        type=int,
        default=0x10_57_7A_4D_13,
        help="Seed that drives the per-cell progression jitter.",
    )
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    output_roots = resolve_output_roots(args.output_root)

    steps = build_generation_steps()
    full_step = steps["full"]
    dependency_radius = max(0, len(full_step.accumulated_dependencies) - 1)
    view_radius = PLAYER_FULL_RADIUS + dependency_radius + OUTER_EMPTY_RING
    side_cells = view_radius * 2 + 1
    square_size = side_cells * CELL_SIZE_PX
    canvas_size = max(MIN_CANVAS_SIZE, square_size + CANVAS_PADDING * 2)
    target_grid = build_target_status_grid(view_radius, full_step.accumulated_dependencies)
    frame_grids = simulate_frame_grids(target_grid, args.seed)

    for output_root in output_roots:
        items_dir = output_root / "items"
        textures_dir = output_root / "textures" / "item" / "startup_worldgen"
        models_dir = output_root / "models" / "item"
        items_dir.mkdir(parents=True, exist_ok=True)
        textures_dir.mkdir(parents=True, exist_ok=True)
        models_dir.mkdir(parents=True, exist_ok=True)

        for frame_index, grid in enumerate(frame_grids):
            frame = render_frame(grid, canvas_size, side_cells)
            texture_name = f"frame_{frame_index:02d}"
            frame.save(textures_dir / f"{texture_name}.png")
            model_name = f"startup_worldgen_frame_{frame_index:02d}"
            write_model(models_dir / f"{model_name}.json", texture_name)
            write_item(items_dir / f"{model_name}.json", model_name)

    print(
        "generated "
        f"{FRAME_COUNT} vanilla loading-square frames "
        f"(dependency_radius={dependency_radius}, view_radius={view_radius}, side_pixels={square_size}) "
        f"into {', '.join(str(root) for root in output_roots)}"
    )


def resolve_output_roots(primary_root: Path) -> list[Path]:
    roots = [primary_root]
    if primary_root == DEFAULT_OUTPUT_ROOT and POLYMER_OUTPUT_ROOT not in roots:
        roots.append(POLYMER_OUTPUT_ROOT)
    return roots


def build_generation_steps() -> dict[str, Step]:
    steps: dict[str, Step] = {}
    previous: Step | None = None
    for status in STATUSES:
        direct = [previous.name] if previous is not None else []
        for required_status, radius in GENERATION_REQUIREMENTS.get(status, []):
            direct = add_requirement(direct, required_status, radius)
        accumulated = build_accumulated_dependencies(direct, previous)
        current = Step(status, tuple(direct), tuple(accumulated))
        steps[status] = current
        previous = current
    return steps


def add_requirement(direct: list[str], required_status: str, radius: int) -> list[str]:
    target_length = radius + 1
    if target_length > len(direct):
        expanded = [required_status] * target_length
        for index in range(len(direct)):
            expanded[index] = status_max(direct[index], required_status)
        return expanded

    updated = list(direct)
    for index in range(min(target_length, len(direct))):
        updated[index] = status_max(updated[index], required_status)
    return updated


def build_accumulated_dependencies(direct: list[str], parent: Step | None) -> list[str]:
    if parent is None:
        return list(direct)

    parent_shift = get_radius_of_parent(direct, parent.name)
    parent_accumulated = list(parent.accumulated_dependencies)
    result_length = max(parent_shift + len(parent_accumulated), len(direct))
    result: list[str] = []
    for index in range(result_length):
        parent_index = index - parent_shift
        parent_value = (
            parent_accumulated[parent_index]
            if 0 <= parent_index < len(parent_accumulated)
            else None
        )
        direct_value = direct[index] if index < len(direct) else None
        if direct_value is None:
            result.append(parent_value if parent_value is not None else "empty")
        elif parent_value is None:
            result.append(direct_value)
        else:
            result.append(status_max(direct_value, parent_value))
    return result


def get_radius_of_parent(direct: list[str], parent_status: str) -> int:
    for index in range(len(direct) - 1, -1, -1):
        if STATUS_INDEX[direct[index]] >= STATUS_INDEX[parent_status]:
            return index
    return 0


def status_max(left: str, right: str) -> str:
    return left if STATUS_INDEX[left] >= STATUS_INDEX[right] else right


def status_for_distance(
    full_distance: int,
    dependency_radius: int,
    full_dependencies: tuple[str, ...],
) -> str | None:
    if full_distance <= 0:
        return "full"
    if full_distance > dependency_radius:
        return None
    return full_dependencies[full_distance]


def build_target_status_grid(
    view_radius: int,
    full_dependencies: tuple[str, ...],
) -> list[list[int]]:
    dependency_radius = max(0, len(full_dependencies) - 1)
    side_cells = view_radius * 2 + 1
    grid = [[-1 for _ in range(side_cells)] for _ in range(side_cells)]
    for grid_y in range(-view_radius, view_radius + 1):
        for grid_x in range(-view_radius, view_radius + 1):
            chebyshev = max(abs(grid_x), abs(grid_y))
            full_distance = max(0, chebyshev - PLAYER_FULL_RADIUS)
            status = status_for_distance(full_distance, dependency_radius, full_dependencies)
            grid[grid_y + view_radius][grid_x + view_radius] = (
                -1 if status is None else STATUS_INDEX[status]
            )
    return grid


def simulate_frame_grids(target_grid: list[list[int]], seed: int) -> list[list[list[int]]]:
    arrival_grid = build_arrival_grid(target_grid, seed)
    return [
        build_frame_grid(target_grid, arrival_grid, frame / (FRAME_COUNT - 1))
        for frame in range(FRAME_COUNT)
    ]


def build_arrival_grid(target_grid: list[list[int]], seed: int) -> list[list[float]]:
    side_cells = len(target_grid)
    center = side_cells // 2
    scores = [[0.0 if target_grid[y][x] >= 0 and x == center and y == center else float("inf")
               for x in range(side_cells)]
              for y in range(side_cells)]

    for ring in range(1, center + 1):
        for y in range(side_cells):
            for x in range(side_cells):
                if target_grid[y][x] < 0:
                    continue
                if chebyshev_distance(x, y, center) != ring:
                    continue
                parent_score = best_parent_score(scores, x, y, center, ring)
                if parent_score == float("inf"):
                    continue
                scores[y][x] = parent_score + frontier_step_cost(seed, x - center, y - center, ring)

    max_score = max(
        scores[y][x]
        for y in range(side_cells)
        for x in range(side_cells)
        if target_grid[y][x] >= 0 and scores[y][x] != float("inf")
    )
    normalized = [[1.0 for _ in range(side_cells)] for _ in range(side_cells)]
    for y in range(side_cells):
        for x in range(side_cells):
            if target_grid[y][x] < 0:
                continue
            normalized[y][x] = 0.0 if max_score <= 0.0 else scores[y][x] / max_score
    return normalized


def build_frame_grid(
    target_grid: list[list[int]],
    arrival_grid: list[list[float]],
    progress: float,
) -> list[list[int]]:
    grid = [[-1 for _ in row] for row in target_grid]
    for y, row in enumerate(target_grid):
        for x, target_index in enumerate(row):
            if target_index < 0:
                continue
            arrival = arrival_grid[y][x] * FRONTIER_PROGRESS_BUDGET
            if progress < arrival:
                continue
            cell_window = resolve_cell_window(target_index, arrival_grid[y][x], x, y)
            local_progress = clamp01((progress - arrival) / cell_window)
            grid[y][x] = resolve_display_index(target_index, local_progress)
    return grid


def resolve_cell_window(target_index: int, raw_arrival: float, x: int, y: int) -> float:
    normalized_work = total_status_weight(target_index) / total_status_weight(len(STATUSES) - 1)
    jitter = 0.92 + hash01(0xC0FFEE, x, y, target_index) * 0.18
    window = (CELL_WINDOW_BASE + normalized_work * CELL_WINDOW_RANGE) * jitter
    max_window = max(1.0e-6, 1.0 - raw_arrival * FRONTIER_PROGRESS_BUDGET)
    return min(window, max_window)


def resolve_display_index(target_index: int, local_progress: float) -> int:
    total = total_status_weight(target_index)
    completed = 0.0
    for status_index in range(target_index + 1):
        completed += STATUS_DURATION_WEIGHTS[STATUSES[status_index]]
        if local_progress < completed / total or status_index == target_index:
            return status_index
    return target_index


def total_status_weight(target_index: int) -> float:
    return sum(STATUS_DURATION_WEIGHTS[STATUSES[index]] for index in range(target_index + 1))


def best_parent_score(
    scores: list[list[float]],
    x: int,
    y: int,
    center: int,
    ring: int,
) -> float:
    best = float("inf")
    for parent_y in range(max(0, y - 1), min(len(scores), y + 2)):
        for parent_x in range(max(0, x - 1), min(len(scores[parent_y]), x + 2)):
            if parent_x == x and parent_y == y:
                continue
            if chebyshev_distance(parent_x, parent_y, center) != ring - 1:
                continue
            best = min(best, scores[parent_y][parent_x])
    return best


def frontier_step_cost(seed: int, relative_x: int, relative_y: int, ring: int) -> float:
    coarse_noise = hash01(seed ^ 0xA5A5_5A5A, relative_x // 3, relative_y // 3, ring // 2) - 0.5
    fine_noise = hash01(seed, relative_x, relative_y, ring) - 0.5
    axis_bias = hash01(seed ^ 0x5F37_59DF, signum(relative_x), signum(relative_y), ring // 4) - 0.5
    return max(0.55, 1.0 + coarse_noise * 0.34 + fine_noise * 0.16 + axis_bias * 0.10)


def chebyshev_distance(x: int, y: int, center: int) -> int:
    return max(abs(x - center), abs(y - center))


def signum(value: int) -> int:
    if value < 0:
        return -1
    if value > 0:
        return 1
    return 0


def smoothstep(value: float) -> float:
    clamped = clamp01(value)
    return clamped * clamped * (3.0 - 2.0 * clamped)


def clamp01(value: float) -> float:
    if value <= 0.0:
        return 0.0
    if value >= 1.0:
        return 1.0
    return value


def copy_grid(grid: list[list[int]]) -> list[list[int]]:
    return [row.copy() for row in grid]


def render_frame(
    grid: list[list[int]],
    canvas_size: int,
    side_cells: int,
) -> Image.Image:
    square_size = side_cells * CELL_SIZE_PX
    offset = (canvas_size - square_size) // 2
    canvas = Image.new("RGBA", (canvas_size, canvas_size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(canvas)

    for y, row in enumerate(grid):
        for x, status_index in enumerate(row):
            if status_index < 0:
                continue
            color = COLORS[STATUSES[status_index]]
            rgba = ((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, 255)
            x0 = offset + x * CELL_SIZE_PX
            y0 = offset + y * CELL_SIZE_PX
            x1 = x0 + CELL_SIZE_PX - 1
            y1 = y0 + CELL_SIZE_PX - 1
            draw.rectangle((x0, y0, x1, y1), fill=rgba)

    return canvas


def hash01(seed: int, x: int, y: int, salt: int) -> float:
    value = seed & 0xFFFFFFFFFFFFFFFF
    for component in (x, y, salt):
        value ^= (component + 0x9E3779B97F4A7C15) & 0xFFFFFFFFFFFFFFFF
        value = (value * 0xBF58476D1CE4E5B9) & 0xFFFFFFFFFFFFFFFF
        value ^= value >> 30
        value = (value * 0x94D049BB133111EB) & 0xFFFFFFFFFFFFFFFF
        value ^= value >> 31
    return ((value >> 11) & ((1 << 53) - 1)) / float(1 << 53)


def write_model(path: Path, texture_name: str) -> None:
    model = {
        "parent": "minecraft:item/generated",
        "gui_light": "front",
        "textures": {
            "layer0": f"lg2:item/startup_worldgen/{texture_name}",
        },
        "display": {
            "fixed": {
                "rotation": [90, 0, 0],
                "translation": [0, 0, 0],
                "scale": [1, 1, 1],
            }
        },
    }
    path.write_text(json.dumps(model, ensure_ascii=True, indent=2) + "\n", encoding="utf-8")


def write_item(path: Path, model_name: str) -> None:
    item = {
        "model": {
            "type": "minecraft:model",
            "model": f"lg2:item/{model_name}",
        }
    }
    path.write_text(json.dumps(item, ensure_ascii=True, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
