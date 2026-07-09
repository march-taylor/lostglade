#!/usr/bin/env python3
from __future__ import annotations

import argparse
import fnmatch
import hashlib
import json
import math
import os
import re
import shutil
import subprocess
import sys
import tempfile
import wave
from dataclasses import dataclass
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
WORKER_SCRIPT = ROOT / "scripts/season_tts_worker.py"
DEFAULT_QWEN_PYTHON = Path.home() / ".local/share/qwen3-tts/bin/python"
DEFAULT_QWEN_SCRIPT = Path.home() / ".local/share/qwen3-tts/qwen3-say.py"
DEFAULT_REF_AUDIO = Path.home() / "Downloads/walter_white_reference_24k.wav"
DEFAULT_LANGUAGE = "Russian"
DEFAULT_DEVICE = "cuda:0"
DEFAULT_DTYPE = "float16"
DEFAULT_ATTN = "eager"
DEFAULT_MODE = "clone"
DEFAULT_MAX_CHARS = 0
DEFAULT_TAIL_SILENCE_MS = 350
MANIFEST_NAME = ".season_tts_manifest.json"
GENERATOR_VERSION = 4
WHITESPACE_RE = re.compile(r"\s+")
NON_ALNUM_RE = re.compile(r"[^a-zA-Z0-9._-]+")
UNSUPPORTED_STRESS_MARKS_RE = re.compile(r"[\u0301\u02ca]")


class LocalTtsError(RuntimeError):
    pass


@dataclass(slots=True)
class PreparedCue:
    cue: dict[str, Any]
    cue_id: str
    text: str
    output_path: Path
    fingerprint: str


@dataclass(slots=True)
class PartTask:
    prepared: PreparedCue
    part_path: Path
    part_text: str
    part_index: int
    total_parts: int


def normalize_text(text: str) -> str:
    return WHITESPACE_RE.sub(" ", text).strip()


def normalize_tts_text(text: str) -> str:
    text = UNSUPPORTED_STRESS_MARKS_RE.sub("", text)
    return normalize_text(text)


def sentence_units(text: str) -> list[str]:
    return [part.strip() for part in re.split(r"(?<=[.!?…])\s+", text) if part.strip()]


def clause_units(text: str) -> list[str]:
    return [part.strip() for part in re.split(r"(?<=[,;:])\s+", text) if part.strip()]


def force_split_words(text: str, max_chars: int) -> list[str]:
    words = text.split()
    if not words:
        return []

    parts: list[str] = []
    current = words[0]
    for word in words[1:]:
        candidate = f"{current} {word}"
        if len(candidate) <= max_chars:
            current = candidate
            continue
        if len(word) > max_chars:
            if current:
                parts.append(current)
                current = ""
            for index in range(0, len(word), max_chars):
                parts.append(word[index:index + max_chars])
            continue
        parts.append(current)
        current = word

    if current:
        parts.append(current)
    return parts


def split_text_recursive(text: str, max_chars: int, depth: int = 0) -> list[str]:
    text = normalize_text(text)
    if not text:
        return []
    if max_chars <= 0 or len(text) <= max_chars:
        return [text]

    splitters = [sentence_units, clause_units]
    if depth >= len(splitters):
        return force_split_words(text, max_chars)

    units = splitters[depth](text)
    if len(units) <= 1:
        return split_text_recursive(text, max_chars, depth + 1)

    parts: list[str] = []
    current: list[str] = []
    current_len = 0
    for unit in units:
        if len(unit) > max_chars:
            if current:
                parts.extend(split_text_recursive(" ".join(current), max_chars, depth + 1))
                current = []
                current_len = 0
            parts.extend(split_text_recursive(unit, max_chars, depth + 1))
            continue

        separator = 1 if current else 0
        next_len = current_len + separator + len(unit)
        if next_len <= max_chars:
            current.append(unit)
            current_len = next_len
            continue

        if current:
            parts.append(" ".join(current))
        current = [unit]
        current_len = len(unit)

    if current:
        parts.append(" ".join(current))
    return parts


