package com.dnz.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/** "What am I looking at" (module "lookat"): block with its picture and position, or the creature with its health. */
final class LookAt extends HudModule {
	LookAt() {
		super("lookat", Category.INFO, 0.42F, 0.01F, false);
	}

	@Override
	public int[] render(HudRender r) {
		Minecraft mc = r.mc;
		HitResult hit = mc.hitResult;
		String title;
		String line;
		ItemStack icon = ItemStack.EMPTY;
		if (hit instanceof BlockHitResult b && hit.getType() == HitResult.Type.BLOCK && mc.level != null) {
			BlockPos p = b.getBlockPos();
			BlockState state = mc.level.getBlockState(p);
			title = state.getBlock().getName().getString();
			line = p.getX() + " " + p.getY() + " " + p.getZ();
			icon = new ItemStack(state.getBlock().asItem());
		} else if (hit instanceof EntityHitResult e) {
			Entity entity = e.getEntity();
			title = entity.getDisplayName().getString();
			line = entity instanceof LivingEntity l ? String.format("%.1f / %.0f ❤", l.getHealth(), l.getMaxHealth()) : "";
		} else if (r.editor) {
			title = "Diamond Ore";
			line = "120 -54 33";
			icon = new ItemStack(Items.DIAMOND_ORE);
		} else {
			return new int[] {0, 0};
		}
		int textX = icon.isEmpty() ? 6 : 26;
		int w = Math.max(r.width(title), r.width(line)) + textX + 6;
		int h = 24;
		if (r.measure) {
			return new int[] {w, h};
		}
		r.panel(w, h);
		r.accentBar(h);
		if (!icon.isEmpty()) {
			r.item(icon, 6, 4);
		}
		r.text(title, textX, 3, 0xFFFFFFFF);
		r.text(line, textX, 13, 0xFFAAB0C0);
		return new int[] {w, h};
	}
}
