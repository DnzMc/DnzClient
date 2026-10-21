package com.dnz.client189;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MathHelper;
import org.lwjgl.input.Mouse;

/** All DNZ modules for 1.8.9. */
public final class Modules {
	public static final List<Module> ALL = new ArrayList<Module>();

	/** Mouse clicks of the last second, for the CPS module. */
	static final LinkedList<Long> LEFT_CLICKS = new LinkedList<Long>();
	static final LinkedList<Long> RIGHT_CLICKS = new LinkedList<Long>();

	private Modules() {
	}

	public static Module get(String id) {
		for (Module m : ALL) {
			if (m.id.equals(id)) {
				return m;
			}
		}
		return null;
	}

	static int cps(LinkedList<Long> clicks) {
		long now = System.currentTimeMillis();
		Iterator<Long> it = clicks.iterator();
		while (it.hasNext()) {
			if (now - it.next() > 1000) {
				it.remove();
			}
		}
		return clicks.size();
	}

	static int[] textBox(Minecraft mc, String label, String value) {
		float size = 8;
		float labelW = label.isEmpty() ? 0 : TtfFont.BOLD.width(label + " ", size);
		int w = Math.round(labelW + TtfFont.MEDIUM.width(value, size)) + 12;
		int h = 16;
		Render2D.roundedRect(0, 0, w, h, 5, 0xA60A0D12);
		if (!label.isEmpty()) {
			TtfFont.BOLD.draw(label, 6, 4, size, Render2D.ORANGE);
		}
		TtfFont.MEDIUM.draw(value, 6 + labelW, 4, size, Render2D.TEXT);
		return new int[] {w, h};
	}

