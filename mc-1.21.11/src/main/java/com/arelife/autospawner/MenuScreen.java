package com.arelife.autospawner;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.Item;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Menu (K key). Four tabs: Mobs, Foods, Settings, Status.
 * Uses no widgets, draws everything itself, so it is less affected by version changes.
 */
public class MenuScreen extends Screen {
	private static final int ACCENT = 0xFFFF8A1F;
	private static final int ACCENT_DIM = 0xFF8A4A10;
	private static final int PANEL = 0xF0161616;
	private static final int PANEL_LIGHT = 0xFF262626;
	private static final int HOVER = 0xFF333333;
	private static final int TEXT = 0xFFFFFFFF;
	private static final int TEXT_DIM = 0xFFAAAAAA;
	private static final int GREEN = 0xFF55FF55;
	private static final int RED = 0xFFFF5555;

	private static final int W = 340;
	private static final int H = 340;
	private static final int ROW_H = 20;

	private static List<EntityType<?>> ALL_MOBS;
	private static final String VERSION = "v" + net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer(AutoSpawnerClient.ID).map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("");
	/** Mobs that normally come from spawners first. */
	private static final List<String> MOB_PRIORITY = List.of("blaze", "zombie", "skeleton", "spider", "cave_spider", "silverfish", "magma_cube", "slime", "creeper", "enderman", "zombified_piglin", "wither_skeleton", "husk", "stray", "drowned", "witch", "pig", "cow", "chicken", "sheep");

	private int tab = 0; // 0 mobs, 1 foods, 2 settings, 3 status
	private String search = "";
	private int scroll = 0;

	private int px, py;

	public MenuScreen() {
		super(Text.literal("Auto Spawner Mob Attack"));
	}

	@Override
	protected void init() {
		px = (width - W) / 2;
		py = (height - H) / 2;
		openedAt = System.currentTimeMillis();
		rebuildLists();
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	public void close() {
		Config.get().save();
		super.close();
	}

	private static List<EntityType<?>> allMobs() {
		if (ALL_MOBS == null) {
			List<EntityType<?>> list = new ArrayList<>();
			for (EntityType<?> type : Registries.ENTITY_TYPE) {
				if (type.getSpawnGroup() != SpawnGroup.MISC) {
					list.add(type);
				}
			}
			list.sort(Comparator.comparing(t -> t.getName().getString()));
			ALL_MOBS = list;
		}
		return ALL_MOBS;
	}

	private List<EntityType<?>> filteredMobs() {
		String q = search.toLowerCase();
		List<EntityType<?>> out = new ArrayList<>();
		for (EntityType<?> t : allMobs()) {
			String id = Registries.ENTITY_TYPE.getId(t).toString();
			if (q.isEmpty() || t.getName().getString().toLowerCase().contains(q) || id.contains(q)) {
				out.add(t);
			}
		}
		// Selected ones first
		Config cfg = Config.get();
		out.sort(Comparator.comparing((EntityType<?> t) -> !cfg.mobs.contains(Registries.ENTITY_TYPE.getId(t).toString()))
				.thenComparingInt(t -> MOB_PRIORITY.indexOf(Registries.ENTITY_TYPE.getId(t).getPath()) < 0 ? 1000 : MOB_PRIORITY.indexOf(Registries.ENTITY_TYPE.getId(t).getPath())));
		return out;
	}

	private List<Item> filteredFoods() {
		String q = search.toLowerCase();
		List<Item> out = new ArrayList<>();
		for (Item t : FoodList.all()) {
			String id = Registries.ITEM.getId(t).toString();
			if (q.isEmpty() || t.getName().getString().toLowerCase().contains(q) || id.contains(q)) {
				out.add(t);
			}
		}
		Config cfg = Config.get();
		out.sort(Comparator.comparing((Item t) -> !cfg.foods.contains(Registries.ITEM.getId(t).toString())));
		return out;
	}

	private int listTop() {
		return py + 80;
	}

	private int visibleRows() {
		return (py + H - 22 - listTop()) / ROW_H;
	}

	// ------------------------------------------------------------ drawing

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		ctx.fill(0, 0, width, height, 0x90000000);
		ctx.fill(px - 1, py - 1, px + W + 1, py + H + 1, ACCENT);
		ctx.fill(px, py, px + W, py + H, PANEL);

		ctx.drawText(textRenderer, "AUTO SPAWNER MOB ATTACK " + VERSION, px + 10, py + 8, ACCENT, true);
		boolean on = AutoSpawnerClient.enabled;
		ctx.fill(px + W - 48, py + 5, px + W - 10, py + 19, on ? 0xFF1F5A1F : 0xFF5A1F1F);
		ctx.drawText(textRenderer, on ? "ON" : "OFF", px + W - 29 - textRenderer.getWidth(on ? "ON" : "OFF") / 2, py + 8, on ? GREEN : RED, false);

		String[] tabs = {"Mobs", "Foods", "Settings", "Status"};
		int tabW = (W - 20) / 4;
		for (int i = 0; i < 4; i++) {
			int tx = px + 10 + i * tabW;
			int ty = py + 24;
			boolean hover = inside(mouseX, mouseY, tx, ty, tabW - 4, 20);
			ctx.fill(tx, ty, tx + tabW - 4, ty + 20, i == tab ? ACCENT : (hover ? HOVER : PANEL_LIGHT));
			ctx.drawText(textRenderer, tabs[i], tx + (tabW - 4) / 2 - textRenderer.getWidth(tabs[i]) / 2, ty + 6, i == tab ? 0xFF111111 : TEXT, false);
		}

		switch (tab) {
			case 0 -> renderMobs(ctx, mouseX, mouseY);
			case 1 -> renderFoods(ctx, mouseX, mouseY);
			case 2 -> renderSettings(ctx, mouseX, mouseY);
			default -> renderStatus(ctx, mouseX, mouseY);
		}

		ctx.drawText(textRenderer, "F10: toggle   K: menu   ESC: close", px + 10, py + H - 14, TEXT_DIM, false);
	}

