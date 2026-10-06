package com.dnz.client.gui;

import com.dnz.client.L;
import com.dnz.client.DnzConfig;
import com.dnz.client.gui.config.ChoiceOption;
import com.dnz.client.gui.config.HeaderOption;
import com.dnz.client.gui.config.Option;
import com.dnz.client.gui.config.OptionList;
import com.dnz.client.gui.config.SliderOption;
import com.dnz.client.gui.config.SwitchOption;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

/** DNZ menu "Visual" tab: accent color, theme and display toggles. */
public class DnzVisualScreen extends DnzMenuScreen {
	private static final int SWATCH = 22;
	private static final int ROW_H = 22;
		private static final int TOGGLES_TOP = 102;
	/** Blur strengths for Off / Low / Medium / High. */
	private static final int[] BLUR_STEPS = {0, 3, 6, 10};

	public DnzVisualScreen(Screen parent) {
		super(Tab.VISUAL, parent);
	}

	@Override
	protected void initContent() {
		DnzConfig config = DnzConfig.get();
		if (Theme.dnzStyle()) {
			this.initOptions(config);
			return;
		}

		// Accent colors
		int count = Theme.ACCENTS.length;
		int gap = Math.min(8, (this.cw - 4 - count * SWATCH) / (count - 1));
		for (int i = 0; i < count; i++) {
			int index = i;
			this.addRenderableWidget(new Swatch(this.cx + 2 + i * (SWATCH + gap), this.cy + 14, Theme.ACCENTS[i], config.accent == i, () -> {
				config.accent = index;
				config.save();
				this.rebuildWidgets();
			}));
		}

		// Themes, three per row
		int colW = (this.cw - 8) / 3;
		for (int i = 0; i < Theme.STYLES.length; i++) {
			int index = i;
			int bx = this.cx + (i % 3) * (colW + 4);
			int by = this.cy + 56 + (i / 3) * 22;
			this.addRenderableWidget(new DnzButton(bx, by, colW, 20, Component.literal(Theme.STYLES[i].label()), () -> {
				config.theme = index;
				config.save();
				this.rebuildWidgets();
			}).selected(config.theme == i));
		}

		// Toggles: two columns of "Name: value" buttons, so everything fits on small screens.
		List<DnzButton> toggles = new ArrayList<>();

		// Menu style cycles DNZ -> Simple -> Java.
		String styleName = config.javaStyle ? "Java" : config.simpleStyle ? "Simple" : "DNZ";
		toggles.add(new DnzButton(0, 0, 0, 0, Component.literal(L.t("visual.style") + ": " + styleName), () -> {
			if (config.javaStyle) {
				config.javaStyle = false;
				config.simpleStyle = false;
			} else if (config.simpleStyle) {
				config.javaStyle = true;
			} else {
				config.simpleStyle = true;
			}
			config.save();
			this.rebuildWidgets();
		}).selected(true));

		// Menu background blur: Minecraft's own setting (0-10), in four steps.
		int blur = this.minecraft.options.getMenuBackgroundBlurriness();
		int step = blur <= 0 ? 0 : blur <= 3 ? 1 : blur <= 6 ? 2 : 3;
		toggles.add(new DnzButton(0, 0, 0, 0, Component.literal(L.t("visual.blur_short") + ": " + L.t("visual.blur." + step)), () -> {
			this.minecraft.options.menuBackgroundBlurriness().set(BLUR_STEPS[(step + 1) % BLUR_STEPS.length]);
			this.minecraft.options.save();
			this.rebuildWidgets();
		}).selected(step > 0));

		toggles.add(new DnzButton(0, 0, 0, 0,
			Component.literal(L.t("visual.containers") + ": " + (config.containerDark ? L.t("visual.black") : L.t("visual.white"))), () -> {
				config.containerDark = !config.containerDark;
				config.save();
				this.rebuildWidgets();
			}).selected(config.containerDark));

		toggles.add(new DnzButton(0, 0, 0, 0,
			Component.literal(L.t("visual.fullbright") + ": " + (config.fullbright ? L.t("on") : L.t("off"))), () -> {
				config.fullbright = !config.fullbright;
				config.save();
				this.rebuildWidgets();
			}).selected(config.fullbright));

		toggles.add(new DnzButton(0, 0, 0, 0,
			Component.literal(L.t("visual.streamer") + ": " + (config.streamerMode ? L.t("on") : L.t("off"))), () -> {
				config.streamerMode = !config.streamerMode;
				config.save();
				if (this.minecraft.level != null) {
					this.minecraft.levelExtractor.allChanged(); // re-render blocks with the new rotation
				}
				this.rebuildWidgets();
			}).selected(config.streamerMode));

		int cellW = (this.cw - 6) / 2;
		for (int i = 0; i < toggles.size(); i++) {
			DnzButton toggle = toggles.get(i);
			toggle.setRectangle(cellW, 20, this.cx + (i % 2) * (cellW + 6), this.cy + TOGGLES_TOP + (i / 2) * ROW_H);
			this.addRenderableWidget(toggle);
		}
	}

