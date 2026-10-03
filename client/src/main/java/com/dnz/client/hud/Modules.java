package com.dnz.client.hud;

import com.dnz.client.DnzConfig;
import com.dnz.client.L;
import com.dnz.client.hud.HudModule.Category;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.Level;

/** Every built-in module of DNZ Client, in the order the Modules list shows them. */
public final class Modules {
	private Modules() {
	}

	public static List<HudModule> all() {
		DnzConfig c = DnzConfig.get();
		List<HudModule> list = new ArrayList<>();
		// ---- Info
		list.add(new TextModule("fps", Category.INFO, 0.005F, 0.01F, true, mc -> Integer.toString(mc.getFps())));
		list.add(new TextModule("ping", Category.INFO, 0.005F, 0.055F, true, Modules::ping));
		list.add(new CoordsModule());
		list.add(new CompassModule());
		list.add(new TextModule("biome", Category.INFO, 0.84F, 0.01F, false, Modules::biome));
		list.add(new TextModule("clock", Category.INFO, 0.84F, 0.055F, false,
			mc -> String.format("%02d:%02d", LocalTime.now().getHour(), LocalTime.now().getMinute())));
		list.add(new TextModule("gametime", Category.INFO, 0.84F, 0.10F, false, Modules::gameTime));
		list.add(new TextModule("speed", Category.INFO, 0.84F, 0.145F, false, mc -> String.format("%.1f b/s", CombatTracker.speed())));
		list.add(new TextModule("memory", Category.INFO, 0.84F, 0.19F, false, Modules::memory));
		list.add(new TextModule("server", Category.INFO, 0.84F, 0.235F, false, Modules::server));
		list.add(new TextModule("players", Category.INFO, 0.84F, 0.28F, false,
			mc -> mc.getConnection() == null ? "-" : Integer.toString(mc.getConnection().getOnlinePlayers().size())));
		list.add(new TextModule("light", Category.INFO, 0.84F, 0.325F, false, Modules::light));
		list.add(new TextModule("session", Category.INFO, 0.84F, 0.37F, false, mc -> duration(CombatTracker.sessionMillis())));

		// ---- PvP
		list.add(new KeystrokesModule());
		list.add(new TextModule("cps", Category.PVP, 0.005F, 0.10F, true,
			mc -> DnzHud.leftCps() + " | " + DnzHud.rightCps()));
		list.add(new TextModule("reach", Category.PVP, 0.20F, 0.01F, false, mc -> {
			double r = CombatTracker.reach();
			return r < 0 ? "-" : String.format("%.2f", r);
		}));
		list.add(new TextModule("combo", Category.PVP, 0.20F, 0.055F, false, mc -> Integer.toString(CombatTracker.combo())));
		list.add(new TargetModule());
		list.add(new HudModule.Feature("togglesprint", Category.PVP, false));

		// ---- Items
		list.add(new ArmorModule());
		list.add(new HeldItemModule());
		list.add(new EffectsModule());
		list.add(new CounterModule("totems", 0.005F, 0.70F, true, () -> new ItemStack(Items.TOTEM_OF_UNDYING), s -> s.is(Items.TOTEM_OF_UNDYING)));
		list.add(new CounterModule("pots", 0.06F, 0.70F, false, Modules::potIcon, Modules::isHealingSplash));
		list.add(new CounterModule("gapples", 0.115F, 0.70F, false, () -> new ItemStack(Items.GOLDEN_APPLE),
			s -> s.is(Items.GOLDEN_APPLE) || s.is(Items.ENCHANTED_GOLDEN_APPLE)));
		list.add(new CounterModule("pearls", 0.17F, 0.70F, false, () -> new ItemStack(Items.ENDER_PEARL), s -> s.is(Items.ENDER_PEARL)));
		list.add(new CounterModule("arrows", 0.225F, 0.70F, false, () -> new ItemStack(Items.ARROW),
			s -> s.is(Items.ARROW) || s.is(Items.SPECTRAL_ARROW) || s.is(Items.TIPPED_ARROW)));
		list.add(new HudModule.Feature("shulkerpreview", Category.ITEMS, true));

		// ---- Food (on the vanilla hunger bar and item tooltips)
		list.add(new HudModule.Feature("foodsaturation", Category.FOOD, true));
		list.add(new HudModule.Feature("foodpreview", Category.FOOD, true));
		list.add(new HudModule.Feature("foodtooltip", Category.FOOD, true));

		// ---- Visual and performance (existing settings, now switchable here too)
		list.add(new HudModule.Feature("zoom", Category.VISUAL, true));
		list.add(new HudModule.Feature("cleanhotbar", Category.VISUAL, false));
		list.add(new HudModule.Feature("fullbright", Category.VISUAL, false, () -> DnzConfig.get().fullbright, v -> DnzConfig.get().fullbright = v));
		list.add(new HudModule.Feature("streamer", Category.VISUAL, false, () -> DnzConfig.get().streamerMode, v -> DnzConfig.get().streamerMode = v));
		list.add(new HudModule.Feature("turbo", Category.PERFORMANCE, true, () -> DnzConfig.get().turbo, v -> DnzConfig.get().turbo = v));
		return list;
	}