	private void renderMobs(DrawContext ctx, int mx, int my) {
		Config cfg = Config.get();
		int sx = px + 10, sy = py + 50;
		ctx.fill(sx, sy, px + W - 10, sy + 20, PANEL_LIGHT);
		ctx.drawStrokedRectangle(sx, sy, W - 20, 20, ACCENT_DIM);
		String shown = search.isEmpty() ? "Search... (type to search)" : search + (System.currentTimeMillis() / 500 % 2 == 0 ? "_" : "");
		ctx.drawText(textRenderer, shown, sx + 6, sy + 6, search.isEmpty() ? TEXT_DIM : TEXT, false);

		List<EntityType<?>> list = mobView;
		int rows = visibleRows();
		int maxScroll = Math.max(0, list.size() - rows);
		scroll = Math.max(0, Math.min(maxScroll, scroll));

		for (int i = 0; i < rows; i++) {
			int idx = scroll + i;
			if (idx >= list.size()) break;
			EntityType<?> t = list.get(idx);
			String id = Registries.ENTITY_TYPE.getId(t).toString();
			boolean on = cfg.mobs.contains(id);
			int ry = listTop() + i * ROW_H;
			boolean hover = inside(mx, my, sx, ry, W - 20, ROW_H - 2);
			ctx.fill(sx, ry, px + W - 10, ry + ROW_H - 2, on ? 0xFF3A2A12 : (hover ? HOVER : PANEL_LIGHT));

			SpawnEggItem egg = SpawnEggItem.forEntity(t);
			if (egg != null) {
				ctx.drawItem(new ItemStack(egg), sx + 2, ry);
			}
			ctx.drawText(textRenderer, t.getName().getString(), sx + 24, ry + 5, on ? ACCENT : TEXT, false);

			// anahtar
			int kx = px + W - 10 - 34;
			ctx.fill(kx, ry + 3, kx + 28, ry + 15, on ? ACCENT : 0xFF444444);
			ctx.fill(on ? kx + 16 : kx + 2, ry + 5, on ? kx + 26 : kx + 12, ry + 13, TEXT);
		}

		drawScrollbar(ctx, list.size(), rows);
		if (list.isEmpty()) {
			ctx.drawText(textRenderer, "No matching mobs", sx + 6, listTop() + 6, TEXT_DIM, false);
		}
		ctx.drawText(textRenderer, "Selected: " + cfg.mobs.size(), px + W - 130, py + 10, TEXT_DIM, false);
	}

