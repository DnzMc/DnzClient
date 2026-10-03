package com.dnz.client.mixin;

import com.dnz.client.gui.DnzPauseScreen;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Gui.class)
public class GuiMixin {
	/** Swap the vanilla ESC menu for the DNZ one (F3+Esc "paused" overlay stays vanilla). */
	@ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
	private Screen dnz$replacePauseScreen(Screen screen) {
		if (screen instanceof PauseScreen pause && pause.showsPauseMenu()) {
			return new DnzPauseScreen();
		}
		return screen;
	}
}
