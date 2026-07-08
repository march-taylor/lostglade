#!/usr/bin/env python3
from __future__ import annotations

import argparse
import configparser
import contextlib
import json
import math
import os
import re
import shutil
import socket
import sqlite3
import subprocess
import sys
import tarfile
import tempfile
import time
import wave
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any
from urllib.parse import urlencode

try:
    import requests
except ImportError as exc:  # pragma: no cover - runtime guard
    raise SystemExit(
        "Missing dependency: requests. Install it with `python3 -m pip install requests`."
    ) from exc


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_MODEL_ID = "cc1b79b1108f4ed3b8aac118ba6ebd07"
DEFAULT_VERSION = "s2.1-pro"
DEFAULT_FISH_LOCALE = "ru"
DEFAULT_FISH_ORIGIN = "https://fish.audio"
DEFAULT_TTS_PATH = "/ru/app/text-to-speech/"
DEFAULT_PAUSE_SECONDS = 1.0
DEFAULT_TIMEOUT_SECONDS = 120.0
MAX_FISH_CHARS = 500
DEFAULT_GECKODRIVER_CACHE = Path("/tmp/geckodriver-test/geckodriver")
DEFAULT_ZEN_CONFIG_DIR = Path.home() / ".config" / "zen"
DEFAULT_ZEN_BINARY_CANDIDATES = [
    Path("/opt/zen-browser-bin/zen-bin"),
    Path("/usr/lib/zen-browser/zen-bin"),
]
GITHUB_API_LATEST_GECKO = "https://api.github.com/repos/mozilla/geckodriver/releases/latest"
PAUSE_RE = re.compile(
    r"^(?:пауза|pause)(?:\s+(?P<value>\d+(?:[.,]\d+)?)(?:\s*(?P<unit>с|сек|секунд(?:ы)?|s|sec|seconds?|тик(?:ов|а)?|ticks?))?)?[.!?…]*$",
    re.IGNORECASE,
)
WHITESPACE_RE = re.compile(r"\s+")


class FishTtsError(RuntimeError):
    pass


@dataclass(slots=True)
class ZenSessionData:
    token: str
    active_team_id: str
    active_workspace_id: str
    profile_dir: Path


@dataclass(slots=True)
class RawScriptItem:
    kind: str
    text: str | None = None
    seconds: float | None = None
    source_index: int = 0


@dataclass(slots=True)
class SpeechJob:
    job_id: str
    text: str
    output_path: Path
    source_index: int
    chunk_index: int
    total_chunks: int
    cue_id: str | None = None
    audio_file: str | None = None
    chat_text: str | None = None
    skip_if_exists: bool = False


def utc_now_iso() -> str:
    return datetime.now(timezone.utc).replace(microsecond=0).isoformat()


def slugify_stem(value: str, fallback: str = "voice") -> str:
    cleaned = re.sub(r"[^a-zA-Z0-9_-]+", "_", value.strip())
    cleaned = cleaned.strip("_")
    return cleaned or fallback


def normalize_tts_text(text: str) -> str:
    return WHITESPACE_RE.sub(" ", text).strip()


def parse_pause_seconds(text: str, default_seconds: float) -> float | None:
    match = PAUSE_RE.match(text.strip())
    if not match:
        return None
    value = match.group("value")
    unit = (match.group("unit") or "").lower()
    if not value:
        return default_seconds
    amount = float(value.replace(",", "."))
    if unit.startswith("тик") or unit.startswith("tick"):
        return amount / 20.0
    return amount


def parse_outside_quotes(source: str, start_index: int, default_pause_seconds: float) -> list[RawScriptItem]:
    items: list[RawScriptItem] = []
    paragraph_lines: list[str] = []
    paragraph_index = start_index

    def flush_paragraph() -> None:
        nonlocal paragraph_lines, paragraph_index
        if not paragraph_lines:
            return
        text = normalize_tts_text("\n".join(paragraph_lines))
        if text:
            items.append(RawScriptItem(kind="speech", text=text, source_index=paragraph_index))
        paragraph_lines = []

    for line_no, raw_line in enumerate(source.splitlines(), start=start_index):
        stripped = raw_line.strip()
        if not stripped:
            flush_paragraph()
            continue

        pause_seconds = parse_pause_seconds(stripped, default_pause_seconds)
        if pause_seconds is not None:
            flush_paragraph()
            items.append(RawScriptItem(kind="pause", seconds=pause_seconds, source_index=line_no))
            continue

        if not paragraph_lines:
            paragraph_index = line_no
        paragraph_lines.append(stripped)

    flush_paragraph()
    return items


def parse_script_text(source: str, default_pause_seconds: float) -> list[RawScriptItem]:
    items: list[RawScriptItem] = []
    outside_buffer: list[str] = []
    quote_buffer: list[str] = []
    in_quote = False
    source_index = 1
    quote_index = 1

    def flush_outside() -> None:
        nonlocal outside_buffer
        if not outside_buffer:
            return
        items.extend(parse_outside_quotes("".join(outside_buffer), source_index, default_pause_seconds))
        outside_buffer = []

    for char in source:
        if char == '"':
            if in_quote:
                text = normalize_tts_text("".join(quote_buffer))
                if text:
                    items.append(RawScriptItem(kind="speech", text=text, source_index=quote_index))
                quote_buffer = []
                in_quote = False
            else:
                flush_outside()
                in_quote = True
                quote_index = source_index
            continue

        if in_quote:
            quote_buffer.append(char)
        else:
            outside_buffer.append(char)

        if char == "\n":
            source_index += 1

    if in_quote and quote_buffer:
        text = normalize_tts_text("".join(quote_buffer))
        if text:
            items.append(RawScriptItem(kind="speech", text=text, source_index=quote_index))
    else:
        flush_outside()

    return items


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
    text = normalize_tts_text(text)
    if not text:
        return []
    if len(text) <= max_chars:
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

        candidate_len = len(unit) if not current else current_len + 1 + len(unit)
        if candidate_len <= max_chars:
            current.append(unit)
            current_len = candidate_len
            continue

        parts.append(" ".join(current))
        current = [unit]
        current_len = len(unit)

    if current:
        parts.append(" ".join(current))

    return parts


