package com.dnz.schematic;

import com.dnz.schematic.litematic.Litematic;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/** Small panel on the right: schematic name, layer, progress, the nearest mistake and what the looked-at block should be. */
public final class SchematicHud {
	private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

	private record Line(String text, int color) {
	}

	private SchematicHud() {
	}

	public static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.gui.hud.isHidden() || mc.player == null || mc.gui.screen() != null) {
			return;
		}
		List<Line> lines = lines(mc);
		if (lines.isEmpty()) {
			return;
		}
		Font font = mc.font;
		int w = 0;
		for (Line l : lines) {
			w = Math.max(w, font.width(l.text));
		}
		int h = lines.size() * 11 + 6;
		int x = g.guiWidth() - w - 14;
		int y = g.guiHeight() / 2 - h / 2 - 20;
		g.fill(x - 4, y - 3, x + w + 6, y + h - 3, 0x88000000);
		g.fill(x - 4, y - 3, x - 2, y + h - 3, 0xFF4FA3FF);
		for (Line l : lines) {
			g.text(font, l.text, x + 2, y, l.color);
			y += 11;
		}
	}

	static List<Line> lines(Minecraft mc) {
		List<Line> out = new ArrayList<>();
		SchematicConfig config = SchematicConfig.get();
		Placement p = Schematics.placement();
		Checker checker = Schematics.checker();
		if (p != null && checker != null && config.hud) {
			Litematic schem = p.schematic;
			out.add(new Line(schem.name, 0xFFFFFFFF));
			if (config.layerMode != 0) {
				String mode = S.t(config.layerMode == 1 ? "layer.single" : "layer.below");
				out.add(new Line(S.t("hud.layer", config.layer + 1, schem.sizeY, mode), 0xFF9FD0FF));
			}
			int total = schem.nonAirCount();
			int ok = checker.count(Checker.OK);
			int percent = total == 0 ? 100 : (int) (ok * 100L / total);
			out.add(new Line(S.t("hud.progress", ok, total, percent), 0xFF7CE38B));
			if (!checker.complete()) {
				out.add(new Line(S.t("hud.checking"), 0xFFAAAAAA));
			}
			int missing = checker.count(Checker.MISSING);
			int wrong = checker.count(Checker.WRONG_BLOCK) + checker.count(Checker.WRONG_STATE);
			int extra = checker.count(Checker.EXTRA);
			if (missing > 0) {
				out.add(new Line(S.t("hud.missing", missing), 0xFF000000 | GhostRenderer.BLUE));
			}
			if (wrong > 0) {
				out.add(new Line(S.t("hud.wrong", wrong), 0xFF000000 | GhostRenderer.RED));
			}
			if (extra > 0) {
				out.add(new Line(S.t("hud.extra", extra), 0xFF000000 | GhostRenderer.ORANGE));
			}

			// The block under the crosshair, if it is a mistake.
			HitResult hit = mc.hitResult;
			if (hit instanceof BlockHitResult bhit && hit.getType() == HitResult.Type.BLOCK) {
				BlockPos pos = bhit.getBlockPos();
				int i = p.toIndex(pos.getX(), pos.getY(), pos.getZ());
				if (i >= 0 && Checker.isError(checker.status(i))) {
					out.add(new Line(S.t("hud.looking"), 0xFFFFFFFF));
					describe(out, mc, p, checker, i, pos);
				}
			}

			BlockPos nearest = Schematics.nearestError();
			int ni = Schematics.nearestErrorIndex();
			if (nearest != null && ni >= 0 && Checker.isError(checker.status(ni))) {
				double dx = nearest.getX() + 0.5 - mc.player.getX();
				double dz = nearest.getZ() + 0.5 - mc.player.getZ();
				int dy = nearest.getY() - mc.player.blockPosition().getY();
				int dist = (int) Math.round(Math.sqrt(dx * dx + dz * dz + dy * dy));
				// Minecraft's yaw: 0 = south, and it grows when turning right.
				float target = (float) Math.toDegrees(Math.atan2(-dx, dz));
				float rel = Mth.wrapDegrees(target - mc.player.getYRot());
				String arrow = ARROWS[Math.floorMod(Math.round(rel / 45.0F), 8)];
				String height = dy > 0 ? " " + S.t("hud.up", dy) : dy < 0 ? " " + S.t("hud.down", -dy) : "";
				out.add(new Line(S.t("hud.nearest", arrow, dist) + height, 0xFFFFFFFF));
				if (!(hit instanceof BlockHitResult b && nearest.equals(b.getBlockPos()))) {
					describe(out, mc, p, checker, ni, nearest);
				}
			} else if (checker.complete() && missing + wrong + extra == 0 && total > 0) {
				out.add(new Line(S.t("hud.done"), 0xFF7CE38B));
			}
		}
		String message = Schematics.message();
		if (message != null) {
			out.add(new Line(message, 0xFFFFD60A));
		}
		return out;
	}

	private static void describe(List<Line> out, Minecraft mc, Placement p, Checker checker, int index, BlockPos pos) {
		BlockState expected = p.placedState(p.schematic.paletteAt(index));
		BlockState actual = mc.level.getBlockState(pos);
		byte status = checker.status(index);
		if (status == Checker.EXTRA) {
			out.add(new Line("  " + S.t("hud.remove", actual.getBlock().getName().getString()), 0xFF000000 | GhostRenderer.ORANGE));
		} else if (status == Checker.WRONG_STATE) {
			out.add(new Line("  " + S.t("hud.turn", expected.getBlock().getName().getString()), 0xFF000000 | GhostRenderer.YELLOW));
		} else {
			out.add(new Line("  " + S.t("hud.should", expected.getBlock().getName().getString()), 0xFF9FD0FF));
			if (status == Checker.WRONG_BLOCK) {
				out.add(new Line("  " + S.t("hud.is", actual.getBlock().getName().getString()), 0xFF000000 | GhostRenderer.RED));
			}
		}
	}
}