	private void renderFoods(DrawContext ctx, int mx, int my) {
		Config cfg = Config.get();
		int sx = px + 10, sy = py + 50;
		ctx.fill(sx, sy, px + W - 10, sy + 20, PANEL_LIGHT);
		ctx.drawStrokedRectangle(sx, sy, W - 20, 20, ACCENT_DIM);
		String shown = search.isEmpty() ? "Search foods... (type to search)" : search + (System.currentTimeMillis() / 500 % 2 == 0 ? "_" : "");
		ctx.drawText(textRenderer, shown, sx + 6, sy + 6, search.isEmpty() ? TEXT_DIM : TEXT, false);

		List<Item> list = foodView;
		int rows = visibleRows();
		int maxScroll = Math.max(0, list.size() - rows);
		scroll = Math.max(0, Math.min(maxScroll, scroll));

		for (int i = 0; i < rows; i++) {
			int idx = scroll + i;
			if (idx >= list.size()) break;
			Item t = list.get(idx);
			boolean on = cfg.foods.contains(Registries.ITEM.getId(t).toString());
			int ry = listTop() + i * ROW_H;
			boolean hover = inside(mx, my, sx, ry, W - 20, ROW_H - 2);
			ctx.fill(sx, ry, px + W - 10, ry + ROW_H - 2, on ? 0xFF3A2A12 : (hover ? HOVER : PANEL_LIGHT));
			ctx.drawItem(new ItemStack(t), sx + 2, ry);
			ctx.drawText(textRenderer, t.getName().getString(), sx + 24, ry + 5, on ? ACCENT : TEXT, false);
			int kx = px + W - 10 - 34;
			ctx.fill(kx, ry + 3, kx + 28, ry + 15, on ? ACCENT : 0xFF444444);
			ctx.fill(on ? kx + 16 : kx + 2, ry + 5, on ? kx + 26 : kx + 12, ry + 13, TEXT);
		}
		drawScrollbar(ctx, list.size(), rows);
		if (list.isEmpty()) {
			ctx.drawText(textRenderer, "No matching foods", sx + 6, listTop() + 6, TEXT_DIM, false);
		}
		ctx.drawText(textRenderer, "Selected: " + cfg.foods.size(), px + W - 130, py + 10, TEXT_DIM, false);
	}

	private void renderSettings(DrawContext ctx, int mx, int my) {
		Config cfg = Config.get();
		int y = py + 56;
		for (int id = 0; id < SLIDERS.length; id++) {
			sliderRow(ctx, mx, my, id);
		}
		drawHungerBar(ctx, px + 14, y + 114, cfg.hungerThreshold);
		toggleRow(ctx, y + 140, "Pick up items", cfg.pickup);
		toggleRow(ctx, y + 192, "Stop if sword is about to break", cfg.stopOnLowDurability);
		toggleRow(ctx, y + 218, "Stop when inventory is full", cfg.stopOnFullInventory);
		toggleRow(ctx, y + 244, "Show info panel (HUD)", cfg.hudEnabled);
		ctx.drawText(textRenderer, "HUD corner", px + 14, y + 276, TEXT, false);
		String[] corners = {"Top left", "Top right", "Bottom left", "Bottom right"};
		for (int i = 0; i < 4; i++) {
			int cx = px + 110 + i * 56;
			boolean sel = cfg.hudCorner == i;
			boolean hov = inside(mx, my, cx, y + 270, 53, 20);
			ctx.fill(cx, y + 270, cx + 53, y + 290, sel ? ACCENT : (hov ? HOVER : PANEL_LIGHT));
			String label = corners[i].replace("Bottom ", "B. ").replace("Top ", "T. ");
			ctx.drawText(textRenderer, label, cx + 26 - textRenderer.getWidth(label) / 2, y + 276, sel ? 0xFF111111 : TEXT, false);
		}
	}

	private void toggleRow(DrawContext ctx, int ry, String label, boolean on) {
		ctx.drawText(textRenderer, label, px + 14, ry + 6, TEXT, false);
		int kx = px + W - 10 - 34;
		ctx.fill(kx, ry + 3, kx + 28, ry + 15, on ? ACCENT : 0xFF444444);
		ctx.fill(on ? kx + 16 : kx + 2, ry + 5, on ? kx + 26 : kx + 12, ry + 13, TEXT);
	}

	/** Hunger bar: filled drumsticks = hunger level at which the bot starts eating. */
	private void drawHungerBar(DrawContext ctx, int x, int y, int threshold) {
		for (int i = 0; i < 10; i++) {
			int ix = x + i * 18;
			ctx.fill(ix, y, ix + 17, y + 17, 0xFF2E2E2E);
			int filled = threshold - i * 2; // 2 = full, 1 = half, <=0 = empty
			if (filled >= 1) {
				ctx.drawItem(new ItemStack(Items.COOKED_CHICKEN), ix, y);
				if (filled == 1) {
					ctx.fill(ix + 8, y, ix + 17, y + 17, 0xB0222222);
				}
			}
		}
	}