def split_for_fish(text: str, max_chars: int = MAX_FISH_CHARS) -> list[str]:
    parts = split_text_recursive(text, max_chars)
    final_parts: list[str] = []
    for part in parts:
        final_parts.extend(force_split_words(part, max_chars) if len(part) > max_chars else [part])
    return [part for part in final_parts if part]


def detect_zen_binary(explicit_binary: str | None) -> Path:
    candidates: list[Path] = []
    if explicit_binary:
        candidates.append(Path(explicit_binary).expanduser())
    candidates.extend(DEFAULT_ZEN_BINARY_CANDIDATES)
    for candidate in candidates:
        if candidate.exists():
            return candidate
    raise FishTtsError(
        "Zen binary not found. Pass --browser-binary /opt/zen-browser-bin/zen-bin."
    )


def detect_zen_profile(zen_config_dir: Path, explicit_profile: str | None) -> Path:
    def has_fish_storage(profile_dir: Path) -> bool:
        return (
            profile_dir
            / "storage"
            / "default"
            / "https+++fish.audio"
            / "ls"
            / "data.sqlite"
        ).exists()

    if explicit_profile:
        profile_path = Path(explicit_profile).expanduser()
        if profile_path.exists():
            return profile_path
        named_path = zen_config_dir / explicit_profile
        if named_path.exists():
            return named_path
        raise FishTtsError(f"Zen profile `{explicit_profile}` not found.")

    profiles_ini = zen_config_dir / "profiles.ini"
    discovered_profiles: list[Path] = []

    if profiles_ini.exists():
        parser = configparser.ConfigParser()
        parser.read(profiles_ini)
        for section in parser.sections():
            if not section.startswith("Profile"):
                continue
            if parser.get(section, "Default", fallback="0") != "1":
                continue
            raw_path = parser.get(section, "Path", fallback="")
            if not raw_path:
                continue
            is_relative = parser.getboolean(section, "IsRelative", fallback=True)
            profile_dir = (zen_config_dir / raw_path) if is_relative else Path(raw_path)
            if profile_dir.exists():
                discovered_profiles.append(profile_dir)
        for section in parser.sections():
            if not section.startswith("Profile"):
                continue
            raw_path = parser.get(section, "Path", fallback="")
            if not raw_path:
                continue
            is_relative = parser.getboolean(section, "IsRelative", fallback=True)
            profile_dir = (zen_config_dir / raw_path) if is_relative else Path(raw_path)
            if profile_dir.exists():
                discovered_profiles.append(profile_dir)

    profile_dirs = sorted(path for path in zen_config_dir.iterdir() if path.is_dir())
    for profile_dir in profile_dirs:
        if profile_dir not in discovered_profiles:
            discovered_profiles.append(profile_dir)

    for profile_dir in discovered_profiles:
        if has_fish_storage(profile_dir):
            return profile_dir

    for profile_dir in profile_dirs:
        if "default" in profile_dir.name.lower():
            return profile_dir
    if discovered_profiles:
        return discovered_profiles[0]
    raise FishTtsError(f"No Zen profiles found in {zen_config_dir}.")


def read_zen_fish_session(profile_dir: Path) -> ZenSessionData:
    db_path = profile_dir / "storage" / "default" / "https+++fish.audio" / "ls" / "data.sqlite"
    if not db_path.exists():
        raise FishTtsError(f"Fish localStorage database not found: {db_path}")

    with tempfile.NamedTemporaryFile(prefix="fish-audio-ls-", suffix=".sqlite", delete=False) as handle:
        temp_db_path = Path(handle.name)
    try:
        shutil.copy2(db_path, temp_db_path)
        values: dict[str, str] = {}
        with sqlite3.connect(temp_db_path) as connection:
            cursor = connection.execute(
                "SELECT key, value FROM data WHERE key IN ('token', 'active_team_id', 'active_workspace_id')"
            )
            for key, value in cursor.fetchall():
                payload = value.tobytes() if isinstance(value, memoryview) else value
                if isinstance(payload, bytes):
                    values[key] = payload.decode("utf-8", errors="ignore").replace("\x00", "")
        token = values.get("token", "").strip()
        team_id = values.get("active_team_id", "").strip()
        workspace_id = values.get("active_workspace_id", "").strip()
        if not token or not team_id or not workspace_id:
            raise FishTtsError(
                f"Fish session keys were not found in {db_path}. Open fish.audio in Zen once and log in."
            )
        return ZenSessionData(
            token=token,
            active_team_id=team_id,
            active_workspace_id=workspace_id,
            profile_dir=profile_dir,
        )
    finally:
        temp_db_path.unlink(missing_ok=True)


def pick_free_port() -> int:
    with contextlib.closing(socket.socket(socket.AF_INET, socket.SOCK_STREAM)) as sock:
        sock.bind(("127.0.0.1", 0))
        return int(sock.getsockname()[1])


