package com.dnz.client.hud;

import com.dnz.client.L;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;

/**
 * Food info on the vanilla hunger bar: saturation drawn as golden drumsticks, and while holding food,
 * the drumsticks it would fill blink. Food items also show their hunger and saturation in the tooltip.
 */
public final class FoodOverlay {
	private static final Identifier FULL = Identifier.withDefaultNamespace("hud/food_full");
	private static final Identifier HALF = Identifier.withDefaultNamespace("hud/food_half");
	private static final int GOLD = 0xFFFFC23D;

	private FoodOverlay() {
	}

	public static void register() {
		HudElementRegistry.attachElementAfter(VanillaHudElements.FOOD_BAR, Identifier.fromNamespaceAndPath("dnzclient", "food"), FoodOverlay::render);
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
			HudModule module = DnzHud.module("foodtooltip");
			FoodProperties food = stack.get(DataComponents.FOOD);
			if (module != null && module.enabled() && food != null) {
				lines.add(Component.literal(L.t("food.tooltip", food.nutrition(), String.format("%.1f", food.saturation())))
					.withStyle(ChatFormatting.GOLD));
			}
		});
	}

	private static boolean on(String id) {
		HudModule module = DnzHud.module(id);
		return module != null && module.enabled();
	}

	private static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		boolean saturation = on("foodsaturation");
		boolean preview = on("foodpreview");
		if ((!saturation && !preview) || mc.player == null || mc.gameMode == null || !mc.gameMode.canHurtPlayer()
			|| mc.gui.hud.isHidden()) {
			return;
		}
		// While riding a horse the hunger bar is replaced by the horse's health.
		if (mc.player.getVehicle() instanceof LivingEntity) {
			return;
		}
		FoodData data = mc.player.getFoodData();
		int food = data.getFoodLevel();
		float sat = data.getSaturationLevel();
		int right = g.guiWidth() / 2 + 91;
		int top = g.guiHeight() - 39;

		if (saturation) {
			drawUnits(g, right, top, 0, Math.round(sat), GOLD);
		}
		if (preview) {
			ItemStack held = heldFood(mc);
			FoodProperties props = held.isEmpty() ? null : held.get(DataComponents.FOOD);
			if (props != null && (food < 20 || props.canAlwaysEat())) {
				int newFood = Math.min(20, food + props.nutrition());
				float newSat = Math.min(newFood, sat + props.saturation());
				// Slow blink, like a hint rather than an alarm.
				float pulse = 0.35F + 0.45F * (float) (0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 220.0));
				int alpha = Math.round(pulse * 255) << 24;
				drawUnits(g, right, top, food, newFood, alpha | 0xFFFFFF);
				if (saturation) {
					drawUnits(g, right, top, Math.round(sat), Math.round(newSat), alpha | (GOLD & 0xFFFFFF));
				}
			}
		}
	}

	private static ItemStack heldFood(Minecraft mc) {
		ItemStack main = mc.player.getMainHandItem();
		if (main.has(DataComponents.FOOD)) {
			return main;
		}
		ItemStack off = mc.player.getOffhandItem();
		return off.has(DataComponents.FOOD) ? off : ItemStack.EMPTY;
	}

	/** Draws hunger units [from, to) (2 units = one drumstick) with a tint, right to left like the vanilla bar. */
	private static void drawUnits(GuiGraphicsExtractor g, int right, int top, int from, int to, int color) {
		for (int i = 0; i < 10; i++) {
			int start = i * 2;
			int x = right - i * 8 - 9;
			boolean first = from <= start && start < to;
			boolean second = from <= start + 1 && start + 1 < to;
			if (first && second) {
				g.blitSprite(RenderPipelines.GUI_TEXTURED, FULL, x, top, 9, 9, color);
			} else if (first) {
				g.blitSprite(RenderPipelines.GUI_TEXTURED, HALF, x, top, 9, 9, color);
			} else if (second) {
				// Only the right half of this drumstick is new.
				g.enableScissor(x + 4, top, x + 9, top + 9);
				g.blitSprite(RenderPipelines.GUI_TEXTURED, FULL, x, top, 9, 9, color);
				g.disableScissor();
			}
		}
	}
}
