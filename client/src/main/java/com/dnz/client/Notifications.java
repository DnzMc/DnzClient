package com.dnz.client;

import java.util.ArrayList;
import java.util.List;

/** DNZ notifications of this game session (friends joining or leaving...), shown on the DNZ title screen. */
public final class Notifications {
	public record Entry(String title, String text, long time) {
	}

	private static final List<Entry> ENTRIES = new ArrayList<>();

	private Notifications() {
	}

	public static synchronized void add(String title, String text) {
		ENTRIES.add(new Entry(title, text, System.currentTimeMillis()));
		if (ENTRIES.size() > 20) {
			ENTRIES.remove(0);
		}
	}

	/** The newest [count], newest first. */
	public static synchronized List<Entry> latest(int count) {
		List<Entry> out = new ArrayList<>();
		for (int i = ENTRIES.size() - 1; i >= 0 && out.size() < count; i--) {
			out.add(ENTRIES.get(i));
		}
		return out;
	}
}
