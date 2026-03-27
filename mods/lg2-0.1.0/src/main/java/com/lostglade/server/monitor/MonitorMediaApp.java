package com.lostglade.server.monitor;

public final class MonitorMediaApp implements MonitorApp {
	@Override
	public String id() {
		return "media";
	}

	@Override
	public String title() {
		return "MEDIA";
	}

	@Override
	public String iconResourcePath() {
		return "/assets/lg2/textures/monitor/media_app.png";
	}

	@Override
	public int accentStartRgb() {
		return 0x40ADFF;
	}

	@Override
	public int accentEndRgb() {
		return 0x1054BC;
	}

	@Override
	public int panelRgb() {
		return 0x10161C;
	}

	@Override
	public String screenTitle() {
		return "Local media";
	}

	@Override
	public String screenHint() {
		return "Future gallery and image feed";
	}
}
