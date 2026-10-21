package com.dnz.client189;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.opengl.GL11;

/** Cuts drawing to a rectangle given in GUI units (the module grid scrolls inside its area). */
public final class Scissor {
	private Scissor() {
	}

	public static void push(float x, float y, float w, float h, Minecraft mc) {
		int f = new ScaledResolution(mc).getScaleFactor();
		GL11.glEnable(GL11.GL_SCISSOR_TEST);
		GL11.glScissor(Math.round(x * f), Math.round(mc.displayHeight - (y + h) * f), Math.round(w * f), Math.round(h * f));
	}

	public static void pop() {
		GL11.glDisable(GL11.GL_SCISSOR_TEST);
	}
}
