package com.arelife.autospawner;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/** List of foods shown in the menu: common foods first, harmful ones left out. */
public final class FoodList {
	private static final List<String> PRIORITY = List.of(
			"golden_carrot", "cooked_beef", "cooked_porkchop", "golden_apple", "enchanted_golden_apple",
			"cooked_mutton", "cooked_chicken", "cooked_salmon", "cooked_cod", "cooked_rabbit", "bread",
			"baked_potato", "pumpkin_pie", "carrot", "apple", "melon_slice", "sweet_berries", "glow_berries",
			"cookie", "dried_kelp", "beetroot", "beetroot_soup", "mushroom_stew", "rabbit_stew", "cake");

	/** Foods with bad effects or that are not real foods; never listed. */
	private static final Set<String> BAD = Set.of(
			"pufferfish", "spider_eye", "rotten_flesh", "poisonous_potato", "chorus_fruit", "chicken",
			"suspicious_stew", "ominous_bottle", "honey_bottle", "milk_bucket", "potion", "water_bucket",
			"tropical_fish", "beef", "porkchop", "mutton", "rabbit", "cod", "salmon", "potato");

	private static List<Item> all;

	private FoodList() {
	}

	private static int rank(Item item) {
		int i = PRIORITY.indexOf(Registries.ITEM.getId(item).getPath());
		return i < 0 ? 1000 : i;
	}

	public static List<Item> all() {
		if (all == null) {
			List<Item> list = new ArrayList<>();
			for (Item item : Registries.ITEM) {
				if (item.getComponents().contains(DataComponentTypes.FOOD)
						&& !BAD.contains(Registries.ITEM.getId(item).getPath())) {
					list.add(item);
				}
			}
			list.sort(Comparator.comparingInt(FoodList::rank).thenComparing(t -> t.getName().getString()));
			all = list;
		}
		return all;
	}
}
