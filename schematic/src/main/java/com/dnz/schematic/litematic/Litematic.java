package com.dnz.schematic.litematic;

import com.mojang.serialization.Dynamic;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A .litematic file: one or more regions of blocks, merged into a single box.
 * Blocks are stored as palette indexes (index 0 is always air) so even large buildings use little memory.
 * Block names from older Minecraft versions are updated to today's names while reading.
 */
public final class Litematic {
	/** 32 million blocks (e.g. 320 x 320 x 320) = 64 MB; anything bigger is refused. */
	public static final long MAX_VOLUME = 32L * 1024 * 1024;

	public final Path file;
	public final String name;
	public final String author;
	public final int sizeX;
	public final int sizeY;
	public final int sizeZ;
	private final BlockState[] palette;
	private final char[] blocks;
	/** How many positions use each palette entry. */
	private final int[] totals;

	private Litematic(Path file, String name, String author, int sizeX, int sizeY, int sizeZ, BlockState[] palette, char[] blocks) {
		this.file = file;
		this.name = name;
		this.author = author;
		this.sizeX = sizeX;
		this.sizeY = sizeY;
		this.sizeZ = sizeZ;
		this.palette = palette;
		this.blocks = blocks;
		this.totals = new int[palette.length];
		for (char b : blocks) {
			this.totals[b]++;
		}
	}

	public int volume() {
		return this.blocks.length;
	}

	public int index(int x, int y, int z) {
		return (y * this.sizeZ + z) * this.sizeX + x;
	}

	public int xOf(int index) {
		return index % this.sizeX;
	}

	public int zOf(int index) {
		return (index / this.sizeX) % this.sizeZ;
	}

	public int yOf(int index) {
		return index / (this.sizeX * this.sizeZ);
	}

	/** Palette entry at a position (0 = air). */
	public int paletteAt(int index) {
		return this.blocks[index];
	}

	public BlockState state(int paletteIndex) {
		return this.palette[paletteIndex];
	}

	public int paletteSize() {
		return this.palette.length;
	}

	public int total(int paletteIndex) {
		return this.totals[paletteIndex];
	}

	public int nonAirCount() {
		return this.blocks.length - this.totals[0];
	}

	// ------------------------------------------------------------------ reading

