package com.dnz.client189.engine;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.entity.Entity;

/**
 * DNZ Engine: DNZ's own rendering speed-ups for Minecraft 1.8.9 (see the mixins): particles off screen not
 * drawn, far dropped stacks drawn once, and the far culling and particle cap of Culling.
 */
public final class Engine {
	private Engine() {
	}

	public static boolean enabled() {
		return Culling.enabled();
	}

	/** The camera's view at this frame (same matrices as Minecraft's own culling). */
	public static Frustum frustum(Entity viewer, float partialTicks) {
		Frustum f = new Frustum();
		double x = viewer.lastTickPosX + (viewer.posX - viewer.lastTickPosX) * partialTicks;
		double y = viewer.lastTickPosY + (viewer.posY - viewer.lastTickPosY) * partialTicks;
		double z = viewer.lastTickPosZ + (viewer.posZ - viewer.lastTickPosZ) * partialTicks;
		f.setPosition(x, y, z);
		return f;
	}

	/** A dropped item at (x, y, z) relative to the camera is far enough to draw only once. */
	public static boolean farItem(double x, double y, double z) {
		return enabled() && x * x + y * y + z * z > 12 * 12;
	}
}