	/** DNZ style: only the look of the menu (Modern or Minecraft, dark or white). */
	private void initOptions(DnzConfig config) {
		List<Option> rows = new ArrayList<>();
		rows.add(new HeaderOption(L.t("visual.section.look")));
		rows.add(new ChoiceOption(L.t("visual.look"), List.of(L.t("visual.look.modern"), L.t("visual.look.minecraft")),
			() -> config.menuLook, i -> {
				config.menuLook = i;
				config.save();
				this.rebuildWidgets(); // every text changes its font
			}).note(L.t("visual.look.note")).wide());
		boolean minecraft = config.menuLook == 1;
		rows.add(new ChoiceOption(L.t("visual.mode"), List.of(L.t(minecraft ? "visual.mode.black" : "visual.mode.dark"), L.t("visual.mode.white")),
			() -> config.menuLight ? 1 : 0, i -> {
				config.menuLight = i == 1;
				config.save();
			}).note(L.t("visual.mode.note")).wide());
		this.addRenderableWidget(new OptionList(this.cx, this.cy, this.cw, this.ch).set(rows));
	}

	/** Menu and game settings shown on the Preferences page (menu size, blur, chests, fullbright, streamer mode). */
	static List<Option> moreRows(Minecraft mc, Runnable rebuild) {
		DnzConfig config = DnzConfig.get();
		List<Option> rows = new ArrayList<>();
		rows.add(new SliderOption(L.t("visual.menu_size"), 70, 130, 5, () -> config.menuSize, v -> config.menuSize = v, v -> v + "%")
			.onRelease(() -> {
				config.save();
				rebuild.run(); // the menu takes its new size when the knob is let go
			}).tooltip(Component.literal(L.t("visual.menu_size.note"))));
		rows.add(new ChoiceOption(L.t("visual.blur"), List.of(L.t("visual.blur.0"), L.t("visual.blur.1"), L.t("visual.blur.2"), L.t("visual.blur.3")),
			() -> {
				int blur = mc.options.getMenuBackgroundBlurriness();
				return blur <= 0 ? 0 : blur <= 3 ? 1 : blur <= 6 ? 2 : 3;
			}, i -> {
				mc.options.menuBackgroundBlurriness().set(BLUR_STEPS[i]);
				mc.options.save();
			}).note(L.t("visual.blur.note")));
		rows.add(new ChoiceOption(L.t("visual.containers"), List.of(L.t("visual.white"), L.t("visual.black")),
			() -> config.containerDark ? 1 : 0, i -> {
				config.containerDark = i == 1;
				config.save();
			}).note(L.t("visual.containers.note")));
		rows.add(new SwitchOption(L.t("visual.fullbright"), () -> config.fullbright, on -> {
			config.fullbright = on;
			config.save();
		}).note(L.t("visual.fullbright.note")));
		rows.add(new SwitchOption(L.t("visual.streamer"), () -> config.streamerMode, on -> {
			config.streamerMode = on;
			config.save();
			if (mc.level != null) {
				mc.levelExtractor.allChanged(); // re-render blocks with the new rotation
			}
		}).note(L.t("visual.streamer.note")));
		rows.add(new SwitchOption(L.t("cool.title"), () -> config.coolMode, on -> {
			config.coolMode = on;
			config.save();
		}).note(L.t("cool.note")));
		// DNZ Cloud (off until the player turns it on; also in DNZ Launcher settings).
		rows.add(new SwitchOption(L.t("cloud.title"), com.dnz.client.CloudSync::enabled, com.dnz.client.CloudSync::setEnabled)
			.note(L.t("cloud.note")));
		return rows;
	}

	@Override
	protected void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		if (Theme.dnzStyle()) {
			return;
		}
		Theme.text(g, this.font, Component.literal(L.t("visual.accent")), this.cx, this.cy + 2, 0xFFFFFFFF);
		Component accentName = Theme.label(Component.literal(Theme.accentInfo().label()));
		Theme.text(g, this.font, accentName, this.cx + this.cw - this.font.width(accentName), this.cy + 2, Theme.accent());
		Theme.text(g, this.font, Component.literal(L.t("visual.theme")), this.cx, this.cy + 44, 0xFFFFFFFF);
		g.fill(this.cx, this.cy + TOGGLES_TOP - 5, this.cx + this.cw, this.cy + TOGGLES_TOP - 4, 0x30FFFFFF);
	}

	/** A clickable colored square. */
	private static class Swatch extends AbstractButton {
		private final Theme.Accent accent;
		private final boolean selected;
		private final Runnable action;

		Swatch(int x, int y, Theme.Accent accent, boolean selected, Runnable action) {
			super(x, y, SWATCH, SWATCH, Component.literal(accent.label()));
			this.accent = accent;
			this.selected = selected;
			this.action = action;
		}

		@Override
		public void onPress(InputWithModifiers input) {
			this.action.run();
		}

		@Override
		protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
			int x = this.getX();
			int y = this.getY();
			if (this.selected || this.isHoveredOrFocused()) {
				Theme.roundedRect(g, x - 2, y - 2, this.width + 4, this.height + 4, this.selected ? 0xFFFFFFFF : 0x80FFFFFF);
			}
			Theme.roundedRect(g, x, y, this.width, this.height, 0xFF000000 | this.accent.rgb());
			if (this.isHovered()) {
				g.setTooltipForNextFrame(Component.literal(this.accent.label()), mouseX, mouseY);
			}
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput output) {
			this.defaultButtonNarrationText(output);
		}
	}
}
