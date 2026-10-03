package com.dnz.schematic;

import com.dnz.schematic.gui.SchematicScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

/**
 * DNZ Schematic: loads .litematic buildings, shows them as see-through blocks and marks every mistake.
 * M opens the menu, Page Up / Page Down change the layer.
 */
public class DnzSchematic implements ClientModInitializer {
	public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("dnzschematic", "main"));
	public static final KeyMapping MENU_KEY = new KeyMapping("key.dnzschematic.menu", InputConstants.KEY_M, CATEGORY);
	public static final KeyMapping LAYER_UP = new KeyMapping("key.dnzschematic.layer_up", InputConstants.KEY_PAGEUP, CATEGORY);
	public static final KeyMapping LAYER_DOWN = new KeyMapping("key.dnzschematic.layer_down", InputConstants.KEY_PAGEDOWN, CATEGORY);

	@Override
	public void onInitializeClient() {
		SchematicConfig.get();
		KeyMappingHelper.registerKeyMapping(MENU_KEY);
		KeyMappingHelper.registerKeyMapping(LAYER_UP);
		KeyMappingHelper.registerKeyMapping(LAYER_DOWN);
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("dnzschematic", "hud"), SchematicHud::render);
		GhostRenderer.register();

		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			while (MENU_KEY.consumeClick()) {
				if (mc.gui.screen() == null) {
					mc.gui.setScreen(new SchematicScreen(null));
				}
			}
			while (LAYER_UP.consumeClick()) {
				Schematics.changeLayer(1);
			}
			while (LAYER_DOWN.consumeClick()) {
				Schematics.changeLayer(-1);
			}
			Schematics.tick(mc);
			GhostRenderer.tick(mc);
		});
	}
}
