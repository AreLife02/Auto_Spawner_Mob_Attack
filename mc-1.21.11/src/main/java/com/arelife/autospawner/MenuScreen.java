package com.arelife.autospawner;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Menü (K tuşu). Üç sekme: Canlılar, Ayarlar, Durum.
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
	private static final int H = 250;
	private static final int ROW_H = 20;

	private static List<EntityType<?>> ALL_MOBS;

	private int tab = 0; // 0 canlılar, 1 ayarlar, 2 durum
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
		// Seçililer üstte
		Config cfg = Config.get();
		out.sort(Comparator.comparing((EntityType<?> t) -> !cfg.mobs.contains(Registries.ENTITY_TYPE.getId(t).toString())));
		return out;
	}

	private int listTop() {
		return py + 80;
	}

	private int visibleRows() {
		return (py + H - 22 - listTop()) / ROW_H;
	}

	// ------------------------------------------------------------ çizim

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		ctx.fill(0, 0, width, height, 0x90000000);
		ctx.fill(px - 1, py - 1, px + W + 1, py + H + 1, ACCENT);
		ctx.fill(px, py, px + W, py + H, PANEL);

		ctx.drawText(textRenderer, "AUTO SPAWNER MOB ATTACK", px + 10, py + 8, ACCENT, true);

		String[] tabs = {"Canlılar", "Ayarlar", "Durum"};
		int tabW = (W - 20) / 3;
		for (int i = 0; i < 3; i++) {
			int tx = px + 10 + i * tabW;
			int ty = py + 24;
			boolean hover = inside(mouseX, mouseY, tx, ty, tabW - 4, 20);
			ctx.fill(tx, ty, tx + tabW - 4, ty + 20, i == tab ? ACCENT : (hover ? HOVER : PANEL_LIGHT));
			ctx.drawCenteredTextWithShadow(textRenderer, tabs[i], tx + (tabW - 4) / 2, ty + 6, i == tab ? 0xFF111111 : TEXT);
		}

		switch (tab) {
			case 0 -> renderMobs(ctx, mouseX, mouseY);
			case 1 -> renderSettings(ctx, mouseX, mouseY);
			default -> renderStatus(ctx, mouseX, mouseY);
		}

		ctx.drawText(textRenderer, "F10: aç/kapa   K: menü   ESC: kapat", px + 10, py + H - 14, TEXT_DIM, false);
	}

	private void renderMobs(DrawContext ctx, int mx, int my) {
		Config cfg = Config.get();
		int sx = px + 10, sy = py + 50;
		ctx.fill(sx, sy, px + W - 10, sy + 20, PANEL_LIGHT);
		ctx.drawStrokedRectangle(sx, sy, W - 20, 20, ACCENT_DIM);
		String shown = search.isEmpty() ? "Ara... (yazarak ara)" : search + (System.currentTimeMillis() / 500 % 2 == 0 ? "_" : "");
		ctx.drawText(textRenderer, shown, sx + 6, sy + 6, search.isEmpty() ? TEXT_DIM : TEXT, false);

		List<EntityType<?>> list = filteredMobs();
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

		if (list.isEmpty()) {
			ctx.drawText(textRenderer, "Eşleşen canlı yok", sx + 6, listTop() + 6, TEXT_DIM, false);
		}
		ctx.drawText(textRenderer, "Seçili: " + cfg.mobs.size(), px + W - 70, py + 10, TEXT_DIM, false);
	}

	private void renderSettings(DrawContext ctx, int mx, int my) {
		Config cfg = Config.get();
		int y = py + 56;
		settingRow(ctx, mx, my, y, "Arama menzili", fmt(cfg.searchRange) + " blok");
		settingRow(ctx, mx, my, y + 30, "Vuruş mesafesi", fmt(cfg.attackDistance) + " blok");
		settingRow(ctx, mx, my, y + 60, "Açlık eşiği", cfg.hungerThreshold + " (" + fmt(cfg.hungerThreshold / 2.0) + " kemik)");

		settingRow(ctx, mx, my, y + 90, "Vuruş aralığı", cfg.attackIntervalTicks + " tik");

		// eşya toplama anahtarı
		int ry = y + 120;
		ctx.drawText(textRenderer, "Eşya topla", px + 14, ry + 6, TEXT, false);
		int kx = px + W - 10 - 34;
		boolean on = cfg.pickup;
		ctx.fill(kx, ry + 3, kx + 28, ry + 15, on ? ACCENT : 0xFF444444);
		ctx.fill(on ? kx + 16 : kx + 2, ry + 5, on ? kx + 26 : kx + 12, ry + 13, TEXT);

		ctx.drawText(textRenderer, "Kılıç: 1. slot   Altın havuç: 8. ve 9. slot", px + 14, ry + 30, TEXT_DIM, false);
	}

	private void settingRow(DrawContext ctx, int mx, int my, int y, String label, String value) {
		ctx.drawText(textRenderer, label, px + 14, y + 6, TEXT, false);
		int minusX = px + W - 150;
		int plusX = px + W - 36;
		smallButton(ctx, mx, my, minusX, y, "-");
		ctx.drawCenteredTextWithShadow(textRenderer, value, (minusX + 20 + plusX) / 2, y + 6, ACCENT);
		smallButton(ctx, mx, my, plusX, y, "+");
	}

	private void smallButton(DrawContext ctx, int mx, int my, int x, int y, String label) {
		boolean hover = inside(mx, my, x, y, 20, 20);
		ctx.fill(x, y, x + 20, y + 20, hover ? ACCENT_DIM : PANEL_LIGHT);
		ctx.drawCenteredTextWithShadow(textRenderer, label, x + 10, y + 6, TEXT);
	}

	private void renderStatus(DrawContext ctx, int mx, int my) {
		Config cfg = Config.get();
		int y = py + 58;
		ctx.drawText(textRenderer, "Durum: ", px + 14, y, TEXT, false);
		ctx.drawText(textRenderer, AutoSpawnerClient.enabled ? "AÇIK" : "KAPALI", px + 56, y, AutoSpawnerClient.enabled ? GREEN : RED, false);
		ctx.drawText(textRenderer, "Yapılan vuruş: " + AutoSpawnerClient.attackCount, px + 14, y + 16, TEXT, false);
		ctx.drawText(textRenderer, "Kalan altın havuç: " + AutoSpawnerClient.carrotsLeft(), px + 14, y + 32, TEXT, false);
		ctx.drawText(textRenderer, "Seçili canlı sayısı: " + cfg.mobs.size(), px + 14, y + 48, TEXT, false);

		int bx = px + 14, by = y + 76, bw = 140;
		boolean hover = inside(mx, my, bx, by, bw, 22);
		ctx.fill(bx, by, bx + bw, by + 22, hover ? ACCENT : ACCENT_DIM);
		ctx.drawCenteredTextWithShadow(textRenderer, AutoSpawnerClient.enabled ? "Kapat" : "Aç", bx + bw / 2, by + 7, TEXT);
	}

	// ------------------------------------------------------------ girdi

	@Override
	public boolean mouseClicked(Click click, boolean doubled) {
		int mx = (int) click.x(), my = (int) click.y();
		Config cfg = Config.get();

		// sekmeler
		int tabW = (W - 20) / 3;
		for (int i = 0; i < 3; i++) {
			if (inside(mx, my, px + 10 + i * tabW, py + 24, tabW - 4, 20)) {
				tab = i;
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
					String id = Registries.ENTITY_TYPE.getId(list.get(idx)).toString();
					if (!cfg.mobs.remove(id)) {
						cfg.mobs.add(id);
					}
					cfg.save();
					return true;
				}
			}
		} else if (tab == 1) {
			int y = py + 56;
			int minusX = px + W - 150;
			int plusX = px + W - 36;
			if (inside(mx, my, minusX, y, 20, 20)) { cfg.searchRange -= 1; cfg.save(); return true; }
			if (inside(mx, my, plusX, y, 20, 20)) { cfg.searchRange += 1; cfg.save(); return true; }
			if (inside(mx, my, minusX, y + 30, 20, 20)) { cfg.attackDistance -= 0.25; cfg.save(); return true; }
			if (inside(mx, my, plusX, y + 30, 20, 20)) { cfg.attackDistance += 0.25; cfg.save(); return true; }
			if (inside(mx, my, minusX, y + 60, 20, 20)) { cfg.hungerThreshold -= 1; cfg.save(); return true; }
			if (inside(mx, my, plusX, y + 60, 20, 20)) { cfg.hungerThreshold += 1; cfg.save(); return true; }
			if (inside(mx, my, minusX, y + 90, 20, 20)) { cfg.attackIntervalTicks -= 1; cfg.save(); return true; }
			if (inside(mx, my, plusX, y + 90, 20, 20)) { cfg.attackIntervalTicks += 1; cfg.save(); return true; }
			if (inside(mx, my, px + 10, y + 120, W - 20, 20)) { cfg.pickup = !cfg.pickup; cfg.save(); return true; }
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
		if (tab == 0) {
			scroll -= (int) Math.signum(verticalAmount) * 2;
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	@Override
	public boolean charTyped(CharInput input) {
		if (tab == 0 && input.isValidChar() && search.length() < 24) {
			search += input.asString();
			scroll = 0;
			return true;
		}
		return super.charTyped(input);
	}

	@Override
	public boolean keyPressed(KeyInput input) {
		if (tab == 0 && input.key() == GLFW.GLFW_KEY_BACKSPACE) {
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
