package com.dnz.client.mixin;

import net.minecraft.client.gui.components.AbstractSelectionList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The list a row belongs to (to know whether the row is the selected one). */
@Mixin(targets = "net.minecraft.client.gui.components.AbstractSelectionList$Entry")
public interface SelectionEntryAccessor {
	@Accessor("list")
	AbstractSelectionList<?> dnz$list();
}
