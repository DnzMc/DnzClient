package com.dnz.client.hud;

import com.dnz.client.DnzConfig;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;

/** Module "chatheads": the skin head of the player who wrote a chat message, before the message. */
public final class ChatHeads {
	private static final int SIZE = 8;
	/** Message -> its writer (absent = no writer found). */
	private static final Map<GuiMessage, PlayerInfo> OWNERS = Collections.synchronizedMap(new WeakHashMap<>());
	private static final Map<FormattedCharSequence, PlayerInfo> LINES = Collections.synchronizedMap(new WeakHashMap<>());

	private ChatHeads() {
	}

	private static boolean on() {
		return DnzConfig.get().chatHeads;
	}

	/** The message text to split into lines: with room for the head when it has a writer. */
	public static FormattedText indent(GuiMessage message, FormattedText content, Font font) {
		if (!on() || owner(message) == null) {
			return content;
		}
		int space = Math.max(1, font.width(" "));
		String pad = " ".repeat((SIZE + 2 + space - 1) / space);
		return content instanceof Component c ? Component.literal(pad).append(c) : FormattedText.composite(FormattedText.of(pad), content);
	}

	public static void lines(GuiMessage message, List<FormattedCharSequence> lines) {
		if (!on() || lines.isEmpty()) {
			return;
		}
		PlayerInfo owner = owner(message);
		if (owner != null) {
			LINES.put(lines.getFirst(), owner);
		}
	}

	/** Draws the head before a chat line (text starts at x 0, [y] is its top). */
	public static void draw(GuiGraphicsExtractor g, int y, float alpha, FormattedCharSequence line) {
		if (!on()) {
			return;
		}
		PlayerInfo owner = LINES.get(line);
		if (owner != null) {
			PlayerFaceExtractor.extractRenderState(g, owner.getSkin(), 0, y, SIZE, ARGB.white(alpha));
		}
	}

	private static PlayerInfo owner(GuiMessage message) {
		if (OWNERS.containsKey(message)) {
			return OWNERS.get(message);
		}
		PlayerInfo found = find(message.content().getString());
		OWNERS.put(message, found);
		return found;
	}

	private static PlayerInfo find(String text) {
		ClientPacketListener connection = Minecraft.getInstance().getConnection();
		if (connection == null || text.isEmpty()) {
			return null;
		}
		int separator = firstSeparator(text);
		if (separator < 0) {
			return null;
		}
		PlayerInfo best = null;
		int bestAt = Integer.MAX_VALUE;
		for (PlayerInfo info : connection.getOnlinePlayers()) {
			String name = info.getProfile().name();
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

	private static int firstSeparator(String text) {
		int best = -1;
		for (char c : new char[] {':', '>', '»'}) {
			int i = text.indexOf(c);
			if (i > 0 && (best < 0 || i < best)) {
				best = i;
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
