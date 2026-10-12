package com.dnz.client.hud;

import com.dnz.client.hud.HudModule.Category;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.InputStream;
import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;

/** Additional self-contained panels for the HUD editor. */
public final class ExtraModules {
	private ExtraModules() {}
	private static final List<ItemStack> ENDER_CHEST = new ArrayList<>();
	private static Object enderLevel;
	private static final Deque<Integer> FPS_HISTORY = new ArrayDeque<>();
	private static final Deque<Integer> PING_HISTORY = new ArrayDeque<>();
	private static final Deque<Double> TPS_HISTORY = new ArrayDeque<>();
	private static long statsAt;
	private static final Deque<Long> PLACEMENTS = new ArrayDeque<>();
	private static final List<PendingPlace> PENDING = new ArrayList<>();
	private static final Deque<float[]> MOUSE_TRAIL = new ArrayDeque<>();
	private static float mouseX = 40, mouseY = 40, lastYaw, lastPitch;
	private static Object mousePlayer;
	private record PendingPlace(Level level, BlockPos hit, BlockState oldHit, BlockPos adjacent, BlockState oldAdjacent, net.minecraft.world.level.block.Block block) {}

	public static void register() {
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (level.isClientSide() && player.getItemInHand(hand).getItem() instanceof BlockItem item) {
				BlockPos at = hit.getBlockPos(), beside = at.relative(hit.getDirection());
				PENDING.add(new PendingPlace(level, at.immutable(), level.getBlockState(at), beside.immutable(),
					level.getBlockState(beside), item.getBlock()));
			}
			return InteractionResult.PASS;
		});
	}

	static int placements(int seconds) {
		long cutoff = System.currentTimeMillis() - Math.clamp(seconds, 1, 3600) * 1000L;
		while (!PLACEMENTS.isEmpty() && PLACEMENTS.peekFirst() < cutoff) PLACEMENTS.removeFirst();
		return PLACEMENTS.size();
	}

	static String fpsText(HudModule module, Minecraft mc) {
		int current = mc.getFps();
		if (FPS_HISTORY.isEmpty()) return Integer.toString(current);
		int[] values = FPS_HISTORY.stream().mapToInt(Integer::intValue).sorted().toArray();
		StringBuilder out = new StringBuilder().append(current);
		if (module.opt("average")) out.append("  avg ").append(Math.round(java.util.Arrays.stream(values).average().orElse(current)));
		if (module.opt("median")) out.append("  med ").append(values[values.length / 2]);
		if (module.opt("percentile")) out.append("  1% ").append(values[Math.max(0, values.length / 100)]);
		if (module.opt("consistency")) {
			double mean = java.util.Arrays.stream(values).average().orElse(current);
			double variance = 0;
			for (int value : values) variance += Math.pow(value - mean, 2);
			out.append("  ±").append(Math.round(Math.sqrt(variance / values.length)));
		}
		return out.toString();
	}

	static String pingText(HudModule module, Minecraft mc) {
		if (mc.player == null || mc.getConnection() == null) return "-";
		var info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
		if (info == null) return "-";
		int current = info.getLatency();
		StringBuilder out = new StringBuilder().append(current).append(" ms");
		if (module.opt("average") && !PING_HISTORY.isEmpty()) out.append("  avg ").append(Math.round(PING_HISTORY.stream().mapToInt(Integer::intValue).average().orElse(current)));
		if (module.opt("highest") && !PING_HISTORY.isEmpty()) out.append("  max ").append(PING_HISTORY.stream().mapToInt(Integer::intValue).max().orElse(current));
		return out.toString();
	}

	static String tpsText(HudModule module, Minecraft mc) {
		var server = mc.getSingleplayerServer();
		if (server == null) return "-";
		double current = Math.min(20, 1000 / Math.max(50, server.getAverageTickTimeNanos() / 1e6));
		StringBuilder out = new StringBuilder(String.format("%.1f", current));
		if (module.opt("average") && !TPS_HISTORY.isEmpty())
			out.append(String.format("  avg %.1f", TPS_HISTORY.stream().mapToDouble(Double::doubleValue).average().orElse(current)));
		if (module.opt("lowest") && !TPS_HISTORY.isEmpty())
			out.append(String.format("  low %.1f", TPS_HISTORY.stream().mapToDouble(Double::doubleValue).min().orElse(current)));
		return out.toString();
	}

	public static void tick(Minecraft mc) {
		if (mc.player != null) {
			if (mousePlayer == mc.player) {
				mouseX = Math.clamp(mouseX - Mth.wrapDegrees(mc.player.getYRot() - lastYaw) * 0.55F, 4, 76);
				mouseY = Math.clamp(mouseY + (mc.player.getXRot() - lastPitch) * 0.55F, 4, 76);
				mouseX += (40 - mouseX) * 0.02F;
				mouseY += (40 - mouseY) * 0.02F;
				MOUSE_TRAIL.addLast(new float[] {mouseX, mouseY});
				while (MOUSE_TRAIL.size() > 18) MOUSE_TRAIL.removeFirst();
			} else { mouseX = 40; mouseY = 40; MOUSE_TRAIL.clear(); }
			mousePlayer = mc.player;
			lastYaw = mc.player.getYRot(); lastPitch = mc.player.getXRot();
		} else { mousePlayer = null; MOUSE_TRAIL.clear(); }
		for (PendingPlace p : PENDING) {
			if (p.level != mc.level) continue;
			boolean hitChanged = !p.level.getBlockState(p.hit).equals(p.oldHit) && p.level.getBlockState(p.hit).is(p.block);
			boolean adjacentChanged = !p.level.getBlockState(p.adjacent).equals(p.oldAdjacent) && p.level.getBlockState(p.adjacent).is(p.block);
			if (hitChanged || adjacentChanged) PLACEMENTS.addLast(System.currentTimeMillis());
		}
		PENDING.clear();
		long now = System.currentTimeMillis();
		if (now - statsAt >= 1000) {
			statsAt = now;
			FPS_HISTORY.addLast(mc.getFps());
			while (FPS_HISTORY.size() > 60) FPS_HISTORY.removeFirst();
			if (mc.player != null && mc.getConnection() != null) {
				var info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
				if (info != null) {
					PING_HISTORY.addLast(info.getLatency());
					while (PING_HISTORY.size() > 60) PING_HISTORY.removeFirst();
				}
			} else PING_HISTORY.clear();
			var server = mc.getSingleplayerServer();
			if (server != null) {
				TPS_HISTORY.addLast(Math.min(20, 1000 / Math.max(50, server.getAverageTickTimeNanos() / 1e6)));
				while (TPS_HISTORY.size() > 60) TPS_HISTORY.removeFirst();
			} else TPS_HISTORY.clear();
		}
		if (mc.level != enderLevel) {
			enderLevel = mc.level;
			ENDER_CHEST.clear();
			PLACEMENTS.clear();
			PENDING.clear();
		}
		if (mc.gui.screen() instanceof ContainerScreen screen
			&& screen.getTitle().getString().equals(net.minecraft.network.chat.Component.translatable("container.enderchest").getString())) {
			ENDER_CHEST.clear();
			for (int i = 0; i < Math.min(27, screen.getMenu().slots.size()); i++)
				ENDER_CHEST.add(screen.getMenu().slots.get(i).getItem().copy());
		}
	}

	private static List<ItemStack> heldShulker(Minecraft mc) {
		if (mc.player == null) return List.of();
		ItemStack held = mc.player.getMainHandItem();
		if (!(held.getItem() instanceof BlockItem block) || !(block.getBlock() instanceof ShulkerBoxBlock)) return List.of();
		ItemContainerContents contents = held.get(DataComponents.CONTAINER);
		if (contents == null) return List.of();
		NonNullList<ItemStack> slots = NonNullList.withSize(27, ItemStack.EMPTY);
		contents.copyInto(slots);
		return slots;
	}

	static List<HudModule> all() {
		List<HudModule> result = new ArrayList<>();
		result.add(new BlockAbove());
		result.add(new BlockPlaceCount());
		result.add(new InventoryPanel());
		result.add(new ItemCounter());
		result.add(new ItemTracker());
		result.add(new AnalogClock());
		result.add(new PlayerHead());
		result.add(new PlayerPreview());
		result.add(new ShapePanel());
		result.add(new MouseStrokes());
		result.add(new ItemLorePanel());
		result.add(new ResourcePackPanel());
		result.add(new CustomText());
		result.add(new CustomImage());
		result.add(new Modules.TextModule("daycounter", Category.INFO, 0.84F, 0.865F, false,
			mc -> mc.level == null ? "-" : Long.toString(mc.level.getOverworldClockTime() / 24000 + 1)));
		result.add(new Modules.TextModule("digitalclock", Category.INFO, 0.84F, 0.91F, false,
			mc -> LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))) {
			@Override public List<String> options() { return List.of("seconds", "twelvehour"); }
			@Override protected String value(Minecraft mc) {
				return LocalTime.now().format(DateTimeFormatter.ofPattern(
					this.opt("twelvehour") ? (this.opt("seconds") ? "hh:mm:ss a" : "hh:mm a")
						: (this.opt("seconds") ? "HH:mm:ss" : "HH:mm")));
			}
			@Override public boolean defaultOption(String key) { return false; }
		});
		return result;
	}

	private static final class BlockAbove extends Modules.TextModule {
		BlockAbove() { super("blockabove", Category.INFO, 0.40F, 0.10F, false, BlockAbove::distance); }
		private long alertedAt;
		@Override public List<String> options() { return List.of("sound"); }
		@Override public boolean defaultOption(String key) { return false; }
		@Override public boolean editableValue() { return true; }
		@Override public int[] render(HudRender r) {
			if (!r.editor && !r.measure && this.opt("sound") && r.mc.player != null) {
				try {
					int limit = Math.clamp(Integer.parseInt(this.pos().value.isBlank() ? "3" : this.pos().value), 1, 64);
					int distance = Integer.parseInt(distance(r.mc));
					long now = System.currentTimeMillis();
					if (distance <= limit && now - alertedAt >= 3000) {
						r.mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 0.6F, 1.2F);
						alertedAt = now;
					}
				} catch (NumberFormatException ignored) { }
			}
			return super.render(r);
		}
		private static String distance(Minecraft mc) {
			if (mc.player == null || mc.level == null) return "-";
			BlockPos origin = mc.player.blockPosition();
			BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
			for (int y = origin.getY() + 2; y < mc.level.getMaxY(); y++) {
				pos.set(origin.getX(), y, origin.getZ());
				if (!mc.level.getBlockState(pos).getCollisionShape(mc.level, pos).isEmpty())
					return Integer.toString(y - origin.getY());
			}
			return "∞";
		}
	}

	private static final class BlockPlaceCount extends Modules.TextModule {
		BlockPlaceCount() { super("blockplacecount", Category.PVP, 0.30F, 0.10F, false, mc -> "0"); }
		@Override public boolean editableValue() { return true; }
		@Override protected String value(Minecraft mc) {
			int seconds;
			try { seconds = Integer.parseInt(this.pos().value.isBlank() ? "60" : this.pos().value); }
			catch (NumberFormatException ex) { seconds = 60; }
			return Integer.toString(placements(seconds));
		}
	}

	/** Main inventory and hotbar, with the same slot order as the vanilla inventory screen. */
	private static final class InventoryPanel extends HudModule {
		InventoryPanel() { super("inventory", Category.ITEMS, 0.70F, 0.65F, false); }
		@Override public List<String> options() { return List.of("shulker", "enderchest"); }
		@Override public boolean defaultOption(String key) { return false; }
		@Override public int[] render(HudRender r) {
			List<ItemStack> container = this.opt("shulker") ? heldShulker(r.mc) : List.of();
			if (container.isEmpty() && this.opt("enderchest") && !ENDER_CHEST.isEmpty()) container = ENDER_CHEST;
			int rows = container.isEmpty() ? 4 : 3;
			int w = 9 * 18 + 6, h = rows * 18 + 8;
			r.panel(w, h);
			if (r.mc.player == null && container.isEmpty()) return new int[] {w, h};
			var inventory = r.mc.player == null ? null : r.mc.player.getInventory();
			for (int row = 0; row < rows; row++) {
				for (int column = 0; column < 9; column++) {
					int slot = row == 3 ? column : 9 + row * 9 + column;
					int x = 3 + column * 18, y = 3 + row * 18 + (row == 3 ? 2 : 0);
					r.fill(x, y, x + 17, y + 17, 0x30000000);
					r.itemWithCount(container.isEmpty() ? inventory.getItem(slot) : container.get(row * 9 + column), x, y);
				}
			}
			return new int[] {w, h};
		}
	}

	/** Counts the selected item; an empty configured identifier follows the main hand. */
	private static final class ItemCounter extends HudModule {
		ItemCounter() { super("itemcounter", Category.ITEMS, 0.30F, 0.70F, false); }
		@Override public boolean editableValue() { return true; }
		@Override public List<String> options() { return List.of("hidezero", "shulker", "enderchest"); }
		@Override public boolean defaultOption(String key) { return false; }
		@Override public int[] render(HudRender r) {
			ItemStack selected = r.mc.player == null ? ItemStack.EMPTY : r.mc.player.getMainHandItem();
			String chosen = this.pos().value == null ? "" : this.pos().value.trim();
			if (!chosen.isEmpty() && r.mc.player != null) {
				selected = ItemStack.EMPTY;
				var inv = r.mc.player.getInventory();
				for (int i = 0; i < inv.getContainerSize(); i++) {
					ItemStack stack = inv.getItem(i);
					if (!stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(chosen)) { selected = stack; break; }
				}
				if (selected.isEmpty() && this.opt("enderchest")) for (ItemStack stack : ENDER_CHEST)
					if (!stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(chosen)) { selected = stack; break; }
				if (selected.isEmpty()) {
					Identifier id = Identifier.tryParse(chosen);
					if (id != null) selected = BuiltInRegistries.ITEM.getOptional(id).map(ItemStack::new).orElse(ItemStack.EMPTY);
				}
			}
			int count = 0;
			if (!selected.isEmpty() && r.mc.player != null) {
				var inv = r.mc.player.getInventory();
				for (int i = 0; i < inv.getContainerSize(); i++)
					if (matches(inv.getItem(i), selected, !chosen.isEmpty())) count += inv.getItem(i).getCount();
			}
			if (!selected.isEmpty() && this.opt("shulker") && r.mc.player != null) {
				var inv = r.mc.player.getInventory();
				for (int i = 0; i < inv.getContainerSize(); i++) {
					ItemStack box = inv.getItem(i);
					if (!(box.getItem() instanceof BlockItem block) || !(block.getBlock() instanceof ShulkerBoxBlock)) continue;
					ItemContainerContents contents = box.get(DataComponents.CONTAINER);
					if (contents != null) for (ItemStack stack : (Iterable<ItemStack>) contents.nonEmptyItemCopyStream()::iterator)
						if (matches(stack, selected, !chosen.isEmpty())) count += stack.getCount();
				}
			}
			if (!selected.isEmpty() && this.opt("enderchest")) for (ItemStack stack : ENDER_CHEST)
				if (matches(stack, selected, !chosen.isEmpty())) count += stack.getCount();
			if (count == 0 && this.opt("hidezero") && !r.editor) return new int[] {0, 0};
			String value = "x" + count;
			int w = 26 + r.width(value), h = 20;
			r.panel(w, h);
			r.item(selected, 2, 2);
			r.text(value, 22, 6, 0xFFFFFFFF);
			return new int[] {w, h};
		}
		private static boolean matches(ItemStack stack, ItemStack selected, boolean byId) {
			return !stack.isEmpty() && (byId ? ItemStack.isSameItem(stack, selected)
				: ItemStack.isSameItemSameComponents(stack, selected));
		}
	}

	/** A small feed of changes in inventory item totals, sampled four times per second. */
	private static final class ItemTracker extends HudModule {
		private final Map<Item, Integer> previous = new HashMap<>();
		private final List<Change> changes = new ArrayList<>();
		private Object player;
		private long sampledAt;
		ItemTracker() { super("itemtracker", Category.ITEMS, 0.70F, 0.45F, false); }
		private record Change(ItemStack icon, String name, int delta, long at) {}
		private void sample(Minecraft mc) {
			if (mc.player == null) { player = null; previous.clear(); changes.clear(); return; }
			long now = System.currentTimeMillis();
			if (now - sampledAt < 250) return;
			sampledAt = now;
			Map<Item, Integer> totals = new HashMap<>();
			Map<Item, ItemStack> examples = new HashMap<>();
			var inv = mc.player.getInventory();
			for (int i = 0; i < inv.getContainerSize(); i++) {
				ItemStack s = inv.getItem(i);
				if (!s.isEmpty()) { totals.merge(s.getItem(), s.getCount(), Integer::sum); examples.putIfAbsent(s.getItem(), s); }
			}
			if (player == mc.player) {
				for (Item item : totals.keySet()) {
					int delta = totals.get(item) - previous.getOrDefault(item, 0);
					if (delta != 0) changes.add(new Change(examples.get(item).copyWithCount(1), examples.get(item).getHoverName().getString(), delta, now));
				}
				for (Item item : previous.keySet()) if (!totals.containsKey(item))
					changes.add(new Change(new ItemStack(item), new ItemStack(item).getHoverName().getString(), -previous.get(item), now));
			}
			player = mc.player;
			previous.clear(); previous.putAll(totals);
			changes.removeIf(c -> now - c.at > 6000);
			while (changes.size() > 5) changes.remove(0);
		}
		@Override public int[] render(HudRender r) {
			sample(r.mc);
			if (changes.isEmpty() && !r.editor) return new int[] {0, 0};
			int n = Math.max(1, changes.size());
			int w = 110, h = n * 20 + 4;
			for (Change c : changes) w = Math.max(w, 27 + r.width(c.name + " " + (c.delta > 0 ? "+" : "") + c.delta));
			r.panel(w, h);
			if (changes.isEmpty()) r.text(this.label(), 5, 8, 0xFFFFFFFF);
			for (int i = 0; i < changes.size(); i++) {
				Change c = changes.get(changes.size() - 1 - i);
				r.item(c.icon, 3, 2 + i * 20);
				r.text(c.name + " " + (c.delta > 0 ? "+" : "") + c.delta, 23, 6 + i * 20,
					c.delta > 0 ? 0xFF7CE38B : 0xFFFF7777);
			}
			return new int[] {w, h};
		}
	}

	private static final class CustomText extends HudModule {
		CustomText() { super("customtext", Category.VISUAL, 0.40F, 0.30F, false); }
		@Override public boolean editableValue() { return true; }
		@Override public int[] render(HudRender r) {
			String raw = this.pos().value == null || this.pos().value.isBlank() ? (r.editor ? this.label() : "") : this.pos().value;
			if (raw.isEmpty()) return new int[] {0, 0};
			String[] lines = raw.replace("\\n", "\n").split("\n", -1);
			int w = 12;
			for (String line : lines) w = Math.max(w, r.width(line) + 12);
			int h = lines.length * 11 + 6;
			r.panel(w, h);
			for (int i = 0; i < lines.length; i++) r.text(lines[i], 6, 4 + i * 11, 0xFFFFFFFF);
			return new int[] {w, h};
		}
	}

	/** PNG files are loaded from config/dnzclient/hud-images, with size and opacity set as file.png|width|opacity. */
	private static final class CustomImage extends HudModule {
		private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("dnzclient", "hud_custom_image");
		private String loadedName = "";
		private long loadedModified = -1, checkedAt;
		private int imageW, imageH;
		private boolean loaded;
		CustomImage() { super("customimage", Category.VISUAL, 0.50F, 0.30F, false); }
		@Override public boolean editableValue() { return true; }
		@Override public int[] render(HudRender r) {
			String[] parts = (this.pos().value == null ? "" : this.pos().value).split("\\|", -1);
			String name = parts[0].trim();
			int width = number(parts, 1, 80, 16, 512), opacity = number(parts, 2, 100, 0, 100);
			if (!r.measure && System.currentTimeMillis() - checkedAt > 1000) {
				checkedAt = System.currentTimeMillis();
				load(r.mc, name);
			}
			if (!loaded || imageW <= 0 || imageH <= 0) {
				if (!r.editor) return new int[] {0, 0};
				r.panel(100, 34);
				r.text(name.isEmpty() ? this.label() : name, 6, 12, 0xFFFFFFFF);
				return new int[] {100, 34};
			}
			int height = Math.max(1, Math.round(width * imageH / (float) imageW));
			if (!r.measure) r.g.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, 0, 0, 0, 0, width, height,
				imageW, imageH, Math.round(opacity * 255 / 100.0F) << 24 | 0xFFFFFF);
			return new int[] {width, height};
		}
		private static int number(String[] parts, int index, int fallback, int min, int max) {
			if (parts.length <= index) return fallback;
			try { return Math.clamp(Integer.parseInt(parts[index].trim()), min, max); }
			catch (NumberFormatException ex) { return fallback; }
		}
		private void load(Minecraft mc, String name) {
			try {
				if (name.isBlank() || !name.equals(Path.of(name).getFileName().toString())
					|| !name.toLowerCase(java.util.Locale.ROOT).endsWith(".png")) { clear(mc); return; }
				Path file = FabricLoader.getInstance().getConfigDir().resolve("dnzclient").resolve("hud-images").resolve(name);
				if (!Files.isRegularFile(file) || Files.size(file) > 2_000_000) { clear(mc); return; }
				long modified = Files.getLastModifiedTime(file).toMillis();
				if (loaded && name.equals(loadedName) && modified == loadedModified) return;
				try (InputStream in = Files.newInputStream(file); NativeImage image = NativeImage.read(in)) {
					if (image.getWidth() > 1024 || image.getHeight() > 1024) { clear(mc); return; }
					NativeImage pixels = NativeImage.read(Files.readAllBytes(file));
					mc.getTextureManager().release(TEXTURE);
					mc.getTextureManager().register(TEXTURE, new DynamicTexture(() -> "DNZ HUD image", pixels));
					imageW = image.getWidth(); imageH = image.getHeight();
					loadedName = name; loadedModified = modified; loaded = true;
				}
			}
			catch (Exception ex) { clear(mc); }
		}
		private void clear(Minecraft mc) {
			if (loaded) mc.getTextureManager().release(TEXTURE);
			loaded = false; loadedName = ""; loadedModified = -1;
		}
	}

	private static final class AnalogClock extends HudModule {
		AnalogClock() { super("analogclock", Category.VISUAL, 0.60F, 0.10F, false); }
		@Override public int[] render(HudRender r) {
			int size = 64, center = size / 2;
			r.panel(size, size);
			for (int i = 0; i < 12; i++) {
				double a = i * Math.PI / 6 - Math.PI / 2;
				r.fill(center + (int) (Math.cos(a) * 26), center + (int) (Math.sin(a) * 26),
					center + (int) (Math.cos(a) * 26) + 2, center + (int) (Math.sin(a) * 26) + 2, 0xFFFFFFFF);
			}
			LocalTime time = LocalTime.now();
			line(r, center, center, (time.getHour() % 12 + time.getMinute() / 60.0) * Math.PI / 6 - Math.PI / 2, 15, 0xFFFFFFFF);
			line(r, center, center, (time.getMinute() + time.getSecond() / 60.0) * Math.PI / 30 - Math.PI / 2, 22, HudRender.accent());
			if (this.opt("seconds")) line(r, center, center, time.getSecond() * Math.PI / 30 - Math.PI / 2, 24, 0xFFFF7777);
			return new int[] {size, size};
		}
		@Override public List<String> options() { return List.of("seconds"); }
		@Override public boolean defaultOption(String key) { return true; }
		private static void line(HudRender r, int x, int y, double angle, int length, int color) {
			int endX = x + (int) Math.round(Math.cos(angle) * length), endY = y + (int) Math.round(Math.sin(angle) * length);
			int steps = Math.max(Math.abs(endX - x), Math.abs(endY - y));
			for (int i = 0; i <= steps; i++) {
				int px = x + (endX - x) * i / Math.max(1, steps), py = y + (endY - y) * i / Math.max(1, steps);
				r.fill(px, py, px + 1, py + 1, color);
			}
		}
	}

	private static final class PlayerHead extends HudModule {
		PlayerHead() { super("playerhead", Category.VISUAL, 0.60F, 0.22F, false); }
		@Override public int[] render(HudRender r) {
			r.panel(36, 36);
			if (!r.measure && r.mc.player != null)
				PlayerFaceExtractor.extractRenderState(r.g, r.mc.player.getSkin(), 2, 2, 32);
			return new int[] {36, 36};
		}
	}

	private static final class PlayerPreview extends HudModule {
		PlayerPreview() { super("playerpreview", Category.VISUAL, 0.67F, 0.22F, false); }
		@Override public List<String> options() { return List.of("nametag", "rotation"); }
		@Override public boolean defaultOption(String key) { return key.equals("rotation"); }
		@Override public int[] render(HudRender r) {
			int w = 76, h = this.opt("nametag") ? 100 : 88;
			r.panel(w, h);
			if (!r.measure && r.mc.player != null) {
				float angle = this.opt("rotation") ? (float) (System.currentTimeMillis() % 8000) / 8000 * 360 : 0;
				InventoryScreen.extractEntityInInventoryFollowsMouse(r.g, 4, 2, w - 4, 86, 28, 0, angle, 0, r.mc.player);
			}
			if (this.opt("nametag") && r.mc.player != null) {
				String name = r.mc.player.getName().getString();
				r.centered(name, w / 2, 88, 0xFFFFFFFF);
			}
			return new int[] {w, h};
		}
	}

	private static final class ItemLorePanel extends HudModule {
		ItemLorePanel() { super("itemlore", Category.ITEMS, 0.30F, 0.76F, false); }
		@Override public int[] render(HudRender r) {
			ItemStack held = r.mc.player == null ? ItemStack.EMPTY : r.mc.player.getMainHandItem();
			var lore = held.get(DataComponents.LORE);
			List<net.minecraft.network.chat.Component> lines = lore == null ? List.of() : lore.styledLines();
			if (lines.isEmpty() && !r.editor) return new int[] {0, 0};
			int count = Math.max(1, Math.min(8, lines.size()));
			int w = r.width(this.label()) + 12;
			for (int i = 0; i < Math.min(8, lines.size()); i++) w = Math.max(w, r.width(lines.get(i)) + 12);
			int h = count * 11 + 6;
			r.panel(w, h);
			if (lines.isEmpty()) r.text(this.label(), 6, 4, 0xFFFFFFFF);
			for (int i = 0; i < Math.min(8, lines.size()); i++) r.text(lines.get(i), 6, 4 + i * 11, 0xFFFFFFFF);
			return new int[] {w, h};
		}
	}

	private static final class ResourcePackPanel extends HudModule {
		private static final Identifier ICON = Identifier.fromNamespaceAndPath("dnzclient", "hud_resource_pack_icon");
		private String loadedPack = "";
		private boolean hasIcon;
		private int iconW, iconH;
		ResourcePackPanel() { super("resourcepack", Category.INFO, 0.40F, 0.20F, false); }
		@Override public List<String> options() { return List.of("description"); }
		@Override public boolean defaultOption(String key) { return true; }
		@Override public int[] render(HudRender r) {
			var packs = r.mc.getResourcePackRepository().getSelectedPacks().stream().filter(p -> !p.isRequired()).toList();
			var pack = packs.isEmpty() ? null : packs.getLast();
			if (!r.measure && !(pack == null ? "" : pack.getId()).equals(loadedPack)) loadIcon(r.mc, pack);
			String title = packs.isEmpty() ? "Default" : packs.getLast().getTitle().getString();
			String detail = packs.isEmpty() ? "" : packs.getLast().getDescription().getString();
			if (detail.length() > 70) detail = detail.substring(0, 67) + "...";
			int w = Math.max(r.width(title), this.opt("description") ? r.width(detail) : 0) + 32;
			int h = this.opt("description") && !detail.isEmpty() ? 28 : 24;
			r.panel(w, h);
			if (hasIcon && !r.measure) r.g.blit(RenderPipelines.GUI_TEXTURED, ICON, 3, 2, 0, 0, 20, 20, iconW, iconH);
			else r.fill(3, 2, 23, 22, 0x70808080);
			r.text(title, 27, 4, 0xFFFFFFFF);
			if (h > 24) r.text(detail, 27, 15, 0xFFB8C0CC);
			return new int[] {w, h};
		}
		private void loadIcon(Minecraft mc, net.minecraft.server.packs.repository.Pack pack) {
			mc.getTextureManager().release(ICON);
			hasIcon = false;
			loadedPack = pack == null ? "" : pack.getId();
			if (pack == null) return;
			try {
				byte[] bytes = com.dnz.client.compat.Compat.packIcon(pack);
				if (bytes == null) return;
					if (bytes.length > 2_000_000) return;
					NativeImage image = NativeImage.read(bytes);
					if (image.getWidth() > 1024 || image.getHeight() > 1024) { image.close(); return; }
					iconW = image.getWidth(); iconH = image.getHeight();
					mc.getTextureManager().register(ICON, new DynamicTexture(() -> "DNZ pack icon", image));
					hasIcon = true;
			} catch (Exception ignored) { }
		}
	}

	/** Flat primitives, selected by entering rectangle, ellipse, triangle, polygon, star or ring. */
	private static final class ShapePanel extends HudModule {
		ShapePanel() { super("shape", Category.VISUAL, 0.60F, 0.32F, false); }
		@Override public boolean editableValue() { return true; }
		@Override public int[] render(HudRender r) {
			int size = 64;
			if (r.measure) return new int[] {size, size};
			String shape = this.pos().value == null ? "rectangle" : this.pos().value.trim().toLowerCase(java.util.Locale.ROOT);
			for (int y = 0; y < size; y++) {
				int start = -1;
				for (int x = 0; x <= size; x++) {
					double dx = (x + 0.5 - 32) / 30.0, dy = (y + 0.5 - 32) / 30.0;
					double radius = Math.hypot(dx, dy);
					boolean inside = x < size && switch (shape) {
						case "ellipse" -> radius <= 1;
						case "ring" -> radius <= 1 && radius >= 0.72;
						case "triangle" -> dy >= -1 && dy <= 1 && Math.abs(dx) <= (dy + 1) / 2;
						case "polygon" -> Math.abs(dx) + Math.abs(dy) <= 1.25;
						case "star" -> radius <= 0.56 + 0.36 * Math.cos(5 * Math.atan2(dy, dx) - Math.PI / 2);
						default -> true;
					};
					if (inside && start < 0) start = x;
					else if (!inside && start >= 0) { r.fill(start, y, x, y + 1, HudRender.accent()); start = -1; }
				}
			}
			return new int[] {size, size};
		}
	}

	private static final class MouseStrokes extends HudModule {
		MouseStrokes() { super("mousestrokes", Category.VISUAL, 0.76F, 0.22F, false); }
		@Override public List<String> options() { return List.of("snake"); }
		@Override public boolean defaultOption(String key) { return true; }
		@Override public int[] render(HudRender r) {
			r.panel(80, 80);
			if (this.opt("snake")) {
				int index = 0, count = MOUSE_TRAIL.size();
				for (float[] point : MOUSE_TRAIL) {
					int alpha = 40 + 170 * ++index / Math.max(1, count);
					r.fill(Math.round(point[0]) - 1, Math.round(point[1]) - 1, Math.round(point[0]) + 2,
						Math.round(point[1]) + 2, alpha << 24 | (HudRender.accent() & 0xFFFFFF));
				}
			}
			int x = Math.round(mouseX), y = Math.round(mouseY);
			r.fill(x - 2, y - 2, x + 3, y + 3, 0xFFFFFFFF);
			return new int[] {80, 80};
		}
	}
}
