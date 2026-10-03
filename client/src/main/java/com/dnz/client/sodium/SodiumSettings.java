package com.dnz.client.sodium;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import net.caffeinemc.mods.sodium.api.config.option.OptionImpact;
import net.caffeinemc.mods.sodium.api.config.option.SteppedValidator;
import net.caffeinemc.mods.sodium.client.config.ConfigManager;
import net.caffeinemc.mods.sodium.client.config.structure.BooleanOption;
import net.caffeinemc.mods.sodium.client.config.structure.Config;
import net.caffeinemc.mods.sodium.client.config.structure.EnumOption;
import net.caffeinemc.mods.sodium.client.config.structure.ExternalButtonOption;
import net.caffeinemc.mods.sodium.client.config.structure.ExternalPage;
import net.caffeinemc.mods.sodium.client.config.structure.IntegerOption;
import net.caffeinemc.mods.sodium.client.config.structure.ModOptions;
import net.caffeinemc.mods.sodium.client.config.structure.Option;
import net.caffeinemc.mods.sodium.client.config.structure.OptionGroup;
import net.caffeinemc.mods.sodium.client.config.structure.Page;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Reads Sodium's option pages (and those other mods like Sodium Extra add to it) into plain rows
 * that DNZ screens draw with their own widgets. Values are changed the same way Sodium's screen does:
 * pending until {@link #apply()}.
 * <p>
 * This is the only class that touches Sodium; only call it when Sodium is loaded.
 */
public final class SodiumSettings {
	public enum Kind { HEADER, TOGGLE, CYCLE, SLIDER, BUTTON }

	public record PageInfo(Component mod, Component name) {
	}

	/**
	 * One line in the list. {@code press} is used by TOGGLE, CYCLE and BUTTON (it gets the current screen),
	 * {@code set} by SLIDER. CYCLE rows also list their {@code choices} ({@code current} is the chosen one) and
	 * pick one by its index with {@code set}.
	 */
	public record Row(Kind kind, Component name, Component tooltip, boolean enabled, boolean changed, Component value,
		int min, int max, int step, int current, IntFunction<Component> format, IntConsumer set, Consumer<Screen> press,
		List<Component> choices) {
		Row(Kind kind, Component name, Component tooltip, boolean enabled, boolean changed, Component value,
			int min, int max, int step, int current, IntFunction<Component> format, IntConsumer set, Consumer<Screen> press) {
			this(kind, name, tooltip, enabled, changed, value, min, max, step, current, format, set, press, List.of());
		}

		static Row header(Component name) {
			return new Row(Kind.HEADER, name, null, true, false, null, 0, 0, 0, 0, null, null, null);
		}
	}

	private SodiumSettings() {
	}

	private static Config config() {
		return ConfigManager.CONFIG;
	}

	private static List<Page> allPages() {
		List<Page> pages = new ArrayList<>();
		for (ModOptions mod : config().getModOptions()) {
			pages.addAll(mod.pages());
		}
		return pages;
	}

	public static List<PageInfo> pages() {
		List<PageInfo> result = new ArrayList<>();
		for (ModOptions mod : config().getModOptions()) {
			for (Page page : mod.pages()) {
				result.add(new PageInfo(Component.literal(mod.name()), page.name()));
			}
		}
		return result;
	}

	public static List<Row> rows(int pageIndex) {
		List<Page> pages = allPages();
		List<Row> rows = new ArrayList<>();
		if (pageIndex < 0 || pageIndex >= pages.size()) {
			return rows;
		}
		Page page = pages.get(pageIndex);
		if (page instanceof ExternalPage external) {
			rows.add(new Row(Kind.BUTTON, page.name(), null, true, false, Component.translatable("dnzclient.perf.open"),
				0, 0, 0, 0, null, null, external.currentScreenConsumer()));
			return rows;
		}
		for (OptionGroup group : page.groups()) {
			if (group.name() != null && !group.name().getString().isEmpty()) {
				rows.add(Row.header(group.name()));
			}
			for (Option option : group.options()) {
				Row row = row(option);
				if (row != null) {
					rows.add(row);
				}
			}
		}
		return rows;
	}

	private static Row row(Option option) {
		Component tooltip = tooltip(option);
		boolean enabled = option.isEnabled();
		boolean changed = option.hasChanged();
		if (option instanceof BooleanOption bool) {
			boolean on = bool.getValidatedValue();
			return new Row(Kind.TOGGLE, option.getName(), tooltip, enabled, changed,
				Component.translatable(on ? "dnzclient.on" : "dnzclient.off"), 0, 1, 1, on ? 1 : 0, null, null,
				screen -> bool.modifyValue(!bool.getValidatedValue()));
		}
		if (option instanceof IntegerOption integer) {
			SteppedValidator range = integer.getSteppedValidator();
			return new Row(Kind.SLIDER, option.getName(), tooltip, enabled, changed, null,
				range.min(), range.max(), Math.max(1, range.step()), integer.getValidatedValue(), integer::formatValue,
				integer::modifyValue, null);
		}
		if (option instanceof EnumOption<?> enumOption) {
			return enumRow(enumOption, tooltip, enabled, changed);
		}
		if (option instanceof ExternalButtonOption button) {
			return new Row(Kind.BUTTON, option.getName(), tooltip, enabled, false, Component.translatable("dnzclient.perf.open"),
				0, 0, 0, 0, null, null, button.getCurrentScreenConsumer());
		}
		return null;
	}

	private static <E extends Enum<E>> Row enumRow(EnumOption<E> option, Component tooltip, boolean enabled, boolean changed) {
		List<E> allowed = new ArrayList<>();
		for (E value : option.getEnumClass().getEnumConstants()) {
			if (option.isValueAllowed(value)) {
				allowed.add(value);
			}
		}
		E current = option.getValidatedValue();
		List<Component> names = new ArrayList<>();
		for (E value : allowed) {
			names.add(option.getElementName(value));
		}
		return new Row(Kind.CYCLE, option.getName(), tooltip, enabled && allowed.size() > 1, changed, option.getElementName(current),
			0, allowed.size() - 1, 1, Math.max(0, allowed.indexOf(current)), null,
			index -> option.modifyValue(allowed.get(Math.max(0, Math.min(index, allowed.size() - 1)))), screen -> {
				int index = allowed.indexOf(option.getValidatedValue());
				option.modifyValue(allowed.get((index + 1) % allowed.size()));
			}, names);
	}

	private static Component tooltip(Option option) {
		Component text = option.getTooltip();
		OptionImpact impact = option.getImpact();
		if (impact == null) {
			return text;
		}
		Component line = Component.translatable("dnzclient.perf.impact", impact.getName()).withColor(0xFFD24A);
		return text == null ? line : text.copy().append("\n\n").append(line);
	}

	/** Loads the saved values again, dropping pending changes (also used when the tab opens). */
	public static void undo() {
		config().resetAllOptionsFromBindings();
	}

	public static boolean anyChanged() {
		return config().anyOptionChanged();
	}

	/** Saves and applies every pending change, like Sodium's "Apply" button. */
	public static void apply() {
		config().applyAllOptions();
	}
}
