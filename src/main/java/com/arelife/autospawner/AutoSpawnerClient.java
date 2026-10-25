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

	/** Kılıç: 1. slot (index 0). Altın havuç: 8. ve 9. slot (index 7 ve 8). */
	private static final int SWORD_SLOT = 0;
	private static final int[] FOOD_SLOTS = {7, 8};

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

	private static final java.util.Random RNG = new java.util.Random();
	/** Gezinme alanının merkezi (F10'a basıldığındaki konum). */
	private static Vec3d anchor = null;
	private static Vec3d wanderPoint = null;
	private static int wanderTicks = 0;
	/** Hedefe hangi açıdan yaklaşılacağı (radyan); her vuruştan sonra değişir. */
	private static double approachAngle = 0;
	private static int idleTicks = 0;
	private static int jumpTicksLeft = 0;
	/** En son vurulan canlı; sıradaki vuruş başka canlıya yapılır. */
	private static int lastHitId = -1;

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

		if (!enabled) {
			anchor = null;
			wanderPoint = null;
			lastHitId = -1;
		}
		if (!enabled || client.player == null || client.world == null || client.interactionManager == null) {
			return;
		}
		if (anchor == null) {
			anchor = client.player.getPos();
		}
		tickCounter++;
		if (ticksSinceAttack < 1000) ticksSinceAttack++;
		if (tickCounter % 20 == 0 && !eating) {
			client.player.sendMessage(statusText(), true);
		}

		Config cfg = Config.get();
		PlayerEntity player = client.player;

		// ---- Yemek ----
		int food = player.getHungerManager().getFoodLevel();
		if (eating) {
			int slot = findFoodSlot(player);
			if (food >= 20 || slot < 0) {
				eating = false;
				client.options.useKey.setPressed(false);
				player.getInventory().selectedSlot = SWORD_SLOT;
				if (slot < 0 && food < 20) {
					disableWithMessage(client, "Altın havuç bitti, mod kapandı");
				}
			} else {
				player.getInventory().selectedSlot = slot;
				client.options.useKey.setPressed(true);
				stopMovement(client);
				return;
			}
		} else if (food <= cfg.hungerThreshold) {
			int slot = findFoodSlot(player);
			if (slot < 0) {
				disableWithMessage(client, "Altın havuç bitti, mod kapandı");
				return;
			}
			eating = true;
			keysHeldByMod = true;
			player.getInventory().selectedSlot = slot;
			client.options.useKey.setPressed(true);
			stopMovement(client);
			return;
		}

		// ---- Kılıç ----
		if (player.getInventory().selectedSlot != SWORD_SLOT) {
			player.getInventory().selectedSlot = SWORD_SLOT;
		}

		// ---- Eşya toplama dönemi: her 100 tikte (5 sn) ilk 40 tik (2 sn) ----
		if (cfg.pickup && tickCounter % 100 < 40) {
			ItemEntity drop = findItem(client, cfg);
			if (drop != null) {
				aimedTargetId = -1;
				lookAt(player, drop.getPos());
				keysHeldByMod = true;
				stopMovement(client);
				client.options.forwardKey.setPressed(horizontalDistance(player, drop) > 0.4);
				return;
			}
		}

		// ---- Hedef ----
		LivingEntity target = findTarget(client, cfg);
		if (target != null) {
			lookAt(player, target.getPos().add(0, target.getHeight() * 0.5, 0));
			double dist = horizontalDistance(player, target);
			idleTicks = 0;
			wanderPoint = null;
			jumpTicksLeft = 0;
			client.options.jumpKey.setPressed(false);

			// Önceki tikte de bu hedefe bakıyorsak bakış sunucuya ulaşmıştır
			boolean alreadyAimed = (aimedTargetId == target.getId());
			aimedTargetId = target.getId();

			keysHeldByMod = true;
			if (cfg.roamSize > 0 && anchor != null) {
				// Hedefin çevresinde, seçilen açıdaki noktaya yürü (gezinme alanının içinde kalarak)
				double half = cfg.roamSize / 2.0;
				double gx = clamp(target.getX() + Math.cos(approachAngle) * cfg.attackDistance, anchor.x - half, anchor.x + half);
				double gz = clamp(target.getZ() + Math.sin(approachAngle) * cfg.attackDistance, anchor.z - half, anchor.z + half);
				moveToward(client, player, gx, gz, 0.25);
			} else {
				stopMovement(client);
				client.options.forwardKey.setPressed(dist > cfg.attackDistance);
			}

			if (alreadyAimed
					&& ticksSinceAttack >= cfg.attackIntervalTicks
					&& player.squaredDistanceTo(target) <= sq(cfg.attackDistance + 0.6)
					&& player.getAttackCooldownProgress(0.5f) >= 1.0f) {
				client.interactionManager.attackEntity(player, target);
				player.swingHand(Hand.MAIN_HAND);
				attackCount++;
				ticksSinceAttack = 0;
				approachAngle = RNG.nextDouble() * Math.PI * 2; // bir sonraki vuruş başka açıdan
				lastHitId = target.getId(); // öldürmeye odaklanma, sıradaki vuruş başka canlıya
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
				stopMovement(client);
				client.options.forwardKey.setPressed(horizontalDistance(player, item) > 0.4);
				return;
			}
		}

		// ---- Boşta: alanda gezin, ara sıra zıpla ----
		idleTicks++;
		if (cfg.roamSize > 0 && anchor != null) {
			double half = cfg.roamSize / 2.0;
			wanderTicks--;
			if (wanderPoint != null
					&& Math.hypot(wanderPoint.x - player.getX(), wanderPoint.z - player.getZ()) < 0.3) {
				wanderTicks = 0;
			}
			if (wanderPoint == null || wanderTicks <= 0) {
				wanderPoint = new Vec3d(
						anchor.x + (RNG.nextDouble() * 2 - 1) * half,
						anchor.y,
						anchor.z + (RNG.nextDouble() * 2 - 1) * half);
				wanderTicks = 40 + RNG.nextInt(60);
			}
			moveToward(client, player, wanderPoint.x, wanderPoint.z, 0.3);
		} else {
			stopMovement(client);
		}

		if (cfg.idleJump && idleTicks % 50 == 0) {
			jumpTicksLeft = 2;
		}
		if (jumpTicksLeft > 0) {
			keysHeldByMod = true;
			client.options.jumpKey.setPressed(true);
			jumpTicksLeft--;
		} else {
			client.options.jumpKey.setPressed(false);
		}
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
		// Son vurulan canlıyı atla; başka canlı yoksa ona devam et
		List<LivingEntity> others = list.stream().filter(e -> e.getId() != lastHitId).toList();
		List<LivingEntity> pool = others.isEmpty() ? list : others;
		return pool.stream().min(Comparator.comparingDouble(player::squaredDistanceTo)).orElse(null);
	}

	private static ItemEntity findItem(MinecraftClient client, Config cfg) {
		PlayerEntity player = client.player;
		Box box = player.getBoundingBox().expand(cfg.searchRange);
		List<ItemEntity> list = client.world.getEntitiesByClass(ItemEntity.class, box, e ->
				e.isAlive() && player.distanceTo(e) <= cfg.searchRange);
		return list.stream().min(Comparator.comparingDouble(player::squaredDistanceTo)).orElse(null);
	}

	/** 8. ve 9. slottaki altın havuç; ilk dolu olanın slot numarasını döner, yoksa -1. */
	private static int findFoodSlot(PlayerEntity player) {
		for (int slot : FOOD_SLOTS) {
			if (player.getInventory().getStack(slot).isOf(Items.GOLDEN_CARROT)) {
				return slot;
			}
		}
		return -1;
	}

	public static int carrotsLeft() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null) return 0;
		int total = 0;
		for (int slot : FOOD_SLOTS) {
			var stack = client.player.getInventory().getStack(slot);
			if (stack.isOf(Items.GOLDEN_CARROT)) total += stack.getCount();
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

	private static double clamp(double v, double lo, double hi) {
		return Math.max(lo, Math.min(hi, v));
	}

	/** Bakış yönünü değiştirmeden, hedef noktaya W/A/S/D ile yürür. */
	private static void moveToward(MinecraftClient client, PlayerEntity player, double gx, double gz, double tol) {
		double dx = gx - player.getX();
		double dz = gz - player.getZ();
		boolean fwd = false, back = false, left = false, right = false;
		if (Math.sqrt(dx * dx + dz * dz) > tol) {
			double yaw = Math.toRadians(player.getYaw());
			double f = dx * (-Math.sin(yaw)) + dz * Math.cos(yaw);     // ileri bileşeni
			double r = dx * (-Math.cos(yaw)) + dz * (-Math.sin(yaw));  // sağ bileşeni
			fwd = f > 0.15;
			back = f < -0.15;
			right = r > 0.15;
			left = r < -0.15;
		}
		client.options.forwardKey.setPressed(fwd);
		client.options.backKey.setPressed(back);
		client.options.leftKey.setPressed(left);
		client.options.rightKey.setPressed(right);
		keysHeldByMod = true;
	}

	/** Yürüme tuşlarını bırakır (zıplama ve kullanma hariç). */
	private static void stopMovement(MinecraftClient client) {
		client.options.forwardKey.setPressed(false);
		client.options.backKey.setPressed(false);
		client.options.leftKey.setPressed(false);
		client.options.rightKey.setPressed(false);
	}

	private static double sq(double v) {
		return v * v;
	}

	private static void disableWithMessage(MinecraftClient client, String message) {
		enabled = false;
		eating = false;
		releaseKeys(client);
		if (client.player != null) {
			client.player.getInventory().selectedSlot = SWORD_SLOT;
			client.player.sendMessage(Text.literal(message).formatted(Formatting.RED), true);
		}
	}

	/** Modun bastığı tuşları bırakır. */
	private static void releaseKeys(MinecraftClient client) {
		if (keysHeldByMod || eating) {
			stopMovement(client);
			client.options.jumpKey.setPressed(false);
			client.options.useKey.setPressed(false);
			keysHeldByMod = false;
			eating = false;
			jumpTicksLeft = 0;
			wanderPoint = null;
		}
	}

	public static Text statusText() {
		return enabled
				? Text.literal("Auto Spawner Mob Attack: AÇIK").formatted(Formatting.GREEN)
				: Text.literal("Auto Spawner Mob Attack: KAPALI").formatted(Formatting.RED);
	}
}