	// ------------------------------------------------------------ sliders

	private static final double[][] SLIDERS = {{2, 12, 1}, {0.5, 3.0, 0.25}, {1, 40, 1}, {1, 19, 1}, {0, 19, 1}};
	private static final int[] SLIDER_Y = {0, 30, 60, 90, 166};
	private static final String[] SLIDER_LABEL = {"Search range", "Attack distance", "Attack interval", "Eat when hunger is at", "Stop when health is at"};

	private int sliderX() {
		return px + 150;
	}

	private static final int SLIDER_W = 120;

	private static double sliderGet(Config c, int id) {
		return switch (id) {
			case 0 -> c.searchRange;
			case 1 -> c.attackDistance;
			case 2 -> c.attackIntervalTicks;
			case 3 -> c.hungerThreshold;
			default -> c.minHealth;
		};
	}

	private static void sliderSet(Config c, int id, double v) {
		switch (id) {
			case 0 -> c.searchRange = v;
			case 1 -> c.attackDistance = v;
			case 2 -> c.attackIntervalTicks = (int) Math.round(v);
			case 3 -> c.hungerThreshold = (int) Math.round(v);
			default -> c.minHealth = (int) Math.round(v);
		}
		c.save();
	}

	private static String sliderText(Config c, int id) {
		return switch (id) {
			case 0 -> fmt(c.searchRange) + " blocks";
			case 1 -> fmt(c.attackDistance) + " blocks";
			case 2 -> c.attackIntervalTicks + " ticks";
			case 3 -> fmt(c.hungerThreshold / 2.0) + " / 10";
			default -> c.minHealth == 0 ? "Off" : fmt(c.minHealth / 2.0) + " hearts";
		};
	}

	private void sliderRow(DrawContext ctx, int mx, int my, int id) {
		Config cfg = Config.get();
		int y = py + 56 + SLIDER_Y[id];
		ctx.drawText(textRenderer, SLIDER_LABEL[id], px + 14, y + 6, TEXT, false);
		double[] d = SLIDERS[id];
		double frac = (sliderGet(cfg, id) - d[0]) / (d[1] - d[0]);
		frac = Math.max(0, Math.min(1, frac));
		int x0 = sliderX();
		boolean hover = inside(mx, my, x0 - 4, y, SLIDER_W + 8, 20);
		ctx.fill(x0, y + 8, x0 + SLIDER_W, y + 12, PANEL_LIGHT);
		ctx.fill(x0, y + 8, x0 + (int) (SLIDER_W * frac), y + 12, hover ? ACCENT : ACCENT_DIM);
		int tx = x0 + (int) (SLIDER_W * frac);
		ctx.fill(tx - 2, y + 4, tx + 2, y + 16, ACCENT);
		ctx.drawText(textRenderer, sliderText(cfg, id), x0 + SLIDER_W + 10, y + 6, ACCENT, false);
	}

	/** Returns the slider under the mouse, or -1. */
	private int sliderAt(int mx, int my) {
		for (int id = 0; id < SLIDERS.length; id++) {
			if (inside(mx, my, sliderX() - 4, py + 56 + SLIDER_Y[id], SLIDER_W + 8, 20)) {
				return id;
			}
		}
		return -1;
	}

	private void sliderClick(int id, int mx) {
		double[] d = SLIDERS[id];
		double frac = Math.max(0, Math.min(1, (mx - sliderX()) / (double) SLIDER_W));
		double v = d[0] + frac * (d[1] - d[0]);
		v = Math.round((v - d[0]) / d[2]) * d[2] + d[0];
		sliderSet(Config.get(), id, v);
	}

	// ------------------------------------------------------------ list helpers

	private List<EntityType<?>> mobView = new ArrayList<>();
	private List<Item> foodView = new ArrayList<>();
	private long openedAt = 0;

	/** Sorts the lists once (on open and when the search text changes) so rows do not jump around. */
	private void rebuildLists() {
		mobView = filteredMobs();
		foodView = filteredFoods();
	}

