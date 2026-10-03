package com.dnz.schematic;

import com.dnz.schematic.litematic.Litematic;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/** The loaded schematic and everything around it: loading, layers, the nearest mistake, saving the placement. */
public final class Schematics {
	private static Placement placement;
	private static Checker checker;
	private static boolean loading;
	private static boolean restored;
	private static String message;
	private static long messageUntil;
	private static int ticks;

	/** Nearest mistake to the player (world position), or null. */
	private static BlockPos nearestError;
	private static int nearestErrorIndex = -1;

	private Schematics() {
	}

	public static Placement placement() {
		return placement;
	}

	public static Checker checker() {
		return checker;
	}

	public static boolean loading() {
		return loading;
	}

	public static BlockPos nearestError() {
		return nearestError;
	}

	public static int nearestErrorIndex() {
		return nearestErrorIndex;
	}

	/** Short message for the menu and HUD (e.g. "loaded", or why a file could not be opened). */
	public static String message() {
		return System.currentTimeMillis() < messageUntil ? message : null;
	}

	public static void say(String text) {
		message = text;
		messageUntil = System.currentTimeMillis() + 6000;
	}

	public static Path folder() {
		return FabricLoader.getInstance().getGameDir().resolve("schematics");
	}

	/** .litematic files in the schematics folder (and its sub folders), newest first. */
	public static List<Path> files() {
		Path dir = folder();
		try {
			Files.createDirectories(dir);
			try (Stream<Path> s = Files.walk(dir, 4)) {
				return s.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().toLowerCase().endsWith(".litematic"))
					.sorted(Comparator.comparingLong(Schematics::modified).reversed())
					.toList();
			}
		} catch (IOException e) {
			return new ArrayList<>();
		}
	}

	private static long modified(Path p) {
		try {
			return Files.getLastModifiedTime(p).toMillis();
		} catch (IOException e) {
			return 0;
		}
	}

	/** Loads a file in the background and places it at the player's feet. */
	public static void load(Path file) {
		Minecraft mc = Minecraft.getInstance();
		BlockPos at = mc.player != null ? mc.player.blockPosition() : BlockPos.ZERO;
		load(file, at, Rotation.NONE, Mirror.NONE, true);
	}

	private static void load(Path file, BlockPos origin, Rotation rotation, Mirror mirror, boolean resetLayer) {
		if (loading) {
			return;
		}
		loading = true;
		say(S.t("msg.loading", file.getFileName().toString()));
		Thread thread = new Thread(() -> {
			try {
				Litematic schem = Litematic.read(file);
				Minecraft.getInstance().execute(() -> {
					Placement p = new Placement(schem, origin);
					p.setRotation(rotation);
					p.setMirror(mirror);
					placement = p;
					checker = new Checker(p);
					nearestError = null;
					nearestErrorIndex = -1;
					SchematicConfig config = SchematicConfig.get();
					if (resetLayer) {
						config.layerMode = 0;
						config.layer = 0;
						config.visible = true;
					}
					config.layer = Math.min(config.layer, schem.sizeY - 1);
					saveState();
					say(S.t("msg.loaded", schem.name, schem.sizeX + " x " + schem.sizeY + " x " + schem.sizeZ));
					loading = false;
				});
			} catch (Throwable e) {
				Minecraft.getInstance().execute(() -> {
					say(S.t("msg.load_failed", file.getFileName().toString(), String.valueOf(e.getMessage())));
					loading = false;
				});
			}
		}, "DNZ Schematic loader");
		thread.setDaemon(true);
		thread.start();
	}

	public static void remove() {
		placement = null;
		checker = null;
		nearestError = null;
		nearestErrorIndex = -1;
		SchematicConfig config = SchematicConfig.get();
		config.file = "";
		config.save();
	}

	/** Remembers the placement, so it comes back after restarting the game. */
	public static void saveState() {
		SchematicConfig config = SchematicConfig.get();
		if (placement != null) {
			config.file = placement.schematic.file.toAbsolutePath().toString();
			config.x = placement.origin().getX();
			config.y = placement.origin().getY();
			config.z = placement.origin().getZ();
			config.rotation = placement.rotation().name();
			config.mirror = placement.mirror().name();
		}
		config.save();
	}

	/** Whether a height inside the schematic (0 = bottom) is shown by the layer setting. */
	public static boolean layerVisible(int relY) {
		SchematicConfig config = SchematicConfig.get();
		return switch (config.layerMode) {
			case 1 -> relY == config.layer;
			case 2 -> relY <= config.layer;
			default -> true;
		};
	}

	public static void changeLayer(int delta) {
		if (placement == null) {
			return;
		}
		SchematicConfig config = SchematicConfig.get();
		if (config.layerMode == 0) {
			config.layerMode = 1;
		}
		config.layer = Math.clamp(config.layer + delta, 0, placement.schematic.sizeY - 1);
		config.save();
		say(S.t("msg.layer", config.layer + 1, placement.schematic.sizeY));
	}

	public static void tick(Minecraft mc) {
		if (mc.level == null || mc.player == null) {
			return;
		}
		if (!restored) {
			restored = true;
			restore();
		}
		if (placement == null || checker == null) {
			return;
		}
		BlockPos player = mc.player.blockPosition();
		checker.tick(mc.level, player);
		// Twice a second is plenty for an arrow on the HUD, and nothing to search when there are no mistakes.
		if (++ticks % 10 == 0) {
			if (!checker.hasErrors()) {
				nearestError = null;
				nearestErrorIndex = -1;
				return;
			}
			findNearestError(player);
		}
	}

	private static void restore() {
		SchematicConfig config = SchematicConfig.get();
		if (config.file.isBlank() || !Files.isRegularFile(Path.of(config.file))) {
			return;
		}
		Rotation rotation;
		Mirror mirror;
		try {
			rotation = Rotation.valueOf(config.rotation);
			mirror = Mirror.valueOf(config.mirror);
		} catch (IllegalArgumentException e) {
			rotation = Rotation.NONE;
			mirror = Mirror.NONE;
		}
		load(Path.of(config.file), new BlockPos(config.x, config.y, config.z), rotation, mirror, false);
	}

	/** Searches the blocks near the player for the closest mistake that the layer setting shows. */
	private static void findNearestError(BlockPos player) {
		BlockPos min = placement.min(), max = placement.max();
		int r = 20;
		int x0 = Math.max(min.getX(), player.getX() - r), x1 = Math.min(max.getX(), player.getX() + r);
		int y0 = Math.max(min.getY(), player.getY() - r), y1 = Math.min(max.getY(), player.getY() + r);
		int z0 = Math.max(min.getZ(), player.getZ() - r), z1 = Math.min(max.getZ(), player.getZ() + r);
		long best = Long.MAX_VALUE;
		BlockPos found = null;
		int foundIndex = -1;
		for (int y = y0; y <= y1; y++) {
			if (!layerVisible(y - min.getY())) {
				continue;
			}
			for (int z = z0; z <= z1; z++) {
				for (int x = x0; x <= x1; x++) {
					int i = placement.toIndex(x, y, z);
					if (i < 0 || !Checker.isError(checker.status(i))) {
						continue;
					}
					long dx = x - player.getX(), dy = y - player.getY(), dz = z - player.getZ();
					long d = dx * dx + dy * dy + dz * dz;
					if (d < best) {
						best = d;
						found = new BlockPos(x, y, z);
						foundIndex = i;
					}
				}
			}
		}
		nearestError = found;
		nearestErrorIndex = foundIndex;
	}
}
