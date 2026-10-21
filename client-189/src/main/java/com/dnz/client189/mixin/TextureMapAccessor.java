package com.dnz.client189.mixin;

import java.util.List;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The animated textures of the block atlas (for DNZ Engine's animated textures). */
@Mixin(TextureMap.class)
public interface TextureMapAccessor {
	@Accessor("listAnimatedSprites")
	List<TextureAtlasSprite> dnz$animated();
}