	// ---------------------------------------------------------------- values

	private static String ping(Minecraft mc) {
		if (mc.player == null || mc.getConnection() == null) {
			return "-";
		}
		PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
		return info == null ? "-" : info.getLatency() + " ms";
	}

	private static String biome(Minecraft mc) {
		if (mc.player == null || mc.level == null) {
			return "-";
		}
		return mc.level.getBiome(mc.player.blockPosition()).unwrapKey()
			.map(k -> Component.translatable("biome." + k.identifier().getNamespace() + "." + k.identifier().getPath()).getString())
			.orElse("-");
	}

	private static String gameTime(Minecraft mc) {
		if (mc.level == null) {
			return "-";
		}
		long time = mc.level.getOverworldClockTime();
		long day = time / 24000 + 1;
		// Minecraft's day starts at 06:00.
		int t = (int) ((time + 6000) % 24000);
		return L.t("hud.day", day) + "  " + String.format("%02d:%02d", t / 1000, (t % 1000) * 60 / 1000);
	}

	private static String memory(Minecraft mc) {
		Runtime rt = Runtime.getRuntime();
		long used = (rt.totalMemory() - rt.freeMemory()) / 1048576L;
		long max = rt.maxMemory() / 1048576L;
		return (used * 100 / Math.max(1, max)) + "%  " + used + "/" + max + " MB";
	}

	private static String server(Minecraft mc) {
		if (mc.hasSingleplayerServer()) {
			return L.t("hud.singleplayer");
		}
		ServerData data = mc.getCurrentServer();
		return data == null ? "-" : data.ip;
	}

	private static String light(Minecraft mc) {
		if (mc.player == null || mc.level == null) {
			return "-";
		}
		BlockPos pos = mc.player.blockPosition();
		// Monsters spawn where block light is 0, so that number matters most.
		return mc.level.getBrightness(LightLayer.BLOCK, pos) + "  (" + L.t("hud.sky") + " " + mc.level.getBrightness(LightLayer.SKY, pos) + ")";
	}

