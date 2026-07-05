package com.lostglade.server;

import com.lostglade.item.ModItems;
import com.lostglade.item.RainbowHarnessItem;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;

public final class RainbowHarnessColorSystem {
	private RainbowHarnessColorSystem() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(RainbowHarnessColorSystem::tick);
	}

	private static void tick(MinecraftServer server) {
		if (server == null) {
			return;
		}
		for (ServerLevel level : server.getAllLevels()) {
			Equippable frameEquippable = RainbowHarnessItem.frameEquippableForGameTime(level.getGameTime());
			for (HappyGhast ghast : level.getEntities(EntityType.HAPPY_GHAST, HappyGhast::isAlive)) {
				updateHarnessFrame(ghast, frameEquippable);
			}
		}
	}

	private static void updateHarnessFrame(HappyGhast ghast, Equippable frameEquippable) {
		ItemStack stack = ghast.getItemBySlot(EquipmentSlot.BODY);
		if (!stack.is(ModItems.RAINBOW_HARNESS) || RainbowHarnessItem.hasAnimatedFrame(stack, frameEquippable)) {
			return;
		}
		RainbowHarnessItem.setAnimatedFrame(stack, frameEquippable);
	}
}
