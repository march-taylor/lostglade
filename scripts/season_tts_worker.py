#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
from pathlib import Path

import numpy as np
import soundfile as sf
import torch

try:
    torch.set_grad_enabled(False)
except Exception:
    pass

try:
    torch.backends.cuda.enable_flash_sdp(False)
    torch.backends.cuda.enable_mem_efficient_sdp(False)
    torch.backends.cuda.enable_math_sdp(True)
except Exception:
    pass

from qwen_tts import Qwen3TTSModel


MORIARTY_PROMPT = (
    "Very low Russian male voice, dark calm villain tone, cold intellectual delivery, "
    "slow confident speech, deep bass-baritone, restrained menace, clear Russian diction."
)


def pick_device(device: str) -> str:
    if device != "auto":
        return device
    if torch.cuda.is_available():
        return "cuda:0"
    if getattr(torch.backends, "mps", None) and torch.backends.mps.is_available():
        return "mps"
    return "cpu"


def pick_dtype(device: str, dtype: str) -> torch.dtype:
    if dtype == "float32":
        return torch.float32
    if dtype == "float16":
        return torch.float16
    if dtype == "bfloat16":
        return torch.bfloat16
    if device.startswith("cuda"):
        return torch.float16
    if device == "mps":
        return torch.float16
    return torch.float32


def load_model(model_id: str, device: str, dtype: torch.dtype, attn: str) -> Qwen3TTSModel:
    kwargs = {
        "device_map": device,
        "dtype": dtype,
    }
    if attn != "none":
        kwargs["attn_implementation"] = attn
    return Qwen3TTSModel.from_pretrained(model_id, **kwargs)


def write_audio(output: str, wav, sr: int) -> None:
    out = Path(output).expanduser()
    out.parent.mkdir(parents=True, exist_ok=True)
    sf.write(str(out), wav, sr)
    print(f"Saved: {out}", flush=True)


def to_numpy_audio(wav) -> np.ndarray:
    if hasattr(wav, "detach"):
        wav = wav.detach()
    if hasattr(wav, "cpu"):
        wav = wav.cpu()
    if hasattr(wav, "numpy"):
        wav = wav.numpy()
    return np.asarray(wav)


def append_tail_silence(wav, sr: int, tail_silence_ms: int):
    if tail_silence_ms <= 0:
        return wav
    audio = to_numpy_audio(wav)
    silence_samples = max(1, int(sr * (tail_silence_ms / 1000.0)))
    if audio.ndim == 1:
        silence = np.zeros(silence_samples, dtype=audio.dtype)
    else:
        silence_shape = (silence_samples,) + tuple(audio.shape[1:])
        silence = np.zeros(silence_shape, dtype=audio.dtype)
    return np.concatenate([audio, silence], axis=0)


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Batch Qwen3-TTS worker for Lost Glade")
    parser.add_argument("jobs_file", type=Path)
    parser.add_argument("--mode", choices=["clone", "design"], default="clone")
    parser.add_argument("--language", default="Russian")
    parser.add_argument("--model", default=None)
    parser.add_argument("--instruct", default=None)
    parser.add_argument("--ref-audio")
    parser.add_argument("--ref-text")
    parser.add_argument("--device", default="auto")
    parser.add_argument("--dtype", default="auto", choices=["auto", "float32", "float16", "bfloat16"])
    parser.add_argument("--attn", default="sdpa", choices=["sdpa", "eager", "flash_attention_2", "none"])
    parser.add_argument(
        "--cpu-threads",
        type=int,
        default=4,
        help="Maximum CPU worker threads for audio preparation; model inference still runs on the selected GPU.",
    )
    parser.add_argument("--tail-silence-ms", type=int, default=350)
    return parser


def main() -> int:
    parser = build_parser()
    args = parser.parse_args()
    cpu_threads = max(1, args.cpu_threads)
    # Qwen's small CPU-side audio/token preparation can otherwise claim every core even
    # when the model itself is correctly executing on ROCm. This does not change audio
    # quality; it only caps parallel preprocessing work.
    try:
        torch.set_num_threads(cpu_threads)
        torch.set_num_interop_threads(cpu_threads)
    except RuntimeError:
        pass

    jobs = json.loads(args.jobs_file.read_text(encoding="utf-8"))
    if not isinstance(jobs, list) or not jobs:
        raise SystemExit("No jobs provided.")

    device = pick_device(args.device)
    dtype = pick_dtype(device, args.dtype)
    print(f"[worker] loading model on {device} with {dtype} for {len(jobs)} job(s); CPU threads capped at {cpu_threads}", flush=True)

    with torch.inference_mode():
        if args.mode == "clone":
            if not args.ref_audio:
                raise SystemExit("--ref-audio is required for clone mode")
            model_id = args.model or "Qwen/Qwen3-TTS-12Hz-1.7B-Base"
            model = load_model(model_id, device, dtype, args.attn)
            prompt_kwargs = {
                "ref_audio": args.ref_audio,
            }
            if args.ref_text:
                prompt_kwargs["ref_text"] = args.ref_text
                prompt_kwargs["x_vector_only_mode"] = False
                print("[worker] creating reusable clone prompt with reference transcript", flush=True)
            else:
                prompt_kwargs["ref_text"] = None
                prompt_kwargs["x_vector_only_mode"] = True
                print("[worker] creating reusable clone prompt without reference transcript", flush=True)
            voice_clone_prompt = model.create_voice_clone_prompt(**prompt_kwargs)
            for index, job in enumerate(jobs, start=1):
                text = str(job.get("text") or "").strip()
                output = str(job.get("output") or "").strip()
                cue_id = str(job.get("cueId") or "").strip()
                part_index = int(job.get("partIndex") or 1)
                total_parts = int(job.get("totalParts") or 1)
                if not text or not output:
                    raise SystemExit(f"Invalid job at index {index}: {job}")
                if total_parts > 1:
                    print(
                        f"[worker] {index}/{len(jobs)} {cue_id} part {part_index}/{total_parts} ({len(text)} chars)",
                        flush=True,
                    )
                else:
                    print(
                        f"[worker] {index}/{len(jobs)} {cue_id} ({len(text)} chars)",
                        flush=True,
                    )

                kwargs = {
                    "text": text,
                    "language": args.language,
                    "voice_clone_prompt": voice_clone_prompt,
                }
                wavs, sr = model.generate_voice_clone(**kwargs)
                write_audio(output, append_tail_silence(wavs[0], sr, args.tail_silence_ms), sr)
        else:
            model_id = args.model or "Qwen/Qwen3-TTS-12Hz-1.7B-VoiceDesign"
            model = load_model(model_id, device, dtype, args.attn)
            for index, job in enumerate(jobs, start=1):
                text = str(job.get("text") or "").strip()
                output = str(job.get("output") or "").strip()
                cue_id = str(job.get("cueId") or "").strip()
                part_index = int(job.get("partIndex") or 1)
                total_parts = int(job.get("totalParts") or 1)
                if not text or not output:
                    raise SystemExit(f"Invalid job at index {index}: {job}")
                if total_parts > 1:
                    print(
                        f"[worker] {index}/{len(jobs)} {cue_id} part {part_index}/{total_parts} ({len(text)} chars)",
                        flush=True,
                    )
                else:
                    print(
                        f"[worker] {index}/{len(jobs)} {cue_id} ({len(text)} chars)",
                        flush=True,
                    )

                wavs, sr = model.generate_voice_design(
                    text=text,
                    language=args.language,
                    instruct=args.instruct or MORIARTY_PROMPT,
                )
                write_audio(output, append_tail_silence(wavs[0], sr, args.tail_silence_ms), sr)

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
