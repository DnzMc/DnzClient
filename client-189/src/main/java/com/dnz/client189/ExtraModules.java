package com.dnz.client189;

import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;

/**
 * The rest of DNZ Client's modules on 1.8.9, so it has the same list as 26.x (modules that need something
 * 1.8.9 does not have - totems, shulker boxes, shields, shader packs - are left out).
 */
final class ExtraModules {
	private ExtraModules() {
	}

	private interface Value {
		String get(Minecraft mc);
	}

	/** Start places of the text modules: columns of ten, so modules turned on together don't overlap. */
	private static int placed;

	private static Module text(String id, String tr, String en, boolean on, float unused, final String label, final Value value) {
		int n = placed++;
		return new Module(id, tr, en, true, on, 0.01F + (n / 10) * 0.16F, 0.36F + (n % 10) * 0.055F) {
			@Override
			public int[] render(Minecraft mc, boolean editor) {
				String v;
				try {
					v = mc.thePlayer == null && !editor ? "-" : value.get(mc);
				} catch (Exception e) {
					v = "-";
				}
				return Modules.textBox(mc, label, v == null ? "-" : v);
			}
		};
	}

	private static int count(Minecraft mc, Item item, int meta) {
		if (mc.thePlayer == null) {
			return 0;
		}
		int n = 0;
		for (ItemStack s : mc.thePlayer.inventory.mainInventory) {
			if (s != null && s.getItem() == item && (meta < 0 || s.getMetadata() == meta)) {
				n += s.stackSize;
			}
		}
		return n;
	}

	private static String two(int v) {
		return v < 10 ? "0" + v : String.valueOf(v);
	}

	private static String facing(Minecraft mc) {
		String[] tr = {"G", "B", "K", "D"};
		String[] en = {"S", "W", "N", "E"};
		int f = MathHelper.floor_double(mc.thePlayer.rotationYaw * 4.0F / 360.0F + 0.5D) & 3;
		return Lang.tr() ? tr[f] : en[f];
	}

