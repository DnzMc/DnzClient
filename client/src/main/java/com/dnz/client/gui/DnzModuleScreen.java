package com.dnz.client.gui;

import com.dnz.client.DnzConfig;
import com.dnz.client.L;
import com.dnz.client.gui.config.ButtonOption;
import com.dnz.client.gui.config.HeaderOption;
import com.dnz.client.gui.config.Option;
import com.dnz.client.gui.config.OptionList;
import com.dnz.client.gui.config.SliderOption;
import com.dnz.client.gui.config.SwitchOption;
import com.dnz.client.gui.config.TextOption;
import com.dnz.client.hud.HudModule;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Settings of one module in the DNZ menu: setting rows in two columns (name and a short note on the left,
 * the control on the right) on a scrolling page. Opened by clicking a card on the Mods page.
 */
public class DnzModuleScreen extends DnzMenuScreen {
	private final HudModule module;

	public DnzModuleScreen(Screen parent, HudModule module) {
		super(Tab.HUD, parent);
		this.module = module;
	}

	@Override
	protected String pageTitle() {
		return this.module.label();
	}

	@Override
	protected void initContent() {
		HudModule m = this.module;
		DnzConfig.HudPos p = m.pos();
		List<Option> rows = new ArrayList<>();
		rows.add(new HeaderOption(L.t("cfg.general")));
		rows.add(new SwitchOption(L.t("cfg.enabled"), m::enabled, on -> {
			m.setEnabled(on);
			DnzConfig.get().save();
		}).note(m.movable() ? L.t("cfg.enabled.hud") : L.t("cfg.enabled.feature")));
		if (m.movable()) {
			rows.add(new SwitchOption(L.t("hud.opt.bg"), () -> p.bg, on -> {
				p.bg = on;
				DnzConfig.get().save();
			}).note(L.t("cfg.bg")));
			for (String key : m.options()) {
				rows.add(new SwitchOption(L.t("hud.opt." + key), () -> m.opt(key), on -> {
					p.opts.put(key, on);
					DnzConfig.get().save();
				}).note(L.t("cfg.option")));
			}
			if (m.hasLabel()) {
				rows.add(new TextOption(L.t("cfg.label"), m.label(), 24, () -> p.label, text -> {
					p.label = text;
					DnzConfig.get().save();
				}).note(L.t("cfg.label.note")));
			}
			rows.add(new SliderOption(L.t("hud.opt.size"), 50, 250, 5, () -> Math.round(p.scale * 100), v -> p.scale = v / 100.0F,
				v -> v + "%").onRelease(() -> DnzConfig.get().save()).tooltip(Component.literal(L.t("cfg.size"))));
			rows.add(new ButtonOption(L.t("cfg.position"), L.t("cfg.edit"), () -> this.minecraft.gui.setScreen(new DnzHudScreen(this)))
				.note(L.t("cfg.position.note")));
		}
		if (m.id.equals("crosshair")) {
			com.dnz.client.DnzConfig.Crosshair c = com.dnz.client.DnzConfig.get().crosshair;
			Runnable save = () -> com.dnz.client.DnzConfig.get().save();
			rows.add(new HeaderOption(L.t("cross.look")));
			List<String> styles = new ArrayList<>();
			for (String s : com.dnz.client.hud.Crosshair.STYLES) {
				styles.add(L.t("cross.style." + s));
			}
			rows.add(new com.dnz.client.gui.config.ChoiceOption(L.t("cross.style"), styles, () -> c.style, i -> {
				c.style = i;
				save.run();
			}));
			rows.add(new SliderOption(L.t("cross.size"), 1, 30, 1, () -> Math.round(c.size), v -> c.size = v, v -> v + " px").onRelease(save));
			rows.add(new SliderOption(L.t("cross.gap"), 0, 20, 1, () -> Math.round(c.gap), v -> c.gap = v, v -> v + " px").onRelease(save));
			rows.add(new SliderOption(L.t("cross.thickness"), 1, 8, 1, () -> Math.round(c.thickness), v -> c.thickness = v, v -> v + " px").onRelease(save));
			int[] colors = {0xFFFFFFFF, 0xFF55FF55, 0xFF00FFFF, 0xFFFF5555, 0xFFFFFF55, 0xFFFF55FF, 0xFFFF8A3D, 0xFF000000};
			List<String> names = new ArrayList<>();
			for (String s : new String[] {"white", "green", "cyan", "red", "yellow", "pink", "orange", "black"}) {
				names.add(L.t("cross.color." + s));
			}
			rows.add(new com.dnz.client.gui.config.ChoiceOption(L.t("cross.color"), names, () -> {
				for (int i = 0; i < colors.length; i++) {
					if (colors[i] == c.color) {
						return i;
					}
				}
				return 0;
			}, i -> {
				c.color = colors[i];
				save.run();
			}));
			rows.add(new SwitchOption(L.t("cross.outline"), () -> c.outline, on -> {
				c.outline = on;
				save.run();
			}));
			rows.add(new SwitchOption(L.t("cross.dot"), () -> c.dot, on -> {
				c.dot = on;
				save.run();
			}));
		}
		this.addRenderableWidget(new OptionList(this.cx, this.cy, this.cw, this.ch).set(rows));
	}
}
