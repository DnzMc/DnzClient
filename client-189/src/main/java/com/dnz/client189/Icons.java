package com.dnz.client189;

import java.util.HashMap;
import java.util.Map;

/** Material icon code points, the same as DNZ Client 26.x (font icons.ttf). */
public final class Icons {
	public static final char EDIT_HUD = '';
	public static final char SEARCH = '';
	public static final char MODS = '';
	public static final char PROFILES = '';
	public static final char KEYBINDS = '';
	public static final char THEMES = '';
	public static final char PREFERENCES = '';
	public static final char CLOSE = '';

	private static final Map<String, Character> MODULES = new HashMap<String, Character>();

	static {
		String table = "fps e9e4 ping e63e cps e323 coords e55c armor e9e0 effects ea4b compass e87a biome ea63 clock efd6 "
			+ "gametime e430 speed e566 memory e322 server e875 players ea21 light e90f session e425 keystrokes e312 "
			+ "reach e41c combo ea0b target e55c togglesprint e566 helditem e925 pots e544 gapples e87e pearls e837 "
			+ "arrows f1e1 foodtooltip e88e zoom e8ff fullbright e1ac streamer e04b engine eb9b analogclock e8b5 "
			+ "blockabove e25a block efc9 chunks e3ec crosshair e762 customtext e262 date e935 dimension ea07 "
			+ "direction e55d discord e8af durability f8cd entities e91d frametime e922 hitboxes e835 inventory f19c "
			+ "itemcount e242 lookat e8f4 playerhead f008 rotation e84d saturation e561 tps e01b xp e8d0 "
			+ "daycount e935 weather f172 stats f889 digitalclock efd6 dnzbadge ef76 blockoutline e3c2 chatheads e87c";
		String[] parts = table.split(" ");
		for (int i = 0; i + 1 < parts.length; i += 2) {
			MODULES.put(parts[i], (char) Integer.parseInt(parts[i + 1], 16));
		}
	}

	private Icons() {
	}

	public static char of(String id) {
		Character c = MODULES.get(id);
		return c == null ? MODS : c;
	}
}
