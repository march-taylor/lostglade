package com.lostglade.server;

import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.vehicle.minecart.MinecartHopper;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
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
	private static final String ERA_STONE = "era_stone";
	private static final String ERA_COPPER = "era_copper";
	private static final String ERA_IRON_GOLD = "era_iron_gold";
	private static final String ERA_DIAMOND = "era_diamond";
	private static final String ERA_NETHERITE = "era_netherite";

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
	private static final Set<Item> STONE_ERA_ITEMS = Set.of(
			Items.STONE_PICKAXE,
			Items.STONE_AXE,
			Items.STONE_SHOVEL,
			Items.STONE_HOE,
			Items.STONE_SWORD
	);
	private static final Set<Item> IRON_AND_GOLD_ERA_ITEMS = Set.of(
			Items.IRON_PICKAXE,
			Items.IRON_AXE,
			Items.IRON_SHOVEL,
			Items.IRON_HOE,
			Items.IRON_SWORD,
			Items.IRON_HELMET,
			Items.IRON_CHESTPLATE,
			Items.IRON_LEGGINGS,
			Items.IRON_BOOTS,
			Items.GOLDEN_PICKAXE,
			Items.GOLDEN_AXE,
			Items.GOLDEN_SHOVEL,
			Items.GOLDEN_HOE,
			Items.GOLDEN_SWORD,
			Items.GOLDEN_HELMET,
			Items.GOLDEN_CHESTPLATE,
			Items.GOLDEN_LEGGINGS,
			Items.GOLDEN_BOOTS,
			Items.GOLDEN_APPLE,
			Items.GOLDEN_CARROT,
			Items.GLISTERING_MELON_SLICE
	);
	private static final Set<Item> DIAMOND_ERA_ITEMS = Set.of(
			Items.DIAMOND_PICKAXE,
			Items.DIAMOND_AXE,
			Items.DIAMOND_SHOVEL,
			Items.DIAMOND_HOE,
			Items.DIAMOND_SWORD,
			Items.DIAMOND_HELMET,
			Items.DIAMOND_CHESTPLATE,
			Items.DIAMOND_LEGGINGS,
			Items.DIAMOND_BOOTS,
			Items.SHIELD
	);
	private static final Set<Item> NETHERITE_ERA_ITEMS = Set.of(
			Items.NETHERITE_PICKAXE,
			Items.NETHERITE_AXE,
			Items.NETHERITE_SHOVEL,
			Items.NETHERITE_HOE,
			Items.NETHERITE_SWORD,
			Items.NETHERITE_HELMET,
			Items.NETHERITE_CHESTPLATE,
			Items.NETHERITE_LEGGINGS,
			Items.NETHERITE_BOOTS
	);
	private static final Set<String> COPPER_GEAR_SUFFIXES = Set.of(
			"pickaxe",
			"axe",
			"shovel",
			"hoe",
			"sword",
			"helmet",
			"chestplate",
			"leggings",
			"boots"
	);
	private static final Set<Block> IRON_GOLEM_BODY_BLOCKS = Set.of(Blocks.IRON_BLOCK);
	private static final Set<Block> COPPER_GOLEM_BODY_BLOCKS = Set.of(
			Blocks.COPPER_BLOCK,
			Blocks.EXPOSED_COPPER,
			Blocks.WEATHERED_COPPER,
			Blocks.OXIDIZED_COPPER,
			Blocks.WAXED_COPPER_BLOCK,
			Blocks.WAXED_EXPOSED_COPPER,
			Blocks.WAXED_WEATHERED_COPPER,
			Blocks.WAXED_OXIDIZED_COPPER
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

		ServerTickEvents.END_SERVER_TICK.register(ServerMechanicsGateSystem::tickIllegalItems);
	}

	public static boolean canTakeCraftResult(ServerPlayer player, ItemStack stack) {
		if (player == null) {
			return true;
		}
		String requirement = requiredUpgradeForCraftResult(stack);
		return requirement == null || ServerUpgradeUiSystem.hasUpgrade(player, requirement);
	}

	public static boolean canPlaceBlock(ServerPlayer player, Block block) {
		if (player == null) {
			return true;
		}
		String requirement = requiredUpgradeForPlacedBlock(null, block);
		return requirement == null || ServerUpgradeUiSystem.hasUpgrade(player, requirement);
	}

	public static boolean canPlaceBlock(ServerPlayer player, BlockPlaceContext context, Block block) {
		if (player == null) {
			return true;
		}
		String requirement = requiredUpgradeForPlacedBlock(context, block);
		return requirement == null || ServerUpgradeUiSystem.hasUpgrade(player, requirement);
	}

	public static boolean canInteractWithBlock(ServerPlayer player, Block block) {
		if (player == null || block == null) {
			return true;
		}
		String requirement = requiredUpgradeForInteraction(block.defaultBlockState());
		return requirement == null || ServerUpgradeUiSystem.hasUpgrade(player, requirement);
	}

	public static boolean canOwnItem(ServerPlayer player, ItemStack stack) {
		if (player == null) {
			return true;
		}
		String requirement = requiredUpgradeForCraftResult(stack);
		return requirement == null || ServerUpgradeUiSystem.hasUpgrade(player, requirement);
	}

	private static String requiredUpgradeForCraftResult(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return null;
		}
		Item item = stack.getItem();
		String eraRequirement = requiredEraForItem(item);
		if (eraRequirement != null) {
			return eraRequirement;
		}
		if (item instanceof BlockItem blockItem) {
			return requiredUpgradeForBlock(blockItem.getBlock());
		}
		return null;
	}

	private static String requiredEraForItem(Item item) {
		if (item == null || item == Items.AIR) {
			return null;
		}
		if (STONE_ERA_ITEMS.contains(item)) {
			return ERA_STONE;
		}
		if (isCopperEraItem(item)) {
			return ERA_COPPER;
		}
		if (IRON_AND_GOLD_ERA_ITEMS.contains(item) || hasItemPathToken(item, "iron_golem")) {
			return ERA_IRON_GOLD;
		}
		if (DIAMOND_ERA_ITEMS.contains(item)) {
			return ERA_DIAMOND;
		}
		if (NETHERITE_ERA_ITEMS.contains(item)) {
			return ERA_NETHERITE;
		}
		return null;
	}

	private static boolean isCopperEraItem(Item item) {
		if (hasItemPathToken(item, "copper_golem")) {
			return true;
		}
		Identifier id = BuiltInRegistries.ITEM.getKey(item);
		if (id == null) {
			return false;
		}
		String path = id.getPath();
		for (String suffix : COPPER_GEAR_SUFFIXES) {
			if (path.equals("copper_" + suffix) || path.equals(suffix + "_copper")) {
				return true;
			}
		}
		return false;
	}

	private static boolean hasItemPathToken(Item item, String token) {
		Identifier id = BuiltInRegistries.ITEM.getKey(item);
		return id != null && id.getPath().contains(token);
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
		if (entity instanceof MinecartHopper) {
			return REDSTONE;
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

	private static String requiredUpgradeForPlacedBlock(BlockPlaceContext context, Block block) {
		String requirement = requiredUpgradeForBlock(block);
		if (requirement != null || context == null || !isGolemHeadBlock(block)) {
			return requirement;
		}

		BlockPos headPos = resolvePlacementPos(context);
		if (headPos == null) {
			return null;
		}
		Level level = context.getLevel();
		if (level == null) {
			return null;
		}

		if (wouldFormGolem(level, headPos, IRON_GOLEM_BODY_BLOCKS)) {
			return ERA_IRON_GOLD;
		}
		if (wouldFormGolem(level, headPos, COPPER_GOLEM_BODY_BLOCKS)) {
			return ERA_COPPER;
		}
		return null;
	}

	private static boolean isGolemHeadBlock(Block block) {
		return block == Blocks.CARVED_PUMPKIN || block == Blocks.JACK_O_LANTERN;
	}

	private static BlockPos resolvePlacementPos(BlockPlaceContext context) {
		if (context == null) {
			return null;
		}
		Level level = context.getLevel();
		BlockPos clickedPos = context.getClickedPos();
		if (level == null || clickedPos == null) {
			return null;
		}
		if (level.getBlockState(clickedPos).canBeReplaced(context)) {
			return clickedPos;
		}
		return clickedPos.relative(context.getClickedFace());
	}

	private static boolean wouldFormGolem(Level level, BlockPos headPos, Set<Block> bodyBlocks) {
		if (level == null || headPos == null || bodyBlocks == null || bodyBlocks.isEmpty()) {
			return false;
		}
		BlockPos armLine = headPos.below();
		BlockPos torso = armLine.below();
		if (!isAnyOf(level.getBlockState(armLine).getBlock(), bodyBlocks)
				|| !isAnyOf(level.getBlockState(torso).getBlock(), bodyBlocks)) {
			return false;
		}

		boolean eastWest = isAnyOf(level.getBlockState(armLine.west()).getBlock(), bodyBlocks)
				&& isAnyOf(level.getBlockState(armLine.east()).getBlock(), bodyBlocks);
		if (eastWest) {
			return true;
		}

		return isAnyOf(level.getBlockState(armLine.north()).getBlock(), bodyBlocks)
				&& isAnyOf(level.getBlockState(armLine.south()).getBlock(), bodyBlocks);
	}

	private static boolean isAnyOf(Block block, Set<Block> candidates) {
		return block != null && candidates.contains(block);
	}

	private static void tickIllegalItems(MinecraftServer server) {
		if (server == null) {
			return;
		}

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			sanitizeIllegalInventory(player);
		}
	}

	private static void sanitizeIllegalInventory(ServerPlayer player) {
		if (player == null || player.level().isClientSide()) {
			return;
		}

		boolean changed = false;
		Inventory inventory = player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.isEmpty() || canOwnItem(player, stack)) {
				continue;
			}

			ItemStack removed = inventory.removeItemNoUpdate(slot);
			if (!removed.isEmpty()) {
				dropIllegalStack(player, removed);
				changed = true;
			}
		}

		ItemStack carried = player.containerMenu.getCarried();
		if (!carried.isEmpty() && !canOwnItem(player, carried)) {
			player.containerMenu.setCarried(ItemStack.EMPTY);
			dropIllegalStack(player, carried);
			changed = true;
		}

		if (!changed) {
			return;
		}

		inventory.setChanged();
		player.inventoryMenu.broadcastChanges();
		if (player.containerMenu != player.inventoryMenu) {
			player.containerMenu.broadcastChanges();
		}
	}

	private static void dropIllegalStack(ServerPlayer player, ItemStack stack) {
		if (player == null || stack == null || stack.isEmpty()) {
			return;
		}

		ItemEntity dropped = player.drop(stack, true);
		if (dropped != null) {
			dropped.setPickUpDelay(20);
		}
	}
}