def ensure_geckodriver(explicit_path: str | None, auto_download: bool) -> Path:
    candidates: list[Path] = []
    if explicit_path:
        candidates.append(Path(explicit_path).expanduser())
    env_path = os.environ.get("GECKODRIVER")
    if env_path:
        candidates.append(Path(env_path).expanduser())
    which_path = shutil.which("geckodriver")
    if which_path:
        candidates.append(Path(which_path))
    candidates.append(DEFAULT_GECKODRIVER_CACHE)

    for candidate in candidates:
        if candidate.exists():
            return candidate

    if not auto_download:
        raise FishTtsError(
            "geckodriver not found. Pass --geckodriver /path/to/geckodriver or use --download-geckodriver."
        )

    DEFAULT_GECKODRIVER_CACHE.parent.mkdir(parents=True, exist_ok=True)
    response = requests.get(GITHUB_API_LATEST_GECKO, timeout=30)
    response.raise_for_status()
    release = response.json()
    asset = next(
        (
            item
            for item in release.get("assets", [])
            if str(item.get("name", "")).endswith("linux64.tar.gz")
        ),
        None,
    )
    if asset is None:
        raise FishTtsError("Could not locate a linux64 geckodriver asset in the latest GitHub release.")

    tarball_path = DEFAULT_GECKODRIVER_CACHE.parent / asset["name"]
    with requests.get(asset["browser_download_url"], stream=True, timeout=60) as download:
        download.raise_for_status()
        with tarball_path.open("wb") as handle:
            for chunk in download.iter_content(chunk_size=1024 * 1024):
                if chunk:
                    handle.write(chunk)

    with tarfile.open(tarball_path, "r:gz") as archive:
        member = archive.getmember("geckodriver")
        archive.extract(member, DEFAULT_GECKODRIVER_CACHE.parent)
    tarball_path.unlink(missing_ok=True)
    DEFAULT_GECKODRIVER_CACHE.chmod(0o755)
    return DEFAULT_GECKODRIVER_CACHE


class WebDriverClient:
    def __init__(
        self,
        geckodriver_path: Path,
        browser_binary: Path,
        *,
        headless: bool,
        log_path: Path,
    ) -> None:
        self.geckodriver_path = geckodriver_path
        self.browser_binary = browser_binary
        self.headless = headless
        self.log_path = log_path
        self.port = pick_free_port()
        self.base_url = f"http://127.0.0.1:{self.port}"
        self.session = requests.Session()
        self.process: subprocess.Popen[str] | None = None
        self.session_id: str | None = None
        self.profile_dir: Path | None = None
        self.download_dir: Path | None = None

    def start(self) -> None:
        self.profile_dir = Path(tempfile.mkdtemp(prefix="zen-fish-profile-"))
        self.download_dir = self.profile_dir / "downloads"
        self.download_dir.mkdir(parents=True, exist_ok=True)
        log_handle = self.log_path.open("w", encoding="utf-8")
        self.process = subprocess.Popen(
            [str(self.geckodriver_path), "--port", str(self.port)],
            stdout=log_handle,
            stderr=subprocess.STDOUT,
            text=True,
        )
        self._wait_for_driver()

        args = ["-profile", str(self.profile_dir)]
        if self.headless:
            args.insert(0, "--headless")

        payload = {
            "capabilities": {
                "alwaysMatch": {
                    "browserName": "firefox",
                    "acceptInsecureCerts": True,
                    "moz:firefoxOptions": {
                        "binary": str(self.browser_binary),
                        "args": args,
                        "prefs": {
                            "browser.download.folderList": 2,
                            "browser.download.dir": str(self.download_dir),
                            "browser.download.useDownloadDir": True,
                            "browser.download.manager.showWhenStarting": False,
                            "browser.helperApps.neverAsk.saveToDisk": (
                                "audio/mpeg,audio/mp3,audio/wav,audio/x-wav,application/octet-stream"
                            ),
                            "browser.download.always_ask_before_handling_new_types": False,
                        },
                    },
                }
            }
        }
        response = self.session.post(f"{self.base_url}/session", json=payload, timeout=60)
        response.raise_for_status()
        body = response.json()
        self.session_id = body.get("sessionId") or body.get("value", {}).get("sessionId")
        if not self.session_id:
            raise FishTtsError(f"Failed to create WebDriver session: {body}")

    def _wait_for_driver(self) -> None:
        deadline = time.time() + 20.0
        while time.time() < deadline:
            if self.process and self.process.poll() is not None:
                raise FishTtsError(
                    f"geckodriver exited early with code {self.process.returncode}. See {self.log_path}"
                )
            try:
                response = self.session.get(f"{self.base_url}/status", timeout=2)
                if response.ok and response.json().get("value", {}).get("ready", False):
                    return
            except requests.RequestException:
                pass
            time.sleep(0.25)
        raise FishTtsError(f"Timed out waiting for geckodriver. See {self.log_path}")

    def navigate(self, url: str) -> None:
        self._post("/url", {"url": url}, timeout=60)

    def execute(self, script: str, args: list[Any] | None = None) -> Any:
        return self._post("/execute/sync", {"script": script, "args": args or []}, timeout=60)

    def wait_for(self, predicate, *, timeout: float, interval: float = 0.5, description: str) -> Any:
        deadline = time.time() + timeout
        last_value = None
        while time.time() < deadline:
            last_value = predicate()
            if last_value:
                return last_value
            time.sleep(interval)
        raise FishTtsError(f"Timed out waiting for {description}.")

    def _post(self, path: str, payload: dict[str, Any], *, timeout: float) -> Any:
        if not self.session_id:
            raise FishTtsError("WebDriver session was not started.")
        response = self.session.post(
            f"{self.base_url}/session/{self.session_id}{path}",
            json=payload,
            timeout=timeout,
        )
        response.raise_for_status()
        body = response.json()
        value = body.get("value")
        if isinstance(value, dict) and value.get("error"):
            raise FishTtsError(f"WebDriver error: {value}")
        return value

    def close(self) -> None:
        if self.session_id:
            with contextlib.suppress(Exception):
                self.session.delete(f"{self.base_url}/session/{self.session_id}", timeout=15)
            self.session_id = None
        if self.process:
            self.process.terminate()
            with contextlib.suppress(Exception):
                self.process.wait(timeout=5)
            if self.process.poll() is None:
                self.process.kill()
            self.process = None
        if self.profile_dir:
            shutil.rmtree(self.profile_dir, ignore_errors=True)
            self.profile_dir = None
        self.session.close()


