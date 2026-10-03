package com.dnz.client.hud;

import com.dnz.client.DnzConfig;
import com.dnz.client.L;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * One module of the DNZ Modules list: either something drawn on the HUD (movable, scalable)
 * or a feature without a panel (zoom, turbo, food overlay...). Every module can be turned on and off.
 */
public abstract class HudModule {
	public enum Category {
		INFO, PVP, ITEMS, FOOD, VISUAL, PERFORMANCE;

		public String label() {
			return L.t("cat." + this.name().toLowerCase(java.util.Locale.ROOT));
		}
	}

	public final String id;
	public final Category category;
	private final float defaultX;
	private final float defaultY;
	private final boolean defaultOn;

	protected HudModule(String id, Category category, float defaultX, float defaultY, boolean defaultOn) {
		this.id = id;
		this.category = category;
		this.defaultX = defaultX;
		this.defaultY = defaultY;
		this.defaultOn = defaultOn;
	}

	public String label() {
		return L.t("hud." + this.id);
	}

	/** Shows "label value" text, so the player can write their own label (see {@link #shownLabel()}). */
	public boolean hasLabel() {
		return false;
	}

	/** The label drawn on the HUD: the player's own text, or the module's name. */
	public String shownLabel() {
		String custom = this.pos().label;
		return custom == null || custom.isBlank() ? this.label() : custom;
	}

	/** Drawn on the HUD and movable in the editor. */
	public boolean movable() {
		return true;
	}

	/** Keys of this module's on/off options (shown in the editor's right-click menu). */
	public List<String> options() {
		return List.of();
	}

	public boolean defaultOption(String key) {
		return true;
	}

	public DnzConfig.HudPos pos() {
		return DnzConfig.get().hud.computeIfAbsent(this.id, k -> this.defaultPos());
	}

	public DnzConfig.HudPos defaultPos() {
		DnzConfig.HudPos p = new DnzConfig.HudPos(this.defaultX, this.defaultY);
		p.enabled = this.defaultOn;
		return p;
	}

	public boolean enabled() {
		return this.pos().enabled;
	}

	public void setEnabled(boolean on) {
		this.pos().enabled = on;
	}

	public boolean opt(String key) {
		Boolean value = this.pos().opts.get(key);
		return value == null ? this.defaultOption(key) : value;
	}

	/** Draws the module with its top-left corner at 0,0 (the caller moves and scales). Returns {width, height}; 0 width = nothing to show. */
	public abstract int[] render(HudRender r);

	/** A feature that is only switched on/off (no panel). Its state can live in an existing config field. */
	public static final class Feature extends HudModule {
		private final Supplier<Boolean> getter;
		private final Consumer<Boolean> setter;

		/** State kept in the module list itself. */
		public Feature(String id, Category category, boolean defaultOn) {
			this(id, category, defaultOn, null, null);
		}

		/** State kept in an existing setting (e.g. DnzConfig.turbo). */
		public Feature(String id, Category category, boolean defaultOn, Supplier<Boolean> getter, Consumer<Boolean> setter) {
			super(id, category, 0, 0, defaultOn);
			this.getter = getter;
			this.setter = setter;
		}

		@Override
		public boolean movable() {
			return false;
		}

		@Override
		public boolean enabled() {
			return this.getter != null ? this.getter.get() : super.enabled();
		}

		@Override
		public void setEnabled(boolean on) {
			if (this.setter != null) {
				this.setter.accept(on);
			} else {
				super.setEnabled(on);
			}
		}

		@Override
		public int[] render(HudRender r) {
			return new int[] {0, 0};
		}
	}
}
