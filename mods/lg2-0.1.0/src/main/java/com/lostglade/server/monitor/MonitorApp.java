package com.lostglade.server.monitor;

public interface MonitorApp {
	String id();

	String title();

	String iconResourcePath();

	int accentStartRgb();

	int accentEndRgb();

	int panelRgb();

	String screenTitle();

	String screenHint();
}
