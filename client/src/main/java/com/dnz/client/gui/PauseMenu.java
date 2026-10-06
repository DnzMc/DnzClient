package com.dnz.client.gui;

import java.util.List;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

/**
 * Minecraft's own ESC menu with a "DNZ Settings" button (opens the Right Shift screen), in the place of "Report Bugs".
 * Without that button: in the place of Open to LAN, else next to a narrowed Options button.
 */
public final class PauseMenu {
	private PauseMenu() {
	}

	public static void addDnzButton(Minecraft mc, PauseScreen screen) {
		List<AbstractWidget> widgets = Screens.getWidgets(screen);
		int x, y, w, h;
		// In the place of "Report Bugs" (a link to Mojang's bug site), so Open to LAN and World Options stay;
		// without it, in the place of "Open to LAN".
		AbstractWidget lan = find(widgets, "menu.reportBugs");
		if (lan == null) {
			lan = find(widgets, "menu.multiplayerOptions.button");
		}
		if (lan != null) {
			x = lan.getX();
			y = lan.getY();
			w = lan.getWidth();
			h = lan.getHeight();
			widgets.remove(lan);
		} else {
			AbstractWidget options = find(widgets, "menu.options");
			AbstractWidget advancements = find(widgets, "gui.advancements");
			AbstractWidget stats = find(widgets, "gui.stats");
			if (options == null || advancements == null || stats == null) {
				return;
			}
			// Same columns as the Advancements / Statistics row above.
			options.setWidth(advancements.getWidth());
			x = stats.getX();
			y = options.getY();
			w = stats.getWidth();
			h = options.getHeight();
		}
		widgets.add(Button.builder(Component.literal("DNZ Settings"), b -> mc.gui.setScreen(new DnzShiftScreen()))
			.bounds(x, y, w, h).build());
	}

	private static AbstractWidget find(List<AbstractWidget> widgets, String key) {
		for (AbstractWidget w : widgets) {
			if (w.getMessage().getContents() instanceof TranslatableContents t && t.getKey().equals(key)) {
				return w;
			}
		}
		return null;
	}
}