	public static Litematic read(Path file) throws IOException {
		CompoundTag root = readNbt(file);
		int dataVersion = root.getIntOr("MinecraftDataVersion", 0);
		int currentVersion = SharedConstants.getCurrentVersion().dataVersion().version();
		CompoundTag meta = root.getCompoundOrEmpty("Metadata");
		CompoundTag regions = root.getCompoundOrEmpty("Regions");
		if (regions.keySet().isEmpty()) {
			throw new IOException("no regions");
		}

		// The box around all regions. A region's size can be negative (it was selected "backwards").
		List<Region> list = new ArrayList<>();
		int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
		for (String key : regions.keySet()) {
			CompoundTag r = regions.getCompoundOrEmpty(key);
			CompoundTag pos = r.getCompoundOrEmpty("Position");
			CompoundTag size = r.getCompoundOrEmpty("Size");
			int sx = size.getIntOr("x", 0), sy = size.getIntOr("y", 0), sz = size.getIntOr("z", 0);
			if (sx == 0 || sy == 0 || sz == 0) {
				continue;
			}
			Region region = new Region(r,
				pos.getIntOr("x", 0) + (sx < 0 ? sx + 1 : 0),
				pos.getIntOr("y", 0) + (sy < 0 ? sy + 1 : 0),
				pos.getIntOr("z", 0) + (sz < 0 ? sz + 1 : 0),
				Math.abs(sx), Math.abs(sy), Math.abs(sz));
			list.add(region);
			minX = Math.min(minX, region.x);
			minY = Math.min(minY, region.y);
			minZ = Math.min(minZ, region.z);
			maxX = Math.max(maxX, region.x + region.sx);
			maxY = Math.max(maxY, region.y + region.sy);
			maxZ = Math.max(maxZ, region.z + region.sz);
		}
		if (list.isEmpty()) {
			throw new IOException("empty");
		}
		int sizeX = maxX - minX, sizeY = maxY - minY, sizeZ = maxZ - minZ;
		if ((long) sizeX * sizeY * sizeZ > MAX_VOLUME) {
			throw new IOException("too big: " + sizeX + " x " + sizeY + " x " + sizeZ);
		}

		List<BlockState> palette = new ArrayList<>();
		Map<BlockState, Integer> paletteIds = new HashMap<>();
		palette.add(Blocks.AIR.defaultBlockState());
		paletteIds.put(Blocks.AIR.defaultBlockState(), 0);
		char[] blocks = new char[sizeX * sizeY * sizeZ];

		for (Region region : list) {
			ListTag paletteTag = region.tag.getListOrEmpty("BlockStatePalette");
			int[] toGlobal = new int[Math.max(1, paletteTag.size())];
			for (int i = 0; i < paletteTag.size(); i++) {
				BlockState state = readState(paletteTag.getCompoundOrEmpty(i), dataVersion, currentVersion);
				Integer id = paletteIds.get(state);
				if (id == null) {
					if (palette.size() >= Character.MAX_VALUE) {
						throw new IOException("too many different blocks");
					}
					id = palette.size();
					palette.add(state);
					paletteIds.put(state, id);
				}
				toGlobal[i] = id;
			}
			long[] data = region.tag.getLongArray("BlockStates").orElse(new long[0]);
			int bits = Math.max(2, 32 - Integer.numberOfLeadingZeros(Math.max(1, paletteTag.size() - 1)));
			long mask = (1L << bits) - 1;
			int volume = region.sx * region.sy * region.sz;
			if ((long) volume * bits > (long) data.length * 64) {
				throw new IOException("block data too short");
			}
			int ox = region.x - minX, oy = region.y - minY, oz = region.z - minZ;
			for (int y = 0; y < region.sy; y++) {
				for (int z = 0; z < region.sz; z++) {
					for (int x = 0; x < region.sx; x++) {
						int i = (y * region.sz + z) * region.sx + x;
						int value = (int) readPacked(data, i, bits, mask);
						int global = value < toGlobal.length ? toGlobal[value] : 0;
						if (global != 0) {
							blocks[((y + oy) * sizeZ + (z + oz)) * sizeX + (x + ox)] = (char) global;
						}
					}
				}
			}
		}

		String fileName = file.getFileName().toString().replaceFirst("(?i)\\.litematic$", "");
		// "Unnamed" is what the file gets when its maker never typed a name; the file name says more.
		String metaName = meta.getStringOr("Name", "").trim();
		String name = metaName.isEmpty() || metaName.equalsIgnoreCase("Unnamed") ? fileName : metaName;
		return new Litematic(file, name, meta.getStringOr("Author", ""), sizeX, sizeY, sizeZ,
			palette.toArray(new BlockState[0]), blocks);
	}

	/** Usually gzip-compressed, but some websites hand out plain (uncompressed) files. */
	private static CompoundTag readNbt(Path file) throws IOException {
		byte[] head = new byte[2];
		try (var in = Files.newInputStream(file)) {
			if (in.readNBytes(head, 0, 2) < 2) {
				throw new IOException("empty file");
			}
		}
		if ((head[0] & 0xFF) == 0x1F && (head[1] & 0xFF) == 0x8B) {
			return NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
		}
		try (var in = new java.io.DataInputStream(new java.io.BufferedInputStream(Files.newInputStream(file)))) {
			return NbtIo.read(in, NbtAccounter.unlimitedHeap());
		}
	}

