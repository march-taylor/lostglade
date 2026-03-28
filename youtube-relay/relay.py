#!/usr/bin/env python3
from __future__ import annotations

import argparse
import base64
import json
import os
import shlex
import subprocess
import threading
import time
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import Any
from urllib.parse import parse_qs, urlparse


YT_DLP_BIN = os.environ.get("YT_DLP_BIN", "yt-dlp")
FFMPEG_BIN = os.environ.get("FFMPEG_BIN", "ffmpeg")
FRAME_RATE = float(os.environ.get("LG2_YT_FRAME_RATE", "10"))
FRAME_WIDTH = int(os.environ.get("LG2_YT_FRAME_WIDTH", "480"))
SESSION_IDLE_TIMEOUT_SEC = int(os.environ.get("LG2_YT_IDLE_TIMEOUT_SEC", "600"))
STREAM_START_TIMEOUT_SEC = int(os.environ.get("LG2_YT_STREAM_START_TIMEOUT_SEC", "20"))


def run_command(args: list[str], timeout: int = 30) -> str:
    completed = subprocess.run(
        args,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
        timeout=timeout,
        check=False,
    )
    if completed.returncode != 0:
        stderr = (completed.stderr or "").strip()
        stdout = (completed.stdout or "").strip()
        message = stderr or stdout or f"command failed: {shlex.join(args)}"
        raise RuntimeError(message)
    return completed.stdout


def resolve_youtube(url: str) -> tuple[str, int, bool, str]:
    metadata_json = run_command(
        [YT_DLP_BIN, "--dump-single-json", "--no-playlist", url],
        timeout=45,
    )
    metadata = json.loads(metadata_json)
    title = metadata.get("title") or "YouTube"
    duration_ms = int(float(metadata.get("duration") or 0) * 1000.0)
    is_live = bool(metadata.get("is_live") or metadata.get("live_status") == "is_live")
    stream_url = run_command(
        [YT_DLP_BIN, "-g", "-f", "best[height<=480]/best", "--no-playlist", url],
        timeout=45,
    ).strip().splitlines()[0]
    return title, duration_ms, is_live, stream_url


def jpeg_frames_from_stream(stream, on_frame) -> None:
    buffer = bytearray()
    while True:
        chunk = stream.read(8192)
        if not chunk:
            break
        buffer.extend(chunk)
        while True:
            start = buffer.find(b"\xff\xd8")
            if start < 0:
                if len(buffer) > 2:
                    del buffer[:-2]
                break
            end = buffer.find(b"\xff\xd9", start + 2)
            if end < 0:
                if start > 0:
                    del buffer[:start]
                break
            frame = bytes(buffer[start:end + 2])
            del buffer[:end + 2]
            on_frame(frame)


