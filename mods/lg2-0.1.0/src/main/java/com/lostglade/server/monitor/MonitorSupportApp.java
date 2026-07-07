package com.lostglade.server.monitor;

public final class MonitorSupportApp implements MonitorApp {
	@Override
	public String id() {
		return "support";
	}

	@Override
	public String title() {
		return "Поддержка";
	}

	@Override
	public String iconResourcePath() {
		return "/monitor/support_app.png";
	}

	@Override
	public int accentStartRgb() {
		return 0x56CCF2;
	}

	@Override
	public int accentEndRgb() {
		return 0x2F80ED;
	}

	@Override
	public int panelRgb() {
		return 0xF5F7FA;
	}

	@Override
	public String screenTitle() {
		return "Поддержка";
	}

	@Override
	public String screenHint() {
		return "Баги и идеи по серверу";
	}
}
