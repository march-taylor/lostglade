package com.lostglade.server.progress;

public final class TaskProgress {
	public static final long COMPLETION_HOLD_MILLIS = 300L;
	public static final long COMPLETION_FADE_MILLIS = 900L;
	public static final long COMPLETION_VISIBLE_MILLIS = COMPLETION_HOLD_MILLIS + COMPLETION_FADE_MILLIS;

	private final Runnable onChange;
	private String stage;
	private long completed;
	private long total;
	private boolean determinate;
	private boolean active;
	private long completedAtMillis;

	public TaskProgress() {
		this(null);
	}

	public TaskProgress(Runnable onChange) {
		this.onChange = onChange;
		this.stage = "";
		this.completed = 0L;
		this.total = 0L;
		this.determinate = false;
		this.active = false;
		this.completedAtMillis = -1L;
	}

	public synchronized void setStage(String stage) {
		this.stage = stage != null ? stage : "";
		this.active = true;
		this.completedAtMillis = -1L;
		notifyChange();
	}

	public synchronized void setIndeterminate(String stage) {
		this.stage = stage != null ? stage : "";
		this.completed = 0L;
		this.total = 0L;
		this.determinate = false;
		this.active = true;
		this.completedAtMillis = -1L;
		notifyChange();
	}

	public synchronized void setProgress(String stage, long completed, long total) {
		this.stage = stage != null ? stage : "";
		this.completed = Math.max(0L, completed);
		this.total = Math.max(0L, total);
		this.determinate = this.total > 0L;
		this.active = true;
		this.completedAtMillis = -1L;
		notifyChange();
	}

	public synchronized void complete(String stage) {
		this.stage = stage != null ? stage : "";
		this.completed = 1L;
		this.total = 1L;
		this.determinate = true;
		this.active = false;
		this.completedAtMillis = System.currentTimeMillis();
		notifyChange();
	}

	public synchronized void clear() {
		this.stage = "";
		this.completed = 0L;
		this.total = 0L;
		this.determinate = false;
		this.active = false;
		this.completedAtMillis = -1L;
		notifyChange();
	}

	public synchronized Snapshot snapshot() {
		float fraction = 0.0F;
		if (this.determinate && this.total > 0L) {
			fraction = Math.max(0.0F, Math.min(1.0F, (float) this.completed / (float) this.total));
		}
		float alpha = this.active ? 1.0F : 0.0F;
		boolean visible = this.active;
		if (!this.active && this.completedAtMillis >= 0L) {
			long elapsed = Math.max(0L, System.currentTimeMillis() - this.completedAtMillis);
			if (elapsed < COMPLETION_HOLD_MILLIS) {
				alpha = 1.0F;
				visible = true;
			} else if (elapsed < COMPLETION_VISIBLE_MILLIS) {
				alpha = 1.0F - ((float) (elapsed - COMPLETION_HOLD_MILLIS) / (float) COMPLETION_FADE_MILLIS);
				visible = true;
			}
		}
		return new Snapshot(this.stage, fraction, this.determinate, this.active, visible, Math.max(0.0F, Math.min(1.0F, alpha)));
	}

	private void notifyChange() {
		if (this.onChange != null) {
			this.onChange.run();
		}
	}

	public record Snapshot(
			String stage,
			float fraction,
			boolean determinate,
			boolean active,
			boolean visible,
			float alpha
	) {
	}
}
