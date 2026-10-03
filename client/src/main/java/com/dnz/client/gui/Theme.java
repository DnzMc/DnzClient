package com.dnz.client.gui;

import com.dnz.client.L;
import com.dnz.client.DnzConfig;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;

/** Shared colors and drawing helpers. Colors follow the player's Visual settings. */
public final class Theme {
	public record Accent(String name, int rgb) {
		public String label() {
			return L.t("accent." + this.name);
		}
	}

	public record Style(String name, int panel, int button, int buttonHover, int bgTop, int bgBottom) {
		public String label() {
			return L.t("theme." + this.name);
		}
	}

	public static final Accent[] ACCENTS = {
		new Accent("blue", 0x4FA3FF),
		new Accent("purple", 0xA56BFF),
		new Accent("red", 0xFF5A5A),
		new Accent("green", 0x4CD97B),
		new Accent("orange", 0xFF9F43),
		new Accent("pink", 0xFF6BC1),
		new Accent("turquoise", 0x3ED6D0),
		new Accent("gold", 0xFFD24A),
	};

	public static final Style[] STYLES = {
		new Style("dark", 0xC00E1118, 0xA0161A24, 0xD02A3550, 0x900A0D14, 0xC0060810),
		new Style("midnight", 0xC00B1630, 0xA0122044, 0xD01E3570, 0x900A1430, 0xC0050A1C),
		new Style("purple_night", 0xC0180E26, 0xA0241638, 0xD03A2458, 0x90140A20, 0xC0080410),
		new Style("glass", 0x60101418, 0x60202830, 0x90405060, 0x40000000, 0x70000000),
		new Style("amoled", 0xF0000000, 0xE0080808, 0xF0181818, 0xC0000000, 0xE0000000),
	};

	public static final int PANEL_BORDER = 0x40FFFFFF;
	public static final int MUTED = 0xFF9AA3B5;

	private Theme() {
	}

	public static Accent accentInfo() {
		int i = DnzConfig.get().accent;
		return ACCENTS[i >= 0 && i < ACCENTS.length ? i : 0];
	}

	public static Style style() {
		int i = DnzConfig.get().theme;
		return STYLES[i >= 0 && i < STYLES.length ? i : 0];
	}

	public static boolean javaStyle() {
		return DnzConfig.get().javaStyle;
	}

	/** Simple style: clean glass menus (rounded, dark glass, smooth font). */
	public static boolean simpleStyle() {
		DnzConfig config = DnzConfig.get();
		return config.simpleStyle && !config.javaStyle;
	}

	/** DNZ style: the default rounded menus (neither Java nor Simple). */
	public static boolean dnzStyle() {
		return !javaStyle() && !simpleStyle();
	}

	/** Anti-aliased font (Inter) from assets/dnzclient/font/smooth.json. */
	public static final FontDescription SMOOTH_FONT = new FontDescription.Resource(Identifier.fromNamespaceAndPath("dnzclient", "smooth"));

	public static MutableComponent smooth(String text) {
		return smooth(Component.literal(text));
	}

	/** The text in the smooth font (parts with their own font keep it). */
	public static MutableComponent smooth(Component text) {
		return text.copy().withStyle(style -> style.withFont(SMOOTH_FONT));
	}

	/** Poppins weights of the DNZ menu (assets/dnzclient/font/ui_*.json, drawn at 8x so they stay sharp at any GUI scale). */
	public static final FontDescription UI_REGULAR = new FontDescription.Resource(Identifier.fromNamespaceAndPath("dnzclient", "ui_regular"));
	public static final FontDescription UI_MEDIUM = new FontDescription.Resource(Identifier.fromNamespaceAndPath("dnzclient", "ui_medium"));
	public static final FontDescription UI_SEMIBOLD = new FontDescription.Resource(Identifier.fromNamespaceAndPath("dnzclient", "ui_semibold"));

