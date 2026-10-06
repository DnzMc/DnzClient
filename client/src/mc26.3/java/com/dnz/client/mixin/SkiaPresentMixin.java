package com.dnz.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

/** Skia menus are an experiment on Minecraft 26.2 only for now; nothing happens here. */
@Pseudo
@Mixin(targets = "com.dnz.client.skia.NotOn26_3")
public class SkiaPresentMixin {
}
