package com.lostglade.server.monitor;

public enum MonitorAppRole {
	GENERIC(false, false),
	GALLERY_LIBRARY(true, true),
	SBER_DRONES(true, true),
	CAMERA_RECORDER(false, false),
	YOUTUBE_VIDEO(true, false),
	YOUTUBE_MUSIC(true, false);

	private final boolean mediaRuntime;
	private final boolean librarySurface;

	MonitorAppRole(boolean mediaRuntime, boolean librarySurface) {
		this.mediaRuntime = mediaRuntime;
		this.librarySurface = librarySurface;
	}

	public boolean usesMediaRuntime() {
		return this.mediaRuntime;
	}

	public boolean usesLibrarySurface() {
		return this.librarySurface;
	}

	public boolean usesMediaRenderer() {
		return this.mediaRuntime;
	}
}