def slugify(value: str) -> str:
    return NON_ALNUM_RE.sub("_", value).strip("._-") or "cue"


def resolve_output_path(config_path: Path, audio_file: str) -> Path:
    audio_path = Path(audio_file)
    if audio_path.is_absolute():
        return audio_path
    voice_root = config_path.with_suffix("")
    return (voice_root / audio_path).resolve()


def manifest_path_for_config(config_path: Path) -> Path:
    return config_path.with_suffix("").resolve() / MANIFEST_NAME


def ensure_parent(path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)


def display_path(path: Path) -> str:
    try:
        return str(path.resolve().relative_to(ROOT))
    except ValueError:
        return str(path.resolve())


def normalized_resolved(path: Path | None) -> str | None:
    if path is None:
        return None
    return str(path.expanduser().resolve())


def run_command(command: list[str], env: dict[str, str] | None = None) -> None:
    process = subprocess.run(command, env=env, cwd=ROOT)
    if process.returncode != 0:
        joined = " ".join(command)
        raise LocalTtsError(f"Command failed with exit code {process.returncode}: {joined}")


def same_path(first: Path, second: Path) -> bool:
    try:
        return first.resolve() == second.resolve()
    except FileNotFoundError:
        return str(first.expanduser()) == str(second.expanduser())


def concat_wav_files(parts: list[Path], output_path: Path) -> None:
    if not parts:
        raise LocalTtsError(f"No audio parts were generated for {output_path}")
    ensure_parent(output_path)
    if len(parts) == 1:
        if same_path(parts[0], output_path):
            return
        if output_path.exists():
            output_path.unlink()
        shutil.move(str(parts[0]), str(output_path))
        return
    if output_path.exists():
        output_path.unlink()

    params: tuple[Any, ...] | None = None
    with wave.open(str(output_path), "wb") as output_wav:
        for index, part in enumerate(parts, start=1):
            with wave.open(str(part), "rb") as input_wav:
                current_params = input_wav.getparams()
                if params is None:
                    params = current_params
                    output_wav.setparams(current_params)
                elif current_params[:4] != params[:4] or current_params[4:] != params[4:]:
                    raise LocalTtsError(
                        f"WAV parameters differ for concatenation in {output_path} at part {index}: "
                        f"{current_params} vs {params}"
                    )
                output_wav.writeframes(input_wav.readframes(input_wav.getnframes()))


def audio_duration_seconds(path: Path) -> float:
    with wave.open(str(path), "rb") as wav_file:
        frames = wav_file.getnframes()
        sample_rate = wav_file.getframerate()
        if sample_rate <= 0:
            raise LocalTtsError(f"Invalid sample rate in {path}")
        return frames / float(sample_rate)


def duration_to_ticks(seconds: float) -> int:
    return max(1, int(math.ceil(seconds * 20.0)))


