package com.dnz.client.gui;

/** Small animation helpers that move at the same speed at any frame rate. */
public final class Anim {
	private static long lastFrame;
	private static float frameSeconds = 1.0F / 60.0F;

	private Anim() {
	}

	/** Seconds since the last frame (capped), shared by all animations of the frame. */
	private static float delta() {
		long now = System.nanoTime();
		if (now - lastFrame > 1_000_000L) {
			frameSeconds = lastFrame == 0 ? 1.0F / 60.0F : Math.min(0.1F, (now - lastFrame) / 1.0E9F);
			lastFrame = now;
		}
		return frameSeconds;
	}

	/** Moves [current] toward [target]; [speed] ~ how many times per second the gap closes (higher = snappier). */
	public static float approach(float current, float target, float speed) {
		float t = 1.0F - (float) Math.exp(-speed * delta());
		float next = current + (target - current) * t;
		return Math.abs(target - next) < 0.002F ? target : next;
	}

	/** Ease-out (fast start, soft end) for 0..1. */
	public static float easeOut(float t) {
		t = Math.max(0.0F, Math.min(1.0F, t));
		float inv = 1.0F - t;
		return 1.0F - inv * inv * inv;
	}
}