	static String duration(long ms) {
		long s = ms / 1000;
		return s >= 3600 ? String.format("%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60) : String.format("%d:%02d", s / 60, s % 60);
	}

	private static ItemStack potIcon() {
		return PotionContents.createItemStack(Items.SPLASH_POTION, Potions.HEALING);
	}

	private static boolean isHealingSplash(ItemStack s) {
		if (!s.is(Items.SPLASH_POTION)) {
			return false;
		}
		PotionContents contents = s.get(DataComponents.POTION_CONTENTS);
		return contents != null && (contents.is(Potions.HEALING) || contents.is(Potions.STRONG_HEALING));
	}

	// ---------------------------------------------------------------- module types

	/** "Label value" in a small panel; the value is refreshed at most 10 times a second. */
	public static class TextModule extends HudModule {
		private final Function<Minecraft, String> value;
		private String cached;
		private long cachedAt;

		public TextModule(String id, Category category, float x, float y, boolean on, Function<Minecraft, String> value) {
			super(id, category, x, y, on);
			this.value = value;
		}

		@Override
		public boolean hasLabel() {
			return true;
		}

		protected String value(Minecraft mc) {
			long now = System.nanoTime();
			if (this.cached == null || now - this.cachedAt > 100_000_000L) {
				this.cached = this.value.apply(mc);
				this.cachedAt = now;
			}
			return this.cached;
		}

		/** The finished line and its width, rebuilt only when the value text changes (not every frame). */
		private Component text;
		private String textFor;
		private int textWidth;

		@Override
		public int[] render(HudRender r) {
			String value = this.value(r.mc);
			String label = this.shownLabel();
			String key = label + '\0' + value + '\0' + HudRender.accentRgb();
			if (!key.equals(this.textFor)) {
				this.text = Component.literal(label + " ").withColor(HudRender.accentRgb())
					.append(Component.literal(value).withColor(0xFFFFFF));
				this.textFor = key;
				this.textWidth = r.width(this.text);
			}
			Component text = this.text;
			int w = this.textWidth + 10;
			int h = 16;
			r.panel(w, h);
			r.accentBar(h);
			r.text(text, 6, 4, 0xFFFFFFFF);
			return new int[] {w, h};
		}
	}

	/** Coordinates with facing; optionally the matching Nether / Overworld position. */
	static final class CoordsModule extends TextModule {
		CoordsModule() {
			super("coords", Category.INFO, 0.005F, 0.145F, true, CoordsModule::text);
		}

		@Override
		public List<String> options() {
			return List.of("direction", "nether");
		}

		@Override
		public boolean defaultOption(String key) {
			return !key.equals("nether");
		}

		@Override
		public int[] render(HudRender r) {
			if (DnzConfig.get().streamerMode && !r.editor) {
				return new int[] {0, 0};
			}
			return super.render(r);
		}

		private static String text(Minecraft mc) {
			if (mc.player == null) {
				return "0 64 0";
			}
			HudModule self = DnzHud.module("coords");
			int x = mc.player.getBlockX(), y = mc.player.getBlockY(), z = mc.player.getBlockZ();
			StringBuilder s = new StringBuilder().append(x).append(' ').append(y).append(' ').append(z);
			if (self == null || self.opt("direction")) {
				String[] dirs = {"s", "w", "n", "e"};
				int dir = Math.floorMod(Math.round(mc.player.getYRot() / 90.0F), 4);
				s.append("  ").append(L.t("hud.dir." + dirs[dir]));
			}
			if (self != null && self.opt("nether") && mc.level != null) {
				if (mc.level.dimension() == Level.NETHER) {
					s.append("  ›  ").append(x * 8).append(' ').append(z * 8);
				} else if (mc.level.dimension() == Level.OVERWORLD) {
					s.append("  ›  ").append(Math.floorDiv(x, 8)).append(' ').append(Math.floorDiv(z, 8));
				}
			}
			return s.toString();
		}
	}

	/** A strip with N / E / S / W that turns with the camera. */
	static final class CompassModule extends HudModule {
		private static final String[] NAMES = {"n", "ne", "e", "se", "s", "sw", "w", "nw"};

		CompassModule() {
			super("compass", Category.INFO, 0.40F, 0.005F, false);
		}

		@Override
		public List<String> options() {
			return List.of("degrees");
		}

		@Override
		public int[] render(HudRender r) {
			int w = 160, h = this.opt("degrees") ? 26 : 16;
			if (r.measure) {
				return new int[] {w, h};
			}
			r.panel(w, h);
			float yaw = r.mc.player == null ? 180 : r.mc.player.getYRot();
			// 0 = north, 90 = east (Minecraft's yaw has 0 = south).
			float heading = Mth.wrapDegrees(yaw + 180.0F);
			if (heading < 0) {
				heading += 360;
			}
			float half = 80.0F; // degrees visible on each side
			int cx = w / 2;
			for (int deg = 0; deg < 360; deg += 15) {
				float delta = Mth.wrapDegrees(deg - heading);
				if (Math.abs(delta) > half) {
					continue;
				}
				int x = cx + Math.round(delta / half * (w / 2 - 8));
				if (deg % 45 == 0) {
					String name = L.t("hud.compass." + NAMES[deg / 45]);
					boolean main = deg % 90 == 0;
					r.centered(name, x, 4, main ? 0xFFFFFFFF : 0xFFAAB0C0);
				} else {
					r.fill(x, 6, x + 1, 10, 0x80FFFFFF);
				}
			}
			r.fill(cx, 1, cx + 1, 3, HudRender.accent());
			r.fill(cx, h - 3, cx + 1, h - 1, HudRender.accent());
			if (this.opt("degrees")) {
				r.centered(Math.round(heading) + "°", cx, 15, HudRender.accent());
			}
			return new int[] {w, h};
		}
	}

	/** W A S D, mouse buttons with clicks per second, and space: lights up while pressed. */
	static final class KeystrokesModule extends HudModule {
		private static final int K = 22, GAP = 2;

		KeystrokesModule() {
			super("keystrokes", Category.PVP, 0.88F, 0.60F, false);
		}

		@Override
		public List<String> options() {
			return List.of("mouse", "space");
		}

		@Override
		public int[] render(HudRender r) {
			int w = K * 3 + GAP * 2;
			int h = K * 2 + GAP;
			if (this.opt("mouse")) {
				h += GAP + K;
			}
			if (this.opt("space")) {
				h += GAP + 12;
			}
			if (r.measure) {
				return new int[] {w, h};
			}
			var o = r.mc.options;
			key(r, K + GAP, 0, K, K, label(o.keyUp), o.keyUp.isDown(), null);
			key(r, 0, K + GAP, K, K, label(o.keyLeft), o.keyLeft.isDown(), null);
			key(r, K + GAP, K + GAP, K, K, label(o.keyDown), o.keyDown.isDown(), null);
			key(r, 2 * (K + GAP), K + GAP, K, K, label(o.keyRight), o.keyRight.isDown(), null);
			int y = 2 * (K + GAP);
			if (this.opt("mouse")) {
				int mw = (w - GAP) / 2;
				key(r, 0, y, mw, K, "LMB", o.keyAttack.isDown(), DnzHud.leftCps() + " CPS");
				key(r, mw + GAP, y, w - mw - GAP, K, "RMB", o.keyUse.isDown(), DnzHud.rightCps() + " CPS");
				y += K + GAP;
			}
			if (this.opt("space")) {
				key(r, 0, y, w, 12, "", o.keyJump.isDown(), null);
				r.fill(w / 2 - 12, y + 5, w / 2 + 12, y + 7, o.keyJump.isDown() ? 0xFF10141C : 0xFFFFFFFF);
			}
			return new int[] {w, h};
		}

		/** Key names change only when the player rebinds a key, so they are looked up once a second, not every frame. */
		private static final Map<net.minecraft.client.KeyMapping, String> LABELS = new HashMap<>();
		private static long labelsAt;

		private static String label(net.minecraft.client.KeyMapping key) {
			long now = System.nanoTime();
			if (now - labelsAt > 1_000_000_000L) {
				LABELS.clear();
				labelsAt = now;
			}
			return LABELS.computeIfAbsent(key, k -> {
				String s = k.getTranslatedKeyMessage().getString();
				return s.length() > 3 ? s.substring(0, 3) : s;
			});
		}

		private static void key(HudRender r, int x, int y, int w, int h, String text, boolean down, String sub) {
			r.rounded(x, y, w, h, down ? 0xE0FFFFFF : 0x90101420);
			int color = down ? 0xFF10141C : 0xFFFFFFFF;
			if (sub == null) {
				r.centered(text, x + w / 2, y + (h - 8) / 2, color);
			} else {
				r.centered(text, x + w / 2, y + 3, color);
				if (!r.measure) {
					r.g.pose().pushMatrix();
					r.g.pose().translate(x + w / 2.0F, y + 13);
					r.g.pose().scale(0.7F, 0.7F);
					r.g.text(r.font, sub, -r.font.width(sub) / 2, 0, down ? 0xFF303848 : 0xFFAAB0C0, false);
					r.g.pose().popMatrix();
				}
			}
		}
	}

	/** Name, health bar, armor and distance of the player/mob under the crosshair or hit last. */
	static final class TargetModule extends HudModule {
		TargetModule() {
			super("target", Category.PVP, 0.56F, 0.58F, false);
		}

		@Override
		public int[] render(HudRender r) {
			LivingEntity t = CombatTracker.target(r.mc);
			boolean example = t == null && r.editor;
			if (t == null && !example) {
				return new int[] {0, 0};
			}
			int w = 140, h = 42;
			if (r.measure) {
				return new int[] {w, h};
			}
			String name = example ? "Steve" : t.getDisplayName().getString();
			float health = example ? 14.5F : t.getHealth();
			float max = example ? 20.0F : t.getMaxHealth();
			float absorption = example ? 4.0F : t.getAbsorptionAmount();
			int armor = example ? 16 : t.getArmorValue();
			double dist = example ? 3.1 : (r.mc.player == null ? 0 : t.distanceTo(r.mc.player));

			r.panel(w, h);
			r.accentBar(h);
			r.text(r.font.plainSubstrByWidth(name, w - 34), 6, 4, 0xFFFFFFFF);
			// Health bar: green -> yellow -> red, absorption in gold on top.
			int barW = w - 34, barX = 6, barY = 16;
			r.fill(barX, barY, barX + barW, barY + 5, 0x60000000);
			float part = Mth.clamp(health / Math.max(1, max), 0, 1);
			int color = part > 0.5F ? 0xFF55E36B : part > 0.25F ? 0xFFFFD24A : 0xFFFF5555;
			r.fill(barX, barY, barX + Math.round(barW * part), barY + 5, color);
			if (absorption > 0) {
				r.fill(barX, barY, barX + Math.round(barW * Mth.clamp(absorption / Math.max(1, max), 0, 1)), barY + 2, 0xFFFFC83D);
			}
			String hp = String.format("%.1f", health) + (absorption > 0 ? "+" + Math.round(absorption) : "") + " ❤";
			r.text(hp, 6, 26, color);
			String right = armor + " ⛨  " + String.format("%.1fm", dist);
			r.text(right, w - 28 - r.width(right), 26, 0xFFAAB0C0);
			ItemStack held = example ? new ItemStack(Items.DIAMOND_SWORD) : t.getMainHandItem();
			r.item(held, w - 22, 4);
			return new int[] {w, h};
		}
	}

	/** Armor pieces with durability; flashes red when a piece is about to break. */
	static final class ArmorModule extends HudModule {
		private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

		ArmorModule() {
			super("armor", Category.ITEMS, 0.005F, 0.45F, true);
		}

		@Override
		public List<String> options() {
			return List.of("durability", "horizontal", "warn");
		}

		@Override
		public boolean defaultOption(String key) {
			return !key.equals("horizontal");
		}

		@Override
		public int[] render(HudRender r) {
			boolean text = this.opt("durability");
			boolean horizontal = this.opt("horizontal");
			int cell = text ? (horizontal ? 36 : 18) : 18;
			int w = horizontal ? SLOTS.length * (text ? 40 : 18) + 4 : (text ? 22 + r.width("100%") + 4 : 22);
			int h = horizontal ? 22 : SLOTS.length * 18 + 4;
			if (r.measure) {
				return new int[] {w, h};
			}
			r.panel(w, h);
			boolean blink = (System.currentTimeMillis() / 300) % 2 == 0;
			for (int i = 0; i < SLOTS.length; i++) {
				int x = horizontal ? 3 + i * (text ? 40 : 18) : 3;
				int y = horizontal ? 3 : 2 + i * 18;
				ItemStack stack = r.mc.player == null ? ItemStack.EMPTY : r.mc.player.getItemBySlot(SLOTS[i]);
				if (stack.isEmpty()) {
					r.fill(x + 2, y + 5, x + 14, y + 11, 0x30FFFFFF);
					continue;
				}
				float left = durability(stack);
				if (this.opt("warn") && left >= 0 && left < 0.15F && blink) {
					r.fill(x - 1, y - 1, x + 17, y + 17, 0x90FF3030);
				}
				r.itemWithCount(stack, x, y);
				if (text && left >= 0) {
					String pct = Math.round(left * 100) + "%";
					r.text(pct, x + 18, y + 4, durabilityColor(left));
				}
			}
			return new int[] {w, h};
		}
	}

	/** The item in hand: durability left for tools/weapons, total count in the inventory for everything else. */
	static final class HeldItemModule extends HudModule {
		HeldItemModule() {
			super("helditem", Category.ITEMS, 0.20F, 0.10F, false);
		}

		@Override
		public int[] render(HudRender r) {
			ItemStack stack = r.mc.player == null ? ItemStack.EMPTY : r.mc.player.getMainHandItem();
			if (stack.isEmpty()) {
				if (!r.editor) {
					return new int[] {0, 0};
				}
				stack = new ItemStack(Items.DIAMOND_PICKAXE);
			}
			String text;
			int color = 0xFFFFFFFF;
			if (stack.isDamageableItem()) {
				int left = stack.getMaxDamage() - stack.getDamageValue();
				text = left + "/" + stack.getMaxDamage();
				color = durabilityColor(left / (float) stack.getMaxDamage());
			} else {
				text = "x" + DnzHud.countInInventory(r.mc, stack);
			}
			int w = 24 + r.width(text) + 6, h = 20;
			r.panel(w, h);
			r.item(stack, 2, 2);
			r.text(text, 22, 6, color);
			return new int[] {w, h};
		}
	}

	/** Active potion effects with icon, level and time left (red when ending). */
	static final class EffectsModule extends HudModule {
		EffectsModule() {
			super("effects", Category.ITEMS, 0.005F, 0.20F, true);
		}

		/** The effect list is sorted 4 times a second instead of every frame (times only change once a second). */
		private List<MobEffectInstance> effects = List.of();
		private long effectsAt;

		@Override
		public int[] render(HudRender r) {
			long now = System.nanoTime();
			if (now - this.effectsAt > 250_000_000L) {
				this.effectsAt = now;
				this.effects = r.mc.player == null ? List.of()
					: r.mc.player.getActiveEffects().stream().filter(MobEffectInstance::showIcon).sorted().toList();
			}
			List<MobEffectInstance> effects = this.effects;
			if (effects.isEmpty()) {
				if (!r.editor) {
					return new int[] {0, 0};
				}
				int w = r.width(this.label()) + 34, h = 24;
				r.panel(w, h);
				r.fill(4, 3, 22, 21, 0x30FFFFFF);
				r.text(this.label(), 26, 8, 0xFFFFFFFF);
				return new int[] {w, h};
			}
			int w = 0;
			for (MobEffectInstance e : effects) {
				w = Math.max(w, 26 + Math.max(r.width(name(e)), r.width(time(e))) + 6);
			}
			int h = effects.size() * 22 + 2;
			r.panel(w, h);
			for (int i = 0; i < effects.size(); i++) {
				MobEffectInstance e = effects.get(i);
				int ry = 2 + i * 22;
				if (!r.measure) {
					r.g.blitSprite(RenderPipelines.GUI_TEXTURED, net.minecraft.client.gui.Hud.getMobEffectSprite(e.getEffect()), 4, ry + 1, 18, 18);
				}
				r.text(name(e), 26, ry + 1, 0xFFFFFFFF);
				boolean ending = !e.isInfiniteDuration() && e.getDuration() < 200;
				r.text(time(e), 26, ry + 11, ending ? 0xFFFF6B6B : HudRender.accent());
			}
			return new int[] {w, h};
		}

		private static Component name(MobEffectInstance e) {
			Component name = Component.translatable(e.getDescriptionId());
			if (e.getAmplifier() > 0) {
				name = name.copy().append(" ").append(Component.translatable("enchantment.level." + (e.getAmplifier() + 1)));
			}
			return name;
		}

		private static String time(MobEffectInstance e) {
			if (e.isInfiniteDuration()) {
				return "∞";
			}
			int seconds = e.getDuration() / 20;
			return String.format("%d:%02d", seconds / 60, seconds % 60);
		}
	}

	/** How many of an item (totems, healing pots, golden apples...) are in the inventory. */
	static final class CounterModule extends HudModule {
		private static final Map<String, long[]> CACHE = new HashMap<>();
		/** Made on first use: items can't be created while the game is still starting. */
		private final java.util.function.Supplier<ItemStack> iconFactory;
		private ItemStack icon;
		private final Predicate<ItemStack> match;

		CounterModule(String id, float x, float y, boolean on, java.util.function.Supplier<ItemStack> icon, Predicate<ItemStack> match) {
			super(id, Category.ITEMS, x, y, on);
			this.iconFactory = icon;
			this.match = match;
		}

		@Override
		public List<String> options() {
			return List.of("hidezero");
		}

		@Override
		public boolean defaultOption(String key) {
			return false;
		}

		@Override
		public int[] render(HudRender r) {
			int count = this.count(r.mc);
			if (count == 0 && this.opt("hidezero") && !r.editor) {
				return new int[] {0, 0};
			}
			String text = "x" + count;
			int w = 24 + r.width(text) + 6, h = 20;
			r.panel(w, h);
			if (this.icon == null) {
				this.icon = this.iconFactory.get();
			}
			r.item(this.icon, 2, 2);
			r.text(text, 22, 6, count == 0 ? 0xFFFF6B6B : 0xFFFFFFFF);
			return new int[] {w, h};
		}

		/** Counting the whole inventory every frame is wasteful: 4 times a second is plenty. */
		private int count(Minecraft mc) {
			long now = System.nanoTime();
			long[] c = CACHE.computeIfAbsent(this.id, k -> new long[] {0, -1});
			if (c[1] < 0 || now - c[0] > 250_000_000L) {
				c[0] = now;
				int n = 0;
				if (mc.player != null) {
					Inventory inv = mc.player.getInventory();
					for (int i = 0; i < inv.getContainerSize(); i++) {
						ItemStack stack = inv.getItem(i);
						if (this.match.test(stack)) {
							n += stack.getCount();
						}
					}
				}
				c[1] = n;
			}
			return (int) c[1];
		}
	}

	/** Durability left, 0..1, or -1 for items without durability. */
	static float durability(ItemStack stack) {
		if (!stack.isDamageableItem() || stack.getMaxDamage() <= 0) {
			return -1;
		}
		return (stack.getMaxDamage() - stack.getDamageValue()) / (float) stack.getMaxDamage();
	}

	static int durabilityColor(float left) {
		return left > 0.5F ? 0xFF7CE38B : left > 0.2F ? 0xFFFFD24A : 0xFFFF5555;
	}
}
