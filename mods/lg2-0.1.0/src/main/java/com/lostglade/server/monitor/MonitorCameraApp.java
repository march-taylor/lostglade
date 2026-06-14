package com.lostglade.server.monitor;

public final class MonitorCameraApp implements MonitorApp {
	@Override
	public String id() {
		return "cameraapp";
	}

	@Override
	public String title() {
		return "MI camera";
	}

	@Override
	public String iconResourcePath() {
		return "/assets/lg2/textures/monitor/camera_app.png";
	}

	@Override
	public int accentStartRgb() {
		return 0xE9EEF5;
	}

	@Override
	public int accentEndRgb() {
		return 0x6F8799;
	}

	@Override
	public int panelRgb() {
		return 0x101419;
	}

	@Override
	public String screenTitle() {
		return "MI camera";
	}

	@Override
	public String screenHint() {
		return "Фото, видео и диктофон";
	}

	@Override
	public MonitorAppRole role() {
		return MonitorAppRole.CAMERA_RECORDER;
	}
}
