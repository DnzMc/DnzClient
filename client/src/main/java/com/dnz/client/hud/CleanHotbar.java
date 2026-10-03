package com.dnz.client.hud;

import com.dnz.client.gui.Theme;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Borderless hotbar: one see-through rounded bar instead of the nine boxed slots, the selected slot
 * lit in the accent color. Same place and size as the vanilla hotbar, so nothing else moves.
 */
public final class CleanHotbar {
	private CleanHotbar() {
	}

	public static void register() {
		HudElementRegistry.replaceElement(VanillaHudElements.HOTBAR, vanilla -> (g, delta) -> {
			HudModule module = DnzHud.module("cleanhotbar");
			Minecraft mc = Minecraft.getInstance();
			// Spectators get their own menu there; leave it to vanilla.
			if (module != null && module.enabled() && mc.player != null && !mc.player.isSpectator()) {
				render(g, mc, mc.player);
			} else {
				vanilla.extractRenderState(g, delta);
			}
		});
	}

	private static void render(GuiGraphicsExtractor g, Minecraft mc, Player player) {
		int x = g.guiWidth() / 2 - 91;
		int y = g.guiHeight() - 22;
		int accent = Theme.accent() & 0xFFFFFF;
		Theme.roundedRect(g, x, y, 182, 22, 0x70000000);
		int selected = player.getInventory().getSelectedSlot();
		Theme.roundedRect(g, x + 1 + selected * 20, y + 1, 20, 20, 0x90 << 24 | accent);
		g.outline(x + 1 + selected * 20, y + 1, 20, 20, 0xFFFFFFFF);

		for (int i = 0; i < 9; i++) {
			item(g, mc, player.getInventory().getItem(i), x + 3 + i * 20, y + 3);
		}
		ItemStack offhand = player.getOffhandItem();
		if (!offhand.isEmpty()) {
			// Offhand on the side opposite the main hand, like vanilla.
			boolean left = player.getMainArm() == HumanoidArm.RIGHT;
			int ox = left ? x - 26 : x + 182 + 4;
			Theme.roundedRect(g, ox, y, 22, 22, 0x70000000);
			item(g, mc, offhand, ox + 3, y + 3);
		}
	}

	private static void item(GuiGraphicsExtractor g, Minecraft mc, ItemStack stack, int x, int y) {
		if (!stack.isEmpty()) {
			g.item(stack, x, y);
			g.itemDecorations(mc.font, stack, x, y);
		}
	}
}
