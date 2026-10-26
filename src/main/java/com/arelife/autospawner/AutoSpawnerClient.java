package com.arelife.autospawner;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
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

	private static KeyBinding toggleKey;
	private static KeyBinding menuKey;

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

		toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key." + ID + ".toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_F10, "key.categories." + ID));
		menuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key." + ID + ".menu", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_K, "key.categories." + ID));

		ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			enabled = false;
			releaseKeys(client);
		});
	}

	private void onTick(MinecraftClient client) {
		while (toggleKey.wasPressed()) {
			enabled = !enabled;
			if (!enabled) releaseKeys(client);
			if (client.player != null) {
				client.player.sendMessage(statusText(), true);
			}
		}
		while (menuKey.wasPressed()) {
			if (client.currentScreen == null) {
				client.setScreen(new MenuScreen());
			}
		}

		if (!enabled || client.player == null || client.world == null || client.interactionManager == null) {
			return;
		}
		tickCounter++;
		if (ticksSinceAttack < 1000) ticksSinceAttack++;
		if (tickCounter % 20 == 0 && !eating) {
			client.player.sendMessage(statusText(), true);
		}

		Config cfg = Config.get();
		PlayerEntity player = client.player;

		// ---- Safety checks ----
		if (cfg.minHealth > 0 && player.getHealth() <= cfg.minHealth) {
			disableWithMessage(client, "Health too low, mod turned off");
			return;
		}
		if (cfg.stopOnLowDurability) {
			int sw = findSwordSlot(player);
			if (sw >= 0) {
				var sword = player.getInventory().getStack(sw);
				if (sword.getMaxDamage() > 0 && sword.getMaxDamage() - sword.getDamage() <= 10) {
					disableWithMessage(client, "Sword is about to break, mod turned off");
					return;
				}
			}
		}
		if (cfg.stopOnFullInventory && inventoryFull(player)) {
			disableWithMessage(client, "Inventory is full, mod turned off");
			return;
		}

		// ---- Yemek ----
		int food = player.getHungerManager().getFoodLevel();
		if (eating) {
			int slot = findFoodSlot(player);
			if (food >= 20 || slot < 0) {
				eating = false;
				client.options.useKey.setPressed(false);
				player.getInventory().selectedSlot = restoreSlot(player);
				if (slot < 0 && food < 20) {
					disableWithMessage(client, "Out of food, mod turned off");
				}
			} else {
				player.getInventory().selectedSlot = slot;
				client.options.useKey.setPressed(true);
				client.options.forwardKey.setPressed(false);
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
			prevSlot = player.getInventory().selectedSlot;
			player.getInventory().selectedSlot = slot;
			client.options.useKey.setPressed(true);
			client.options.forwardKey.setPressed(false);
			return;
		}

		// ---- Kılıç ----
		int swordSlot = findSwordSlot(player);
		if (swordSlot >= 0 && player.getInventory().selectedSlot != swordSlot) {
			player.getInventory().selectedSlot = swordSlot;
		}

		// ---- Eşya toplama dönemi: her 100 tikte (5 sn) ilk 40 tik (2 sn) ----
		if (cfg.pickup && tickCounter % 100 < 40) {
			ItemEntity drop = findItem(client, cfg);
			if (drop != null) {
				aimedTargetId = -1;
				lookAt(player, drop.getPos());
				keysHeldByMod = true;
				client.options.forwardKey.setPressed(horizontalDistance(player, drop) > 0.4);
				return;
			}
		}

		// ---- Hedef ----
		LivingEntity target = findTarget(client, cfg);
		if (target != null) {
			lookAt(player, target.getPos().add(0, target.getHeight() * 0.5, 0));
			double dist = horizontalDistance(player, target);

			// Önceki tikte de bu hedefe bakıyorsak bakış sunucuya ulaşmıştır
			boolean alreadyAimed = (aimedTargetId == target.getId());
			aimedTargetId = target.getId();

			keysHeldByMod = true;
			client.options.forwardKey.setPressed(dist > cfg.attackDistance);

			if (alreadyAimed
					&& ticksSinceAttack >= cfg.attackIntervalTicks
					&& player.squaredDistanceTo(target) <= sq(cfg.attackDistance + 0.6)
					&& player.getAttackCooldownProgress(0.5f) >= 1.0f) {
				client.interactionManager.attackEntity(player, target);
				player.swingHand(Hand.MAIN_HAND);
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
				lookAt(player, item.getPos());
				keysHeldByMod = true;
				client.options.forwardKey.setPressed(horizontalDistance(player, item) > 0.4);
				return;
			}
		}

		releaseKeys(client);
	}

	private static LivingEntity findTarget(MinecraftClient client, Config cfg) {
		PlayerEntity player = client.player;
		Box box = player.getBoundingBox().expand(cfg.searchRange);
		List<LivingEntity> list = client.world.getEntitiesByClass(LivingEntity.class, box, e ->
				e != player
						&& !(e instanceof PlayerEntity)
						&& e.isAlive()
						&& cfg.mobs.contains(Registries.ENTITY_TYPE.getId(e.getType()).toString())
						&& player.distanceTo(e) <= cfg.searchRange);
		return list.stream().min(Comparator.comparingDouble(player::squaredDistanceTo)).orElse(null);
	}

	private static ItemEntity findItem(MinecraftClient client, Config cfg) {
		PlayerEntity player = client.player;
		Box box = player.getBoundingBox().expand(cfg.searchRange);
		List<ItemEntity> list = client.world.getEntitiesByClass(ItemEntity.class, box, e ->
				e.isAlive() && player.distanceTo(e) <= cfg.searchRange);
		return list.stream().min(Comparator.comparingDouble(player::squaredDistanceTo)).orElse(null);
	}

	/** First hotbar slot holding a selected food, or -1. */
	private static boolean inventoryFull(PlayerEntity player) {
		for (int i = 0; i < 36; i++) {
			if (player.getInventory().getStack(i).isEmpty()) {
				return false;
			}
		}
		return true;
	}

	private static int findSwordSlot(PlayerEntity player) {
		for (int slot = 0; slot < 9; slot++) {
			if (player.getInventory().getStack(slot).isIn(ItemTags.SWORDS)) {
				return slot;
			}
		}
		return -1;
	}

	/** Slot to hold after eating: the sword if there is one, otherwise the slot held before eating. */
	private static int restoreSlot(PlayerEntity player) {
		int sword = findSwordSlot(player);
		return sword >= 0 ? sword : prevSlot;
	}

	private static int findFoodSlot(PlayerEntity player) {
		Config cfg = Config.get();
		for (int slot : FOOD_SLOTS) {
			var stack = player.getInventory().getStack(slot);
			if (!stack.isEmpty() && cfg.foods.contains(Registries.ITEM.getId(stack.getItem()).toString())) {
				return slot;
			}
		}
		return -1;
	}

	public static int carrotsLeft() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null) return 0;
		Config cfg = Config.get();
		int total = 0;
		for (int slot : FOOD_SLOTS) {
			var stack = client.player.getInventory().getStack(slot);
			if (!stack.isEmpty() && cfg.foods.contains(Registries.ITEM.getId(stack.getItem()).toString())) total += stack.getCount();
		}
		return total;
	}

	private static void lookAt(PlayerEntity player, Vec3d point) {
		Vec3d eye = player.getEyePos();
		double dx = point.x - eye.x;
		double dy = point.y - eye.y;
		double dz = point.z - eye.z;
		double horizontal = Math.sqrt(dx * dx + dz * dz);
		float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
		float pitch = (float) -Math.toDegrees(Math.atan2(dy, horizontal));
		player.setYaw(yaw);
		player.setPitch(pitch);
	}

	private static double horizontalDistance(PlayerEntity player, net.minecraft.entity.Entity e) {
		double dx = e.getX() - player.getX();
		double dz = e.getZ() - player.getZ();
		return Math.sqrt(dx * dx + dz * dz);
	}

	private static double sq(double v) {
		return v * v;
	}

	private static void disableWithMessage(MinecraftClient client, String message) {
		enabled = false;
		eating = false;
		releaseKeys(client);
		if (client.player != null) {
			client.player.getInventory().selectedSlot = restoreSlot(client.player);
			client.player.sendMessage(Text.literal(message).formatted(Formatting.RED), true);
		}
	}

	/** Modun bastığı tuşları bırakır. */
	private static void releaseKeys(MinecraftClient client) {
		if (keysHeldByMod || eating) {
			client.options.forwardKey.setPressed(false);
			client.options.useKey.setPressed(false);
			keysHeldByMod = false;
			eating = false;
		}
	}

	public static Text statusText() {
		return enabled
				? Text.literal("Auto Spawner Mob Attack: ON").formatted(Formatting.GREEN)
				: Text.literal("Auto Spawner Mob Attack: OFF").formatted(Formatting.RED);
	}
}