class RelaySession:
    def __init__(self, session_id: str) -> None:
        self.session_id = session_id
        self.title = "YouTube"
        self.source_url = ""
        self.stream_url = ""
        self.duration_ms = 0
        self.position_ms = 0
        self.is_live = False
        self.paused = False
        self.audio_placeholder = True
        self.status = "IDLE"
        self.last_error = ""
        self.latest_frame: bytes | None = None
        self.latest_frame_base64 = ""
        self.frame_sequence = 0
        self.last_access_at = time.time()
        self._process: subprocess.Popen[bytes] | None = None
        self._reader_thread: threading.Thread | None = None
        self._lock = threading.RLock()
        self._play_started_at = 0.0
        self._play_base_position_ms = 0

    def touch(self) -> None:
        with self._lock:
            self.last_access_at = time.time()

    def load(self, url: str) -> dict[str, Any]:
        title, duration_ms, is_live, stream_url = resolve_youtube(url)
        with self._lock:
            self.stop_locked()
            self.title = title
            self.source_url = url
            self.stream_url = stream_url
            self.duration_ms = duration_ms
            self.position_ms = 0
            self.is_live = is_live
            self.paused = False
            self.audio_placeholder = True
            self.status = "BUFFERING"
            self.last_error = ""
            self.latest_frame = None
            self.latest_frame_base64 = ""
            self.frame_sequence = 0
            self.last_access_at = time.time()
        self.start_stream()
        return self.snapshot(include_frame=False)

    def snapshot(self, include_frame: bool = True, known_frame_sequence: int = -1) -> dict[str, Any]:
        with self._lock:
            if not self.is_live and not self.paused and self._process is not None:
                self.position_ms = self.current_position_ms_locked()
            frame_base64 = ""
            if include_frame and self.latest_frame_base64 and self.frame_sequence != known_frame_sequence:
                frame_base64 = self.latest_frame_base64
            ready = self.latest_frame is not None
            return {
                "sessionId": self.session_id,
                "title": self.title,
                "frameSequence": self.frame_sequence,
                "positionMs": self.position_ms,
                "durationMs": self.duration_ms,
                "paused": self.paused,
                "live": self.is_live,
                "audioPlaceholder": self.audio_placeholder,
                "ready": ready,
                "status": self.status,
                "frameBase64": frame_base64,
            }

    def control(self, action: str, position_ms: int | None = None) -> dict[str, Any]:
        action = (action or "").strip().lower()
        if action == "pause":
            self.pause()
        elif action == "resume":
            self.resume()
        elif action == "seek":
            if position_ms is None:
                raise RuntimeError("positionMs is required for seek")
            self.seek(position_ms)
        elif action == "close":
            with self._lock:
                self.stop_locked()
                self.status = "CLOSED"
        else:
            raise RuntimeError(f"unsupported action: {action}")
        return self.snapshot(include_frame=False)

    def pause(self) -> None:
        with self._lock:
            if self.paused:
                return
            self.position_ms = self.current_position_ms_locked()
            self.paused = True
            self.status = "PAUSED"
            self.stop_locked()

    def resume(self) -> None:
        with self._lock:
            if not self.source_url or not self.stream_url:
                raise RuntimeError("session is not loaded")
            self.paused = False
            self.status = "BUFFERING"
        self.start_stream()

    def seek(self, position_ms: int) -> None:
        with self._lock:
            if self.is_live:
                raise RuntimeError("live stream is not seekable")
            clamped = max(0, min(position_ms, self.duration_ms if self.duration_ms > 0 else position_ms))
            self.position_ms = clamped
            if self.paused:
                self.status = "PAUSED"
                self.stop_locked()
            else:
                self.status = "BUFFERING"
                self.stop_locked()
        if self.paused:
            self.capture_preview_frame()
        else:
            self.start_stream()

    def capture_preview_frame(self) -> None:
        with self._lock:
            if not self.stream_url:
                return
            stream_url = self.stream_url
            seek_ms = self.position_ms
        args = [FFMPEG_BIN, "-hide_banner", "-loglevel", "error", "-nostdin"]
        if seek_ms > 0:
            args += ["-ss", f"{seek_ms / 1000.0:.3f}"]
        args += [
            "-i",
            stream_url,
            "-frames:v",
            "1",
            "-an",
            "-vf",
            f"scale=w={FRAME_WIDTH}:h=-2:force_original_aspect_ratio=decrease",
            "-q:v",
            "5",
            "-f",
            "image2pipe",
            "-vcodec",
            "mjpeg",
            "-",
        ]
        completed = subprocess.run(
            args,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            timeout=STREAM_START_TIMEOUT_SEC,
            check=False,
        )
        if completed.returncode == 0 and completed.stdout:
            with self._lock:
                self.latest_frame = completed.stdout
                self.latest_frame_base64 = base64.b64encode(completed.stdout).decode("ascii")
                self.frame_sequence += 1

    def start_stream(self) -> None:
        with self._lock:
            if not self.stream_url:
                raise RuntimeError("session is not loaded")
            self.stop_locked()
            args = [FFMPEG_BIN, "-hide_banner", "-loglevel", "error", "-nostdin"]
            if not self.is_live and self.position_ms > 0:
                args += ["-ss", f"{self.position_ms / 1000.0:.3f}"]
            args += [
                "-re",
                "-i",
                self.stream_url,
                "-an",
                "-vf",
                f"fps={FRAME_RATE},scale=w={FRAME_WIDTH}:h=-2:force_original_aspect_ratio=decrease",
                "-q:v",
                "5",
                "-f",
                "image2pipe",
                "-vcodec",
                "mjpeg",
                "-",
            ]
            process = subprocess.Popen(
                args,
                stdout=subprocess.PIPE,
                stderr=subprocess.DEVNULL,
                stdin=subprocess.DEVNULL,
            )
            self._process = process
            self._play_base_position_ms = self.position_ms
            self._play_started_at = time.monotonic()
            self.last_access_at = time.time()
            thread = threading.Thread(target=self._read_frames, name=f"yt-relay-{self.session_id}", daemon=True)
            self._reader_thread = thread
            thread.start()

    def _read_frames(self) -> None:
        process = self._process
        if process is None or process.stdout is None:
            return

        def on_frame(frame: bytes) -> None:
            with self._lock:
                self.latest_frame = frame
                self.latest_frame_base64 = base64.b64encode(frame).decode("ascii")
                self.frame_sequence += 1
                if self.paused:
                    self.status = "PAUSED"
                elif self.is_live:
                    self.status = "LIVE"
                else:
                    self.status = "PLAYING"

        try:
            jpeg_frames_from_stream(process.stdout, on_frame)
        finally:
            with self._lock:
                if self._process is process:
                    if not self.is_live and not self.paused:
                        self.position_ms = self.current_position_ms_locked()
                    if self.status not in {"PAUSED", "CLOSED"}:
                        self.status = "BUFFERING" if self.source_url else "IDLE"
                    self._process = None
                    self._reader_thread = None

    def current_position_ms_locked(self) -> int:
        if self.is_live or self.paused or self._process is None:
            return self.position_ms
        elapsed_ms = int((time.monotonic() - self._play_started_at) * 1000.0)
        position = self._play_base_position_ms + elapsed_ms
        if self.duration_ms > 0:
            position = min(position, self.duration_ms)
        return max(0, position)

    def stop_locked(self) -> None:
        process = self._process
        self._process = None
        self._reader_thread = None
        if process is None:
            return
        try:
            process.terminate()
            process.wait(timeout=2)
        except Exception:
            try:
                process.kill()
                process.wait(timeout=2)
            except Exception:
                pass