	/** Font the DNZ menu uses instead of [font]: Poppins in the Modern look, Minecraft's own pixel font in the Minecraft look. */
	public static FontDescription menuFont(FontDescription font) {
		boolean ours = font.equals(FontDescription.DEFAULT) || font.equals(SMOOTH_FONT);
		if (minecraftLook()) {
			return ours || font.equals(UI_REGULAR) || font.equals(UI_MEDIUM) || font.equals(UI_SEMIBOLD) || font.equals(BOLD_FONT)
				? FontDescription.DEFAULT : font;
		}
		return ours ? UI_REGULAR : font;
	}

	/** DNZ menu in the Minecraft look: pixel font, black and white. */
	public static boolean minecraftLook() {
		return DnzConfig.get().menuLook == 1;
	}

	/** DNZ menu in the White theme. */
	public static boolean lightMenu() {
		return DnzConfig.get().menuLight;
	}

	/** True when [text] starts in a font that is drawn as Minecraft's pixel font in the menu. */
	public static boolean pixelText(FormattedCharSequence text) {
		boolean[] pixel = {false};
		text.accept((index, style, codePoint) -> {
			pixel[0] = menuFont(style.getFont()).equals(FontDescription.DEFAULT);
			return false;
		});
		return pixel[0];
	}

	/** True while the DNZ menu draws: all pixel-font text is drawn and measured in the smooth font (see TextSmoothMixin). */
	public static volatile boolean smoothAll;

	/** Already split text with its pixel-font parts switched to the smooth font. */
	public static FormattedCharSequence smooth(FormattedCharSequence text) {
		return sink -> text.accept((index, style, codePoint) ->
			sink.accept(index, smoothAll ? style.withFont(menuFont(style.getFont())) : style.getFont().equals(FontDescription.DEFAULT) ? style.withFont(SMOOTH_FONT) : style, codePoint));
	}

	/** Selection blue of the DNZ menu (the accent color when another one is chosen). */
	public static int menuAccent() {
		return DnzConfig.get().accent == 0 ? 0xFF4A7CF6 : 0xFF000000 | accentInfo().rgb();
	}

	/** Button text: smooth in the Simple style, the normal Minecraft font otherwise. */
	public static Component label(Component text) {
		return simpleStyle() ? smooth(text) : text;
	}

	/** Accent color with full alpha. */
	public static int accent() {
		return 0xFF000000 | accentInfo().rgb();
	}

