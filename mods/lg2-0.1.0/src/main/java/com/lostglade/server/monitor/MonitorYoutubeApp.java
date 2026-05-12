package com.lostglade.server.monitor;

public final class MonitorYoutubeApp implements MonitorApp {
	@Override
	public String id() {
		return "youtube";
	}

	@Override
	public String title() {
		return "YOUTUBE";
	}

	@Override
	public String iconResourcePath() {
		return "/assets/lg2/textures/monitor/youtube_app.png";
	}

	@Override
	public int accentStartRgb() {
		return 0xFF3131;
	}

	@Override
	public int accentEndRgb() {
		return 0xC90000;
	}

	@Override
	public int panelRgb() {
		return 0x211212;
	}

	@Override
	public String screenTitle() {
		return "YouTube Player";
	}

	@Override
	public String screenHint() {
		return "Видео и стримы по ссылке";
	}

	@Override
	public MonitorAppRole role() {
		return MonitorAppRole.YOUTUBE_VIDEO;
	}
}
