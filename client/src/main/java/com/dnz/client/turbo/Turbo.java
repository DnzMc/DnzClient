package com.dnz.client.turbo;

import com.dnz.client.DnzConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.decoration.painting.Painting;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SkullBlockEntity;

/**
 * DNZ Turbo: skips drawing things that cost FPS on busy servers but don't matter far away
 * (hologram armor stands, item frames, paintings, dropped items, XP orbs, signs, banners, heads)
 * and caps the number of particles. Players and mobs are never hidden, so nothing changes for PvP.
 */
public final class Turbo {
	/** Most particles on screen at once (Minecraft allows far more). */
	public static final int PARTICLE_LIMIT = 1500;
	private static final double DECORATION_DISTANCE = 32;
	private static final double DROP_DISTANCE = 24;
	private static final double BLOCK_ENTITY_DISTANCE = 32;

	private Turbo() {
	}

	public static boolean enabled() {
		return DnzConfig.get().turbo;
	}

	/** True if this entity is far enough away to not be drawn. */
	public static boolean skipEntity(Entity entity, double camX, double camY, double camZ) {
		if (!enabled()) {
			return false;
		}
		double limit;
		if (entity instanceof ArmorStand || entity instanceof ItemFrame || entity instanceof Painting) {
			limit = DECORATION_DISTANCE;
		} else if (entity instanceof ItemEntity || entity instanceof ExperienceOrb) {
			limit = DROP_DISTANCE;
		} else {
			return false;
		}
		return entity.distanceToSqr(camX, camY, camZ) > limit * limit;
	}

	/** True if this sign, banner or head is far enough away to not be drawn. */
	public static boolean skipBlockEntity(BlockEntity blockEntity) {
		if (!enabled() || !(blockEntity instanceof SignBlockEntity || blockEntity instanceof BannerBlockEntity || blockEntity instanceof SkullBlockEntity)) {
			return false;
		}
		Entity viewer = Minecraft.getInstance().getCameraEntity();
		if (viewer == null) {
			return false;
		}
		return blockEntity.getBlockPos().distToCenterSqr(viewer.getX(), viewer.getY(), viewer.getZ()) > BLOCK_ENTITY_DISTANCE * BLOCK_ENTITY_DISTANCE;
	}
}