class FishTtsAutomation:
    def __init__(
        self,
        webdriver: WebDriverClient,
        zen_session: ZenSessionData,
        *,
        locale: str,
        model_id: str,
        version: str,
        timeout_seconds: float,
        warmup_seconds: float,
    ) -> None:
        self.webdriver = webdriver
        self.zen_session = zen_session
        self.locale = locale
        self.model_id = model_id
        self.version = version
        self.timeout_seconds = timeout_seconds
        self.warmup_seconds = warmup_seconds

    @property
    def tts_url(self) -> str:
        return f"{DEFAULT_FISH_ORIGIN}/{self.locale}/app/text-to-speech/"

    def initialize(self) -> None:
        self.webdriver.start()
        self.webdriver.navigate(f"{DEFAULT_FISH_ORIGIN}/{self.locale}/")
        self.webdriver.execute(
            """
            localStorage.setItem("token", arguments[0]);
            localStorage.setItem("active_team_id", arguments[1]);
            localStorage.setItem("active_workspace_id", arguments[2]);
            return {
              href: location.href,
              token: localStorage.getItem("token"),
              team: localStorage.getItem("active_team_id"),
              workspace: localStorage.getItem("active_workspace_id")
            };
            """,
            [
                self.zen_session.token,
                self.zen_session.active_team_id,
                self.zen_session.active_workspace_id,
            ],
        )

    def generate_mp3(self, text: str, output_path: Path) -> str:
        normalized = normalize_tts_text(text)
        if not normalized:
            raise FishTtsError("Cannot generate TTS for an empty text fragment.")
        if len(normalized) > MAX_FISH_CHARS:
            raise FishTtsError(
                f"Text fragment is {len(normalized)} chars long. Split it before generation."
            )

        params = urlencode(
            [
                ("modelId", self.model_id),
                ("modelIds", self.model_id),
                ("version", self.version),
                ("text", normalized),
            ]
        )
        self.webdriver.navigate(f"{self.tts_url}?{params}")
        state = self.webdriver.wait_for(
            lambda: self.webdriver.execute(
                """
                const editor = document.querySelector('[contenteditable="true"]');
                const generateButton = Array.from(document.querySelectorAll('button'))
                  .find((el) => (el.innerText || '').includes('Генерировать речь'));
                if (!editor || !generateButton) {
                  return null;
                }
                return {
                  editorText: (editor.innerText || '').trim(),
                  generateDisabled: !!generateButton.disabled,
                  audioSources: Array.from(document.querySelectorAll('audio'))
                    .map((el) => el.currentSrc || el.src)
                    .filter(Boolean),
                };
                """
            ),
            timeout=self.timeout_seconds,
            description="Fish editor bootstrap",
        )
        before_sources = set(state["audioSources"])
        before_download_buttons = self._download_button_count()
        before_download_files = self._downloaded_files()
        before_page_urls = self._page_mp3_urls()
        if state["editorText"] != normalized:
            raise FishTtsError(
                "Fish did not preload the expected text into the editor. "
                f"Expected `{normalized}`, got `{state['editorText']}`."
            )
        if state["generateDisabled"]:
            raise FishTtsError("Generate button stayed disabled after text bootstrap.")
        if self.warmup_seconds > 0:
            time.sleep(self.warmup_seconds)
            state = self.webdriver.execute(
                """
                const editor = document.querySelector('[contenteditable="true"]');
                const generateButton = Array.from(document.querySelectorAll('button'))
                  .find((el) => (el.innerText || '').includes('Генерировать речь'));
                return {
                  editorText: editor ? (editor.innerText || '').trim() : '',
                  generateDisabled: generateButton ? !!generateButton.disabled : true,
                  audioSources: Array.from(document.querySelectorAll('audio'))
                    .map((el) => el.currentSrc || el.src)
                    .filter(Boolean),
                };
                """
            )
            before_sources = set(state["audioSources"])
            before_download_buttons = self._download_button_count()
            before_download_files = self._downloaded_files()
            before_page_urls = self._page_mp3_urls()
            if state["editorText"] != normalized or state["generateDisabled"]:
                raise FishTtsError("Fish page changed state during warmup and is not ready for generation.")

        click_result = self.webdriver.execute(
            """
            const generateButton = Array.from(document.querySelectorAll('button'))
              .find((el) => (el.innerText || '').includes('Генерировать речь'));
            if (!generateButton) {
              return { clicked: false, reason: 'button_not_found' };
            }
            generateButton.click();
            return { clicked: true };
            """
        )
        if not click_result or not click_result.get("clicked"):
            raise FishTtsError(f"Failed to click generate button: {click_result}")

        result = self._wait_for_generation_result(before_sources, before_download_buttons, before_page_urls)
        if result["mode"] == "url":
            audio_url = str(result["value"])
            download_file(audio_url, output_path)
            return audio_url

        click_result = self.webdriver.execute(
            """
            const buttons = Array.from(document.querySelectorAll('button'))
              .filter((el) => el.getAttribute('aria-label') === 'Скачать');
            if (!buttons.length) {
              return { clicked: false, count: 0 };
            }
            buttons[0].click();
            return { clicked: true, count: buttons.length };
            """
        )
        if not click_result or not click_result.get("clicked"):
            raise FishTtsError(f"Fish showed a download button but it could not be clicked: {click_result}")
        downloaded_file = self._wait_for_downloaded_file(before_download_files)
        output_path.parent.mkdir(parents=True, exist_ok=True)
        shutil.move(str(downloaded_file), str(output_path))
        return f"browser-download:{downloaded_file.name}"

    def _new_audio_url(self, before_sources: set[str]) -> str | None:
        data = self.webdriver.execute(
            """
            return Array.from(document.querySelectorAll('audio'))
              .map((el) => el.currentSrc || el.src)
              .filter(Boolean);
            """
        )
        for source in data or []:
            if source not in before_sources:
                return source
        return None

    def _download_button_count(self) -> int:
        count = self.webdriver.execute(
            """
            return Array.from(document.querySelectorAll('button'))
              .filter((el) => el.getAttribute('aria-label') === 'Скачать').length;
            """
        )
        return int(count or 0)

    def _page_mp3_urls(self) -> set[str]:
        urls = self.webdriver.execute(
            """
            const html = document.documentElement.innerHTML.replaceAll('&amp;', '&');
            const matches = html.match(/https:\\/\\/[^"'\\s<>]+\\.mp3[^"'\\s<>]*/g) || [];
            return Array.from(new Set(matches));
            """
        )
        return {str(url) for url in (urls or [])}

    def _downloaded_files(self) -> set[Path]:
        if not self.webdriver.download_dir:
            return set()
        return {
            path
            for path in self.webdriver.download_dir.iterdir()
            if path.is_file() and not path.name.endswith(".part")
        }

    def _wait_for_downloaded_file(self, before_files: set[Path]) -> Path:
        if not self.webdriver.download_dir:
            raise FishTtsError("Browser download directory is not configured.")
        deadline = time.time() + self.timeout_seconds
        while time.time() < deadline:
            current_files = self._downloaded_files()
            new_files = [path for path in current_files if path not in before_files]
            if new_files:
                newest = max(new_files, key=lambda path: path.stat().st_mtime)
                if newest.stat().st_size > 0:
                    return newest
            time.sleep(0.5)
        raise FishTtsError("Timed out waiting for the Fish download file to appear in the browser download directory.")

    def _wait_for_generation_result(
        self,
        before_sources: set[str],
        before_download_buttons: int,
        before_page_urls: set[str],
    ) -> dict[str, Any]:
        deadline = time.time() + self.timeout_seconds
        while time.time() < deadline:
            source = self._new_audio_url(before_sources)
            if source:
                return {"mode": "url", "value": source}
            page_urls = self._page_mp3_urls()
            new_page_urls = [url for url in page_urls if url not in before_page_urls]
            if new_page_urls:
                return {"mode": "url", "value": new_page_urls[0]}
            if self._download_button_count() > before_download_buttons:
                return {"mode": "download_button", "value": None}
            time.sleep(0.5)
        snapshot = self.webdriver.execute(
            """
            return {
              href: location.href,
              body: document.body.innerText.slice(0, 3000),
              buttons: Array.from(document.querySelectorAll('button'))
                .map((el) => ({
                  text: (el.innerText || '').trim(),
                  aria: el.getAttribute('aria-label'),
                  disabled: !!el.disabled
                }))
                .filter((item) => item.text || item.aria),
              audioSources: Array.from(document.querySelectorAll('audio'))
                .map((el) => el.currentSrc || el.src)
                .filter(Boolean),
              hasRecaptcha: typeof window.grecaptcha !== 'undefined'
            };
            """
        )
        raise FishTtsError(f"Timed out waiting for Fish audio. Snapshot: {snapshot}")


