#!/usr/bin/env python3
"""Generate the IT-branch progress-bar font atlases.

The bar artwork deliberately contains no hard-coded crop coordinates.  Its
coloured differences are split into connected components, then matched to the
requirements graph and lower-inventory positions of the IT upgrades.  Thus a
redesign with wider or differently shaped arrows keeps the parallel paths
attached to the right upgrades as long as the component remains near that path.
"""
from __future__ import annotations

import itertools
import json
from collections import deque
from dataclasses import dataclass
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
CONFIG_PATH = ROOT / "config/lg2-upgrades.json"
SOURCE_DIR = ROOT / "scripts/assets/it_bar"
OUTPUT_DIR = ROOT / "mods/lg2-0.1.0/src/main/resources/assets/lg2/textures/font"

EMPTY_SOURCE = SOURCE_DIR / "default.png"
FILLED_SOURCE = SOURCE_DIR / "full.png"
AVAILABLE_OUTPUT = OUTPUT_DIR / "it_available.png"
PROGRESS_OUTPUT = OUTPUT_DIR / "it_progress.png"

SCREEN_ID = "it_hub"
FRAMES_PER_TRANSITION = 14
POTENTIAL_FILL = 0.45
LOWER_INVENTORY_ORIGIN = (16, 93)
SLOT_PITCH = 18


@dataclass(frozen=True)
class Upgrade:
    upgrade_id: str
    center: tuple[float, float]
    requirements: tuple[str, ...]


@dataclass(frozen=True)
class Edge:
    target: str
    start: tuple[float, float]
    end: tuple[float, float]

    @property
    def midpoint(self) -> tuple[float, float]:
        return ((self.start[0] + self.end[0]) / 2.0, (self.start[1] + self.end[1]) / 2.0)


@dataclass(frozen=True)
class Segment:
    target: str
    pixels: frozenset[tuple[int, int]]
    distances_from_start: dict[tuple[int, int], int]

    @property
    def length(self) -> int:
        return max(self.distances_from_start.values(), default=0)


