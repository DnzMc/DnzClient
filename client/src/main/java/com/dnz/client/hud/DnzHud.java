package com.dnz.client.hud;

import com.dnz.client.DnzConfig;
import com.dnz.client.script.ScriptManager;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * The DNZ Modules: HUD panels (movable, scalable) and switchable features, plus the panels DNZ Script mods add.
 * Positions are fractions of the screen size, so they fit any window size.
 */
public final class DnzHud {
	private static final List<HudModule> BUILT_IN = Modules.all();
	private static final Map<String, HudModule> BY_ID = new LinkedHashMap<>();
	/** Script mod panels ("script:mod:id"), created when first seen. */
	private static final Map<String, HudModule> SCRIPT_MODULES = new HashMap<>();

	static {
		BUILT_IN.forEach(m -> BY_ID.put(m.id, m));
	}

	private static final Deque<Long> LEFT_CLICKS = new ArrayDeque<>();
	private static final Deque<Long> RIGHT_CLICKS = new ArrayDeque<>();
	/** Last drawn size (already scaled) of each panel, used for clamping and dragging. */
	private static final Map<String, int[]> SIZES = new HashMap<>();

	private DnzHud() {
	}

	// ---------------------------------------------------------------- modules

	/** Every module: built-in ones, then the panels of running script mods. */
	public static List<HudModule> modules() {
		List<String> scripts = ScriptManager.hudIds();
		if (scripts.isEmpty()) {
			return BUILT_IN;
		}
		List<HudModule> all = new ArrayList<>(BUILT_IN);
		for (String id : scripts) {
			all.add(SCRIPT_MODULES.computeIfAbsent(id, DnzHud::scriptModule));
		}
		return all;
	}

	public static HudModule module(String id) {
		HudModule m = BY_ID.get(id);
		if (m == null && id.startsWith("script:")) {
			m = SCRIPT_MODULES.computeIfAbsent(id, DnzHud::scriptModule);
		}
		return m;
	}

	private static HudModule scriptModule(String id) {
		// Script panels start on the right, one under the other (7% of the height apart, so they never touch).
		int index = Math.max(0, ScriptManager.hudIds().indexOf(id));
		return new Modules.TextModule(id, HudModule.Category.INFO, 0.84F, 0.01F + 0.07F * (index % 12), true, mc -> ScriptManager.hudValue(id)) {
			@Override
			public String label() {
				return ScriptManager.hudLabel(id);
			}
		};
	}

	/** Ids of the panels that are drawn on the HUD (built-in and script). */
	public static List<String> elements() {
		return modules().stream().filter(HudModule::movable).map(m -> m.id).toList();
	}

	public static DnzConfig.HudPos pos(String id) {
		HudModule m = module(id);
		return m != null ? m.pos() : DnzConfig.get().hud.computeIfAbsent(id, k -> new DnzConfig.HudPos(0.005F, 0.45F));
	}

	public static String label(String id) {
		HudModule m = module(id);
		return m == null ? id : m.label();
	}

	/** Puts every panel back to its default place and size (on/off stays as it is). */
	public static void reset() {
		for (HudModule m : modules()) {
			if (!m.movable()) {
				continue;
			}
			DnzConfig.HudPos p = m.pos();
			DnzConfig.HudPos d = m.defaultPos();
			p.x = d.x;
			p.y = d.y;
			p.scale = 1.0F;
			p.bg = true;
		}
		DnzConfig.get().save();
	}

	/** Screen position of a panel, kept fully on screen. */
	public static int[] screenPos(String id, int screenW, int screenH) {
		DnzConfig.HudPos p = pos(id);
		int[] size = size(id);
		int x = Math.round(p.x * screenW);
		int y = Math.round(p.y * screenH);
		x = Math.max(0, Math.min(x, screenW - size[0]));
		y = Math.max(0, Math.min(y, screenH - size[1]));
		return new int[] {x, y};
	}

	public static int[] size(String id) {
		return SIZES.getOrDefault(id, new int[] {60, 16});
	}

	// ---------------------------------------------------------------- CPS

	public static void onClick(int button) {
		long now = System.currentTimeMillis();
		if (button == 0) {
			LEFT_CLICKS.addLast(now);
		} else if (button == 1) {
			RIGHT_CLICKS.addLast(now);
		}
	}

	private static int cps(Deque<Long> clicks) {
		long limit = System.currentTimeMillis() - 1000;
		while (!clicks.isEmpty() && clicks.peekFirst() < limit) {
			clicks.pollFirst();
		}
		return clicks.size();
	}

	public static int leftCps() {
		return cps(LEFT_CLICKS);
	}

	public static int rightCps() {
		return cps(RIGHT_CLICKS);
	}

	public static int countInInventory(Minecraft mc, ItemStack like) {
		if (mc.player == null) {
			return 0;
		}
		Inventory inv = mc.player.getInventory();
		int n = 0;
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			if (ItemStack.isSameItemSameComponents(s, like)) {
				n += s.getCount();
			}
		}
		return n;
	}

	// ---------------------------------------------------------------- rendering

	/** Fabric HUD callback. */
	public static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.gui.hud.isHidden()) {
			return;
		}
		renderAll(g, false);
	}

	/** Time each panel takes to draw (nanoseconds, summed), only filled while the FPS benchmark measures it. */
	public static final Map<String, long[]> PROFILE = new LinkedHashMap<>();
	public static volatile boolean profiling;

	/** Draws every enabled panel. In the editor, panels with nothing to show draw an example instead. */
	public static void renderAll(GuiGraphicsExtractor g, boolean editor) {
		Minecraft mc = Minecraft.getInstance();
		int w = g.guiWidth();
		int h = g.guiHeight();
		for (HudModule m : modules()) {
			long start = profiling ? System.nanoTime() : 0;
			renderOne(g, mc, m, editor, w, h);
			if (profiling) {
				long[] t = PROFILE.computeIfAbsent(m.id, k -> new long[2]);
				t[0] += System.nanoTime() - start;
				t[1]++;
			}
		}
	}

	private static void renderOne(GuiGraphicsExtractor g, Minecraft mc, HudModule m, boolean editor, int w, int h) {
		if (!m.movable() || !m.enabled()) {
			putSize(m.id, 0, 0);
			return;
		}
		DnzConfig.HudPos p = m.pos();
		float scale = Math.clamp(p.scale, 0.5F, 2.5F);
		// Keeping a panel on screen needs its size. Measuring it every frame would draw everything twice, so
		// the size from the last frame is used (it only changes when the text does); the editor measures exactly.
		if (editor || !SIZES.containsKey(m.id)) {
			int[] size = m.render(new HudRender(g, mc, true, editor, p.bg));
			putSize(m.id, Math.round(size[0] * scale), Math.round(size[1] * scale));
		}
		int[] at = screenPos(m.id, w, h);
		g.pose().pushMatrix();
		g.pose().translate(at[0], at[1]);
		g.pose().scale(scale, scale);
		int[] size = m.render(new HudRender(g, mc, false, editor, p.bg));
		g.pose().popMatrix();
		putSize(m.id, Math.round(size[0] * scale), Math.round(size[1] * scale));
	}

	/** Keeps one array per panel instead of making a new one every frame (less work for Java's memory cleaner). */
	private static void putSize(String id, int w, int h) {
		int[] s = SIZES.get(id);
		if (s == null) {
			SIZES.put(id, new int[] {w, h});
		} else {
			s[0] = w;
			s[1] = h;
		}
	}
}
