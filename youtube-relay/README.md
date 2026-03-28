# LostGlade YouTube Relay

MVP relay for monitor `youtube` app.

What it does:
- accepts a YouTube URL from the Minecraft server
- resolves the stream with `yt-dlp`
- decodes video frames in real time with `ffmpeg`
- exposes the latest frame, duration, pause state, seek position, and live flag over local HTTP

Requirements:
- `python3`
- `yt-dlp`
- `ffmpeg`

Default bind:
- `127.0.0.1:18888`

Launch:

```bash
python3 youtube-relay/relay.py --host 127.0.0.1 --port 18888
```

Optional server override:

```bash
LG2_YOUTUBE_RELAY_URL=http://127.0.0.1:18888
```
