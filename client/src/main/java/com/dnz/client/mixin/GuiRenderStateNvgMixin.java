package com.dnz.client.mixin;

import com.dnz.client.nvg.NvgRecorder;
import com.dnz.client.nvg.NvgText;
import net.minecraft.client.renderer.state.gui.BlitRenderState;
import net.minecraft.client.renderer.state.gui.ColoredRectangleRenderState;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.GuiTextRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** NanoVG menus: while a DNZ menu is recorded, its texts, rectangles and pictures go to NanoVG instead. */
@Mixin(GuiRenderState.class)
public class GuiRenderStateNvgMixin {
	@Inject(method = "addText", at = @At("HEAD"), cancellable = true)
	private void dnz$text(GuiTextRenderState text, CallbackInfo ci) {
		if (NvgRecorder.recording) {
			NvgText.record(text);
			ci.cancel();
		}
	}

	@Inject(method = "addGuiElement", at = @At("HEAD"), cancellable = true)
	private void dnz$element(GuiElementRenderState element, CallbackInfo ci) {
		if (NvgRecorder.recording && element instanceof ColoredRectangleRenderState r && r.textureSetup().texure0() == null) {
			NvgRecorder.add(new NvgRecorder.Shape(NvgRecorder.matrix(r.pose()), r.x0(), r.y0(), r.x1() - r.x0(), r.y1() - r.y0(),
				0, 0, r.col1(), r.col2(), false, r.scissorArea()));
			ci.cancel();
		} else if (NvgRecorder.recording && element instanceof BlitRenderState b) {
			dnz$blit(b);
			ci.cancel();
		}
	}

	@Inject(method = "addBlitToCurrentLayer", at = @At("HEAD"), cancellable = true)
	private void dnz$blitLayer(BlitRenderState b, CallbackInfo ci) {
		if (NvgRecorder.recording) {
			dnz$blit(b);
			ci.cancel();
		}
	}

	private static void dnz$blit(BlitRenderState b) {
		NvgRecorder.add(new NvgRecorder.Image(NvgRecorder.matrix(b.pose()), b.textureSetup().texure0(), b.x0(), b.y0(), b.x1(), b.y1(),
			b.u0(), b.v0(), b.u1(), b.v1(), b.color(), b.scissorArea()));
	}
}