def download_file(url: str, output_path: Path) -> None:
    output_path.parent.mkdir(parents=True, exist_ok=True)
    with requests.get(url, stream=True, timeout=60) as response:
        response.raise_for_status()
        with output_path.open("wb") as handle:
            for chunk in response.iter_content(chunk_size=1024 * 1024):
                if chunk:
                    handle.write(chunk)


def transcode_mp3_to_wav(mp3_path: Path, wav_path: Path, ffmpeg_binary: str) -> None:
    wav_path.parent.mkdir(parents=True, exist_ok=True)
    subprocess.run(
        [
            ffmpeg_binary,
            "-y",
            "-i",
            str(mp3_path),
            "-ac",
            "1",
            "-ar",
            "48000",
            "-c:a",
            "pcm_s16le",
            str(wav_path),
        ],
        check=True,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )


def concat_wav_files(source_paths: list[Path], output_path: Path) -> None:
    if not source_paths:
        raise FishTtsError("No wav chunks were provided for concatenation.")
    output_path.parent.mkdir(parents=True, exist_ok=True)
    with wave.open(str(source_paths[0]), "rb") as first_wave:
        params = first_wave.getparams()
        frames = [first_wave.readframes(first_wave.getnframes())]
    for path in source_paths[1:]:
        with wave.open(str(path), "rb") as chunk_wave:
            if chunk_wave.getparams()[:4] != params[:4]:
                raise FishTtsError(f"WAV chunk parameters do not match for {path}")
            frames.append(chunk_wave.readframes(chunk_wave.getnframes()))
    with wave.open(str(output_path), "wb") as final_wave:
        final_wave.setparams(params)
        for frame_block in frames:
            final_wave.writeframes(frame_block)


