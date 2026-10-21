package com.dnz.client189;

/** Smooth movement toward a target, independent of the frame rate. */
public final class Anim {
	private static long last = System.nanoTime();
	private static float dt = 1 / 60F;

	private Anim() {
	}

	/** Once per frame (from the render tick). */
	public static void frame() {
		long now = System.nanoTime();
		dt = Math.min(0.1F, (now - last) / 1e9F);
		last = now;
	}

	public static float approach(float value, float target) {
		return approach(value, target, 14);
	}

	public static float approach(float value, float target, float speed) {
		float t = 1 - (float) Math.exp(-speed * dt);
		float v = value + (target - value) * t;
		return Math.abs(v - target) < 0.002F ? target : v;
	}
}