	static void register() {
		float y = 0.40F;
		Modules.ALL.add(text("biome", "Biyom", "Biome", false, y, Lang.t("Biyom", "Biome"),
			mc -> mc.theWorld.getBiomeGenForCoords(new BlockPos(mc.thePlayer)).biomeName));
		Modules.ALL.add(text("gametime", "Oyun Zamanı", "Game Time", false, y, "", mc -> {
			long t = (mc.theWorld.getWorldTime() + 6000) % 24000;
			return two((int) (t / 1000)) + ":" + two((int) (t % 1000 * 60 / 1000));
		}));
		Modules.ALL.add(text("speed", "Hız", "Speed", false, y, Lang.t("Hız", "Speed"), mc -> {
			double dx = mc.thePlayer.posX - mc.thePlayer.prevPosX;
			double dz = mc.thePlayer.posZ - mc.thePlayer.prevPosZ;
			return String.format("%.1f m/s", Math.sqrt(dx * dx + dz * dz) * 20);
		}));
		Modules.ALL.add(text("memory", "RAM", "Memory", false, y, "RAM", mc -> {
			Runtime r = Runtime.getRuntime();
			return (r.totalMemory() - r.freeMemory()) * 100 / r.maxMemory() + "%";
		}));
		Modules.ALL.add(text("server", "Sunucu", "Server", false, y, "", mc ->
			mc.getCurrentServerData() != null ? mc.getCurrentServerData().serverIP : Lang.t("Tek oyunculu", "Singleplayer")));
		Modules.ALL.add(text("players", "Oyuncular", "Players", false, y, Lang.t("Oyuncu", "Players"), mc -> {
			Collection<?> list = mc.getNetHandler() == null ? null : mc.getNetHandler().getPlayerInfoMap();
			return list == null ? "1" : String.valueOf(list.size());
		}));
		Modules.ALL.add(text("light", "Işık", "Light", false, y, Lang.t("Işık", "Light"), mc ->
			String.valueOf(mc.theWorld.getLight(new BlockPos(mc.thePlayer)))));
		Modules.ALL.add(text("direction", "Yön", "Direction", false, y, Lang.t("Yön", "Facing"), ExtraModules::facing));
		Modules.ALL.add(text("rotation", "Bakış Açısı", "Rotation", false, y, "", mc ->
			String.format("%.1f / %.1f", MathHelper.wrapAngleTo180_float(mc.thePlayer.rotationYaw), mc.thePlayer.rotationPitch)));
		Modules.ALL.add(text("dimension", "Boyut", "Dimension", false, y, "", mc -> {
			int d = mc.thePlayer.dimension;
			return d == -1 ? "Nether" : d == 1 ? "End" : Lang.t("Dünya", "Overworld");
		}));
		Modules.ALL.add(text("tps", "TPS", "TPS", false, y, "TPS", mc -> String.format("%.1f", Combat.tps())));
		Modules.ALL.add(text("frametime", "Kare Süresi", "Frame Time", false, y, "", mc ->
			String.format("%.1f ms", 1000.0 / Math.max(1, Minecraft.getDebugFPS()))));
		Modules.ALL.add(text("entities", "Varlıklar", "Entities", false, y, Lang.t("Varlık", "Entities"), mc ->
			String.valueOf(mc.theWorld.loadedEntityList.size())));
		Modules.ALL.add(text("chunks", "Chunklar", "Chunks", false, y, "", mc -> mc.renderGlobal.getDebugInfoRenders().split(" ")[1]));
		Modules.ALL.add(text("date", "Tarih", "Date", false, y, "", mc -> new SimpleDateFormat("dd.MM.yyyy").format(new Date())));
		Modules.ALL.add(text("digitalclock", "Dijital Saat", "Digital Clock", false, y, "", mc -> new SimpleDateFormat("HH:mm:ss").format(new Date())));
		Modules.ALL.add(text("daycount", "Gün", "Day", false, y, Lang.t("Gün", "Day"), mc -> String.valueOf(mc.theWorld.getWorldTime() / 24000)));
		Modules.ALL.add(text("weather", "Hava Durumu", "Weather", false, y, "", mc ->
			mc.theWorld.isThundering() ? Lang.t("Fırtına", "Thunder") : mc.theWorld.isRaining() ? Lang.t("Yağmur", "Rain") : Lang.t("Açık", "Clear")));
		Modules.ALL.add(text("xp", "XP Seviyesi", "XP Level", false, y, "XP", mc -> String.valueOf(mc.thePlayer.experienceLevel)));
		Modules.ALL.add(text("saturation", "Doygunluk", "Saturation", false, y, Lang.t("Doygunluk", "Saturation"), mc ->
			String.format("%.1f", mc.thePlayer.getFoodStats().getSaturationLevel())));
		Modules.ALL.add(text("block", "Blok", "Block", false, y, "", mc -> {
			MovingObjectPosition hit = mc.objectMouseOver;
			if (hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) {
				return "-";
			}
			return mc.theWorld.getBlockState(hit.getBlockPos()).getBlock().getLocalizedName();
		}));
		Modules.ALL.add(text("lookat", "Baktığın Şey", "Looking At", false, y, "", mc -> {
			MovingObjectPosition hit = mc.objectMouseOver;
			if (hit == null) {
				return "-";
			}
			if (hit.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY && hit.entityHit != null) {
				return hit.entityHit.getName();
			}
			if (hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
				return mc.theWorld.getBlockState(hit.getBlockPos()).getBlock().getLocalizedName();
			}
			return "-";
		}));
		Modules.ALL.add(text("blockabove", "Üstteki Blok", "Block Above", false, y, Lang.t("Tavan", "Above"), mc -> {
			BlockPos head = new BlockPos(mc.thePlayer).up(2);
			for (int i = 0; i < 10; i++) {
				Block b = mc.theWorld.getBlockState(head.up(i)).getBlock();
				if (b.getMaterial().isSolid()) {
					return (i + 1) + "";
				}
			}
			return "-";
		}));
		Modules.ALL.add(text("helditem", "Eldeki Eşya", "Held Item", false, y, "", mc -> {
			ItemStack s = mc.thePlayer.getHeldItem();
			return s == null ? "-" : s.getDisplayName();
		}));
		Modules.ALL.add(text("durability", "Dayanıklılık", "Durability", false, y, Lang.t("Dayanıklılık", "Durability"), mc -> {
			ItemStack s = mc.thePlayer.getHeldItem();
			return s == null || !s.isItemStackDamageable() ? "-" : String.valueOf(s.getMaxDamage() - s.getItemDamage());
		}));
		Modules.ALL.add(text("itemcount", "Eşya Sayısı", "Item Count", false, y, Lang.t("Adet", "Count"), mc -> {
			ItemStack s = mc.thePlayer.getHeldItem();
			return s == null ? "-" : String.valueOf(count(mc, s.getItem(), s.getMetadata()));
		}));
		Modules.ALL.add(text("pots", "İksir Sayacı", "Potion Counter", false, y, Lang.t("İksir", "Pots"), mc -> {
			int n = 0;
			for (ItemStack s : mc.thePlayer.inventory.mainInventory) {
				if (s != null && s.getItem() == Items.potionitem && ItemPotion.isSplash(s.getMetadata())) {
					n += s.stackSize;
				}
			}
			return String.valueOf(n);
		}));
		Modules.ALL.add(text("gapples", "Altın Elma Sayacı", "Golden Apple Counter", false, y, Lang.t("Elma", "Gapples"), mc -> String.valueOf(count(mc, Items.golden_apple, -1))));
		Modules.ALL.add(text("pearls", "İnci Sayacı", "Pearl Counter", false, y, Lang.t("İnci", "Pearls"), mc -> String.valueOf(count(mc, Items.ender_pearl, -1))));
		Modules.ALL.add(text("arrows", "Ok Sayacı", "Arrow Counter", false, y, Lang.t("Ok", "Arrows"), mc -> String.valueOf(count(mc, Items.arrow, -1))));
		Modules.ALL.add(text("reach", "Menzil", "Reach", false, y, Lang.t("Menzil", "Reach"), mc -> Combat.reach()));
		Modules.ALL.add(text("combo", "Kombo", "Combo", false, y, Lang.t("Kombo", "Combo"), mc -> String.valueOf(Combat.combo())));
		Modules.ALL.add(text("target", "Hedef Bilgisi", "Target", false, y, "", mc -> Combat.target()));
		Modules.ALL.add(text("session", "Oturum", "Session", false, y, Lang.t("Oturum", "Session"), mc -> Combat.sessionTime()));
		Modules.ALL.add(text("stats", "Oyun İstatistikleri", "Game Stats", false, y, "K/D", mc -> Combat.kills() + " / " + Combat.deaths()));
		Modules.ALL.add(text("customtext", "Özel Metin", "Custom Text", false, y, "", mc -> {
			Module m = Modules.get("customtext");
			String v = m == null ? "" : m.state().value;
			return v == null || v.isEmpty() ? "DNZ Client" : v;
		}));

		Modules.ALL.add(new Module("compass", "Pusula", "Compass", true, false, 0.42F, 0.02F) {
			@Override
			public int[] render(Minecraft mc, boolean editor) {
				int w = 150;
				int h = 16;
				Render2D.roundedRect(0, 0, w, h, 5, 0xA60A0D12);
				float yaw = mc.thePlayer == null ? 180 : MathHelper.wrapAngleTo180_float(mc.thePlayer.rotationYaw) + 180;
				String[] names = Lang.tr() ? new String[] {"K", "KD", "D", "GD", "G", "GB", "B", "KB"} : new String[] {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
				for (int i = 0; i < 8; i++) {
					float angle = i * 45 - yaw;
					while (angle < -180) angle += 360;
					while (angle > 180) angle -= 360;
					if (Math.abs(angle) > 70) {
						continue;
					}
					float x = w / 2F + angle * (w / 2F - 10) / 70F;
					float size = i % 2 == 0 ? 8 : 6;
					TtfFont.BOLD.drawCentered(names[i], x, (h - size) / 2F, size, i == 0 ? Render2D.ORANGE : Render2D.TEXT);
				}
				Render2D.roundedRect(w / 2F - 0.5F, 1, 1, 3, 0.5F, Render2D.ORANGE);
				return new int[] {w, h};
			}
		});
		Modules.ALL.add(new Module("effects", "İksirler", "Potion Effects", true, false, 0.01F, 0.62F) {
			@Override
			public int[] render(Minecraft mc, boolean editor) {
				Collection<PotionEffect> list = mc.thePlayer == null ? null : mc.thePlayer.getActivePotionEffects();
				if (list == null || list.isEmpty()) {
					return editor ? Modules.textBox(mc, "", Lang.t("İksirler", "Effects")) : new int[] {0, 0};
				}
				int y = 0;
				int w = 0;
				for (PotionEffect e : list) {
					String name = I18n.format(e.getEffectName());
					if (e.getAmplifier() > 0) {
						name += " " + (e.getAmplifier() + 1);
					}
					GlStateManager.pushMatrix();
					GlStateManager.translate(0, y, 0);
					int[] size = Modules.textBox(mc, name, Potion.getDurationString(e));
					GlStateManager.popMatrix();
					w = Math.max(w, size[0]);
					y += size[1] + 2;
				}
				return new int[] {w, y - 2};
			}
		});
		Modules.ALL.add(new Module("inventory", "Envanter", "Inventory", true, false, 0.36F, 0.80F) {
			@Override
			public int[] render(Minecraft mc, boolean editor) {
				int w = 9 * 18 + 4;
				int h = 3 * 18 + 4;
				Render2D.roundedRect(0, 0, w, h, 5, 0xA60A0D12);
				if (mc.thePlayer != null) {
					RenderHelper.enableGUIStandardItemLighting();
					for (int i = 9; i < 36; i++) {
						ItemStack s = mc.thePlayer.inventory.mainInventory[i];
						if (s != null) {
							int x = 2 + (i % 9) * 18 + 1;
							int y = 2 + (i / 9 - 1) * 18 + 1;
							mc.getRenderItem().renderItemAndEffectIntoGUI(s, x, y);
							mc.getRenderItem().renderItemOverlays(mc.fontRendererObj, s, x, y);
						}
					}
					RenderHelper.disableStandardItemLighting();
					GlStateManager.disableLighting();
				}
				return new int[] {w, h};
			}
		});
		Modules.ALL.add(new Module("playerhead", "Oyuncu Kafası", "Player Head", true, false, 0.01F, 0.52F) {
			@Override
			public int[] render(Minecraft mc, boolean editor) {
				if (mc.thePlayer == null) {
					Render2D.roundedRect(0, 0, 24, 24, 4, Render2D.ORANGE);
					return new int[] {24, 24};
				}
				mc.getTextureManager().bindTexture(mc.thePlayer.getLocationSkin());
				GlStateManager.enableBlend();
				GlStateManager.color(1, 1, 1, 1);
				net.minecraft.client.gui.Gui.drawScaledCustomSizeModalRect(0, 0, 8, 8, 8, 8, 24, 24, 64, 64);
				net.minecraft.client.gui.Gui.drawScaledCustomSizeModalRect(0, 0, 40, 8, 8, 8, 24, 24, 64, 64);
				return new int[] {24, 24};
			}
		});
		Modules.ALL.add(new Module("analogclock", "Analog Saat", "Analog Clock", true, false, 0.90F, 0.10F) {
			@Override
			public int[] render(Minecraft mc, boolean editor) {
				int s = 40;
				float c = s / 2F;
				Render2D.circle(c, c, c, 0xA60A0D12);
				java.util.Calendar now = java.util.Calendar.getInstance();
				hand(c, (now.get(java.util.Calendar.HOUR) + now.get(java.util.Calendar.MINUTE) / 60F) * 30, c * 0.5F, 2, Render2D.TEXT);
				hand(c, now.get(java.util.Calendar.MINUTE) * 6, c * 0.75F, 1.5F, Render2D.TEXT);
				hand(c, now.get(java.util.Calendar.SECOND) * 6, c * 0.8F, 1, Render2D.ORANGE);
				Render2D.circle(c, c, 1.5F, Render2D.ORANGE);
				return new int[] {s, s};
			}

			private void hand(float c, float degrees, float length, float width, int color) {
				GlStateManager.pushMatrix();
				GlStateManager.translate(c, c, 0);
				GlStateManager.rotate(degrees, 0, 0, 1);
				Render2D.roundedRect(-width / 2, -length, width, length, width / 2, color);
				GlStateManager.popMatrix();
			}
		});

		// Features (no HUD box)
		Modules.ALL.add(new Module("hitboxes", "Hitbox", "Hitboxes", false, false, 0, 0) {
			@Override
			public void tick(Minecraft mc) {
				if (mc.getRenderManager() != null && mc.getRenderManager().isDebugBoundingBox() != this.enabled()) {
					mc.getRenderManager().setDebugBoundingBox(this.enabled());
				}
			}
		});
		Modules.ALL.add(new Module("crosshair", "Özel Nişangah", "Custom Crosshair", false, false, 0, 0) {
		});
		Modules.ALL.add(new Module("foodtooltip", "Yemek Değerleri", "Food Values", false, true, 0, 0) {
		});
		Modules.ALL.add(new Module("streamer", "Yayıncı Modu", "Streamer Mode", false, false, 0, 0) {
		});
		Modules.ALL.add(new Module("discord", "Discord Durumu", "Discord Status", false, true, 0, 0) {
		});
		Modules.ALL.add(new Module("dnzbadge", "DNZ Rozeti", "DNZ Badge", false, true, 0, 0) {
		});
		// right-click the card to change the color (see BlockOutline)
		Modules.ALL.add(new Module("blockoutline", "Blok Çerçevesi", "Block Outline", false, false, 0, 0) {
		});
		Modules.ALL.add(new Module("chatheads", "Sohbet Kafaları", "Chat Heads", false, true, 0, 0) {
		});
	}

	static String health(Entity e) {
		if (e instanceof EntityLivingBase) {
			return String.format("%.1f❤", ((EntityLivingBase) e).getHealth() / 2);
		}
		return "";
	}
}