def measure_wav_seconds(path: Path) -> float:
    with wave.open(str(path), "rb") as handle:
        frames = handle.getnframes()
        rate = handle.getframerate()
    return frames / float(rate)


def measure_audio_seconds(path: Path, ffprobe_binary: str) -> float:
    if path.suffix.lower() == ".wav":
        return measure_wav_seconds(path)
    result = subprocess.run(
        [
            ffprobe_binary,
            "-v",
            "error",
            "-show_entries",
            "format=duration",
            "-of",
            "default=noprint_wrappers=1:nokey=1",
            str(path),
        ],
        check=True,
        capture_output=True,
        text=True,
    )
    return float(result.stdout.strip())


def duration_to_ticks(seconds: float) -> int:
    return max(1, math.ceil(seconds * 20.0))


def build_script_jobs(
    script_items: list[RawScriptItem],
    *,
    output_dir: Path,
    stem: str,
    audio_extension: str,
) -> tuple[list[SpeechJob], list[dict[str, Any]]]:
    jobs: list[SpeechJob] = []
    manifest_items: list[dict[str, Any]] = []
    speech_index = 1

    for raw_item in script_items:
        if raw_item.kind == "pause":
            manifest_items.append(
                {
                    "type": "pause",
                    "source_index": raw_item.source_index,
                    "seconds": raw_item.seconds,
                    "duration_ticks": duration_to_ticks(raw_item.seconds or 0.0),
                }
            )
            continue

        chunks = split_for_fish(raw_item.text or "")
        total_chunks = len(chunks)
        for chunk_index, chunk in enumerate(chunks, start=1):
            file_name = f"{stem}_{speech_index:03d}.{audio_extension}"
            output_path = output_dir / file_name
            job = SpeechJob(
                job_id=f"{stem}_{speech_index:03d}",
                text=chunk,
                output_path=output_path,
                source_index=raw_item.source_index,
                chunk_index=chunk_index,
                total_chunks=total_chunks,
            )
            jobs.append(job)
            manifest_items.append(
                {
                    "type": "speech",
                    "job_id": job.job_id,
                    "source_index": job.source_index,
                    "chunk_index": chunk_index,
                    "total_chunks": total_chunks,
                    "text": chunk,
                    "chars": len(chunk),
                    "audio_file": file_name,
                }
            )
            speech_index += 1

    return jobs, manifest_items


def load_json(path: Path) -> dict[str, Any]:
    with path.open("r", encoding="utf-8") as handle:
        return json.load(handle)


