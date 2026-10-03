package com.dnz.client.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** DNZ menu "Feedback" page: a short note and a button to our website. */
public class DnzFeedbackTab extends DnzMenuScreen {
	private static final String SITE = "https://dnzclient.com";

	public DnzFeedbackTab(Screen parent) {
		super(Tab.FEEDBACK, parent);
	}

	@Override
	protected void initContent() {
		int w = Math.min(160, this.cw);
		this.addRenderableWidget(new DnzButton(this.cx + (this.cw - w) / 2, this.cy + this.ch / 2 + 14, w, 20,
			Component.literal("Open Website"), () -> ConfirmLinkScreen.confirmLinkNow(this, java.net.URI.create(SITE))).selected(true));
	}

	@Override
	protected void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		int mid = this.cx + this.cw / 2;
		int y = this.cy + this.ch / 2 - 34;
		g.pose().pushMatrix();
		g.pose().translate(mid, y);
		g.pose().scale(1.2F, 1.2F);
		g.centeredText(this.font, bold("We'd love to hear from you"), 0, 0, 0xFFFFFFFF);
		g.pose().popMatrix();
		g.centeredText(this.font, Theme.smooth("Found a bug or have an idea for DNZ Client?"), mid, y + 18, 0xFFB4BCC9);
		g.centeredText(this.font, Theme.smooth("Tell us on our website."), mid, y + 29, 0xFFB4BCC9);
	}
}