	public static int lerp(int from, int to, float t) {
		t = Math.max(0.0F, Math.min(1.0F, t));
		int a = (int) (((from >>> 24) & 0xFF) + (((to >>> 24) & 0xFF) - ((from >>> 24) & 0xFF)) * t);
		int r = (int) (((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t);
		int gr = (int) (((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t);
		int b = (int) ((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
		return a << 24 | r << 16 | gr << 8 | b;
	}

	/** The color a bit lighter (toward white), for hovered accent buttons. */
	public static int lighter(int rgb) {
		return lerp(0xFF000000 | rgb, 0xFFFFFFFF, 0.18F) & 0xFFFFFF;
	}

	public static int withAlpha(int rgb, float alpha) {
		int a = Math.max(0, Math.min(255, Math.round(alpha * 255.0F)));
		return a << 24 | (rgb & 0xFFFFFF);
	}

	/** Button background for the current theme; hover is 0..1. */
	public static int buttonBg(boolean active, boolean selected, float hover) {
		if (!active) {
			return 0x80202028;
		}
		if (selected) {
			return lerp(withAlpha(accentInfo().rgb(), 0.75F), withAlpha(accentInfo().rgb(), 0.95F), hover);
		}
		Style s = style();
		return lerp(s.button(), s.buttonHover(), hover);
	}

	/** Rectangle with slightly rounded corners (smooth, see {@link Smooth}). */
	public static void roundedRect(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
		Smooth.rect(g, x, y, w, h, 1.5F, color);
	}

	/** Rectangle with round corners of [radius] pixels, soft at any GUI scale (one shape, not pixel steps). */
	public static void roundedRect(GuiGraphicsExtractor g, int x, int y, int w, int h, int radius, int color) {
		Smooth.rect(g, x, y, w, h, radius, color);
	}

	/** Simple-style button background: light glass, lighter on hover, accent when selected. */
	public static int simpleButtonBg(boolean active, boolean selected, float hover) {
		if (!active) {
			return 0x1EFFFFFF;
		}
		if (selected) {
			return withAlpha(accentInfo().rgb(), 0.80F + 0.15F * hover);
		}
		return lerp(0x33FFFFFF, 0x5CFFFFFF, hover);
	}

	/** Static (non-animated) DNZ button look, used for restyled vanilla widgets. */
	public static void button(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean active, boolean hovered) {
		if (simpleStyle()) {
			roundedRect(g, x, y, w, h, 3, simpleButtonBg(active, false, hovered ? 1.0F : 0.0F));
			return;
		}
		roundedRect(g, x, y, w, h, buttonBg(active, false, hovered ? 1.0F : 0.0F));
		if (active) {
			g.fill(x, y + 2, x + (hovered ? 4 : 2), y + h - 2, accent());
		}
	}

	public static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		if (simpleStyle()) {
			// Dark glass with a thin light edge (clean, glass).
			roundedRect(g, x - 1, y - 1, w + 2, h + 2, 7, 0x38FFFFFF);
			roundedRect(g, x, y, w, h, 6, 0xD8121316);
			return;
		}
		// DNZ: deep dark card with large soft corners, a soft shadow and a hairline edge.
		Smooth.shadow(g, x, y + 3, w, h, 12, 16, 0x70000000);
		Smooth.rect(g, x - 1, y - 1, w + 2, h + 2, 13, 0x1CFFFFFF);
		Smooth.rect(g, x, y, w, h, 12, 0xF5101216);
	}

	public static void screenBackground(GuiGraphicsExtractor g, int width, int height) {
		if (simpleStyle()) {
			g.fill(0, 0, width, height, 0x66000000);
			return;
		}
		g.fillGradient(0, 0, width, height, 0x99040508, 0xC8040508);
	}

	/**
	 * Centered, bold, smooth text with extra space between the letters (Simple style titles and buttons).
	 * [spacing] is in pixels before scaling.
	 */
	public static void spacedText(GuiGraphicsExtractor g, Font font, String text, int centerX, int y, float scale, int spacing, int color) {
		spacedText(g, font, text, centerX, y, scale, spacing, color, 0.6F);
	}

	/**
	 * [weight] thickens the letters by drawing them again shifted by fractions of a pixel (screen pixels).
	 * Minecraft's own bold shifts a whole (scaled) pixel, which looks like a blurry double on the smooth font.
	 */
	public static void spacedText(GuiGraphicsExtractor g, Font font, String text, int centerX, int y, float scale, int spacing, int color, float weight) {
		int total = 0;
		int[] widths = new int[text.length()];
		for (int i = 0; i < text.length(); i++) {
			widths[i] = font.width(spacedChar(text.charAt(i)));
			total += widths[i] + (i < text.length() - 1 ? spacing : 0);
		}
		int passes = Math.max(1, Math.round(weight / 0.3F) + 1);
		for (int pass = 0; pass < passes; pass++) {
			float shift = passes == 1 ? 0 : weight * pass / (passes - 1);
			g.pose().pushMatrix();
			g.pose().translate(centerX - total * scale / 2.0F + shift, y);
			g.pose().scale(scale, scale);
			int x = 0;
			for (int i = 0; i < text.length(); i++) {
				g.text(font, spacedChar(text.charAt(i)), x, 0, color, false);
				x += widths[i] + spacing;
			}
			g.pose().popMatrix();
		}
	}

	/** Poppins Bold rendered extra large for the title screen logo (assets/dnzclient/font/title.json). */
	public static final FontDescription TITLE_FONT = new FontDescription.Resource(Identifier.fromNamespaceAndPath("dnzclient", "title"));

	/**
	 * Title screen logo of the Modern look: "DNZ CLIENT" in sharp bold capitals with some room between the letters,
	 * "DNZ" in the accent color. [scale] 1 = normal text size.
	 */
	public static void logo(GuiGraphicsExtractor g, Font font, int centerX, int y, float scale, float alpha) {
		String text = "DNZ CLIENT";
		float spacing = 1.6F;
		float total = 0;
		float[] widths = new float[text.length()];
		for (int i = 0; i < text.length(); i++) {
			widths[i] = font.width(logoChar(text.charAt(i)));
			total += widths[i] + (i < text.length() - 1 ? spacing : 0);
		}
		int accent = withAlpha(lerp(0xFF000000 | accentInfo().rgb(), 0xFFFFFFFF, 0.15F) & 0xFFFFFF, alpha);
		int white = withAlpha(0xFFFFFF, alpha);
		g.pose().pushMatrix();
		g.pose().translate(centerX - total * scale / 2.0F, y);
		g.pose().scale(scale, scale);
		float x = 0;
		for (int i = 0; i < text.length(); i++) {
			g.pose().pushMatrix();
			g.pose().translate(x, 0);
			g.text(font, logoChar(text.charAt(i)), 0, 0, i < 3 ? accent : white, false);
			g.pose().popMatrix();
			x += widths[i] + spacing;
		}
		g.pose().popMatrix();
	}

	private static Component logoChar(char c) {
		return Component.literal(String.valueOf(c)).withStyle(style -> style.withFont(TITLE_FONT));
	}

	/** Real bold font (Poppins Bold) for Simple style titles and buttons. */
	public static final FontDescription BOLD_FONT = new FontDescription.Resource(Identifier.fromNamespaceAndPath("dnzclient", "bold"));

	private static Component spacedChar(char c) {
		return Component.literal(String.valueOf(c)).withStyle(style -> style.withFont(BOLD_FONT));
	}

	/** Text at x,y. In the Simple style without Minecraft's drop shadow (it looks like a blurry double on the smooth font). */
	public static void text(GuiGraphicsExtractor g, Font font, Component text, int x, int y, int color) {
		boolean simple = simpleStyle();
		g.text(font, simple ? smooth(text) : text, x, y, color, !simple);
	}

	/** Centered version of {@link #text}. */
	public static void centeredText(GuiGraphicsExtractor g, Font font, Component text, int centerX, int y, int color) {
		Component shown = simpleStyle() ? smooth(text) : text;
		text(g, font, shown, centerX - font.width(shown) / 2, y, color);
	}

	/** A heading: scaled Minecraft text normally; small, smooth and sharp in the Simple style. */
	public static void heading(GuiGraphicsExtractor g, Font font, Component text, int centerX, int y, float scale, int color) {
		if (simpleStyle()) {
			centeredText(g, font, text, centerX, y + Math.round(4 * (scale - 1)), color);
		} else {
			scaledCentered(g, font, text, centerX, y, scale, color);
		}
	}

	public static Component brand() {
		return Component.literal("DNZ").withColor(accentInfo().rgb()).append(Component.literal(" Client").withColor(0xFFFFFF));
	}

	public static Component poweredBy() {
		return Component.literal("Powered by ").withColor(0xB0B8C8)
			.append(Component.literal("Sodium").withColor(0x7CE38B))
			.append(Component.literal(" and ").withColor(0xB0B8C8))
			.append(Component.literal("Sodium Extra").withColor(0x7CE38B));
	}

	public static void scaledCentered(GuiGraphicsExtractor g, Font font, Component text, int centerX, int y, float scale, int color) {
		g.pose().pushMatrix();
		g.pose().translate(centerX, y);
		g.pose().scale(scale, scale);
		g.centeredText(font, text, 0, 0, color);
		g.pose().popMatrix();
	}
}
