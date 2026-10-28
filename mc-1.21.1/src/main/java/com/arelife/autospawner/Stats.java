package com.arelife.autospawner;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/** Session statistics: kills per mob type and collected items per item type. Reset on game start or with the menu button. */
public final class Stats {
	public static final Map<String, Integer> kills = new LinkedHashMap<>();
	public static final Map<String, Integer> items = new LinkedHashMap<>();
	public static final Map<String, String> names = new HashMap<>();
	public static final Map<String, ItemStack> icons = new HashMap<>();
	/** Ticks the mod has been on (50 ms each). */
	public static long activeTicks = 0;

	/** Mobs we hit that have not died yet. */
	private static final Map<Integer, LivingEntity> hit = new HashMap<>();
	private static Map<String, Integer> lastInventory = null;
	private static int counter = 0;

	private Stats() {
	}

	public static void reset() {
		kills.clear();
		items.clear();
		hit.clear();
		lastInventory = null;
		activeTicks = 0;
		AutoSpawnerClient.attackCount = 0;
	}

	public static void onHit(LivingEntity target) {
		hit.put(target.getId(), target);
		String id = Registries.ENTITY_TYPE.getId(target.getType()).toString();
		names.putIfAbsent(id, target.getType().getName().getString());
	}

	public static void tick(MinecraftClient client) {
		activeTicks++;

		// A mob we hit counts as killed once its health is 0. Mobs that just disappear are not counted.
		Iterator<Map.Entry<Integer, LivingEntity>> it = hit.entrySet().iterator();
		while (it.hasNext()) {
			LivingEntity e = it.next().getValue();
			if (e.getHealth() <= 0.0f) {
				kills.merge(Registries.ENTITY_TYPE.getId(e.getType()).toString(), 1, Integer::sum);
				it.remove();
			} else if (e.isRemoved()) {
				it.remove();
			}
		}

		// Items: only increases of the inventory counts are added up.
		if (++counter % 10 != 0 || client.player == null) {
			return;
		}
		PlayerEntity player = client.player;
		Map<String, Integer> now = new HashMap<>();
		for (int i = 0; i < 36; i++) {
			ItemStack stack = player.getInventory().getStack(i);
			if (stack.isEmpty()) continue;
			String id = Registries.ITEM.getId(stack.getItem()).toString();
			now.merge(id, stack.getCount(), Integer::sum);
			if (!names.containsKey(id)) {
				names.put(id, stack.getName().getString());
				icons.put(id, new ItemStack(stack.getItem()));
			}
		}
		if (lastInventory != null) {
			for (Map.Entry<String, Integer> e : now.entrySet()) {
				int before = lastInventory.getOrDefault(e.getKey(), 0);
				if (e.getValue() > before) {
					items.merge(e.getKey(), e.getValue() - before, Integer::sum);
				}
			}
		}
		lastInventory = now;
	}

	public static int totalKills() {
		int n = 0;
		for (int v : kills.values()) n += v;
		return n;
	}

	/** Per-hour rate as text, or "-" while there is not enough data. */
	public static String perHour(int count) {
		if (activeTicks < 1200) return "-";
		return String.format("%.0f/h", count * 72000.0 / activeTicks);
	}

	/** Entries sorted by count, highest first. */
	public static java.util.List<Map.Entry<String, Integer>> top(Map<String, Integer> map, int limit) {
		java.util.List<Map.Entry<String, Integer>> list = new java.util.ArrayList<>(map.entrySet());
		list.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
		return list.size() > limit ? list.subList(0, limit) : list;
	}
}