	private void drawScrollbar(DrawContext ctx, int total, int rows) {
		if (total <= rows) return;
		int x = px + W - 8;
		int top = listTop();
		int track = rows * ROW_H - 2;
		ctx.fill(x, top, x + 3, top + track, PANEL_LIGHT);
		int thumb = Math.max(12, track * rows / total);
		int maxScroll = total - rows;
		int pos = (track - thumb) * scroll / Math.max(1, maxScroll);
		ctx.fill(x, top + pos, x + 3, top + pos + thumb, ACCENT);
	}

	private void renderStatus(DrawContext ctx, int mx, int my) {
		Config cfg = Config.get();
		int y = py + 58;
		String[][] cards = {
				{"State", AutoSpawnerClient.enabled ? "ON" : "OFF"},
				{"Hits", String.valueOf(AutoSpawnerClient.attackCount)},
				{"Kills", String.valueOf(Stats.totalKills())},
				{"Running time", AutoSpawnerClient.uptimeText()},
				{"Food left", String.valueOf(AutoSpawnerClient.foodLeft())},
				{"Sword durability", AutoSpawnerClient.swordText()},
		};
		int cw = (W - 30) / 2;
		for (int i = 0; i < cards.length; i++) {
			int cx = px + 10 + (i % 2) * (cw + 10);
			int cy = y + (i / 2) * 36;
			ctx.fill(cx, cy, cx + cw, cy + 32, PANEL_LIGHT);
			ctx.fill(cx, cy, cx + 2, cy + 32, ACCENT);
			ctx.drawText(textRenderer, cards[i][0], cx + 10, cy + 5, TEXT_DIM, false);
			int color = i == 0 ? (AutoSpawnerClient.enabled ? GREEN : RED) : TEXT;
			ctx.drawText(textRenderer, cards[i][1], cx + 10, cy + 18, color, false);
		}

		int by = y + 3 * 36 + 4;
		int bx = px + 10;
		int bw = (W - 30) / 2;
		boolean hover = inside(mx, my, bx, by, bw, 24);
		ctx.fill(bx, by, bx + bw, by + 24, hover ? ACCENT : ACCENT_DIM);
		String toggle = AutoSpawnerClient.enabled ? "Turn off" : "Turn on";
		ctx.drawText(textRenderer, toggle, bx + bw / 2 - textRenderer.getWidth(toggle) / 2, by + 8, TEXT, false);
		boolean hover2 = inside(mx, my, bx + bw + 10, by, bw, 24);
		ctx.fill(bx + bw + 10, by, bx + 2 * bw + 10, by + 24, hover2 ? HOVER : PANEL_LIGHT);
		ctx.drawText(textRenderer, "Reset stats", bx + bw + 10 + bw / 2 - textRenderer.getWidth("Reset stats") / 2, by + 8, TEXT, false);

		int ty = by + 32;
		statsTable(ctx, px + 10, ty, cw, "Kills", Stats.kills);
		statsTable(ctx, px + 20 + cw, ty, cw, "Items collected", Stats.items);
	}

	private void statsTable(DrawContext ctx, int x, int y, int w, String title, java.util.Map<String, Integer> map) {
		ctx.drawText(textRenderer, title, x, y, ACCENT, false);
		int row = 0;
		for (java.util.Map.Entry<String, Integer> e : Stats.top(map, 6)) {
			String name = Stats.names.getOrDefault(e.getKey(), e.getKey());
			String right = e.getValue() + " (" + Stats.perHour(e.getValue()) + ")";
			int max = w - textRenderer.getWidth(right) - 8;
			while (name.length() > 3 && textRenderer.getWidth(name) > max) {
				name = name.substring(0, name.length() - 1);
			}
			ctx.drawText(textRenderer, name, x, y + 12 + row * 11, TEXT, false);
			ctx.drawText(textRenderer, right, x + w - textRenderer.getWidth(right), y + 12 + row * 11, TEXT_DIM, false);
			row++;
		}
		if (row == 0) {
			ctx.drawText(textRenderer, "-", x, y + 12, TEXT_DIM, false);
		}
	}


	// ------------------------------------------------------------ input

