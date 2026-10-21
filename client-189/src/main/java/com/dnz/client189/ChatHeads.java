package com.dnz.client189;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;

/** Module "chatheads" on 1.8.9: the skin head of the player who wrote a chat message, before the message. */
public final class ChatHeads {
	private static final int SIZE = 8;
	private static final Map<String, NetworkPlayerInfo> LINES = new LinkedHashMap<String, NetworkPlayerInfo>(256, 0.75F, false) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, NetworkPlayerInfo> eldest) {
			return this.size() > 300;
		}
	};
	private static NetworkPlayerInfo pending;

	private ChatHeads() {
	}

	private static boolean on() {
		Module m = Modules.get("chatheads");
		return m != null && m.enabled();
	}

	/** The message to split into lines: with room for the head when it has a writer. */
	public static IChatComponent indent(IChatComponent message) {
		pending = on() ? find(message.getUnformattedText()) : null;
		if (pending == null) {
			return message;
		}
		int space = Math.max(1, Minecraft.getMinecraft().fontRendererObj.getCharWidth(' '));
		StringBuilder pad = new StringBuilder();
		for (int i = (SIZE + 2 + space - 1) / space; i > 0; i--) {
			pad.append(' ');
		}
		return new ChatComponentText(pad.toString()).appendSibling(message);
	}

	/** Remembers the first line of the message just split. */
	public static void lines(List<IChatComponent> lines) {
		if (pending != null && !lines.isEmpty()) {
			LINES.put(lines.get(0).getFormattedText(), pending);
		}
		pending = null;
	}

	/** Before a chat line is drawn at (x, y) with [color]: its writer's head. */
	public static void draw(String text, float x, float y, int color) {
		if (!on()) {
			return;
		}
		NetworkPlayerInfo info = LINES.get(text);
		if (info == null) {
			return;
		}
		float alpha = (color >>> 24) / 255.0F;
		if (alpha < 0.05F) {
			return;
		}
		Minecraft.getMinecraft().getTextureManager().bindTexture(info.getLocationSkin());
		GlStateManager.enableBlend();
		GlStateManager.enableTexture2D();
		GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
		GlStateManager.color(1, 1, 1, alpha);
		int ix = Math.round(x);
		int iy = Math.round(y);
		Gui.drawScaledCustomSizeModalRect(ix, iy, 8, 8, 8, 8, SIZE, SIZE, 64, 64);
		Gui.drawScaledCustomSizeModalRect(ix, iy, 40, 8, 8, 8, SIZE, SIZE, 64, 64);
		GlStateManager.color(1, 1, 1, 1);
	}

	private static NetworkPlayerInfo find(String text) {
		NetHandlerPlayClient net = Minecraft.getMinecraft().getNetHandler();
		if (net == null || text.isEmpty()) {
			return null;
		}
		int separator = -1;
		for (char c : new char[] {':', '>', '»'}) {
			int i = text.indexOf(c);
			if (i > 0 && (separator < 0 || i < separator)) {
				separator = i;
			}
		}
		if (separator < 0) {
			return null;
		}
		NetworkPlayerInfo best = null;
		int bestAt = Integer.MAX_VALUE;
		for (NetworkPlayerInfo info : net.getPlayerInfoMap()) {
			String name = info.getGameProfile().getName();
			if (name == null || name.length() < 3) {
				continue;
			}
			int at = wordIndex(text, name, separator);
			if (at >= 0 && at < bestAt) {
				best = info;
				bestAt = at;
			}
		}
		return best;
	}

	private static int wordIndex(String text, String name, int end) {
		int from = 0;
		while (true) {
			int i = text.indexOf(name, from);
			if (i < 0 || i + name.length() > end) {
				return -1;
			}
			boolean startOk = i == 0 || !nameChar(text.charAt(i - 1));
			boolean endOk = i + name.length() >= text.length() || !nameChar(text.charAt(i + name.length()));
			if (startOk && endOk) {
				return i;
			}
			from = i + 1;
		}
	}

	private static boolean nameChar(char c) {
		return c == '_' || Character.isLetterOrDigit(c);
	}
}
