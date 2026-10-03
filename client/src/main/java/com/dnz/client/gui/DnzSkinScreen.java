package com.dnz.client.gui;

import com.dnz.client.L;
import com.dnz.client.skin.SkinUploader;
import com.dnz.client.compat.Compat;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;

/** DNZ menu "Skin" tab: pick a PNG from the DNZ skins folder and upload it to the account. */
public class DnzSkinScreen extends DnzMenuScreen {
	private static final int PREVIEW_W = 110;

	private final List<Path> skins = new ArrayList<>();
	private Path selected;
	private boolean slim;
	private boolean busy;
	private int page;
	private int perPage = 6;
	private Component status = Component.literal(L.t("skin.hint"));
	private int statusColor = Theme.MUTED;

	public DnzSkinScreen(Screen parent) {
		super(Tab.SKIN, parent);
	}

	public static Path skinsFolder() {
		Path dir = FabricLoader.getInstance().getGameDir().resolve("dnzclient").resolve("skins");
		try {
			Files.createDirectories(dir);
		} catch (IOException ignored) {
		}
		return dir;
	}

	private int listX() {
		return this.cx + PREVIEW_W + 8;
	}

	private int listW() {
		return this.cw - PREVIEW_W - 8;
	}

	private void reloadSkins() {
		this.skins.clear();
		try (Stream<Path> files = Files.list(skinsFolder())) {
			files.filter(p -> p.getFileName().toString().toLowerCase().endsWith(".png")).sorted().forEach(this.skins::add);
		} catch (IOException ignored) {
		}
		if (this.selected != null && !this.skins.contains(this.selected)) {
			this.selected = null;
		}
		int pages = Math.max(1, (this.skins.size() + this.perPage - 1) / this.perPage);
		this.page = Math.min(this.page, pages - 1);
	}

	@Override
	protected void initContent() {
		this.perPage = Math.max(3, (this.ch - 82) / 21);
		this.reloadSkins();

		int listX = this.listX();
		int listW = this.listW();
		int y = this.cy + 14;

		int from = this.page * this.perPage;
		for (int i = from; i < Math.min(this.skins.size(), from + this.perPage); i++) {
			Path skin = this.skins.get(i);
			String name = skin.getFileName().toString();
			name = name.substring(0, name.length() - 4);
			if (this.font.width(name) > listW - 16) {
				name = this.font.plainSubstrByWidth(name, listW - 24) + "...";
			}
			this.addRenderableWidget(new DnzButton(listX, y, listW, 18, Component.literal(name), () -> {
				this.selected = skin;
				this.rebuildWidgets();
			}).selected(skin.equals(this.selected)));
			y += 21;
		}

		int navY = this.cy + 14 + this.perPage * 21;
		int pages = Math.max(1, (this.skins.size() + this.perPage - 1) / this.perPage);
		if (pages > 1) {
			DnzButton prev = new DnzButton(listX, navY, 40, 16, Component.literal("<"), () -> {
				this.page--;
				this.rebuildWidgets();
			});
			prev.active = this.page > 0;
			DnzButton next = new DnzButton(listX + listW - 40, navY, 40, 16, Component.literal(">"), () -> {
				this.page++;
				this.rebuildWidgets();
			});
			next.active = this.page < pages - 1;
			this.addRenderableWidget(prev);
			this.addRenderableWidget(next);
		}

		int rowY = this.cy + this.ch - 46;
		int third = (listW - 8) / 3;
		this.addRenderableWidget(new DnzButton(listX, rowY, third, 20, Component.literal(L.t("open_folder")),
			() -> Compat.openFolder(skinsFolder())));
		this.addRenderableWidget(new DnzButton(listX + third + 4, rowY, third, 20, Component.literal(L.t("refresh")),
			this::rebuildWidgets));
		this.addRenderableWidget(new DnzButton(listX + 2 * (third + 4), rowY, listW - 2 * (third + 4), 20,
			Component.literal(this.slim ? L.t("skin.model_slim") : L.t("skin.model_classic")), () -> {
				this.slim = !this.slim;
				this.rebuildWidgets();
			}));

		DnzButton apply = new DnzButton(listX, this.cy + this.ch - 20, listW, 20,
			Component.literal(this.busy ? L.t("skin.uploading_label") : L.t("skin.apply")), this::applySkin).selected(true);
		apply.active = this.selected != null && !this.busy;
		this.addRenderableWidget(apply);
	}

