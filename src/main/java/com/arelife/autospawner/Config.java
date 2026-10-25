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
	/** Bu mesafedeki canlılar aranır (blok). */
	public double searchRange = 6.0;
	/** Hedefe bu kadar yaklaşılır (blok). */
	public double attackDistance = 1.0;
	/** Vuruşlar arası en az tik (20 tik = 1 sn). */
	public int attackIntervalTicks = 15;
	/** Açlık bu değer ve altına inince yemek yenir (20 = tam dolu, 14 = 7 kemik). */
	public int hungerThreshold = 14;
	/** Düşen eşyaları topla. */
	public boolean pickup = true;

	private static Config instance;

	public static Config get() {
		if (instance == null) {
			instance = load();
		}
		return instance;
	}

	private static Config load() {
		try {
			if (Files.exists(FILE)) {
				Config c = GSON.fromJson(Files.readString(FILE), Config.class);
				if (c != null) {
					if (c.mobs == null) c.mobs = new LinkedHashSet<>();
					c.clamp();
					return c;
				}
			}
		} catch (Exception e) {
			System.err.println("[AutoSpawnerMobAttack] Ayar dosyası okunamadı, varsayılan kullanılıyor: " + e);
		}
		return new Config();
	}

	public void clamp() {
		searchRange = Math.max(2.0, Math.min(12.0, searchRange));
		attackDistance = Math.max(0.5, Math.min(3.0, attackDistance));
		hungerThreshold = Math.max(1, Math.min(19, hungerThreshold));
		attackIntervalTicks = Math.max(10, Math.min(40, attackIntervalTicks));
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