def write_json(path: Path, payload: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8") as handle:
        json.dump(payload, handle, ensure_ascii=False, indent=2)
        handle.write("\n")


def build_season_jobs(
    config_path: Path,
    *,
    voice_root: Path,
    skip_existing: bool,
) -> tuple[list[SpeechJob], list[dict[str, Any]], dict[str, Any]]:
    config_data = load_json(config_path)
    cues = config_data.get("cues", [])
    jobs: list[SpeechJob] = []
    manifest_items: list[dict[str, Any]] = []

    for cue_index, cue in enumerate(cues, start=1):
        cue_id = str(cue.get("id") or f"cue_{cue_index:03d}")
        chat_text = normalize_tts_text(str(cue.get("chatText") or ""))
        audio_file = str(cue.get("audioFile") or "").strip()
        if not chat_text or not audio_file:
            continue

        chunks = split_for_fish(chat_text)
        target_path = voice_root / audio_file
        manifest_entry = {
            "type": "cue",
            "cue_id": cue_id,
            "trigger": cue.get("trigger"),
            "audio_file": audio_file,
            "chat_text": chat_text,
            "chunks": [],
        }
        for chunk_index, chunk in enumerate(chunks, start=1):
            chunk_name = f"{target_path.stem}__part_{chunk_index:02d}.wav"
            chunk_path = target_path.parent / ".fish_chunks" / chunk_name
            jobs.append(
                SpeechJob(
                    job_id=f"{cue_id}__part_{chunk_index:02d}",
                    text=chunk,
                    output_path=chunk_path,
                    source_index=cue_index,
                    chunk_index=chunk_index,
                    total_chunks=len(chunks),
                    cue_id=cue_id,
                    audio_file=audio_file,
                    chat_text=chat_text,
                    skip_if_exists=skip_existing,
                )
            )
            manifest_entry["chunks"].append(
                {
                    "chunk_index": chunk_index,
                    "text": chunk,
                    "chars": len(chunk),
                    "temp_wav": str(chunk_path.relative_to(voice_root)),
                }
            )
        manifest_items.append(manifest_entry)

    return jobs, manifest_items, config_data


def render_script_mode(args: argparse.Namespace) -> None:
    source_path = Path(args.input).expanduser()
    output_dir = Path(args.output_dir).expanduser()
    output_dir.mkdir(parents=True, exist_ok=True)

    script_source = source_path.read_text(encoding="utf-8")
    script_items = parse_script_text(script_source, args.default_pause_seconds)
    stem = slugify_stem(args.stem or source_path.stem, fallback="scene")
    jobs, manifest_items = build_script_jobs(
        script_items,
        output_dir=output_dir,
        stem=stem,
        audio_extension=args.format,
    )

    log_path = output_dir / "fish_audio_tts_geckodriver.log"
    webdriver = WebDriverClient(
        ensure_geckodriver(args.geckodriver, args.download_geckodriver),
        detect_zen_binary(args.browser_binary),
        headless=not args.show_browser,
        log_path=log_path,
    )
    zen_session = read_zen_fish_session(
        detect_zen_profile(Path(args.zen_config_dir).expanduser(), args.zen_profile)
    )
    automation = FishTtsAutomation(
        webdriver,
        zen_session,
        locale=args.locale,
        model_id=args.model_id,
        version=args.version,
        timeout_seconds=args.timeout,
        warmup_seconds=args.warmup_seconds,
    )

    generated_items: list[dict[str, Any]] = []
    try:
        automation.initialize()
        for job in jobs:
            print(f"[{job.job_id}] generating {len(job.text)} chars -> {job.output_path.name}")
            mp3_target = job.output_path if args.format == "mp3" else job.output_path.with_suffix(".mp3")
            audio_url = automation.generate_mp3(job.text, mp3_target)
            final_output = job.output_path
            if args.format == "wav":
                transcode_mp3_to_wav(mp3_target, final_output, args.ffmpeg)
                if not args.keep_mp3:
                    mp3_target.unlink(missing_ok=True)
            seconds = measure_audio_seconds(final_output, args.ffprobe)
            generated_items.append(
                {
                    "job_id": job.job_id,
                    "source_index": job.source_index,
                    "chunk_index": job.chunk_index,
                    "total_chunks": job.total_chunks,
                    "text": job.text,
                    "audio_file": final_output.name,
                    "audio_url": audio_url,
                    "duration_seconds": round(seconds, 3),
                    "duration_ticks": duration_to_ticks(seconds),
                }
            )
    finally:
        webdriver.close()

    manifest_path = Path(args.manifest).expanduser() if args.manifest else output_dir / f"{stem}_manifest.json"
    manifest_payload = {
        "type": "fish-audio-script",
        "generated_at": utc_now_iso(),
        "source_file": str(source_path),
        "output_dir": str(output_dir),
        "voice_model_id": args.model_id,
        "voice_version": args.version,
        "items": [],
    }
    generated_by_job = {item["job_id"]: item for item in generated_items}
    for item in manifest_items:
        if item["type"] == "pause":
            manifest_payload["items"].append(item)
            continue
        generated = generated_by_job[item["job_id"]]
        manifest_payload["items"].append({**item, **generated})

    write_json(manifest_path, manifest_payload)
    print(f"Saved {len(generated_items)} voiced chunks to {output_dir}")
    print(f"Manifest: {manifest_path}")


def render_season_config_mode(args: argparse.Namespace) -> None:
    config_path = Path(args.config).expanduser()
    voice_root = Path(args.voice_root).expanduser() if args.voice_root else config_path.parent / "lg2-season-start"
    voice_root.mkdir(parents=True, exist_ok=True)

    jobs, manifest_items, config_data = build_season_jobs(
        config_path,
        voice_root=voice_root,
        skip_existing=not args.force,
    )

    log_path = voice_root / "fish_audio_tts_geckodriver.log"
    webdriver = WebDriverClient(
        ensure_geckodriver(args.geckodriver, args.download_geckodriver),
        detect_zen_binary(args.browser_binary),
        headless=not args.show_browser,
        log_path=log_path,
    )
    zen_session = read_zen_fish_session(
        detect_zen_profile(Path(args.zen_config_dir).expanduser(), args.zen_profile)
    )
    automation = FishTtsAutomation(
        webdriver,
        zen_session,
        locale=args.locale,
        model_id=args.model_id,
        version=args.version,
        timeout_seconds=args.timeout,
        warmup_seconds=args.warmup_seconds,
    )

    cue_outputs: dict[str, dict[str, Any]] = {}
    try:
        automation.initialize()
        for job in jobs:
            cue_output = cue_outputs.setdefault(
                job.cue_id or job.job_id,
                {
                    "cue_id": job.cue_id,
                    "audio_file": job.audio_file,
                    "chat_text": job.chat_text,
                    "chunk_files": [],
                },
            )
            if job.output_path.exists() and job.skip_if_exists:
                print(f"[{job.job_id}] reusing existing chunk {job.output_path}")
                cue_output["chunk_files"].append(job.output_path)
                continue
            print(f"[{job.job_id}] generating {len(job.text)} chars")
            mp3_target = job.output_path.with_suffix(".mp3")
            automation.generate_mp3(job.text, mp3_target)
            transcode_mp3_to_wav(mp3_target, job.output_path, args.ffmpeg)
            if not args.keep_mp3:
                mp3_target.unlink(missing_ok=True)
            cue_output["chunk_files"].append(job.output_path)
    finally:
        webdriver.close()

    duration_updates: dict[str, int] = {}
    for manifest_entry in manifest_items:
        cue_id = manifest_entry["cue_id"]
        cue_output = cue_outputs.get(cue_id)
        if not cue_output:
            continue
        final_path = voice_root / manifest_entry["audio_file"]
        chunk_files: list[Path] = cue_output["chunk_files"]
        if not chunk_files:
            continue
        if len(chunk_files) == 1:
            final_path.parent.mkdir(parents=True, exist_ok=True)
            if chunk_files[0] != final_path:
                shutil.copy2(chunk_files[0], final_path)
        else:
            concat_wav_files(chunk_files, final_path)
        seconds = measure_wav_seconds(final_path)
        ticks = duration_to_ticks(seconds)
        manifest_entry["duration_seconds"] = round(seconds, 3)
        manifest_entry["duration_ticks"] = ticks
        manifest_entry["final_wav"] = str(final_path.relative_to(voice_root))
        duration_updates[cue_id] = ticks
        if not args.keep_chunks:
            for chunk_file in chunk_files:
                if chunk_file != final_path:
                    chunk_file.unlink(missing_ok=True)

    if args.write_duration_ticks:
        for cue in config_data.get("cues", []):
            cue_id = str(cue.get("id") or "")
            if cue_id in duration_updates:
                cue["durationTicks"] = duration_updates[cue_id]
        write_json(config_path, config_data)
        print(f"Updated cue durationTicks in {config_path}")

    manifest_path = Path(args.manifest).expanduser() if args.manifest else voice_root / "season_tts_manifest.json"
    manifest_payload = {
        "type": "fish-audio-season-config",
        "generated_at": utc_now_iso(),
        "config_file": str(config_path),
        "voice_root": str(voice_root),
        "voice_model_id": args.model_id,
        "voice_version": args.version,
        "items": manifest_items,
    }
    write_json(manifest_path, manifest_payload)
    print(f"Saved season-start voice files under {voice_root}")
    print(f"Manifest: {manifest_path}")


def build_arg_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Generate Fish Audio TTS through a Zen profile session and export the result for Lost Glade.",
    )
    parser.add_argument("--model-id", default=DEFAULT_MODEL_ID, help="Fish model id to use.")
    parser.add_argument("--version", default=DEFAULT_VERSION, help="Fish TTS version to request.")
    parser.add_argument("--locale", default=DEFAULT_FISH_LOCALE, help="Fish locale prefix, for example `ru`.")
    parser.add_argument("--zen-config-dir", default=str(DEFAULT_ZEN_CONFIG_DIR), help="Zen config root.")
    parser.add_argument("--zen-profile", help="Zen profile path or profile directory name.")
    parser.add_argument("--browser-binary", help="Direct path to the real Zen browser binary.")
    parser.add_argument("--geckodriver", help="Path to geckodriver.")
    parser.add_argument("--download-geckodriver", action="store_true", help="Auto-download geckodriver if missing.")
    parser.add_argument("--show-browser", action="store_true", help="Show the real browser instead of headless mode.")
    parser.add_argument("--timeout", type=float, default=DEFAULT_TIMEOUT_SECONDS, help="Per-fragment generation timeout.")
    parser.add_argument("--warmup-seconds", type=float, default=2.5, help="Extra wait after TTS page bootstrap before clicking generate.")
    parser.add_argument("--ffmpeg", default=shutil.which("ffmpeg") or "ffmpeg", help="ffmpeg binary.")
    parser.add_argument("--ffprobe", default=shutil.which("ffprobe") or "ffprobe", help="ffprobe binary.")
    parser.add_argument("--keep-mp3", action="store_true", help="Keep the raw mp3 downloaded from Fish.")

    subparsers = parser.add_subparsers(dest="command", required=True)

    script_parser = subparsers.add_parser("script", help="Parse a plain text scene file and voice every speech fragment.")
    script_parser.add_argument("input", help="Path to the source script file.")
    script_parser.add_argument("--output-dir", required=True, help="Directory for the generated audio files.")
    script_parser.add_argument("--format", choices=("wav", "mp3"), default="wav", help="Final file format.")
    script_parser.add_argument("--manifest", help="Explicit manifest output path.")
    script_parser.add_argument("--stem", help="Base filename stem for generated files.")
    script_parser.add_argument("--default-pause-seconds", type=float, default=DEFAULT_PAUSE_SECONDS, help="Pause duration for `Пауза.` without a value.")

    season_parser = subparsers.add_parser(
        "season-config",
        help="Read config/lg2-season-start.json and generate wav files for every cue chatText.",
    )
    season_parser.add_argument("config", help="Path to lg2-season-start.json.")
    season_parser.add_argument("--voice-root", help="Output root for relative cue audioFile paths.")
    season_parser.add_argument("--manifest", help="Explicit manifest output path.")
    season_parser.add_argument("--force", action="store_true", help="Regenerate even if temporary chunk files already exist.")
    season_parser.add_argument("--keep-chunks", action="store_true", help="Keep temporary per-chunk wav files.")
    season_parser.add_argument("--write-duration-ticks", action="store_true", help="Write measured durationTicks back into the config json.")

    return parser


def main() -> None:
    parser = build_arg_parser()
    args = parser.parse_args()
    try:
        if args.command == "script":
            render_script_mode(args)
        elif args.command == "season-config":
            render_season_config_mode(args)
        else:  # pragma: no cover - argparse already guards this
            raise FishTtsError(f"Unsupported command: {args.command}")
    except KeyboardInterrupt:
        raise SystemExit(130)
    except FishTtsError as exc:
        print(f"error: {exc}", file=sys.stderr)
        raise SystemExit(1)
    except requests.RequestException as exc:
        print(f"network error: {exc}", file=sys.stderr)
        raise SystemExit(1)
    except subprocess.CalledProcessError as exc:
        print(f"subprocess failed: {exc}", file=sys.stderr)
        raise SystemExit(exc.returncode or 1)


if __name__ == "__main__":
    main()