	private void applySkin() {
		if (this.selected == null || this.busy) {
			return;
		}
		this.busy = true;
		this.setStatus(L.t("skin.uploading"), Theme.accent());
		this.rebuildWidgets();
		SkinUploader.upload(this.minecraft.getUser().getAccessToken(), this.selected, this.slim).thenAccept(error ->
			this.minecraft.execute(() -> {
				this.busy = false;
				if (error == null) {
					this.setStatus(L.t("skin.done"), 0xFF7CE38B);
				} else {
					this.setStatus(error, 0xFFFF6B6B);
				}
				this.rebuildWidgets();
			}));
	}

	private void setStatus(String text, int color) {
		this.status = Component.literal(text);
		this.statusColor = color;
	}

	@Override
	public void onFilesDrop(List<Path> files) {
		Path last = null;
		for (Path file : files) {
			if (!file.getFileName().toString().toLowerCase().endsWith(".png")) {
				continue;
			}
			try {
				Path target = skinsFolder().resolve(file.getFileName().toString());
				Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
				last = target;
			} catch (IOException e) {
				this.setStatus(L.t("skin.copy_error", e.getMessage()), 0xFFFF6B6B);
			}
		}
		if (last != null) {
			this.reloadSkins();
			this.selected = last;
			this.page = Math.max(0, this.skins.indexOf(last)) / this.perPage;
			this.setStatus(L.t("skin.added"), 0xFF7CE38B);
		} else if (!files.isEmpty()) {
			this.setStatus(L.t("skin.png_only"), 0xFFFF6B6B);
		}
		this.rebuildWidgets();
	}

	@Override
	protected void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		// Preview box
		int boxX = this.cx;
		int boxY = this.cy;
		int boxBottom = this.cy + this.ch;
		g.fill(boxX, boxY, boxX + PREVIEW_W, boxBottom, 0x50000000);
		if (this.minecraft.player != null) {
			int size = Math.max(30, Math.min(60, (boxBottom - boxY - 16) / 2));
			// Minecraft draws the player without the menu's scale: hand it the box and the mouse in real GUI units.
			org.joml.Vector2f from = toGui(g, boxX, boxY + 4);
			org.joml.Vector2f to = toGui(g, boxX + PREVIEW_W, boxBottom - 4);
			org.joml.Vector2f mouse = toGui(g, mouseX, mouseY);
			float k = (to.x - from.x) / PREVIEW_W;
			g.pose().pushMatrix();
			g.pose().identity();
			InventoryScreen.extractEntityInInventoryFollowsMouse(g, Math.round(from.x), Math.round(from.y), Math.round(to.x), Math.round(to.y),
				Math.max(1, Math.round(size * k)), 0.0625F, mouse.x, mouse.y, this.minecraft.player);
			g.pose().popMatrix();
		} else {
			int mid = boxY + (boxBottom - boxY) / 2;
			g.centeredText(this.font, Component.literal(L.t("skin.preview1")), boxX + PREVIEW_W / 2, mid - 6, Theme.MUTED);
			g.centeredText(this.font, Component.literal(L.t("skin.preview2")), boxX + PREVIEW_W / 2, mid + 6, Theme.MUTED);
		}

		g.text(this.font, Component.literal(L.t("skin.yours")), this.listX(), this.cy + 2, 0xFFFFFFFF);
		if (this.skins.isEmpty()) {
			g.textWithWordWrap(this.font, Component.literal(L.t("skin.empty")), this.listX(), this.cy + 18, this.listW(), Theme.MUTED);
		}
	}

	@Override
	protected void extractOverlay(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		g.centeredText(this.font, this.status, this.width / 2, this.py + this.panelH + 6, this.statusColor);
	}
}