	@Override
	public boolean mouseClicked(Click click, boolean doubled) {
		int mx = (int) click.x(), my = (int) click.y();
		Config cfg = Config.get();

		// tabs
		int tabW = (W - 20) / 4;
		for (int i = 0; i < 4; i++) {
			if (inside(mx, my, px + 10 + i * tabW, py + 24, tabW - 4, 20)) {
				tab = i;
				scroll = 0;
				search = "";
				rebuildLists();
				return true;
			}
		}

		if (tab == 0) {
			List<EntityType<?>> list = mobView;
			int rows = visibleRows();
			for (int i = 0; i < rows; i++) {
				int idx = scroll + i;
				if (idx >= list.size()) break;
				int ry = listTop() + i * ROW_H;
				if (inside(mx, my, px + 10, ry, W - 20, ROW_H - 2)) {
					String id = Registries.ENTITY_TYPE.getId(list.get(idx)).toString();
					if (!cfg.mobs.remove(id)) {
						cfg.mobs.add(id);
					}
					cfg.save();
					return true;
				}
			}
		} else if (tab == 1) {
			List<Item> list = foodView;
			int rows = visibleRows();
			for (int i = 0; i < rows; i++) {
				int idx = scroll + i;
				if (idx >= list.size()) break;
				int ry = listTop() + i * ROW_H;
				if (inside(mx, my, px + 10, ry, W - 20, ROW_H - 2)) {
					String id = Registries.ITEM.getId(list.get(idx)).toString();
					if (!cfg.foods.remove(id)) {
						cfg.foods.add(id);
					}
					cfg.save();
					return true;
				}
			}
		} else if (tab == 2) {
			int y = py + 56;
			int sid = sliderAt(mx, my);
			if (sid >= 0) { sliderClick(sid, mx); return true; }
			for (int i = 0; i < 10; i++) {
				int ix = px + 14 + i * 18;
				if (inside(mx, my, ix, y + 114, 18, 18)) {
					// left half = half drumstick, right half = full drumstick
					cfg.hungerThreshold = i * 2 + (mx < ix + 8 ? 1 : 2);
					cfg.save();
					return true;
				}
			}
			if (inside(mx, my, px + 10, y + 140, W - 20, 20)) { cfg.pickup = !cfg.pickup; cfg.save(); return true; }
			if (inside(mx, my, px + 10, y + 192, W - 20, 20)) { cfg.stopOnLowDurability = !cfg.stopOnLowDurability; cfg.save(); return true; }
			if (inside(mx, my, px + 10, y + 218, W - 20, 20)) { cfg.stopOnFullInventory = !cfg.stopOnFullInventory; cfg.save(); return true; }
			if (inside(mx, my, px + 10, y + 244, W - 20, 20)) { cfg.hudEnabled = !cfg.hudEnabled; cfg.save(); return true; }
			for (int i = 0; i < 4; i++) {
				if (inside(mx, my, px + 110 + i * 56, y + 270, 53, 20)) { cfg.hudCorner = i; cfg.save(); return true; }
			}
		} else {
			int bx = px + 10, by = py + 58 + 3 * 36 + 4;
			int bw = (W - 30) / 2;
			if (inside(mx, my, bx + bw + 10, by, bw, 24)) {
				Stats.reset();
				return true;
			}
			if (inside(mx, my, bx, by, bw, 24)) {
				AutoSpawnerClient.enabled = !AutoSpawnerClient.enabled;
				return true;
			}
		}
		return super.mouseClicked(click, doubled);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (tab == 2) {
			int sid = sliderAt((int) mouseX, (int) mouseY);
			if (sid >= 0) {
				double[] d = SLIDERS[sid];
				double v = sliderGet(Config.get(), sid) + Math.signum(verticalAmount) * d[2];
				sliderSet(Config.get(), sid, Math.max(d[0], Math.min(d[1], v)));
				return true;
			}
		}
		if (tab <= 1) {
			scroll -= (int) Math.signum(verticalAmount) * 2;
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	@Override
	public boolean charTyped(CharInput input) {
		if (tab <= 1 && System.currentTimeMillis() - openedAt > 150 && input.isValidChar() && search.length() < 24) {
			search += input.asString();
			scroll = 0;
			rebuildLists();
			return true;
		}
		return super.charTyped(input);
	}

	@Override
	public boolean keyPressed(KeyInput input) {
		if (tab <= 1 && System.currentTimeMillis() - openedAt > 150 && input.key() == GLFW.GLFW_KEY_BACKSPACE) {
			if (!search.isEmpty()) {
				search = search.substring(0, search.length() - 1);
				scroll = 0;
				rebuildLists();
			}
			return true;
		}
		return super.keyPressed(input);
	}

	// ------------------------------------------------------------ helpers

	private static boolean inside(int mx, int my, int x, int y, int w, int h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	private static String fmt(double v) {
		return v == Math.floor(v) ? String.valueOf((int) v) : String.format("%.2f", v).replace(',', '.');
	}
}
