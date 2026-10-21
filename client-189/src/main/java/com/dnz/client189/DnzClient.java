package com.dnz.client189;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.Display;

/** DNZ Client for Minecraft 1.8.9 (Forge): DNZ menu (Right Shift), HUD modules, zoom and the pause menu button. */
@Mod(modid = DnzClient.MODID, name = "DNZ Client", version = "0.1.0", clientSideOnly = true, acceptedMinecraftVersions = "[1.8.9]")
public class DnzClient {
	public static final String MODID = "dnzclient";
	public static final String VERSION = "0.1.0";
	private static final int DNZ_BUTTON = 7731;

	private final KeyBinding menuKey = new KeyBinding("DNZ Menu", Keyboard.KEY_RSHIFT, "DNZ Client");
	private final KeyBinding zoomKey = new KeyBinding("DNZ Zoom", Keyboard.KEY_C, "DNZ Client");
	private float zoomFov = 1;

	@Mod.EventHandler
	public void init(FMLInitializationEvent event) {
		ClientRegistry.registerKeyBinding(this.menuKey);
		ClientRegistry.registerKeyBinding(this.zoomKey);
		MinecraftForge.EVENT_BUS.register(this);
		MinecraftForge.EVENT_BUS.register(new Combat());
		DiscordPresence.start();
		Config.get();
		Display.setTitle("DNZ Client 1.8.9");
	}

	@SubscribeEvent
	public void onTick(TickEvent.ClientTickEvent event) {
		if (event.phase != TickEvent.Phase.END) {
			return;
		}
		Minecraft mc = Minecraft.getMinecraft();
		while (this.menuKey.isPressed()) {
			if (mc.currentScreen == null) {
				mc.displayGuiScreen(new DnzShiftScreen());
			}
		}
		for (Module m : Modules.ALL) {
			m.tick(mc);
		}
		com.dnz.client189.engine.Occlusion.tick(mc);
		Combat.tick(mc);
		DiscordPresence.tick(mc);
		DnzBadges.tick(mc);
		this.shots(mc);
		com.dnz.client189.engine.Bench.tick(mc);
	}

	@SubscribeEvent
	public void onOverlay(RenderGameOverlayEvent.Post event) {
		Minecraft mc = Minecraft.getMinecraft();
		if (event.type == RenderGameOverlayEvent.ElementType.ALL && !(mc.currentScreen instanceof HudEditorScreen) && !mc.gameSettings.showDebugInfo) {
			Hud.render(mc, false, 0, 0);
		}
	}

	/** F3 screen: a green "DNZ Engine" line with the version, under Minecraft's own first line. */
	@SubscribeEvent
	public void onDebugText(RenderGameOverlayEvent.Text event) {
		if (Minecraft.getMinecraft().gameSettings.showDebugInfo && event.left != null) {
			Module engine = Modules.get("engine");
			String state = engine != null && engine.enabled()
				? " §7(" + Lang.t("gizli", "culled") + ": " + com.dnz.client189.engine.Occlusion.lastHiddenEntities + " E, " + com.dnz.client189.engine.Occlusion.lastHiddenTiles + " T" + (com.dnz.client189.engine.Regions.active ? ", " + Lang.t("bölge", "regions") + ": " + com.dnz.client189.engine.Regions.regionCount() : "") + ")"
				: " (" + Lang.t("kapalı", "off") + ")";
			event.left.add(Math.min(1, event.left.size()), "§aDNZ Engine " + VERSION + state);
		}
	}

	/** Animations move by real time (once per frame). */
	@SubscribeEvent
	public void onRenderTick(TickEvent.RenderTickEvent event) {
		if (event.phase == TickEvent.Phase.START) {
			Anim.frame();
		}
	}

	@SubscribeEvent
	public void onNameFormat(net.minecraftforge.event.entity.player.PlayerEvent.NameFormat event) {
		if (DnzBadges.has(event.entityPlayer.getUniqueID())) {
			event.displayname = DnzBadges.MARK + event.displayname;
		}
	}

	@SubscribeEvent
	public void onMouse(MouseEvent event) {
		if (event.buttonstate) {
			if (event.button == 0) {
				Modules.LEFT_CLICKS.add(System.currentTimeMillis());
			} else if (event.button == 1) {
				Modules.RIGHT_CLICKS.add(System.currentTimeMillis());
			}
		}
	}

