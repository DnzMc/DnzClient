package com.dnz.client189;

import net.minecraft.client.Minecraft;

/** One DNZ module. */
public abstract class Module {
	public final String id;
	public final String nameTr;
	public final String nameEn;
	public final boolean hud;
	private final boolean defaultOn;
	private final float defaultX;
	private final float defaultY;

	protected Module(String id, String nameTr, String nameEn, boolean hud, boolean defaultOn, float defaultX, float defaultY) {
		this.id = id;
		this.nameTr = nameTr;
		this.nameEn = nameEn;
		this.hud = hud;
		this.defaultOn = defaultOn;
		this.defaultX = defaultX;
		this.defaultY = defaultY;
	}

	public String name() {
		return Lang.tr() ? this.nameTr : this.nameEn;
	}

	public Config.ModuleState state() {
		Config.ModuleState s = Config.get().modules.get(this.id);
		if (s == null) {
			s = new Config.ModuleState();
			s.enabled = this.defaultOn;
			s.x = this.defaultX;
			s.y = this.defaultY;
			Config.get().modules.put(this.id, s);
		}
		return s;
	}

	public boolean enabled() {
		return this.state().enabled;
	}

	public void setEnabled(boolean on) {
		this.state().enabled = on;
		Config.get().save();
		this.onToggle(on);
	}

	protected void onToggle(boolean on) {
	}

	/** Every client tick (only while enabled, except [onToggle]). */
	public void tick(Minecraft mc) {
	}

	/** HUD modules: draws at (0, 0) and returns {width, height}. */
	public int[] render(Minecraft mc, boolean editor) {
		return new int[] {0, 0};
	}
}
