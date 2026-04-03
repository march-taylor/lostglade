package com.lostglade.server.monitor;

public final class MonitorSberDronesApp implements MonitorApp {
	@Override
	public String id() {
		return "sberdrones";
	}

	@Override
	public String title() {
		return "СБЕР ДРОНЫ";
	}

	@Override
	public String iconResourcePath() {
		return "/assets/lg2/textures/monitor/sber_drones_app.png";
	}

	@Override
	public int accentStartRgb() {
		return 0x1D9BF0;
	}

	@Override
	public int accentEndRgb() {
		return 0x23C552;
	}

	@Override
	public int panelRgb() {
		return 0x10161C;
	}

	@Override
	public String screenTitle() {
		return "Сбер дроны";
	}

	@Override
	public String screenHint() {
		return "Подключённые live-камеры";
	}
}