	/** Entries are packed back to back and may be split over two longs. */
	private static long readPacked(long[] data, int index, int bits, long mask) {
		long start = (long) index * bits;
		int word = (int) (start >>> 6);
		int endWord = (int) ((start + bits - 1) >>> 6);
		int offset = (int) (start & 63);
		if (word == endWord) {
			return (data[word] >>> offset) & mask;
		}
		return ((data[word] >>> offset) | (data[endWord] << (64 - offset))) & mask;
	}

	private static BlockState readState(CompoundTag tag, int dataVersion, int currentVersion) {
		if (dataVersion > 0 && dataVersion < currentVersion) {
			// Old block names and properties (e.g. from 1.20) become today's.
			tag = (CompoundTag) DataFixers.getDataFixer()
				.update(References.BLOCK_STATE, new Dynamic<>(NbtOps.INSTANCE, tag), dataVersion, currentVersion)
				.getValue();
		}
		return NbtUtils.readBlockState(BuiltInRegistries.BLOCK, tag);
	}

	private record Region(CompoundTag tag, int x, int y, int z, int sx, int sy, int sz) {
	}

	// ------------------------------------------------------------------ writing (tests and examples)

	/** Writes a one-region .litematic; [states] is indexed like {@link #index}. */
	public static void write(Path file, String name, int sizeX, int sizeY, int sizeZ, BlockState[] states) throws IOException {
		List<BlockState> palette = new ArrayList<>();
		Map<BlockState, Integer> ids = new HashMap<>();
		palette.add(Blocks.AIR.defaultBlockState());
		ids.put(Blocks.AIR.defaultBlockState(), 0);
		int[] values = new int[states.length];
		for (int i = 0; i < states.length; i++) {
			BlockState s = states[i] == null ? Blocks.AIR.defaultBlockState() : states[i];
			values[i] = ids.computeIfAbsent(s, k -> {
				palette.add(k);
				return palette.size() - 1;
			});
		}
		int bits = Math.max(2, 32 - Integer.numberOfLeadingZeros(Math.max(1, palette.size() - 1)));
		long[] data = new long[(int) (((long) states.length * bits + 63) / 64)];
		for (int i = 0; i < values.length; i++) {
			long start = (long) i * bits;
			int word = (int) (start >>> 6);
			int offset = (int) (start & 63);
			data[word] |= (long) values[i] << offset;
			int endWord = (int) ((start + bits - 1) >>> 6);
			if (endWord != word) {
				data[endWord] |= (long) values[i] >>> (64 - offset);
			}
		}

		ListTag paletteTag = new ListTag();
		palette.forEach(s -> paletteTag.add(NbtUtils.writeBlockState(s)));
		CompoundTag region = new CompoundTag();
		region.put("Position", xyz(0, 0, 0));
		region.put("Size", xyz(sizeX, sizeY, sizeZ));
		region.put("BlockStatePalette", paletteTag);
		region.put("BlockStates", new LongArrayTag(data));
		region.put("TileEntities", new ListTag());
		region.put("Entities", new ListTag());
		CompoundTag regions = new CompoundTag();
		regions.put(name, region);

		CompoundTag meta = new CompoundTag();
		meta.putString("Name", name);
		meta.putString("Author", "DNZ");
		meta.put("EnclosingSize", xyz(sizeX, sizeY, sizeZ));
		meta.putInt("RegionCount", 1);
		meta.putLong("TotalVolume", states.length);

		CompoundTag root = new CompoundTag();
		root.putInt("Version", 6);
		root.putInt("MinecraftDataVersion", SharedConstants.getCurrentVersion().dataVersion().version());
		root.put("Metadata", meta);
		root.put("Regions", regions);
		Files.createDirectories(file.toAbsolutePath().getParent());
		NbtIo.writeCompressed(root, file);
	}

	private static CompoundTag xyz(int x, int y, int z) {
		CompoundTag t = new CompoundTag();
		t.putInt("x", x);
		t.putInt("y", y);
		t.putInt("z", z);
		return t;
	}
}
