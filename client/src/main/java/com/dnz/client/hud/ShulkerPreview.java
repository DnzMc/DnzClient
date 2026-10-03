package com.dnz.client.hud;

import net.fabricmc.fabric.api.client.rendering.v1.ClientTooltipComponentCallback;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.ShulkerBoxBlock;

import java.util.Optional;

/** Shulker boxes show their 27 slots as a small colored inventory in the tooltip, instead of a text list. */
public final class ShulkerPreview {
	private static final int SLOTS = 27;
	private static final int COLUMNS = 9;
	private static final int SLOT = 18;
	/** The plain (undyed) shulker's purple. */
	private static final int PLAIN = 0xFF8C6A8C;

	private ShulkerPreview() {
	}

	public static void register() {
		ClientTooltipComponentCallback.EVENT.register(data -> data instanceof Contents contents ? new Grid(contents) : null);
	}

	public static boolean enabled() {
		HudModule module = DnzHud.module("shulkerpreview");
		return module != null && module.enabled();
	}

	/** The preview for [stack], or empty when it is not a filled shulker box (or the module is off). */
	public static Optional<TooltipComponent> of(ItemStack stack) {
		if (!enabled() || !(stack.getItem() instanceof BlockItem item) || !(item.getBlock() instanceof ShulkerBoxBlock box)) {
			return Optional.empty();
		}
		ItemContainerContents container = stack.get(DataComponents.CONTAINER);
		if (container == null || container.nonEmptyItemCopyStream().findAny().isEmpty()) {
			return Optional.empty();
		}
		NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
		container.copyInto(items);
		DyeColor color = box.getColor();
		return Optional.of(new Contents(items, color == null ? PLAIN : 0xFF000000 | color.getTextureDiffuseColor()));
	}

	/** Whether the vanilla "item x5, ... and 3 more" lines should be left out (the grid shows them). */
	public static boolean hidesTextList(ItemStack stack) {
		return of(stack).isPresent();
	}

	public record Contents(NonNullList<ItemStack> items, int color) implements TooltipComponent {
	}

	private record Grid(Contents contents) implements ClientTooltipComponent {
		@Override
		public int getHeight(Font font) {
			return (SLOTS / COLUMNS) * SLOT + 6;
		}

		@Override
		public int getWidth(Font font) {
			return COLUMNS * SLOT + 2;
		}

		@Override
		public void extractImage(Font font, int x, int y, int width, int height, GuiGraphicsExtractor g) {
			int rows = SLOTS / COLUMNS;
			int w = COLUMNS * SLOT + 2;
			int h = rows * SLOT + 2;
			int top = y + 2;
			// Frame in the box's color, darker slots inside.
			g.fill(x, top, x + w, top + h, darken(contents.color, 0.55F));
			g.outline(x, top, w, h, contents.color);
			for (int i = 0; i < SLOTS; i++) {
				int sx = x + 1 + (i % COLUMNS) * SLOT;
				int sy = top + 1 + (i / COLUMNS) * SLOT;
				g.fill(sx + 1, sy + 1, sx + SLOT - 1, sy + SLOT - 1, 0x66000000);
				ItemStack stack = contents.items.get(i);
				if (!stack.isEmpty()) {
					g.item(stack, sx + 1, sy + 1);
					g.itemDecorations(font, stack, sx + 1, sy + 1);
				}
			}
		}

		private static int darken(int argb, float factor) {
			int r = Math.round(((argb >> 16) & 0xFF) * factor);
			int gr = Math.round(((argb >> 8) & 0xFF) * factor);
			int b = Math.round((argb & 0xFF) * factor);
			return 0xF0000000 | (r << 16) | (gr << 8) | b;
		}
	}
}
