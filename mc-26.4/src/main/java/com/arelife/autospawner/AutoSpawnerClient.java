package com.arelife.autospawner;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.Comparator;
import java.util.List;

/**
 * Spawner farmı için istemci modu.
 * - F10: aç/kapa, K: menü.
 * - Listedeki canlıya yaklaşır, bakar, vurur. Oyunculara ve bloklara dokunmaz.
 * - Açlık eşiğin altına inince 8. ve 9. slottaki altın havucu yer.
 * - Canlı yokken yakındaki düşen eşyalara gider.
 */
public class AutoSpawnerClient implements ClientModInitializer {
	public static final String ID = "auto_spawner_mob_attack";

	/** Sword and food are found in the hotbar automatically. */
	private static final int SWORD_SLOT = 0;
	private static final int[] FOOD_SLOTS = {0, 1, 2, 3, 4, 5, 6, 7, 8};
	private static int prevSlot = 0;

	public static boolean enabled = false;
	public static int attackCount = 0;

	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(ID, "main"));

	private static KeyMapping toggleKey;
	private static KeyMapping menuKey;

	private static boolean eating = false;
	private static boolean keysHeldByMod = false;
	private static int tickCounter = 0;

	/** Vuruşlar arası en az bu kadar tik (kılıç dolumu ~12,5 tik; sunucu gecikmesine karşı pay). */
	private static final int MIN_ATTACK_TICKS = 15;
	private static int ticksSinceAttack = 100;
	private static int aimedTargetId = -1;

	@Override
	public void onInitializeClient() {
		Config.get();

		toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key." + ID + ".toggle", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F10, CATEGORY));
		menuKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key." + ID + ".menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, CATEGORY));

		ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			enabled = false;
			releaseKeys(client);
		});
	}

	private void onTick(Minecraft client) {
		while (toggleKey.consumeClick()) {
			enabled = !enabled;
			if (!enabled) releaseKeys(client);
			if (client.player != null) {
				client.gui.setOverlayMessage(statusText(), false);
			}
		}
		while (menuKey.consumeClick()) {
			if (client.screen == null) {
				client.setScreen(new MenuScreen());
			}
		}

		if (!enabled || client.player == null || client.level == null || client.gameMode == null) {
			return;
		}
		tickCounter++;
		if (ticksSinceAttack < 1000) ticksSinceAttack++;
		if (tickCounter % 20 == 0 && !eating) {
			client.gui.setOverlayMessage(statusText(), false);
		}

		Config cfg = Config.get();
		Player player = client.player;

		// ---- Yemek ----
		int food = player.getFoodData().getFoodLevel();
		if (eating) {
			int slot = findFoodSlot(player);
			if (food >= 20 || slot < 0) {
				eating = false;
				client.options.keyUse.setDown(false);
				player.getInventory().setSelectedSlot(restoreSlot(player));
				if (slot < 0 && food < 20) {
					disableWithMessage(client, "Out of food, mod turned off");
				}
			} else {
				player.getInventory().setSelectedSlot(slot);
				client.options.keyUse.setDown(true);
				client.options.keyUp.setDown(false);
				return;
			}
		} else if (food <= cfg.hungerThreshold) {
			int slot = findFoodSlot(player);
			if (slot < 0) {
				disableWithMessage(client, "Out of food, mod turned off");
				return;
			}
			eating = true;
			keysHeldByMod = true;
			prevSlot = player.getInventory().getSelectedSlot();
			player.getInventory().setSelectedSlot(slot);
			client.options.keyUse.setDown(true);
			client.options.keyUp.setDown(false);
			return;
		}

		// ---- Kılıç ----
		int swordSlot = findSwordSlot(player);
		if (swordSlot >= 0 && player.getInventory().getSelectedSlot() != swordSlot) {
			player.getInventory().setSelectedSlot(swordSlot);
		}

		// ---- Eşya toplama dönemi: her 100 tikte (5 sn) ilk 40 tik (2 sn) ----
		if (cfg.pickup && tickCounter % 100 < 40) {
			ItemEntity drop = findItem(client, cfg);
			if (drop != null) {
				aimedTargetId = -1;
				lookAt(player, drop.position());
				keysHeldByMod = true;
				client.options.keyUp.setDown(horizontalDistance(player, drop) > 0.4);
				return;
			}
		}

		// ---- Hedef ----
		LivingEntity target = findTarget(client, cfg);
		if (target != null) {
			lookAt(player, target.position().add(0, target.getBbHeight() * 0.5, 0));
			double dist = horizontalDistance(player, target);

			// Önceki tikte de bu hedefe bakıyorsak bakış sunucuya ulaşmıştır
			boolean alreadyAimed = (aimedTargetId == target.getId());
			aimedTargetId = target.getId();

			keysHeldByMod = true;
			client.options.keyUp.setDown(dist > cfg.attackDistance);

			if (alreadyAimed
					&& ticksSinceAttack >= cfg.attackIntervalTicks
					&& player.distanceToSqr(target) <= sq(cfg.attackDistance + 0.6)
					&& player.getAttackStrengthScale(0.5f) >= 1.0f) {
				client.gameMode.attack(player, target);
				player.swing(InteractionHand.MAIN_HAND);
				attackCount++;
				ticksSinceAttack = 0;
			}
			return;
		}

		aimedTargetId = -1;

		// ---- Eşya toplama ----
		if (cfg.pickup) {
			ItemEntity item = findItem(client, cfg);
			if (item != null) {
				lookAt(player, item.position());
				keysHeldByMod = true;
				client.options.keyUp.setDown(horizontalDistance(player, item) > 0.4);
				return;
			}
		}

		releaseKeys(client);
	}

	private static LivingEntity findTarget(Minecraft client, Config cfg) {
		Player player = client.player;
		AABB box = player.getBoundingBox().inflate(cfg.searchRange);
		List<LivingEntity> list = client.level.getEntitiesOfClass(LivingEntity.class, box, e ->
				e != player
						&& !(e instanceof Player)
						&& e.isAlive()
						&& cfg.mobs.contains(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString())
						&& player.distanceTo(e) <= cfg.searchRange);
		return list.stream().min(Comparator.comparingDouble(player::distanceToSqr)).orElse(null);
	}

	private static ItemEntity findItem(Minecraft client, Config cfg) {
		Player player = client.player;
		AABB box = player.getBoundingBox().inflate(cfg.searchRange);
		List<ItemEntity> list = client.level.getEntitiesOfClass(ItemEntity.class, box, e ->
				e.isAlive() && player.distanceTo(e) <= cfg.searchRange);
		return list.stream().min(Comparator.comparingDouble(player::distanceToSqr)).orElse(null);
	}

	/** First hotbar slot holding a selected food, or -1. */
	private static int findSwordSlot(Player player) {
		for (int slot = 0; slot < 9; slot++) {
			if (player.getInventory().getItem(slot).is(ItemTags.SWORDS)) {
				return slot;
			}
		}
		return -1;
	}

	/** Slot to hold after eating: the sword if there is one, otherwise the slot held before eating. */
	private static int restoreSlot(Player player) {
		int sword = findSwordSlot(player);
		return sword >= 0 ? sword : prevSlot;
	}

	private static int findFoodSlot(Player player) {
		Config cfg = Config.get();
		for (int slot : FOOD_SLOTS) {
			var stack = player.getInventory().getItem(slot);
			if (!stack.isEmpty() && cfg.foods.contains(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString())) {
				return slot;
			}
		}
		return -1;
	}

	public static int carrotsLeft() {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) return 0;
		Config cfg = Config.get();
		int total = 0;
		for (int slot : FOOD_SLOTS) {
			var stack = client.player.getInventory().getItem(slot);
			if (!stack.isEmpty() && cfg.foods.contains(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString())) total += stack.getCount();
		}
		return total;
	}

	private static void lookAt(Player player, Vec3 point) {
		Vec3 eye = player.getEyePosition();
		double dx = point.x - eye.x;
		double dy = point.y - eye.y;
		double dz = point.z - eye.z;
		double horizontal = Math.sqrt(dx * dx + dz * dz);
		float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
		float pitch = (float) -Math.toDegrees(Math.atan2(dy, horizontal));
		player.setYRot(yaw);
		player.setXRot(pitch);
	}

	private static double horizontalDistance(Player player, Entity e) {
		double dx = e.getX() - player.getX();
		double dz = e.getZ() - player.getZ();
		return Math.sqrt(dx * dx + dz * dz);
	}

	private static double sq(double v) {
		return v * v;
	}

	private static void disableWithMessage(Minecraft client, String message) {
		enabled = false;
		eating = false;
		releaseKeys(client);
		if (client.player != null) {
			client.player.getInventory().setSelectedSlot(restoreSlot(client.player));
			client.gui.setOverlayMessage(Component.literal(message).withStyle(ChatFormatting.RED), false);
		}
	}

	/** Modun bastığı tuşları bırakır. */
	private static void releaseKeys(Minecraft client) {
		if (keysHeldByMod || eating) {
			client.options.keyUp.setDown(false);
			client.options.keyUse.setDown(false);
			keysHeldByMod = false;
			eating = false;
		}
	}

	public static Component statusText() {
		return enabled
				? Component.literal("Auto Spawner Mob Attack: ON").withStyle(ChatFormatting.GREEN)
				: Component.literal("Auto Spawner Mob Attack: OFF").withStyle(ChatFormatting.RED);
	}
}
