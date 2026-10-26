package com.arelife.autospawner;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import com.mojang.blaze3d.platform.InputConstants;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Menü (K tuşu). Four tabs: Mobs, Foods, Settings, Status.
 * Widget kullanmaz, her şeyi kendi çizer; böylece sürüm değişikliklerinden az etkilenir.
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
	private static final int H = 310;
	private static final int ROW_H = 20;

	private static List<EntityType<?>> ALL_MOBS;
	/** Mobs that normally come from spawners first. */
	private static final List<String> MOB_PRIORITY = List.of("blaze", "zombie", "skeleton", "spider", "cave_spider", "silverfish", "magma_cube", "slime", "creeper", "enderman", "zombified_piglin", "wither_skeleton", "husk", "stray", "drowned", "witch", "pig", "cow", "chicken", "sheep");

	private int tab = 0; // 0 canlılar, 1 ayarlar, 2 durum
	private String search = "";
	private int scroll = 0;

	private int px, py;

	public MenuScreen() {
		super(Component.literal("Auto Spawner Mob Attack"));
	}

	@Override
	protected void init() {
		px = (width - W) / 2;
		py = (height - H) / 2;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		Config.get().save();
		super.onClose();
	}

	private static List<EntityType<?>> allMobs() {
		if (ALL_MOBS == null) {
			List<EntityType<?>> list = new ArrayList<>();
			for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
				if (type.getCategory() != MobCategory.MISC) {
					list.add(type);
				}
			}
			list.sort(Comparator.comparing(t -> t.getDescription().getString()));
			ALL_MOBS = list;
		}
		return ALL_MOBS;
	}

	private List<EntityType<?>> filteredMobs() {
		String q = search.toLowerCase();
		List<EntityType<?>> out = new ArrayList<>();
		for (EntityType<?> t : allMobs()) {
			String id = BuiltInRegistries.ENTITY_TYPE.getKey(t).toString();
			if (q.isEmpty() || nameOf(t).toLowerCase().contains(q) || id.contains(q)) {
				out.add(t);
			}
		}
		// Seçililer üstte
		Config cfg = Config.get();
		out.sort(Comparator.comparing((EntityType<?> t) -> !cfg.mobs.contains(BuiltInRegistries.ENTITY_TYPE.getKey(t).toString()))
				.thenComparingInt(t -> MOB_PRIORITY.indexOf(BuiltInRegistries.ENTITY_TYPE.getKey(t).getPath()) < 0 ? 1000 : MOB_PRIORITY.indexOf(BuiltInRegistries.ENTITY_TYPE.getKey(t).getPath())));
		return out;
	}

	private List<Item> filteredFoods() {
		String q = search.toLowerCase();
		List<Item> out = new ArrayList<>();
		for (Item t : FoodList.all()) {
			String id = BuiltInRegistries.ITEM.getKey(t).toString();
			if (q.isEmpty() || nameOf(t).toLowerCase().contains(q) || id.contains(q)) {
				out.add(t);
			}
		}
		Config cfg = Config.get();
		out.sort(Comparator.comparing((Item t) -> !cfg.foods.contains(BuiltInRegistries.ITEM.getKey(t).toString())));
		return out;
	}

	private static String nameOf(EntityType<?> t) {
		return t.getDescription().getString();
	}

	private static String nameOf(Item t) {
		return new ItemStack(t).getHoverName().getString();
	}

	private int listTop() {
		return py + 80;
	}

	private int visibleRows() {
		return (py + H - 22 - listTop()) / ROW_H;
	}

	// ------------------------------------------------------------ çizim

	@Override
	public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
		ctx.fill(0, 0, width, height, 0x90000000);
		ctx.fill(px - 1, py - 1, px + W + 1, py + H + 1, ACCENT);
		ctx.fill(px, py, px + W, py + H, PANEL);

		ctx.text(font, "AUTO SPAWNER MOB ATTACK", px + 10, py + 8, ACCENT, true);

		String[] tabs = {"Mobs", "Foods", "Settings", "Status"};
		int tabW = (W - 20) / 4;
		for (int i = 0; i < 4; i++) {
			int tx = px + 10 + i * tabW;
			int ty = py + 24;
			boolean hover = inside(mouseX, mouseY, tx, ty, tabW - 4, 20);
			ctx.fill(tx, ty, tx + tabW - 4, ty + 20, i == tab ? ACCENT : (hover ? HOVER : PANEL_LIGHT));
			ctx.text(font, tabs[i], tx + (tabW - 4) / 2 - font.width(tabs[i]) / 2, ty + 6, i == tab ? 0xFF111111 : TEXT, false);
		}

		switch (tab) {
			case 0 -> renderMobs(ctx, mouseX, mouseY);
			case 1 -> renderFoods(ctx, mouseX, mouseY);
			case 2 -> renderSettings(ctx, mouseX, mouseY);
			default -> renderStatus(ctx, mouseX, mouseY);
		}

		ctx.text(font, "F10: toggle   K: menu   ESC: close", px + 10, py + H - 14, TEXT_DIM, false);
	}

	private void renderMobs(GuiGraphicsExtractor ctx, int mx, int my) {
		Config cfg = Config.get();
		int sx = px + 10, sy = py + 50;
		ctx.fill(sx, sy, px + W - 10, sy + 20, PANEL_LIGHT);
		ctx.outline(sx, sy, W - 20, 20, ACCENT_DIM);
		String shown = search.isEmpty() ? "Search... (type to search)" : search + (System.currentTimeMillis() / 500 % 2 == 0 ? "_" : "");
		ctx.text(font, shown, sx + 6, sy + 6, search.isEmpty() ? TEXT_DIM : TEXT, false);

		List<EntityType<?>> list = filteredMobs();
		int rows = visibleRows();
		int maxScroll = Math.max(0, list.size() - rows);
		scroll = Math.max(0, Math.min(maxScroll, scroll));

		for (int i = 0; i < rows; i++) {
			int idx = scroll + i;
			if (idx >= list.size()) break;
			EntityType<?> t = list.get(idx);
			String id = BuiltInRegistries.ENTITY_TYPE.getKey(t).toString();
			boolean on = cfg.mobs.contains(id);
			int ry = listTop() + i * ROW_H;
			boolean hover = inside(mx, my, sx, ry, W - 20, ROW_H - 2);
			ctx.fill(sx, ry, px + W - 10, ry + ROW_H - 2, on ? 0xFF3A2A12 : (hover ? HOVER : PANEL_LIGHT));

			var egg = SpawnEggItem.byId(t).orElse(null);
			if (egg != null) {
				ctx.item(new ItemStack(egg.value()), sx + 2, ry);
			}
			ctx.text(font, nameOf(t), sx + 24, ry + 5, on ? ACCENT : TEXT, false);

			// anahtar
			int kx = px + W - 10 - 34;
			ctx.fill(kx, ry + 3, kx + 28, ry + 15, on ? ACCENT : 0xFF444444);
			ctx.fill(on ? kx + 16 : kx + 2, ry + 5, on ? kx + 26 : kx + 12, ry + 13, TEXT);
		}

		if (list.isEmpty()) {
			ctx.text(font, "No matching mobs", sx + 6, listTop() + 6, TEXT_DIM, false);
		}
		ctx.text(font, "Selected: " + cfg.mobs.size(), px + W - 70, py + 10, TEXT_DIM, false);
	}

	private void renderFoods(GuiGraphicsExtractor ctx, int mx, int my) {
		Config cfg = Config.get();
		int sx = px + 10, sy = py + 50;
		ctx.fill(sx, sy, px + W - 10, sy + 20, PANEL_LIGHT);
		ctx.outline(sx, sy, W - 20, 20, ACCENT_DIM);
		String shown = search.isEmpty() ? "Search foods... (type to search)" : search + (System.currentTimeMillis() / 500 % 2 == 0 ? "_" : "");
		ctx.text(font, shown, sx + 6, sy + 6, search.isEmpty() ? TEXT_DIM : TEXT, false);

		List<Item> list = filteredFoods();
		int rows = visibleRows();
		int maxScroll = Math.max(0, list.size() - rows);
		scroll = Math.max(0, Math.min(maxScroll, scroll));

		for (int i = 0; i < rows; i++) {
			int idx = scroll + i;
			if (idx >= list.size()) break;
			Item t = list.get(idx);
			boolean on = cfg.foods.contains(BuiltInRegistries.ITEM.getKey(t).toString());
			int ry = listTop() + i * ROW_H;
			boolean hover = inside(mx, my, sx, ry, W - 20, ROW_H - 2);
			ctx.fill(sx, ry, px + W - 10, ry + ROW_H - 2, on ? 0xFF3A2A12 : (hover ? HOVER : PANEL_LIGHT));
			ctx.item(new ItemStack(t), sx + 2, ry);
			ctx.text(font, nameOf(t), sx + 24, ry + 5, on ? ACCENT : TEXT, false);
			int kx = px + W - 10 - 34;
			ctx.fill(kx, ry + 3, kx + 28, ry + 15, on ? ACCENT : 0xFF444444);
			ctx.fill(on ? kx + 16 : kx + 2, ry + 5, on ? kx + 26 : kx + 12, ry + 13, TEXT);
		}
		if (list.isEmpty()) {
			ctx.text(font, "No matching foods", sx + 6, listTop() + 6, TEXT_DIM, false);
		}
		ctx.text(font, "Selected: " + cfg.foods.size(), px + W - 70, py + 10, TEXT_DIM, false);
	}

	private void renderSettings(GuiGraphicsExtractor ctx, int mx, int my) {
		Config cfg = Config.get();
		int y = py + 56;
		settingRow(ctx, mx, my, y, "Search range", fmt(cfg.searchRange) + " blocks");
		settingRow(ctx, mx, my, y + 30, "Attack distance", fmt(cfg.attackDistance) + " blocks");
		settingRow(ctx, mx, my, y + 90, "Eat when hunger is at", (cfg.hungerThreshold / 2.0 == Math.floor(cfg.hungerThreshold / 2.0) ? String.valueOf(cfg.hungerThreshold / 2) : fmt(cfg.hungerThreshold / 2.0)) + " / 10");
		drawHungerBar(ctx, px + 14, y + 114, cfg.hungerThreshold);

		settingRow(ctx, mx, my, y + 60, "Attack interval", cfg.attackIntervalTicks + " ticks");

		// eşya toplama anahtarı
		int ry = y + 140;
		ctx.text(font, "Pick up items", px + 14, ry + 6, TEXT, false);
		int kx = px + W - 10 - 34;
		boolean on = cfg.pickup;
		ctx.fill(kx, ry + 3, kx + 28, ry + 15, on ? ACCENT : 0xFF444444);
		ctx.fill(on ? kx + 16 : kx + 2, ry + 5, on ? kx + 26 : kx + 12, ry + 13, TEXT);

		settingRow(ctx, mx, my, y + 166, "Stop when health is at", cfg.minHealth == 0 ? "Off" : fmt(cfg.minHealth / 2.0) + " hearts");
		toggleRow(ctx, y + 192, "Stop if sword is about to break", cfg.stopOnLowDurability);
		toggleRow(ctx, y + 218, "Stop when inventory is full", cfg.stopOnFullInventory);
	}

	private void toggleRow(GuiGraphicsExtractor ctx, int ry, String label, boolean on) {
		ctx.text(font, label, px + 14, ry + 6, TEXT, false);
		int kx = px + W - 10 - 34;
		ctx.fill(kx, ry + 3, kx + 28, ry + 15, on ? ACCENT : 0xFF444444);
		ctx.fill(on ? kx + 16 : kx + 2, ry + 5, on ? kx + 26 : kx + 12, ry + 13, TEXT);
	}

	/** Hunger bar: filled drumsticks = hunger level at which the bot starts eating. */
	private void drawHungerBar(GuiGraphicsExtractor ctx, int x, int y, int threshold) {
		for (int i = 0; i < 10; i++) {
			int ix = x + i * 18;
			ctx.fill(ix, y, ix + 17, y + 17, 0xFF2E2E2E);
			int filled = threshold - i * 2; // 2 = full, 1 = half, <=0 = empty
			if (filled >= 1) {
				ctx.item(new ItemStack(Items.COOKED_CHICKEN), ix, y);
				if (filled == 1) {
					ctx.fill(ix + 8, y, ix + 17, y + 17, 0xB0222222);
				}
			}
		}
	}

	private void settingRow(GuiGraphicsExtractor ctx, int mx, int my, int y, String label, String value) {
		ctx.text(font, label, px + 14, y + 6, TEXT, false);
		int minusX = px + W - 150;
		int plusX = px + W - 36;
		smallButton(ctx, mx, my, minusX, y, "-");
		ctx.centeredText(font, value, (minusX + 20 + plusX) / 2, y + 6, ACCENT);
		smallButton(ctx, mx, my, plusX, y, "+");
	}

	private void smallButton(GuiGraphicsExtractor ctx, int mx, int my, int x, int y, String label) {
		boolean hover = inside(mx, my, x, y, 20, 20);
		ctx.fill(x, y, x + 20, y + 20, hover ? ACCENT_DIM : PANEL_LIGHT);
		ctx.centeredText(font, label, x + 10, y + 6, TEXT);
	}

	private void renderStatus(GuiGraphicsExtractor ctx, int mx, int my) {
		Config cfg = Config.get();
		int y = py + 58;
		ctx.text(font, "State: ", px + 14, y, TEXT, false);
		ctx.text(font, AutoSpawnerClient.enabled ? "ON" : "OFF", px + 56, y, AutoSpawnerClient.enabled ? GREEN : RED, false);
		ctx.text(font, "Hits: " + AutoSpawnerClient.attackCount, px + 14, y + 16, TEXT, false);
		ctx.text(font, "Food left: " + AutoSpawnerClient.carrotsLeft(), px + 14, y + 32, TEXT, false);
		ctx.text(font, "Selected mobs: " + cfg.mobs.size(), px + 14, y + 48, TEXT, false);

		int bx = px + 14, by = y + 76, bw = 140;
		boolean hover = inside(mx, my, bx, by, bw, 22);
		ctx.fill(bx, by, bx + bw, by + 22, hover ? ACCENT : ACCENT_DIM);
		ctx.centeredText(font, AutoSpawnerClient.enabled ? "Turn off" : "Turn on", bx + bw / 2, by + 7, TEXT);
	}

	// ------------------------------------------------------------ girdi

	@Override
	public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
		int mx = (int) click.x(), my = (int) click.y();
		Config cfg = Config.get();

		// sekmeler
		int tabW = (W - 20) / 4;
		for (int i = 0; i < 4; i++) {
			if (inside(mx, my, px + 10 + i * tabW, py + 24, tabW - 4, 20)) {
				tab = i;
				scroll = 0;
				search = "";
				return true;
			}
		}

		if (tab == 0) {
			List<EntityType<?>> list = filteredMobs();
			int rows = visibleRows();
			for (int i = 0; i < rows; i++) {
				int idx = scroll + i;
				if (idx >= list.size()) break;
				int ry = listTop() + i * ROW_H;
				if (inside(mx, my, px + 10, ry, W - 20, ROW_H - 2)) {
					String id = BuiltInRegistries.ENTITY_TYPE.getKey(list.get(idx)).toString();
					if (!cfg.mobs.remove(id)) {
						cfg.mobs.add(id);
					}
					cfg.save();
					return true;
				}
			}
		} else if (tab == 1) {
			List<Item> list = filteredFoods();
			int rows = visibleRows();
			for (int i = 0; i < rows; i++) {
				int idx = scroll + i;
				if (idx >= list.size()) break;
				int ry = listTop() + i * ROW_H;
				if (inside(mx, my, px + 10, ry, W - 20, ROW_H - 2)) {
					String id = BuiltInRegistries.ITEM.getKey(list.get(idx)).toString();
					if (!cfg.foods.remove(id)) {
						cfg.foods.add(id);
					}
					cfg.save();
					return true;
				}
			}
		} else if (tab == 2) {
			int y = py + 56;
			int minusX = px + W - 150;
			int plusX = px + W - 36;
			if (inside(mx, my, minusX, y, 20, 20)) { cfg.searchRange -= 1; cfg.save(); return true; }
			if (inside(mx, my, plusX, y, 20, 20)) { cfg.searchRange += 1; cfg.save(); return true; }
			if (inside(mx, my, minusX, y + 30, 20, 20)) { cfg.attackDistance -= 0.25; cfg.save(); return true; }
			if (inside(mx, my, plusX, y + 30, 20, 20)) { cfg.attackDistance += 0.25; cfg.save(); return true; }
			if (inside(mx, my, minusX, y + 90, 20, 20)) { cfg.hungerThreshold -= 1; cfg.save(); return true; }
			if (inside(mx, my, plusX, y + 90, 20, 20)) { cfg.hungerThreshold += 1; cfg.save(); return true; }
			for (int i = 0; i < 10; i++) {
				int ix = px + 14 + i * 18;
				if (inside(mx, my, ix, y + 114, 18, 18)) {
					// left half = half drumstick, right half = full drumstick
					cfg.hungerThreshold = i * 2 + (mx < ix + 8 ? 1 : 2);
					cfg.save();
					return true;
				}
			}
			if (inside(mx, my, minusX, y + 60, 20, 20)) { cfg.attackIntervalTicks -= 1; cfg.save(); return true; }
			if (inside(mx, my, plusX, y + 60, 20, 20)) { cfg.attackIntervalTicks += 1; cfg.save(); return true; }
			if (inside(mx, my, px + 10, y + 140, W - 20, 20)) { cfg.pickup = !cfg.pickup; cfg.save(); return true; }
			if (inside(mx, my, minusX, y + 166, 20, 20)) { cfg.minHealth -= 1; cfg.save(); return true; }
			if (inside(mx, my, plusX, y + 166, 20, 20)) { cfg.minHealth += 1; cfg.save(); return true; }
			if (inside(mx, my, px + 10, y + 192, W - 20, 20)) { cfg.stopOnLowDurability = !cfg.stopOnLowDurability; cfg.save(); return true; }
			if (inside(mx, my, px + 10, y + 218, W - 20, 20)) { cfg.stopOnFullInventory = !cfg.stopOnFullInventory; cfg.save(); return true; }
		} else {
			int bx = px + 14, by = py + 58 + 76;
			if (inside(mx, my, bx, by, 140, 22)) {
				AutoSpawnerClient.enabled = !AutoSpawnerClient.enabled;
				return true;
			}
		}
		return super.mouseClicked(click, doubled);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (tab <= 1) {
			scroll -= (int) Math.signum(verticalAmount) * 2;
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	@Override
	public boolean charTyped(CharacterEvent input) {
		if (tab <= 1 && input.codepoint() >= 32 && input.codepoint() != 127 && search.length() < 24) {
			search += new String(Character.toChars(input.codepoint()));
			scroll = 0;
			return true;
		}
		return super.charTyped(input);
	}

	@Override
	public boolean keyPressed(KeyEvent input) {
		if (tab <= 1 && input.key() == InputConstants.KEY_BACKSPACE) {
			if (!search.isEmpty()) {
				search = search.substring(0, search.length() - 1);
				scroll = 0;
			}
			return true;
		}
		return super.keyPressed(input);
	}

	// ------------------------------------------------------------ yardımcılar

	private static boolean inside(int mx, int my, int x, int y, int w, int h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	private static String fmt(double v) {
		return v == Math.floor(v) ? String.valueOf((int) v) : String.format("%.2f", v).replace(',', '.');
	}
}