	/** Zoom: hold C for a quarter of the field of view, eased in and out. */
	@SubscribeEvent
	public void onFov(EntityViewRenderEvent.FOVModifier event) {
		Module zoom = Modules.get("zoom");
		boolean held = zoom != null && zoom.enabled() && Minecraft.getMinecraft().currentScreen == null && this.zoomKey.isKeyDown();
		float target = held ? 0.25F : 1;
		this.zoomFov += (target - this.zoomFov) * 0.25F;
		if (Math.abs(this.zoomFov - target) < 0.01F) {
			this.zoomFov = target;
		}
		event.setFOV(event.getFOV() * this.zoomFov);
	}

	@SubscribeEvent
	public void onGuiInit(GuiScreenEvent.InitGuiEvent.Post event) {
		if (event.gui instanceof GuiIngameMenu) {
			event.buttonList.add(new GuiButton(DNZ_BUTTON, event.gui.width / 2 - 100, event.gui.height / 4 + 152, "DNZ Client"));
		}
	}

	@SubscribeEvent
	public void onGuiAction(GuiScreenEvent.ActionPerformedEvent.Pre event) {
		if (event.gui instanceof GuiIngameMenu && event.button.id == DNZ_BUTTON) {
			Minecraft.getMinecraft().displayGuiScreen(new DnzMenuScreen(event.gui));
		}
	}

	/** "DNZ Client" at the bottom right of the inventory, chests and the pause menu (like on 26.x). */
	@SubscribeEvent
	public void onDrawScreen(GuiScreenEvent.DrawScreenEvent.Post event) {
		if (event.gui instanceof net.minecraft.client.gui.inventory.GuiContainer || event.gui instanceof GuiIngameMenu) {
			float size = 9;
			float dnzW = TtfFont.BOLD.width("DNZ ", size);
			float total = dnzW + TtfFont.BOLD.width("Client", size);
			float x = event.gui.width - total - 6;
			float y = event.gui.height - size - 4;
			TtfFont.BOLD.draw("DNZ ", x, y, size, 0xFFFFFFFF);
			TtfFont.BOLD.draw("Client", x + dnzW, y, size, 0xFF4DA0FF);
		}
	}

	/** Test pictures of the DNZ screens (only when started with -Ddnz.shot=1), then the game closes. */
	private int shotTicks;
	private int worldTicks;

	private void shots(Minecraft mc) {
		if (System.getProperty("dnz.shot") == null && System.getenv("DNZ_SHOT") == null) {
			return;
		}
		this.shotTicks++;
		if (this.shotTicks == 80) {
			mc.displayGuiScreen(new DnzShiftScreen());
		} else if (this.shotTicks == 110) {
			net.minecraft.util.ScreenShotHelper.saveScreenshot(mc.mcDataDir, "dnz-shift.png", mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
		} else if (this.shotTicks == 115) {
			mc.displayGuiScreen(new DnzMenuScreen(null));
		} else if (this.shotTicks == 150) {
			net.minecraft.util.ScreenShotHelper.saveScreenshot(mc.mcDataDir, "dnz-menu.png", mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
		} else if (this.shotTicks == 155) {
			mc.displayGuiScreen(new HudEditorScreen(null));
		} else if (this.shotTicks == 180) {
			net.minecraft.util.ScreenShotHelper.saveScreenshot(mc.mcDataDir, "dnz-hud.png", mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
			mc.displayGuiScreen(null);
			net.minecraft.world.WorldSettings settings = new net.minecraft.world.WorldSettings(1L, net.minecraft.world.WorldSettings.GameType.CREATIVE, false, false, net.minecraft.world.WorldType.FLAT);
			mc.launchIntegratedServer("dnz-test", "dnz-test", settings);
		} else if (this.shotTicks > 180 && mc.theWorld != null && mc.thePlayer != null) {
			this.worldTicks++;
			if (this.worldTicks == 100) {
				mc.gameSettings.showDebugInfo = true;
			} else if (this.worldTicks == 120) {
				net.minecraft.util.ScreenShotHelper.saveScreenshot(mc.mcDataDir, "dnz-f3.png", mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
				mc.gameSettings.showDebugInfo = false;
			} else if (this.worldTicks == 130) {
				net.minecraft.util.ScreenShotHelper.saveScreenshot(mc.mcDataDir, "dnz-game.png", mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
				mc.displayGuiScreen(new net.minecraft.client.gui.inventory.GuiInventory(mc.thePlayer));
			} else if (this.worldTicks == 150) {
				net.minecraft.util.ScreenShotHelper.saveScreenshot(mc.mcDataDir, "dnz-inventory.png", mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
				mc.shutdown();
			}
		}
	}
}
