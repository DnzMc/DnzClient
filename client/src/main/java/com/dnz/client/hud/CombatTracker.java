package com.dnz.client.hud;

import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Numbers the PvP modules show: reach of the last hit, combo (hits in a row without getting hit), the last
 * target, movement speed, time played this session, and kills / deaths since the game started.
 */
public final class CombatTracker {
	/** Reach and target stay visible this long after the last hit. */
	private static final long SHOW_MS = 3000;

	private static double lastReach = -1;
	private static long lastHitTime;
	private static Entity lastTarget;
	private static long lastAttackTime;
	private static boolean hitCounted = true;
	private static int combo;
	private static int lastOwnHurtTime;

	private static double speed;
	private static double rawSpeed;
	private static Vec3 lastPos;
	private static long sessionStart;
	private static Object sessionLevel;

	/** Players killed (died within a few seconds of our last hit) and own deaths since the game started. */
	private static int kills;
	private static int deaths;
	private static Entity killTarget;
	private static long killTargetTime;
	private static boolean wasDead;

	private CombatTracker() {
	}

	public static void register() {
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (level.isClientSide()) {
				onAttack(player.getEyePosition(), entity);
			}
			return InteractionResult.PASS;
		});
	}

	private static void onAttack(Vec3 eye, Entity target) {
		AABB box = target.getBoundingBox();
		// Distance from the eyes to the closest point of the target's hitbox (how reach is measured).
		double dx = Math.max(Math.max(box.minX - eye.x, 0), eye.x - box.maxX);
		double dy = Math.max(Math.max(box.minY - eye.y, 0), eye.y - box.maxY);
		double dz = Math.max(Math.max(box.minZ - eye.z, 0), eye.z - box.maxZ);
		lastReach = Math.sqrt(dx * dx + dy * dy + dz * dz);
		long now = System.currentTimeMillis();
		lastHitTime = now;
		lastAttackTime = now;
		lastTarget = target;
		hitCounted = false;
		if (target instanceof net.minecraft.world.entity.player.Player) {
			killTarget = target;
			killTargetTime = now;
		}
	}

	public static void tick(Minecraft mc) {
		if (mc.player == null || mc.level == null) {
			lastPos = null;
			rawSpeed = 0;
			sessionLevel = null;
			return;
		}
		if (sessionLevel != mc.level) {
			sessionLevel = mc.level;
			sessionStart = System.currentTimeMillis();
			combo = 0;
		}
		long now = System.currentTimeMillis();

		// A hit counts for the combo when the target really took damage right after our attack.
		if (!hitCounted && lastTarget instanceof LivingEntity living && living.hurtTime > 0 && now - lastAttackTime < 500) {
			combo++;
			hitCounted = true;
		}
		// Getting hit, or 3 seconds without a hit, ends the combo.
		int hurt = mc.player.hurtTime;
		if (hurt > lastOwnHurtTime) {
			combo = 0;
		}
		lastOwnHurtTime = hurt;
		// Kill: the player we hit last died within 5 seconds. Death: our own player started dying.
		if (killTarget instanceof LivingEntity victim && victim.isDeadOrDying()) {
			if (now - killTargetTime < 5000) {
				kills++;
			}
			killTarget = null;
		}
		boolean dead = mc.player.isDeadOrDying();
		if (dead && !wasDead) {
			deaths++;
		}
		wasDead = dead;
		if (now - lastAttackTime > SHOW_MS) {
			combo = 0;
		}

		Vec3 pos = mc.player.position();
		if (lastPos != null) {
			double dx = pos.x - lastPos.x, dz = pos.z - lastPos.z;
			// Blocks per tick * 20 = blocks per second (horizontal only, like other speed meters).
			rawSpeed = Math.sqrt(dx * dx + dz * dz) * 20.0;
			speed += (rawSpeed - speed) * 0.5;
		}
		lastPos = pos;
	}

	/** Reach of the last hit in blocks, or -1 when it's too long ago. */
	public static double reach() {
		return System.currentTimeMillis() - lastHitTime < SHOW_MS ? lastReach : -1;
	}

	public static int combo() {
		return combo;
	}

	/** Entity under the crosshair, or the one hit last (for a few seconds). */
	public static LivingEntity target(Minecraft mc) {
		if (mc.crosshairPickEntity instanceof LivingEntity living && living.isAlive()) {
			return living;
		}
		if (lastTarget instanceof LivingEntity living && living.isAlive() && System.currentTimeMillis() - lastHitTime < SHOW_MS
			&& mc.player != null && living.distanceTo(mc.player) < 16) {
			return living;
		}
		return null;
	}

	public static double speed() {
		return speed;
	}

	public static double rawSpeed() {
		return rawSpeed;
	}

	public static int kills() {
		return kills;
	}

	public static int deaths() {
		return deaths;
	}

	public static long sessionMillis() {
		return sessionLevel == null ? 0 : System.currentTimeMillis() - sessionStart;
	}
}