	static {
		ALL.add(new Module("fps", "FPS", "FPS", true, true, 0.01F, 0.02F) {
			@Override
			public int[] render(Minecraft mc, boolean editor) {
				return textBox(mc, "FPS", String.valueOf(Minecraft.getDebugFPS()));
			}
		});
		ALL.add(new Module("cps", "CPS", "CPS", true, true, 0.01F, 0.09F) {
			@Override
			public int[] render(Minecraft mc, boolean editor) {
				return textBox(mc, "CPS", cps(LEFT_CLICKS) + " | " + cps(RIGHT_CLICKS));
			}
		});
		ALL.add(new Module("ping", "Ping", "Ping", true, true, 0.01F, 0.16F) {
			@Override
			public int[] render(Minecraft mc, boolean editor) {
				int ping = -1;
				if (mc.thePlayer != null && mc.getNetHandler() != null) {
					NetworkPlayerInfo info = mc.getNetHandler().getPlayerInfo(mc.thePlayer.getUniqueID());
					if (info != null) {
						ping = info.getResponseTime();
					}
				}
				return textBox(mc, "Ping", ping < 0 ? "-" : ping + " ms");
			}
		});
		ALL.add(new Module("coords", "Koordinatlar", "Coordinates", true, true, 0.01F, 0.23F) {
			@Override
			public int[] render(Minecraft mc, boolean editor) {
				// Streamer mode hides the coordinates (outside the HUD editor)
				Module streamer = Modules.get("streamer");
				if (!editor && streamer != null && streamer.enabled()) {
					return new int[] {0, 0};
				}
				if (mc.thePlayer == null) {
					return textBox(mc, "XYZ", "0 64 0");
				}
				String[] dirs = {"S", "W", "N", "E"};
				int facing = MathHelper.floor_double(mc.thePlayer.rotationYaw * 4.0F / 360.0F + 0.5D) & 3;
				return textBox(mc, "XYZ", String.format("%d %d %d  %s", MathHelper.floor_double(mc.thePlayer.posX),
					MathHelper.floor_double(mc.thePlayer.posY), MathHelper.floor_double(mc.thePlayer.posZ), dirs[facing]));
			}
		});
		ALL.add(new Module("keystrokes", "Tuş Göstergesi", "Keystrokes", true, true, 0.01F, 0.31F) {
			@Override
			public int[] render(Minecraft mc, boolean editor) {
				int s = 22;
				int g = 2;
				key(mc, "W", s + g, 0, s, s, mc.gameSettings.keyBindForward.isKeyDown());
				key(mc, "A", 0, s + g, s, s, mc.gameSettings.keyBindLeft.isKeyDown());
				key(mc, "S", s + g, s + g, s, s, mc.gameSettings.keyBindBack.isKeyDown());
				key(mc, "D", (s + g) * 2, s + g, s, s, mc.gameSettings.keyBindRight.isKeyDown());
				int full = s * 3 + g * 2;
				int half = (full - g) / 2;
				key(mc, "LMB " + cps(LEFT_CLICKS), 0, (s + g) * 2, half, s, Mouse.isButtonDown(0));
				key(mc, "RMB " + cps(RIGHT_CLICKS), half + g, (s + g) * 2, full - half - g, s, Mouse.isButtonDown(1));
				key(mc, "—", 0, (s + g) * 3, full, 12, mc.gameSettings.keyBindJump.isKeyDown());
				return new int[] {full, (s + g) * 3 + 12};
			}

			private void key(Minecraft mc, String text, int x, int y, int w, int h, boolean down) {
				Render2D.roundedRect(x, y, w, h, 5, down ? 0xE6FF8A3D : 0xA60A0D12);
				float size = h < 16 ? 6 : 8;
				TtfFont.BOLD.drawCentered(text, x + w / 2.0F, y + (h - size) / 2.0F, size, down ? 0xFF0A0D12 : Render2D.TEXT);
			}
		});
		ALL.add(new Module("armor", "Zırh", "Armor", true, true, 0.88F, 0.70F) {
			@Override
			public int[] render(Minecraft mc, boolean editor) {
				int rows = 0;
				int width = 0;
				if (mc.thePlayer != null) {
					RenderHelper.enableGUIStandardItemLighting();
					for (int i = 3; i >= 0; i--) {
						ItemStack stack = mc.thePlayer.inventory.armorInventory[i];
						if (stack == null) {
							continue;
						}
						int y = rows * 18;
						mc.getRenderItem().renderItemAndEffectIntoGUI(stack, 0, y);
						if (stack.isItemStackDamageable()) {
							int left = stack.getMaxDamage() - stack.getItemDamage();
							float part = (float) left / stack.getMaxDamage();
							int color = part > 0.5F ? 0xFF55FF55 : part > 0.2F ? 0xFFFFC83D : 0xFFFF5555;
							TtfFont.BOLD.draw(String.valueOf(left), 20, y + 4, 8, color);
							width = Math.max(width, 20 + Math.round(TtfFont.BOLD.width(String.valueOf(left), 8)));
						}
						rows++;
					}
					RenderHelper.disableStandardItemLighting();
					GlStateManager.disableLighting();
				}
				if (rows == 0 && editor) {
					return textBox(mc, "", Lang.t("Zırh", "Armor"));
				}
				return new int[] {Math.max(width, 16), rows * 18};
			}
		});
		ALL.add(new Module("clock", "Saat", "Clock", true, false, 0.90F, 0.02F) {
			@Override
			public int[] render(Minecraft mc, boolean editor) {
				return textBox(mc, "", new java.text.SimpleDateFormat("HH:mm").format(new java.util.Date()));
			}
		});
		ALL.add(new Module("togglesprint", "Otomatik Koşma", "Toggle Sprint", false, false, 0, 0) {
			private boolean held;

			@Override
			public void tick(Minecraft mc) {
				if (this.enabled() && mc.thePlayer != null) {
					KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), true);
					this.held = true;
				} else if (this.held) {
					KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), false);
					this.held = false;
				}
			}
		});
		ALL.add(new Module("fullbright", "Tam Parlaklık", "Fullbright", false, false, 0, 0) {
			private float before = -1;

			@Override
			public void tick(Minecraft mc) {
				if (this.enabled()) {
					if (mc.gameSettings.gammaSetting < 10) {
						this.before = mc.gameSettings.gammaSetting;
						mc.gameSettings.gammaSetting = 100;
					}
				} else if (this.before >= 0) {
					mc.gameSettings.gammaSetting = this.before;
					this.before = -1;
				}
			}
		});
		ALL.add(new Module("engine", "DNZ Engine (FPS)", "DNZ Engine (FPS)", false, true, 0, 0) {
			@Override
			protected void onToggle(boolean on) {
				// the chunks are built again for the other drawing path
				Minecraft mc = Minecraft.getMinecraft();
				if (mc.renderGlobal != null && mc.theWorld != null) {
					mc.renderGlobal.loadRenderers();
				}
			}
		});
		ALL.add(new Module("zoom", "Yakınlaştırma (C)", "Zoom (C)", false, true, 0, 0) {
		});
		ExtraModules.register();
	}
}