def lower_inventory_center(slot: int, menu_rows: int) -> tuple[float, float]:
    lower_index = slot - menu_rows * 9
    if lower_index < 0 or lower_index >= 36:
        raise ValueError(f"IT upgrade slot {slot} is not in the lower inventory")
    return (
        LOWER_INVENTORY_ORIGIN[0] + (lower_index % 9) * SLOT_PITCH,
        LOWER_INVENTORY_ORIGIN[1] + (lower_index // 9) * SLOT_PITCH,
    )


def load_upgrades() -> list[Upgrade]:
    config = json.loads(CONFIG_PATH.read_text())
    screen = config["screens"][SCREEN_ID]
    rows = int(screen["rows"])
    upgrades: list[Upgrade] = []
    for button in screen["buttons"].values():
        upgrade_id = str(button.get("upgradeId", ""))
        if button.get("type") != "purchase_upgrade" or not upgrade_id:
            continue
        requirements = tuple(
            str(requirement["upgradeId"])
            for requirement in button.get("requirements", [])
            if requirement.get("upgradeId")
        )
        upgrades.append(Upgrade(upgrade_id, lower_inventory_center(int(button["slot"]), rows), requirements))
    if not upgrades:
        raise ValueError(f"No purchasable upgrades found in {SCREEN_ID}")
    return upgrades


def changed_components(empty: Image.Image, filled: Image.Image) -> list[set[tuple[int, int]]]:
    changed = {
        (x, y)
        for y in range(empty.height)
        for x in range(empty.width)
        if empty.getpixel((x, y)) != filled.getpixel((x, y))
    }
    components: list[set[tuple[int, int]]] = []
    while changed:
        component = {changed.pop()}
        queue = deque(component)
        while queue:
            x, y = queue.popleft()
            for neighbor in ((x - 1, y - 1), (x, y - 1), (x + 1, y - 1), (x - 1, y), (x + 1, y), (x - 1, y + 1), (x, y + 1), (x + 1, y + 1)):
                if neighbor in changed:
                    changed.remove(neighbor)
                    component.add(neighbor)
                    queue.append(neighbor)
        components.append(component)
    return components


def component_center(component: set[tuple[int, int]]) -> tuple[float, float]:
    return (
        sum(x for x, _ in component) / len(component),
        sum(y for _, y in component) / len(component),
    )


def build_edges(upgrades: list[Upgrade]) -> list[Edge]:
    by_id = {upgrade.upgrade_id: upgrade for upgrade in upgrades}
    edges: list[Edge] = []
    for upgrade in upgrades:
        if not upgrade.requirements:
            # The first arrow enters from one inventory cell to the left.
            edges.append(Edge(upgrade.upgrade_id, (upgrade.center[0] - SLOT_PITCH, upgrade.center[1]), upgrade.center))
            continue
        for requirement in upgrade.requirements:
            if requirement in by_id:
                edges.append(Edge(upgrade.upgrade_id, by_id[requirement].center, upgrade.center))
    return edges


def distances_from_edge_start(component: set[tuple[int, int]], edge: Edge) -> dict[tuple[int, int], int]:
    start = min(component, key=lambda point: (point[0] - edge.start[0]) ** 2 + (point[1] - edge.start[1]) ** 2)
    end = min(component, key=lambda point: (point[0] - edge.end[0]) ** 2 + (point[1] - edge.end[1]) ** 2)
    distances = {start: 0}
    queue = deque([start])
    while queue:
        x, y = queue.popleft()
        for neighbor in ((x - 1, y - 1), (x, y - 1), (x + 1, y - 1), (x - 1, y), (x + 1, y), (x - 1, y + 1), (x, y + 1), (x + 1, y + 1)):
            if neighbor in component and neighbor not in distances:
                distances[neighbor] = distances[(x, y)] + 1
                queue.append(neighbor)
    if end not in distances:
        raise ValueError(f"Arrow component for {edge.target} is not connected from its source")
    return distances


def map_components_to_upgrades(
        components: list[set[tuple[int, int]]], edges: list[Edge]
) -> dict[str, list[Segment]]:
    if len(components) != len(edges):
        raise ValueError(
            f"The artwork has {len(components)} fill components, but the IT requirements graph has {len(edges)} edges"
        )
    centers = [component_center(component) for component in components]
    edge_centers = [edge.midpoint for edge in edges]

    def cost(component_index: int, edge_index: int) -> float:
        x, y = centers[component_index]
        edge_x, edge_y = edge_centers[edge_index]
        return (x - edge_x) ** 2 + (y - edge_y) ** 2

    assignment = min(
        itertools.permutations(range(len(edges))),
        key=lambda permutation: sum(cost(component_index, edge_index) for component_index, edge_index in enumerate(permutation)),
    )
    mapped: dict[str, list[Segment]] = {}
    for component_index, edge_index in enumerate(assignment):
        edge = edges[edge_index]
        component = components[component_index]
        mapped.setdefault(edge.target, []).append(
            Segment(edge.target, frozenset(component), distances_from_edge_start(component, edge))
        )
    return mapped


def available_upgrades(purchased: frozenset[str], upgrades: list[Upgrade]) -> list[Upgrade]:
    return [
        upgrade for upgrade in upgrades
        if upgrade.upgrade_id not in purchased and all(requirement in purchased for requirement in upgrade.requirements)
    ]


def discover_states(upgrades: list[Upgrade]) -> tuple[list[frozenset[str]], list[tuple[int, str, int]]]:
    states = [frozenset()]
    state_indices = {states[0]: 0}
    transitions: list[tuple[int, str, int]] = []
    for state in states:
        source_index = state_indices[state]
        for upgrade in available_upgrades(state, upgrades):
            next_state = state | {upgrade.upgrade_id}
            if next_state not in state_indices:
                state_indices[next_state] = len(states)
                states.append(next_state)
            transitions.append((source_index, upgrade.upgrade_id, state_indices[next_state]))
    return states, transitions


def blend(base: tuple[int, int, int, int], filled: tuple[int, int, int, int], amount: float) -> tuple[int, int, int, int]:
    amount = max(0.0, min(1.0, amount))
    return tuple(round(before + (after - before) * amount) for before, after in zip(base, filled))


def compose(
        empty: Image.Image,
        filled: Image.Image,
        components_by_target: dict[str, list[Segment]],
        amounts: dict[str, float],
) -> Image.Image:
    image = empty.copy()
    for target, component_sets in components_by_target.items():
        amount = amounts.get(target, 0.0)
        if amount <= 0.0:
            continue
        for component in component_sets:
            for x, y in component.pixels:
                image.putpixel((x, y), blend(empty.getpixel((x, y)), filled.getpixel((x, y)), amount))
    return image


def state_amounts(purchased: frozenset[str], upgrades: list[Upgrade]) -> dict[str, float]:
    amounts = {upgrade_id: 1.0 for upgrade_id in purchased}
    for upgrade in available_upgrades(purchased, upgrades):
        amounts[upgrade.upgrade_id] = POTENTIAL_FILL
    return amounts


def reveal_amount(segment: Segment, progress: float, start_amount: float, end_amount: float, pixel: tuple[int, int]) -> float:
    # Distances are measured along the actual coloured component, so an L-shaped
    # arrow fills around its corner instead of being clipped by a rectangular mask.
    travelled = max(0.0, min(1.0, progress)) * (segment.length + 1)
    pixel_progress = max(0.0, min(1.0, travelled - segment.distances_from_start[pixel]))
    return start_amount + (end_amount - start_amount) * pixel_progress


def transition_frames(
        before: frozenset[str], bought_upgrade: str, upgrades: list[Upgrade], empty: Image.Image,
        filled: Image.Image, components_by_target: dict[str, list[Segment]],
) -> list[Image.Image]:
    after = before | {bought_upgrade}
    before_amounts = state_amounts(before, upgrades)
    after_amounts = state_amounts(after, upgrades)
    frames: list[Image.Image] = []
    for frame in range(FRAMES_PER_TRANSITION):
        progress = frame / (FRAMES_PER_TRANSITION - 1)
        image = empty.copy()
        for target, segments in components_by_target.items():
            start_amount = before_amounts.get(target, 0.0)
            end_amount = after_amounts.get(target, 0.0)
            for segment in segments:
                for pixel in segment.pixels:
                    amount = (
                        reveal_amount(segment, progress, start_amount, end_amount, pixel)
                        if start_amount != end_amount
                        else end_amount
                    )
                    if amount > 0.0:
                        image.putpixel(pixel, blend(empty.getpixel(pixel), filled.getpixel(pixel), amount))
        frames.append(image)
    return frames


def make_atlas(frames: list[Image.Image], columns: int) -> Image.Image:
    if not frames:
        raise ValueError("Cannot create an empty atlas")
    rows = (len(frames) + columns - 1) // columns
    atlas = Image.new("RGBA", (frames[0].width * columns, frames[0].height * rows))
    for index, frame in enumerate(frames):
        atlas.alpha_composite(frame, ((index % columns) * frame.width, (index // columns) * frame.height))
    return atlas


def main() -> None:
    empty = Image.open(EMPTY_SOURCE).convert("RGBA")
    filled = Image.open(FILLED_SOURCE).convert("RGBA")
    if empty.size != filled.size:
        raise ValueError("IT bar source layers must have the same dimensions")

    upgrades = load_upgrades()
    components = changed_components(empty, filled)
    components_by_target = map_components_to_upgrades(components, build_edges(upgrades))
    states, transitions = discover_states(upgrades)

    static_frames = [compose(empty, filled, components_by_target, state_amounts(state, upgrades)) for state in states]
    progress_frames = [
        frame
        for source_index, upgrade_id, _ in transitions
        for frame in transition_frames(states[source_index], upgrade_id, upgrades, empty, filled, components_by_target)
    ]

    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    make_atlas(static_frames, len(static_frames)).save(AVAILABLE_OUTPUT)
    make_atlas(progress_frames, 8).save(PROGRESS_OUTPUT)
    print(
        f"Generated {len(static_frames)} static states and {len(progress_frames)} animation frames "
        f"for {len(upgrades)} IT upgrades ({len(components)} arrow components)."
    )
    print("State order:", [sorted(state) for state in states])
    print("Transitions:", [(source, upgrade, target) for source, upgrade, target in transitions])


if __name__ == "__main__":
    main()
