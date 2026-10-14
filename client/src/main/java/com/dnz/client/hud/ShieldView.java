package com.dnz.client.hud;

import com.dnz.client.DnzConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Shield module (Combat): how low and how big the shield is drawn in first person (DnzConfig.shieldLower / shieldSize). */
public final class ShieldView {
	private ShieldView() {
	}

	/** Called right after the game moved the pose to the held item: lowers and shrinks a shield around its own spot. */
	public static void apply(ItemStack stack, PoseStack pose) {
		if (!stack.is(Items.SHIELD)) {
			return;
		}
		HudModule module = DnzHud.module("shield");
		if (module == null || !module.enabled()) {
			return;
		}
		DnzConfig c = DnzConfig.get();
		pose.translate(0.0F, -0.45F * c.shieldLower / 100.0F, 0.0F);
		float s = c.shieldSize / 100.0F;
		pose.scale(s, s, s);
	}
}
