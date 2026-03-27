package com.lostglade.server.monitor;

public final class MonitorMaxApp implements MonitorApp {
	@Override
	public String id() {
		return "max";
	}

	@Override
	public String title() {
		return "MAX";
	}

	@Override
	public String iconResourcePath() {
		return "/assets/lg2/textures/monitor/max_app.png";
	}

	@Override
	public int accentStartRgb() {
		return 0x2AC0FF;
	}

	@Override
	public int accentEndRgb() {
		return 0x9C45FF;
	}

	@Override
	public int panelRgb() {
		return 0x161222;
	}

	@Override
	public String screenTitle() {
		return "Оболочка MAX";
	}

	@Override
	public String screenHint() {
		return "Зарезервировано под будущую логику";
	}
}
