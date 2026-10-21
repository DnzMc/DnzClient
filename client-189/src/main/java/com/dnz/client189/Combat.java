package com.dnz.client189;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemFood;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * What the PvP and info modules need: reach of the last hit, combo, the last target, kills and deaths this
 * session, session time and the server's TPS; plus the custom crosshair and food values in tooltips.
 */
public final class Combat {
	private static double lastReach = -1;
	private static long lastHitAt;
	private static int combo;
	private static Entity target;
	private static long targetAt;
	private static int kills;
	private static int deaths;
	private static boolean wasDead;
	private static final long SESSION_START = System.currentTimeMillis();
	private static long lastWorldTime = -1;
	private static long lastTpsCheck;
	private static double tps = 20;
	private static int lastHurtTime;

	static String reach() {
		return lastReach < 0 || System.currentTimeMillis() - lastHitAt > 3000 ? "-" : String.format("%.2f", lastReach);
	}

	static int combo() {
		return System.currentTimeMillis() - lastHitAt > 2500 ? 0 : combo;
	}

	static String target() {
		if (target == null || System.currentTimeMillis() - targetAt > 5000) {
			return "-";
		}
		return target.getName() + " " + ExtraModules.health(target);
	}

	static int kills() {
		return kills;
	}

	static int deaths() {
		return deaths;
	}

	static String sessionTime() {
		long s = (System.currentTimeMillis() - SESSION_START) / 1000;
		return s / 3600 + ":" + (s / 60 % 60 < 10 ? "0" : "") + s / 60 % 60 + ":" + (s % 60 < 10 ? "0" : "") + s % 60;
	}

	static double tps() {
		return tps;
	}

	/** Every client tick. */
	static void tick(Minecraft mc) {
		if (mc.thePlayer == null || mc.theWorld == null) {
			return;
		}
		// Got hit: the combo ends
		if (mc.thePlayer.hurtTime > lastHurtTime) {
			combo = 0;
		}
		lastHurtTime = mc.thePlayer.hurtTime;
		boolean dead = mc.thePlayer.getHealth() <= 0;
		if (dead && !wasDead) {
			deaths++;
		}
		wasDead = dead;
		if (target instanceof EntityLivingBase && ((EntityLivingBase) target).getHealth() <= 0 && System.currentTimeMillis() - targetAt < 2000) {
			kills++;
			target = null;
		}
		// TPS: how fast the world's clock moves (20 per second on a healthy server)
		long now = System.currentTimeMillis();
		if (now - lastTpsCheck >= 2000) {
			long t = mc.theWorld.getTotalWorldTime();
			if (lastWorldTime >= 0) {
				tps = Math.min(20, (t - lastWorldTime) * 1000.0 / (now - lastTpsCheck));
			}
			lastWorldTime = t;
			lastTpsCheck = now;
		}
	}

	@SubscribeEvent
	public void onMouse(MouseEvent event) {
		Minecraft mc = Minecraft.getMinecraft();
		if (event.button != 0 || !event.buttonstate || mc.thePlayer == null || mc.currentScreen != null) {
			return;
		}
		MovingObjectPosition hit = mc.objectMouseOver;
		if (hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY && hit.entityHit != null) {
			lastReach = mc.thePlayer.getPositionEyes(1).distanceTo(hit.hitVec);
			combo = System.currentTimeMillis() - lastHitAt < 2500 ? combo + 1 : 1;
			lastHitAt = System.currentTimeMillis();
			target = hit.entityHit;
			targetAt = lastHitAt;
		}
	}

	/** Custom crosshair: a thin DNZ plus instead of Minecraft's. */
	@SubscribeEvent
	public void onCrosshair(RenderGameOverlayEvent.Pre event) {
		Module m = Modules.get("crosshair");
		if (event.type != RenderGameOverlayEvent.ElementType.CROSSHAIRS || m == null || !m.enabled()) {
			return;
		}
		event.setCanceled(true);
		float cx = event.resolution.getScaledWidth() / 2F;
		float cy = event.resolution.getScaledHeight() / 2F;
		int color = 0xFFFFFFFF;
		Render2D.roundedRect(cx - 5, cy - 0.5F, 3.5F, 1, 0.5F, color);
		Render2D.roundedRect(cx + 1.5F, cy - 0.5F, 3.5F, 1, 0.5F, color);
		Render2D.roundedRect(cx - 0.5F, cy - 5, 1, 3.5F, 0.5F, color);
		Render2D.roundedRect(cx - 0.5F, cy + 1.5F, 1, 3.5F, 0.5F, color);
		Render2D.circle(cx, cy, 0.6F, Render2D.ORANGE);
	}

	@SubscribeEvent
	public void onTooltip(ItemTooltipEvent event) {
		Module m = Modules.get("foodtooltip");
		if (m == null || !m.enabled() || event.itemStack == null || !(event.itemStack.getItem() instanceof ItemFood)) {
			return;
		}
		ItemFood food = (ItemFood) event.itemStack.getItem();
		int heal = food.getHealAmount(event.itemStack);
		float sat = heal * food.getSaturationModifier(event.itemStack) * 2;
		event.toolTip.add("§6" + Lang.t("Açlık", "Hunger") + ": +" + heal + "  §e" + Lang.t("Doygunluk", "Saturation") + ": +" + String.format("%.1f", sat));
	}
}
