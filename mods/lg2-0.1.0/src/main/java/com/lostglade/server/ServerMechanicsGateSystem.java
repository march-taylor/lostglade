package com.lostglade.server;

import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.BasePressurePlateBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Set;

public final class ServerMechanicsGateSystem {
	private static final String REDSTONE = "mechanic_redstone";
	private static final String BREWING = "mechanic_brewing";
	private static final String ANVIL = "mechanic_anvil";
	private static final String ENCHANTING = "mechanic_enchanting";
	private static final String VILLAGERS = "mechanic_villagers";

	private static final Set<Block> REDSTONE_BLOCKS = Set.of(
			Blocks.REDSTONE_WIRE,
			Blocks.REDSTONE_TORCH,
			Blocks.REDSTONE_WALL_TORCH,
			Blocks.REPEATER,
			Blocks.COMPARATOR,
			Blocks.OBSERVER,
			Blocks.DISPENSER,
			Blocks.DROPPER,
			Blocks.PISTON,
			Blocks.STICKY_PISTON,
			Blocks.LEVER,
			Blocks.NOTE_BLOCK,
			Blocks.DAYLIGHT_DETECTOR,
			Blocks.TARGET,
			Blocks.TRIPWIRE,
			Blocks.TRIPWIRE_HOOK,
			Blocks.HOPPER,
			Blocks.REDSTONE_LAMP,
			Blocks.POWERED_RAIL,
			Blocks.DETECTOR_RAIL,
			Blocks.ACTIVATOR_RAIL,
			Blocks.CRAFTER,
			Blocks.SCULK_SENSOR,
			Blocks.CALIBRATED_SCULK_SENSOR
	);

	private ServerMechanicsGateSystem() {
	}

	public static void register() {
		UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
			if (world.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
				return InteractionResult.PASS;
			}

			BlockState state = world.getBlockState(hitResult.getBlockPos());
			String interactionRequirement = requiredUpgradeForInteraction(state);
			if (interactionRequirement != null && !ServerUpgradeUiSystem.hasUpgrade(serverPlayer, interactionRequirement)) {
				return InteractionResult.FAIL;
			}

			return InteractionResult.PASS;
		});

		AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
			if (world.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
				return InteractionResult.PASS;
			}

			String requirement = requiredUpgradeForBreaking(world.getBlockState(pos));
			if (requirement != null && !ServerUpgradeUiSystem.hasUpgrade(serverPlayer, requirement)) {
				return InteractionResult.SUCCESS;
			}

			return InteractionResult.PASS;
		});

		PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
			if (!(player instanceof ServerPlayer serverPlayer)) {
				return true;
			}

			String requirement = requiredUpgradeForBreaking(state);
			return requirement == null || ServerUpgradeUiSystem.hasUpgrade(serverPlayer, requirement);
		});

		UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			if (world.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
				return InteractionResult.PASS;
			}

			String requirement = requiredUpgradeForEntityInteraction(entity);
			if (requirement != null && !ServerUpgradeUiSystem.hasUpgrade(serverPlayer, requirement)) {
				return InteractionResult.FAIL;
			}

			return InteractionResult.PASS;
		});
	}

	public static boolean canTakeCraftResult(ServerPlayer player, ItemStack stack) {
		String requirement = requiredUpgradeForCraftResult(stack);
		return requirement == null || ServerUpgradeUiSystem.hasUpgrade(player, requirement);
	}

	public static boolean canPlaceBlock(ServerPlayer player, Block block) {
		String requirement = requiredUpgradeForBlock(block);
		return requirement == null || ServerUpgradeUiSystem.hasUpgrade(player, requirement);
	}

	private static String requiredUpgradeForCraftResult(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return null;
		}
		if (stack.getItem() instanceof BlockItem blockItem) {
			return requiredUpgradeForBlock(blockItem.getBlock());
		}
		return null;
	}

	private static String requiredUpgradeForInteraction(BlockState state) {
		if (state == null) {
			return null;
		}
		Block block = state.getBlock();
		if (block instanceof ButtonBlock || block instanceof BasePressurePlateBlock) {
			return null;
		}
		return requiredUpgradeForBlock(block);
	}

	private static String requiredUpgradeForBreaking(BlockState state) {
		if (state == null) {
			return null;
		}
		return requiredUpgradeForBlock(state.getBlock());
	}

	private static String requiredUpgradeForEntityInteraction(Entity entity) {
		if (entity instanceof AbstractVillager) {
			return VILLAGERS;
		}
		return null;
	}

	private static String requiredUpgradeForBlock(Block block) {
		if (block == null || block == Blocks.AIR) {
			return null;
		}
		if (block instanceof AnvilBlock) {
			return ANVIL;
		}
		if (block == Blocks.BREWING_STAND) {
			return BREWING;
		}
		if (block == Blocks.ENCHANTING_TABLE) {
			return ENCHANTING;
		}
		if (REDSTONE_BLOCKS.contains(block) || block instanceof ButtonBlock || block instanceof BasePressurePlateBlock) {
			return REDSTONE;
		}
		return null;
	}
}