def load_json(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def write_json(path: Path, data: dict[str, Any]) -> None:
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def load_manifest(path: Path) -> dict[str, Any]:
    if not path.exists():
        return {"version": GENERATOR_VERSION, "cues": {}}
    try:
        data = load_json(path)
    except Exception:
        return {"version": GENERATOR_VERSION, "cues": {}}
    if not isinstance(data, dict):
        return {"version": GENERATOR_VERSION, "cues": {}}
    cues = data.get("cues")
    if not isinstance(cues, dict):
        cues = {}
    return {"version": data.get("version"), "cues": cues}


def write_manifest(path: Path, data: dict[str, Any]) -> None:
    ensure_parent(path)
    write_json(path, data)


def cue_matches(cue_id: str, patterns: list[str]) -> bool:
    if not patterns:
        return True
    return any(fnmatch.fnmatch(cue_id, pattern) for pattern in patterns)


def fingerprint_for_cue(text: str, output_path: Path, args: argparse.Namespace) -> str:
    payload = {
        "generatorVersion": GENERATOR_VERSION,
        "text": text,
        "outputPath": display_path(output_path),
        "mode": args.mode,
        "language": args.language,
        "model": args.model,
        "device": args.device,
        "dtype": args.dtype,
        "attn": args.attn,
        "maxChars": args.max_chars,
        "qwenPython": normalized_resolved(args.qwen_python),
        "qwenScript": normalized_resolved(args.qwen_script),
        "refAudio": normalized_resolved(args.ref_audio),
        "refText": args.resolved_ref_text,
        "instruct": args.instruct,
        "tailSilenceMs": args.tail_silence_ms,
    }
    raw = json.dumps(payload, ensure_ascii=False, sort_keys=True).encode("utf-8")
    return hashlib.sha256(raw).hexdigest()


def build_prepared_cues(config_path: Path, data: dict[str, Any], args: argparse.Namespace) -> list[PreparedCue]:
    prepared: list[PreparedCue] = []
    for cue in data.get("cues", []):
        cue_id = str(cue.get("id") or "").strip()
        raw_script_text = str(cue.get("scriptText") or cue.get("chatText") or cue.get("ttsText") or "")
        raw_chat_text = str(cue.get("chatText") or raw_script_text)
        raw_tts_text = str(cue.get("ttsText") or raw_script_text or raw_chat_text)
        tts_text = normalize_tts_text(raw_tts_text)
        audio_file = str(cue.get("audioFile") or "").strip()
        if not cue_id or not tts_text or not audio_file:
            continue
        if not cue_matches(cue_id, args.only or []):
            continue
        output_path = resolve_output_path(config_path, audio_file)
        fingerprint = fingerprint_for_cue(tts_text, output_path, args)
        prepared.append(
            PreparedCue(
                cue=cue,
                cue_id=cue_id,
                text=tts_text,
                output_path=output_path,
                fingerprint=fingerprint,
            )
        )
    return prepared


def resolve_ref_text_value(args: argparse.Namespace) -> str | None:
    if args.mode != "clone":
        return None
    if args.ref_text:
        text = normalize_text(args.ref_text)
        return text or None
    if args.ref_text_file:
        text = normalize_text(args.ref_text_file.read_text(encoding="utf-8"))
        return text or None
    sidecar_path = args.ref_audio.with_suffix(".txt")
    if sidecar_path.exists():
        text = normalize_text(sidecar_path.read_text(encoding="utf-8"))
        if text:
            print(f"[info] using reference transcript from {display_path(sidecar_path)}")
            return text
    return None


def cue_status(prepared: PreparedCue, manifest_cues: dict[str, Any]) -> tuple[str, str]:
    if not prepared.output_path.exists():
        return "MISS ", "missing audio"

    entry = manifest_cues.get(prepared.cue_id)
    if not isinstance(entry, dict):
        return "STALE", "missing manifest entry"

    if entry.get("fingerprint") != prepared.fingerprint:
        return "STALE", "text or generator settings changed"

    if entry.get("outputPath") != display_path(prepared.output_path):
        return "STALE", "output path changed"

    return "READY", "up to date"


def print_status(prepared_cues: list[PreparedCue], manifest_cues: dict[str, Any]) -> dict[str, int]:
    counts = {"READY": 0, "MISS ": 0, "STALE": 0}
    for prepared in prepared_cues:
        status, reason = cue_status(prepared, manifest_cues)
        counts[status] += 1
        print(f"[{status}] {prepared.cue_id} -> {display_path(prepared.output_path)} ({reason})")
    print(
        "[status] ready={ready} stale={stale} missing={missing} total={total}".format(
            ready=counts["READY"],
            stale=counts["STALE"],
            missing=counts["MISS "],
            total=len(prepared_cues),
        )
    )
    return counts


def split_generation_tasks(prepared_cues: list[PreparedCue], max_chars: int, tmp_root: Path) -> list[PartTask]:
    tasks: list[PartTask] = []
    for prepared in prepared_cues:
        parts = split_text_recursive(prepared.text, max_chars) if max_chars > 0 else [prepared.text]
        if not parts:
            raise LocalTtsError(f"Refusing to generate empty text for {prepared.cue_id}")
        cue_dir: Path | None = None
        if len(parts) > 1:
            cue_dir = tmp_root / slugify(prepared.cue_id)
            cue_dir.mkdir(parents=True, exist_ok=True)
        for index, part_text in enumerate(parts, start=1):
            tasks.append(
                PartTask(
                    prepared=prepared,
                    part_path=prepared.output_path if len(parts) == 1 else cue_dir / f"part_{index:02d}.wav",
                    part_text=part_text,
                    part_index=index,
                    total_parts=len(parts),
                )
            )
    return tasks


def run_worker_batch(tasks: list[PartTask], args: argparse.Namespace) -> None:
    if not tasks:
        return
    if not args.qwen_python.exists():
        raise LocalTtsError(f"Qwen Python not found: {args.qwen_python}")
    if not args.qwen_script.exists():
        raise LocalTtsError(f"Qwen script not found: {args.qwen_script}")
    if args.mode == "clone" and not args.ref_audio.exists():
        raise LocalTtsError(f"Reference audio not found: {args.ref_audio}")
    if not WORKER_SCRIPT.exists():
        raise LocalTtsError(f"Worker script not found: {WORKER_SCRIPT}")

    with tempfile.TemporaryDirectory(prefix="lg2-qwen-batch-") as tmp_dir:
        jobs_file = Path(tmp_dir) / "jobs.json"
        payload = [
            {
                "cueId": task.prepared.cue_id,
                "partIndex": task.part_index,
                "totalParts": task.total_parts,
                "text": task.part_text,
                "output": str(task.part_path),
            }
            for task in tasks
        ]
        jobs_file.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

        command = [
            str(args.qwen_python),
            str(WORKER_SCRIPT),
            str(jobs_file),
            "--mode",
            args.mode,
            "--language",
            args.language,
            "--device",
            args.device,
            "--dtype",
            args.dtype,
            "--attn",
            args.attn,
        ]
        if args.model:
            command.extend(["--model", args.model])
        if args.ref_audio:
            command.extend(["--ref-audio", str(args.ref_audio)])
        if args.resolved_ref_text:
            command.extend(["--ref-text", args.resolved_ref_text])
        if args.instruct:
            command.extend(["--instruct", args.instruct])
        if args.tail_silence_ms is not None:
            command.extend(["--tail-silence-ms", str(args.tail_silence_ms)])

        env = os.environ.copy()
        env["MIOPEN_ENABLE_LOGGING"] = "0"
        numba_cache_dir = Path("/tmp/lg2-numba-cache")
        numba_cache_dir.mkdir(parents=True, exist_ok=True)
        env["NUMBA_CACHE_DIR"] = str(numba_cache_dir)
        qwen_pythonpath = str(args.qwen_script.expanduser().resolve().parent)
        if env.get("PYTHONPATH"):
            env["PYTHONPATH"] = qwen_pythonpath + os.pathsep + env["PYTHONPATH"]
        else:
            env["PYTHONPATH"] = qwen_pythonpath

        unique_cues = len({task.prepared.cue_id for task in tasks})
        noun = "cue(s)" if unique_cues == len(tasks) else "task(s)"
        print(f"[worker] loading model once for {len(tasks)} {noun}")
        run_command(command, env=env)


def regenerate_cues(
    prepared_cues: list[PreparedCue],
    manifest: dict[str, Any],
    manifest_path: Path,
    args: argparse.Namespace,
) -> tuple[int, int]:
    if not prepared_cues:
        return 0, 0

    with tempfile.TemporaryDirectory(prefix="lg2-season-tts-parts-") as tmp_dir:
        tmp_root = Path(tmp_dir)
        part_tasks = split_generation_tasks(prepared_cues, args.max_chars, tmp_root)
        run_worker_batch(part_tasks, args)

        tasks_by_cue: dict[str, list[PartTask]] = {}
        for task in part_tasks:
            tasks_by_cue.setdefault(task.prepared.cue_id, []).append(task)

        updated = 0
        generated = 0
        manifest_cues = manifest.setdefault("cues", {})
        for prepared in prepared_cues:
            cue_tasks = sorted(tasks_by_cue.get(prepared.cue_id, []), key=lambda task: task.part_index)
            part_paths = [task.part_path for task in cue_tasks]
            for part_path in part_paths:
                if not part_path.exists():
                    raise LocalTtsError(f"Worker did not create {part_path}")

            concat_wav_files(part_paths, prepared.output_path)
            ticks = duration_to_ticks(audio_duration_seconds(prepared.output_path))
            generated += 1
            if args.write_duration_ticks and prepared.cue.get("durationTicks") != ticks:
                prepared.cue["durationTicks"] = ticks
                updated += 1
            manifest_cues[prepared.cue_id] = {
                "fingerprint": prepared.fingerprint,
                "text": prepared.text,
                "outputPath": display_path(prepared.output_path),
                "durationTicks": ticks,
                "generatorVersion": GENERATOR_VERSION,
            }
            print(f"[done] {prepared.cue_id} -> {display_path(prepared.output_path)} ({ticks} ticks)")

        manifest["version"] = GENERATOR_VERSION
        write_manifest(manifest_path, manifest)
        return generated, updated


def handle_season_config(args: argparse.Namespace) -> int:
    config_path = args.config_path.resolve()
    args.resolved_ref_text = resolve_ref_text_value(args)
    if args.mode == "clone" and not args.resolved_ref_text:
        print("[warn] no reference transcript found; clone will run in x_vector_only_mode and quality may be reduced")
    data = load_json(config_path)
    manifest_path = manifest_path_for_config(config_path)
    manifest = load_manifest(manifest_path)
    prepared_cues = build_prepared_cues(config_path, data, args)
    if not prepared_cues:
        print("No matching cues found.")
        return 1

    print_status(prepared_cues, manifest.get("cues", {}))
    if args.status_only:
        return 0

    to_generate: list[PreparedCue] = []
    reused = 0
    updated = 0
    for prepared in prepared_cues:
        status, reason = cue_status(prepared, manifest.get("cues", {}))
        if args.force or status != "READY":
            print(f"[regen] {prepared.cue_id} ({'forced' if args.force else reason})")
            to_generate.append(prepared)
            continue

        reused += 1
        if args.write_duration_ticks:
            ticks = duration_to_ticks(audio_duration_seconds(prepared.output_path))
            if prepared.cue.get("durationTicks") != ticks:
                prepared.cue["durationTicks"] = ticks
                updated += 1
        print(f"[skip] {prepared.cue_id}")

    generated, generated_updates = regenerate_cues(to_generate, manifest, manifest_path, args)
    updated += generated_updates

    if args.write_duration_ticks and updated:
        write_json(config_path, data)
        print(f"[write] updated durationTicks for {updated} cue(s) in {display_path(config_path)}")

    print(f"[summary] reused={reused} generated={generated} total={len(prepared_cues)}")
    return 0


def handle_text(args: argparse.Namespace) -> int:
    output_path = args.output.resolve()
    args.resolved_ref_text = resolve_ref_text_value(args)
    if args.mode == "clone" and not args.resolved_ref_text:
        print("[warn] no reference transcript found; clone will run in x_vector_only_mode and quality may be reduced")
    text = normalize_tts_text(args.text)
    if not text:
        raise LocalTtsError("Refusing to generate empty text")

    with tempfile.TemporaryDirectory(prefix="lg2-season-tts-text-") as tmp_dir:
        tmp_root = Path(tmp_dir)
        parts = split_text_recursive(text, args.max_chars) if args.max_chars > 0 else [text]
        prepared = PreparedCue(
            cue={},
            cue_id="adhoc_text",
            text=text,
            output_path=output_path,
            fingerprint="",
        )
        tasks: list[PartTask] = []
        cue_dir: Path | None = None
        if len(parts) > 1:
            cue_dir = tmp_root / "adhoc_text"
            cue_dir.mkdir(parents=True, exist_ok=True)
        for index, part_text in enumerate(parts, start=1):
            tasks.append(
                PartTask(
                    prepared=prepared,
                    part_path=output_path if len(parts) == 1 else cue_dir / f"part_{index:02d}.wav",
                    part_text=part_text,
                    part_index=index,
                    total_parts=len(parts),
                )
            )

        run_worker_batch(tasks, args)
        part_paths = [task.part_path for task in tasks]
        concat_wav_files(part_paths, output_path)

    ticks = duration_to_ticks(audio_duration_seconds(output_path))
    print(f"[done] {output_path} ({ticks} ticks)")
    return 0


def add_qwen_arguments(parser: argparse.ArgumentParser) -> None:
    parser.add_argument("--qwen-python", type=Path, default=DEFAULT_QWEN_PYTHON)
    parser.add_argument("--qwen-script", type=Path, default=DEFAULT_QWEN_SCRIPT)
    parser.add_argument("--ref-audio", type=Path, default=DEFAULT_REF_AUDIO)
    parser.add_argument("--ref-text", default=None)
    parser.add_argument("--ref-text-file", type=Path, default=None)
    parser.add_argument("--model", default=None)
    parser.add_argument("--mode", default=DEFAULT_MODE, choices=["clone", "design"])
    parser.add_argument("--language", default=DEFAULT_LANGUAGE)
    parser.add_argument("--device", default=DEFAULT_DEVICE)
    parser.add_argument("--dtype", default=DEFAULT_DTYPE)
    parser.add_argument("--attn", default=DEFAULT_ATTN)
    parser.add_argument("--instruct", default=None)
    parser.add_argument(
        "--tail-silence-ms",
        type=int,
        default=DEFAULT_TAIL_SILENCE_MS,
        help="Append silence to the end of each rendered clip to avoid clipped endings.",
    )
    parser.add_argument(
        "--max-chars",
        type=int,
        default=DEFAULT_MAX_CHARS,
        help="Maximum characters per generated chunk. Use 0 to disable splitting entirely (default).",
    )


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Local Lost Glade voice generator powered by qwen3-say.py")
    subparsers = parser.add_subparsers(dest="command", required=True)

    season_config = subparsers.add_parser("season-config", help="Generate all cue files from a season config JSON")
    season_config.add_argument("config_path", type=Path)
    season_config.add_argument("--only", action="append", default=[], help="Glob pattern for cue ids, repeatable")
    season_config.add_argument("--force", action="store_true", help="Regenerate files even if they already exist")
    season_config.add_argument("--status-only", action="store_true", help="Only print READY/STALE/MISS status")
    season_config.add_argument("--write-duration-ticks", action="store_true", help="Write measured durations back to JSON")
    add_qwen_arguments(season_config)

    text = subparsers.add_parser("text", help="Generate a single local TTS file")
    text.add_argument("text")
    text.add_argument("-o", "--output", required=True, type=Path)
    add_qwen_arguments(text)

    return parser


def main() -> int:
    parser = build_parser()
    args = parser.parse_args()
    try:
        if args.command == "season-config":
            return handle_season_config(args)
        if args.command == "text":
            return handle_text(args)
        parser.error(f"Unsupported command: {args.command}")
        return 2
    except LocalTtsError as error:
        print(f"error: {error}", file=sys.stderr)
        return 1
    except KeyboardInterrupt:
        print("error: interrupted", file=sys.stderr)
        return 130


if __name__ == "__main__":
    raise SystemExit(main())
