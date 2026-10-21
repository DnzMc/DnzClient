package com.dnz.client.hud;

import com.dnz.client.L;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Freelook: hold the key (Left Alt by default) and the camera circles around the player while the player
 * keeps looking and walking where they were.
 */
public final class Freelook {
	public static final KeyMapping KEY = new KeyMapping("key.dnzclient.freelook", InputConstants.KEY_LALT, Zoom.CATEGORY);

	private static boolean active;
	private static float yaw;
	private static float pitch;
	private static CameraType before;
	private static boolean lockedShown;

	private Freelook() {
	}

	public static boolean active() {
		return active;
	}

	public static float yaw() {
		return yaw;
	}

	public static float pitch() {
		return pitch;
	}

	/** On a multiplayer server (or a world on another computer): Freelook is off and its switch is locked. */
	public static boolean lockedHere() {
		Minecraft mc = Minecraft.getInstance();
		return mc.getConnection() != null && !mc.hasSingleplayerServer();
	}

	/** Allowed here: the module is on and the world runs on this computer (never on a multiplayer server). */
	private static boolean allowed(Minecraft mc) {
		HudModule module = DnzHud.module("freelook");
		return mc.player != null && mc.hasSingleplayerServer() && (module == null || module.enabled());
	}

	/** Every client tick. */
	public static void tick(Minecraft mc) {
		boolean held = mc.player != null && mc.gui.screen() == null && KEY.isDown();
		if (held && allowed(mc)) {
			if (!active) {
				active = true;
				yaw = mc.player.getYRot();
				pitch = mc.player.getXRot();
				before = mc.options.getCameraType();
				mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
			}
		} else {
			stop(mc);
		}
		// On a server: say once per press that it is locked there.
		if (held && !allowed(mc) && mc.player != null && !mc.hasSingleplayerServer()) {
			if (!lockedShown) {
				lockedShown = true;
				mc.player.sendOverlayMessage(Component.literal(L.t("freelook.locked")));
			}
		} else if (!held) {
			lockedShown = false;
		}
	}

	private static void stop(Minecraft mc) {
		if (active) {
			active = false;
			mc.options.setCameraType(before == null ? CameraType.FIRST_PERSON : before);
		}
	}

	/** The mouse moved while Freelook is on: turn the camera instead of the player (same speed as the game). */
	public static void turn(double dx, double dy) {
		yaw += (float) dx * 0.15F;
		pitch = Mth.clamp(pitch + (float) dy * 0.15F, -90.0F, 90.0F);
	}
}
