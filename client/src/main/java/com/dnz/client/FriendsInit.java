package com.dnz.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/** DNZ friends: once a second, which friends are on the current server. */
public class FriendsInit implements ClientModInitializer {
	private int ticks;

	@Override
	public void onInitializeClient() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (++this.ticks % 20 == 0) {
				Friends.tick(client);
			}
		});
	}
}
