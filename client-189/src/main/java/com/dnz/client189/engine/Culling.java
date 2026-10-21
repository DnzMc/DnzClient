package com.dnz.client189.engine;

import com.dnz.client189.Modules;
import com.dnz.client189.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityItemFrame;
import net.minecraft.entity.item.EntityPainting;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityBanner;
import net.minecraft.tileentity.TileEntitySign;
import net.minecraft.tileentity.TileEntitySkull;

/**
 * DNZ Engine, part 1 (far culling): far away hologram armor stands, item frames, paintings, dropped items, XP
 * orbs, signs, banners and heads are not drawn, and particles are capped.
 */
public final class Culling {
	public static final int PARTICLE_LIMIT = 1500;
	private static final double DECORATION_DISTANCE = 32;
	private static final double DROP_DISTANCE = 24;
	private static final double BLOCK_ENTITY_DISTANCE = 32;

	private Culling() {
	}

	public static boolean enabled() {
		Module m = Modules.get("engine");
		return m == null || m.enabled();
	}

	public static boolean skipEntity(Entity entity) {
		if (!enabled()) {
			return false;
		}
		double limit;
		if (entity instanceof EntityArmorStand || entity instanceof EntityItemFrame || entity instanceof EntityPainting) {
			limit = DECORATION_DISTANCE;
		} else if (entity instanceof EntityItem || entity instanceof EntityXPOrb) {
			limit = DROP_DISTANCE;
		} else {
			return false;
		}
		Entity viewer = Minecraft.getMinecraft().getRenderViewEntity();
		return viewer != null && entity.getDistanceSqToEntity(viewer) > limit * limit;
	}

	public static boolean skipTileEntity(TileEntity te) {
		if (!enabled() || !(te instanceof TileEntitySign || te instanceof TileEntityBanner || te instanceof TileEntitySkull)) {
			return false;
		}
		Entity viewer = Minecraft.getMinecraft().getRenderViewEntity();
		return viewer != null && te.getDistanceSq(viewer.posX, viewer.posY, viewer.posZ) > BLOCK_ENTITY_DISTANCE * BLOCK_ENTITY_DISTANCE;
	}
}
