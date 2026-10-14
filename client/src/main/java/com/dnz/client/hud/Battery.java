package com.dnz.client.hud;

import com.dnz.client.L;
import oshi.SystemInfo;
import oshi.hardware.PowerSource;

/** Battery module: the laptop's charge, read in the background every 30 seconds (reading it can take a moment). */
final class Battery {
	private static volatile String text = "-";
	private static volatile long readAt;
	private static volatile boolean reading;

	private Battery() {
	}

	static String text() {
		long now = System.currentTimeMillis();
		if (!reading && now - readAt > 30_000) {
			reading = true;
			readAt = now;
			Thread.ofVirtual().name("DNZ Battery").start(() -> {
				try {
					text = read();
				} catch (Throwable e) {
					text = "-";
				} finally {
					reading = false;
				}
			});
		}
		return text;
	}

	private static String read() {
		for (PowerSource p : new SystemInfo().getHardware().getPowerSources()) {
			double charge = p.getRemainingCapacityPercent();
			if (p.getMaxCapacity() <= 0 || charge < 0) {
				continue;
			}
			String percent = Math.round(charge * 100) + "%";
			return p.isCharging() || p.isPowerOnLine() && charge < 0.995 ? percent + "  " + L.t("hud.battery.plugged") : percent;
		}
		return L.t("hud.battery.none");
	}
}
