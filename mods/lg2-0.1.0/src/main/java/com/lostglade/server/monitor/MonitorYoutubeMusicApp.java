package com.lostglade.server.monitor;

public final class MonitorYoutubeMusicApp implements MonitorApp {
	@Override
	public String id() {
		return "youtubemusic";
	}

	@Override
	public String title() {
		return "YT MUSIC";
	}

	@Override
	public String iconResourcePath() {
		return "/monitor/youtube_music_app.png";
	}

	@Override
	public int accentStartRgb() {
		return 0xFF4E45;
	}

	@Override
	public int accentEndRgb() {
		return 0xB31217;
	}

	@Override
	public int panelRgb() {
		return 0x1D1114;
	}

	@Override
	public String screenTitle() {
		return "YouTube Music";
	}

	@Override
	public String screenHint() {
		return "Музыка, альбомы и плейлисты";
	}

	@Override
	public MonitorAppRole role() {
		return MonitorAppRole.YOUTUBE_MUSIC;
	}
}
