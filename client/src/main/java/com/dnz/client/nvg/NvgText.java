package com.dnz.client.nvg;

import com.dnz.client.gui.Theme;
import com.dnz.client.mixin.GuiTextStateAccessor;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.state.gui.GuiTextRenderState;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

/** NanoVG menus: turns a Minecraft text piece into runs of one font and color, placed with Minecraft's widths. */
public final class NvgText {
	private NvgText() {
	}

	public static void record(GuiTextRenderState state) {
		GuiTextStateAccessor a = (GuiTextStateAccessor) (Object) state;
		Font font = a.dnz$font();
		int base = a.dnz$color();
		List<NvgRecorder.Run> runs = new ArrayList<>();
		List<Float> xs = new ArrayList<>();
		StringBuilder sb = new StringBuilder();
		Style[] cur = {null};
		float[] x = {a.dnz$x()};
		Runnable flush = () -> {
			if (sb.length() > 0) {
				Style s = cur[0];
				FontDescription f = Theme.smoothAll ? Theme.menuFont(s.getFont()) : s.getFont();
				int rgb = s.getColor() != null ? s.getColor().getValue() : base & 0xFFFFFF;
				String str = sb.toString();
				runs.add(new NvgRecorder.Run(str, f, (base & 0xFF000000) | rgb, s.isBold()));
				xs.add(x[0]);
				x[0] += font.width(FormattedCharSequence.forward(str, s));
				sb.setLength(0);
			}
		};
		a.dnz$text().accept((index, style, codePoint) -> {
			if (cur[0] != null && !same(cur[0], style)) {
				flush.run();
			}
			cur[0] = style;
			sb.appendCodePoint(codePoint);
			return true;
		});
		flush.run();
		if (runs.isEmpty() || (base >>> 24) == 0) {
			return;
		}
		float[] xa = new float[xs.size()];
		for (int i = 0; i < xa.length; i++) {
			xa[i] = xs.get(i);
		}
		NvgRecorder.add(new NvgRecorder.Text(NvgRecorder.matrix(state.pose), runs, xa, a.dnz$y(), a.dnz$shadow(), state.scissor));
	}

	private static boolean same(Style a, Style b) {
		return a.getFont().equals(b.getFont()) && a.isBold() == b.isBold() && java.util.Objects.equals(a.getColor(), b.getColor());
	}
}
