package com.arelife.autospawner;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

/** Ayarlar. Menüden değişince otomatik kaydedilir. */
public class Config {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("auto_spawner_mob_attack.json");

	/** Vurulacak canlılar (örn. "minecraft:blaze"). */
	public Set<String> mobs = new LinkedHashSet<>(Set.of("minecraft:blaze"));
	/** Yenecek yiyecekler (any hotbar slot). */
	public Set<String> foods = new LinkedHashSet<>();
	/** False on first run: all foods get selected once. */
	public boolean foodsInitialized = false;
	/** Mobs within this distance are searched (blocks). */
	public double searchRange = 6.0;
	/** Hedefe bu kadar yaklaşılır (blok). */
	public double attackDistance = 1.0;
	/** Vuruşlar arası en az tik (20 tik = 1 sn). */
	public int attackIntervalTicks = 15;
	/** Açlık bu değer ve altına inince yemek yenir (20 = tam dolu, 14 = 7 kemik). */
	public int hungerThreshold = 14;
	/** Düşen eşyaları topla. */
	public boolean pickup = true;
	/** Turn the mod off when health is at or below this (half hearts). 0 = off. */
	public int minHealth = 6;
	/** Turn the mod off when the sword is about to break. */
	public boolean stopOnLowDurability = true;
	/** Turn the mod off when the inventory has no free slot. */
	public boolean stopOnFullInventory = true;

	private static Config instance;

	public static Config get() {
		if (instance == null) {
			instance = load();
			instance.ensureFoods();
		}
		return instance;
	}

	private static Config load() {
		try {
			if (Files.exists(FILE)) {
				Config c = GSON.fromJson(Files.readString(FILE), Config.class);
				if (c != null) {
					if (c.mobs == null) c.mobs = new LinkedHashSet<>();
					if (c.foods == null) c.foods = new LinkedHashSet<>();
					c.clamp();
					return c;
				}
			}
		} catch (Exception e) {
			System.err.println("[AutoSpawnerMobAttack] Ayar dosyası okunamadı, varsayılan kullanılıyor: " + e);
		}
		return new Config();
	}

	private void ensureFoods() {
		if (foodsInitialized) return;
		try {
			for (net.minecraft.item.Item item : FoodList.all()) {
				foods.add(net.minecraft.registry.Registries.ITEM.getId(item).toString());
			}
			foodsInitialized = true;
			save();
		} catch (Exception e) {
			System.err.println("[AutoSpawnerMobAttack] Could not fill food list: " + e);
		}
	}

	public void clamp() {
		searchRange = Math.max(2.0, Math.min(12.0, searchRange));
		attackDistance = Math.max(0.5, Math.min(3.0, attackDistance));
		hungerThreshold = Math.max(1, Math.min(19, hungerThreshold));
		minHealth = Math.max(0, Math.min(19, minHealth));
		attackIntervalTicks = Math.max(1, Math.min(40, attackIntervalTicks));
	}

	public void save() {
		clamp();
		try {
			Files.writeString(FILE, GSON.toJson(this));
		} catch (IOException e) {
			System.err.println("[AutoSpawnerMobAttack] Ayar dosyası kaydedilemedi: " + e);
		}
	}
}
