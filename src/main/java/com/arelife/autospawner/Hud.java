package com.arelife.autospawner;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Small info panel in a screen corner while the mod is on. */
public final class Hud {
	private static final int ACCENT = 0xFFFF8A1F;
	private static final int TEXT = 0xFFFFFFFF;
	private static final int TEXT_DIM = 0xFFAAAAAA;
	private static final int GREEN = 0xFF55FF55;

	private record Row(ItemStack icon, String text, int color) {
	}

	private Hud() {
	}

	public static void render(DrawContext ctx) {
		Config cfg = Config.get();
		MinecraftClient mc = MinecraftClient.getInstance();
		if (!AutoSpawnerClient.enabled || !cfg.hudEnabled || mc.player == null || mc.options.hudHidden) {
			return;
		}

		List<Row> rows = new ArrayList<>();
		rows.add(new Row(null, "Auto Spawner  ON  " + AutoSpawnerClient.uptimeText(), GREEN));
		rows.add(new Row(null, "Hits " + AutoSpawnerClient.attackCount + "   Kills " + Stats.totalKills(), TEXT));
		for (Map.Entry<String, Integer> e : Stats.top(Stats.kills, 3)) {
			rows.add(new Row(null, Stats.names.getOrDefault(e.getKey(), e.getKey()) + ": " + e.getValue(), TEXT_DIM));
		}
		for (Map.Entry<String, Integer> e : Stats.top(Stats.items, 5)) {
			rows.add(new Row(Stats.icons.get(e.getKey()), Stats.names.getOrDefault(e.getKey(), e.getKey()) + ": " + e.getValue(), TEXT));
		}
		rows.add(new Row(null, "Food " + AutoSpawnerClient.foodLeft() + "   Sword " + AutoSpawnerClient.swordText(), TEXT_DIM));

		int width = 0;
		int height = 8;
		for (Row r : rows) {
			width = Math.max(width, mc.textRenderer.getWidth(r.text()) + (r.icon() != null ? 18 : 0));
			height += r.icon() != null ? 17 : 11;
		}
		width += 12;

		int screenW = mc.getWindow().getScaledWidth();
		int screenH = mc.getWindow().getScaledHeight();
		int corner = cfg.hudCorner;
		int x = (corner == 1 || corner == 3) ? screenW - width - 4 : 4;
		int y = (corner == 2 || corner == 3) ? screenH - height - 4 : 4;

		ctx.fill(x, y, x + width, y + height, 0xA0101010);
		ctx.fill(x, y, x + 2, y + height, ACCENT);
		int ty = y + 5;
		for (Row r : rows) {
			int tx = x + 7;
			if (r.icon() != null) {
				ctx.drawItem(r.icon(), tx, ty - 1);
				tx += 18;
				ctx.drawText(mc.textRenderer, r.text(), tx, ty + 3, r.color(), false);
				ty += 17;
			} else {
				ctx.drawText(mc.textRenderer, r.text(), tx, ty, r.color(), false);
				ty += 11;
			}
		}
	}
}