SESSIONS: dict[str, RelaySession] = {}
SESSIONS_LOCK = threading.RLock()


def get_session(session_id: str, create: bool = False) -> RelaySession:
    with SESSIONS_LOCK:
        session = SESSIONS.get(session_id)
        if session is None and create:
            session = RelaySession(session_id)
            SESSIONS[session_id] = session
        if session is None:
            raise RuntimeError("unknown session")
        session.touch()
        return session


def cleanup_loop() -> None:
    while True:
        time.sleep(30)
        now = time.time()
        stale_ids: list[str] = []
        with SESSIONS_LOCK:
            for session_id, session in SESSIONS.items():
                if now - session.last_access_at > SESSION_IDLE_TIMEOUT_SEC:
                    stale_ids.append(session_id)
            for session_id in stale_ids:
                session = SESSIONS.pop(session_id, None)
                if session is not None:
                    with session._lock:
                        session.stop_locked()


class RelayHandler(BaseHTTPRequestHandler):
    server_version = "LostGladeYoutubeRelay/1.0"

    def do_GET(self) -> None:
        try:
            parsed = urlparse(self.path)
            if parsed.path == "/api/session/snapshot":
                params = parse_qs(parsed.query)
                session_id = (params.get("sessionId") or [""])[0]
                known_frame_sequence_raw = (params.get("knownFrameSequence") or ["-1"])[0]
                if not session_id:
                    self.send_error_json(HTTPStatus.BAD_REQUEST, "sessionId is required")
                    return
                session = get_session(session_id)
                self.send_json(HTTPStatus.OK, session.snapshot(include_frame=True, known_frame_sequence=int(known_frame_sequence_raw or "-1")))
                return
            self.send_error_json(HTTPStatus.NOT_FOUND, "not found")
        except Exception as exc:
            self.send_error_json(HTTPStatus.BAD_REQUEST, str(exc))

    def do_POST(self) -> None:
        try:
            parsed = urlparse(self.path)
            payload = self.read_json_body()
            if parsed.path == "/api/session/load":
                session_id = str(payload.get("sessionId") or "").strip()
                url = str(payload.get("url") or "").strip()
                if not session_id or not url:
                    self.send_error_json(HTTPStatus.BAD_REQUEST, "sessionId and url are required")
                    return
                session = get_session(session_id, create=True)
                self.send_json(HTTPStatus.OK, session.load(url))
                return
            if parsed.path == "/api/session/control":
                session_id = str(payload.get("sessionId") or "").strip()
                action = str(payload.get("action") or "").strip()
                position_ms = payload.get("positionMs")
                if not session_id or not action:
                    self.send_error_json(HTTPStatus.BAD_REQUEST, "sessionId and action are required")
                    return
                session = get_session(session_id, create=action == "close")
                response = session.control(action, int(position_ms) if position_ms is not None else None)
                if action == "close":
                    with SESSIONS_LOCK:
                        SESSIONS.pop(session_id, None)
                self.send_json(HTTPStatus.OK, response)
                return
            self.send_error_json(HTTPStatus.NOT_FOUND, "not found")
        except Exception as exc:
            self.send_error_json(HTTPStatus.BAD_REQUEST, str(exc))

    def log_message(self, format: str, *args) -> None:
        return

    def read_json_body(self) -> dict[str, Any]:
        length = int(self.headers.get("Content-Length") or "0")
        raw = self.rfile.read(length) if length > 0 else b"{}"
        if not raw:
            return {}
        return json.loads(raw.decode("utf-8"))

    def send_json(self, status: HTTPStatus, payload: dict[str, Any]) -> None:
        body = json.dumps(payload, ensure_ascii=True).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def send_error_json(self, status: HTTPStatus, message: str) -> None:
        self.send_json(status, {"error": message})


def main() -> None:
    parser = argparse.ArgumentParser(description="LostGlade YouTube relay")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=18888)
    args = parser.parse_args()

    cleanup_thread = threading.Thread(target=cleanup_loop, name="yt-relay-cleanup", daemon=True)
    cleanup_thread.start()

    server = ThreadingHTTPServer((args.host, args.port), RelayHandler)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()
        with SESSIONS_LOCK:
            sessions = list(SESSIONS.values())
            SESSIONS.clear()
        for session in sessions:
            with session._lock:
                session.stop_locked()


if __name__ == "__main__":
    main()
