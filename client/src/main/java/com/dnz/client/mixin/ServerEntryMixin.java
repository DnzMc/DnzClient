package com.dnz.client.mixin;

import com.dnz.client.DnzConfig;
import com.dnz.client.L;
import com.dnz.client.gui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.client.gui.screens.FaviconTexture;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * DNZ server cards: each saved server becomes a rounded card with its icon, name, message of the day, the ping in
 * milliseconds (colored) and the player count. Vanilla still runs first (it starts the ping and loads the icon);
 * the card is drawn over it.
 */
@Mixin(targets = "net.minecraft.client.gui.screens.multiplayer.ServerSelectionList$OnlineServerEntry")
public abstract class ServerEntryMixin {
	private static final Identifier JOIN = Identifier.withDefaultNamespace("server_list/join");
	private static final Identifier JOIN_HIGHLIGHTED = Identifier.withDefaultNamespace("server_list/join_highlighted");

	@Shadow
	@Final
	private ServerData serverData;
	@Shadow
	@Final
	private FaviconTexture icon;

	@Shadow
	protected abstract void extractIcon(GuiGraphicsExtractor g, int x, int y, Identifier texture);

	@Inject(method = "extractContent", at = @At("TAIL"))
	private void dnz$card(GuiGraphicsExtractor g, int mouseX, int mouseY, boolean hovered, float a, CallbackInfo ci) {
		DnzConfig config = DnzConfig.get();
		if (!config.serverCards || config.javaStyle) {
			return;
		}
		ServerSelectionList.Entry self = (ServerSelectionList.Entry) (Object) this;
		boolean selected = ((SelectionEntryAccessor) self).dnz$list().getSelected() == self;
		Font font = Minecraft.getInstance().font;
		int x = self.getX();
		int y = self.getY();
		int w = self.getWidth();
		int h = self.getHeight();
		int accent = Theme.accentInfo().rgb();

		// Card (covers vanilla's row), accent edge when selected.
		int bg = hovered ? 0xF01D2230 : 0xF0141820;
		if (selected) {
			Theme.roundedRect(g, x - 1, y - 1, w + 2, h + 2, 5, 0xFF000000 | accent);
		}
		Theme.roundedRect(g, x, y, w, h, 4, bg);
		if (selected) {
			g.fill(x + 1, y + 4, x + 3, y + h - 4, 0xFF000000 | accent);
		}

		int cx = self.getContentX();
		int cy = self.getContentY();
		int right = self.getContentRight();
		this.extractIcon(g, cx, cy, this.icon.textureLocation());

		// Right side: ping and players.
		ServerData.State state = this.serverData.state();
		int pillRight = right - 2;
		Component ping;
		int pingColor;
		switch (state) {
			case SUCCESSFUL -> {
				long ms = this.serverData.ping;
				ping = Component.literal(ms + " ms");
				pingColor = ms < 80 ? 0x4CD97B : ms < 150 ? 0xFFD24A : ms < 300 ? 0xFF9F43 : 0xFF5A5A;
			}
			case INCOMPATIBLE -> {
				ping = this.serverData.version.copy();
				pingColor = 0xFF5A5A;
			}
			case UNREACHABLE -> {
				ping = Component.literal(L.t("servers.offline"));
				pingColor = 0xFF5A5A;
			}
			default -> {
				String dots = ".".repeat((int) (System.currentTimeMillis() / 350 % 4));
				ping = Component.literal(L.t("servers.pinging") + dots);
				pingColor = 0x9AA3B5;
			}
		}
		int pingW = pill(g, font, ping, pillRight, cy + 1, pingColor);
		int textRight = pillRight - pingW - 6;
		if (state == ServerData.State.SUCCESSFUL && this.serverData.players != null) {
			Component players = Component.literal("● " + L.t("servers.players", this.serverData.players.online(), this.serverData.players.max()));
			int playersW = pill(g, font, players, pillRight, cy + 15, 0xB0B8C8);
			textRight = Math.min(textRight, pillRight - playersW - 6);
		}

		// Name and message of the day.
		int textX = cx + 38;
		Theme.text(g, font, Component.literal(this.serverData.name), textX, cy + 2, 0xFFFFFFFF);
		List<FormattedCharSequence> motd = font.split(this.serverData.motd, Math.max(40, right - textX - 70));
		for (int i = 0; i < Math.min(2, motd.size()); i++) {
			g.text(font, motd.get(i), textX, cy + 13 + i * 10, 0xFFB0B8C8, false);
		}

		// Join arrow on the icon while hovering, like vanilla (click still works the same).
		if (hovered) {
			g.fill(cx, cy, cx + 32, cy + 32, 0xA0101018);
			boolean onIcon = mouseX >= cx && mouseX < cx + 32 && mouseY >= cy && mouseY < cy + 32;
			g.blitSprite(RenderPipelines.GUI_TEXTURED, onIcon ? JOIN_HIGHLIGHTED : JOIN, cx, cy, 32, 32);
		}
	}

	/** Small rounded label ending at [right]; returns its width. */
	private static int pill(GuiGraphicsExtractor g, Font font, Component text, int right, int y, int rgb) {
		int w = font.width(text) + 10;
		Theme.roundedRect(g, right - w, y, w, 12, 3, Theme.withAlpha(rgb, 0.16F));
		g.text(font, text, right - w + 5, y + 2, 0xFF000000 | rgb, false);
		return w;
	}
}
