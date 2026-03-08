package com.lostglade.server.glitch;

import com.google.gson.JsonObject;
import com.lostglade.config.GlitchConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.HopperMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.ArrayList;
import java.util.List;

public final class ChestDesyncGlitch implements BlockUseGlitchHandler {
	private static final String PHYSICAL_STORAGE_WEIGHT = "physicalStorageWeight";
	private static final String ENDER_CHEST_WEIGHT = "enderChestWeight";

	@Override
	public String id() {
		return "chest_desync";
	}

	@Override
	public GlitchConfig.GlitchEntry defaultEntry() {
		GlitchConfig.GlitchEntry entry = new GlitchConfig.GlitchEntry();
		entry.enabled = true;
		entry.minStabilityPercent = 0.0D;
		entry.maxStabilityPercent = 70.0D;
		entry.chancePerCheck = 0.25D;
		entry.stabilityInfluence = 1.0D;
		entry.minCooldownTicks = 60;
		entry.maxCooldownTicks = 600;

		JsonObject settings = new JsonObject();
		settings.addProperty(PHYSICAL_STORAGE_WEIGHT, 1.0D);
		settings.addProperty(ENDER_CHEST_WEIGHT, 0.7D);
		entry.settings = settings;
		return entry;
	}

	@Override
	public boolean sanitizeSettings(GlitchConfig.GlitchEntry entry) {
		if (entry.settings == null) {
			entry.settings = new JsonObject();
		}

		boolean changed = false;
		changed |= GlitchSettingsHelper.sanitizeDouble(entry.settings, PHYSICAL_STORAGE_WEIGHT, 1.0D, 0.0D, 10.0D);
		changed |= GlitchSettingsHelper.sanitizeDouble(entry.settings, ENDER_CHEST_WEIGHT, 0.7D, 0.0D, 10.0D);
		return changed;
	}

	@Override
	public boolean trigger(MinecraftServer server, RandomSource random, GlitchConfig.GlitchEntry entry, double stabilityPercent) {
		return false;
	}

	@Override
	public boolean triggerOnUseBlock(
			MinecraftServer server,
			RandomSource random,
			GlitchConfig.GlitchEntry entry,
			double stabilityPercent,
			ServerPlayer player,
			ServerLevel level,
			BlockPos pos,
			BlockState state,
			InteractionHand hand,
			BlockHitResult hitResult
	) {
		if (player == null || hand != InteractionHand.MAIN_HAND || state == null) {
			return false;
		}
		if (!isStorageTriggerBlock(state)) {
			return false;
		}

		List<PhysicalStorageTarget> physicalTargets = collectPhysicalStorageTargets(server);
		List<ServerPlayer> enderTargets = collectEnderChestTargets(server);
		if (physicalTargets.isEmpty() && enderTargets.isEmpty()) {
			return false;
		}

		JsonObject settings = entry.settings == null ? new JsonObject() : entry.settings;
		double physicalWeight = GlitchSettingsHelper.getDouble(settings, PHYSICAL_STORAGE_WEIGHT, 1.0D);
		double enderWeight = GlitchSettingsHelper.getDouble(settings, ENDER_CHEST_WEIGHT, 0.7D);

		boolean choosePhysical = pickPhysicalStorageFirst(
				random,
				!physicalTargets.isEmpty(),
				!enderTargets.isEmpty(),
				physicalWeight,
				enderWeight
		);

		if (choosePhysical) {
			return openRandomPhysicalStorage(player, random, physicalTargets)
					|| openRandomEnderChest(player, random, enderTargets);
		}

		return openRandomEnderChest(player, random, enderTargets)
				|| openRandomPhysicalStorage(player, random, physicalTargets);
	}

	private static boolean isStorageTriggerBlock(BlockState state) {
		Block block = state.getBlock();
		return block instanceof ChestBlock
				|| block instanceof EnderChestBlock
				|| block instanceof ShulkerBoxBlock
				|| block instanceof HopperBlock;
	}

	private static List<PhysicalStorageTarget> collectPhysicalStorageTargets(MinecraftServer server) {
		List<PhysicalStorageTarget> targets = new ArrayList<>();
		for (ServerLevel level : server.getAllLevels()) {
			level.getChunkSource().chunkMap.forEachReadyToSendChunk(chunk -> {
				for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
					if (!isPhysicalStorageBlockEntity(blockEntity)) {
						continue;
					}
					targets.add(new PhysicalStorageTarget(level, blockEntity.getBlockPos().immutable()));
				}
			});
		}
		return targets;
	}

	private static List<ServerPlayer> collectEnderChestTargets(MinecraftServer server) {
		List<ServerPlayer> targets = new ArrayList<>();
		for (ServerPlayer target : server.getPlayerList().getPlayers()) {
			if (target.isSpectator() || !target.isAlive()) {
				continue;
			}
			targets.add(target);
		}
		return targets;
	}

	private static boolean pickPhysicalStorageFirst(
			RandomSource random,
			boolean hasPhysical,
			boolean hasEnder,
			double physicalWeight,
			double enderWeight
	) {
		if (hasPhysical && !hasEnder) {
			return true;
		}
		if (!hasPhysical && hasEnder) {
			return false;
		}
		if (!hasPhysical) {
			return false;
		}

		double normalizedPhysicalWeight = Math.max(0.0D, physicalWeight);
		double normalizedEnderWeight = Math.max(0.0D, enderWeight);
		double total = normalizedPhysicalWeight + normalizedEnderWeight;
		if (total <= 1.0E-9D) {
			return random.nextBoolean();
		}

		double physicalChance = normalizedPhysicalWeight / total;
		return random.nextDouble() < physicalChance;
	}

	private static boolean openRandomPhysicalStorage(
			ServerPlayer opener,
			RandomSource random,
			List<PhysicalStorageTarget> candidates
	) {
		if (candidates.isEmpty()) {
			return false;
		}

		List<PhysicalStorageTarget> shuffled = new ArrayList<>(candidates);
		while (!shuffled.isEmpty()) {
			int index = random.nextInt(shuffled.size());
			PhysicalStorageTarget target = shuffled.remove(index);
			if (openPhysicalStorage(opener, target)) {
				return true;
			}
		}
		return false;
	}

	private static boolean openRandomEnderChest(ServerPlayer opener, RandomSource random, List<ServerPlayer> candidates) {
		if (candidates.isEmpty()) {
			return false;
		}

		List<ServerPlayer> shuffled = new ArrayList<>(candidates);
		while (!shuffled.isEmpty()) {
			int index = random.nextInt(shuffled.size());
			ServerPlayer target = shuffled.remove(index);
			if (openEnderChest(opener, target)) {
				return true;
			}
		}
		return false;
	}

	private static boolean openPhysicalStorage(ServerPlayer opener, PhysicalStorageTarget target) {
		BlockEntity blockEntity = target.level.getBlockEntity(target.pos);
		if (!(blockEntity instanceof Container container)) {
			return false;
		}

		Container proxied = new AlwaysValidContainer(container);
		SimpleMenuProvider provider = new SimpleMenuProvider(
				(syncId, inventory, menuPlayer) -> createMenu(syncId, inventory, proxied),
				Component.literal("Container Desync")
		);
		return opener.openMenu(provider).isPresent();
	}

	private static boolean openEnderChest(ServerPlayer opener, ServerPlayer target) {
		if (target == null) {
			return false;
		}

		Container proxied = new AlwaysValidContainer(target.getEnderChestInventory());
		SimpleMenuProvider provider = new SimpleMenuProvider(
				(syncId, inventory, menuPlayer) -> ChestMenu.threeRows(syncId, inventory, proxied),
				Component.literal("Ender Chest Desync: " + target.getScoreboardName())
		);
		return opener.openMenu(provider).isPresent();
	}

	private static AbstractContainerMenu createMenu(int syncId, Inventory inventory, Container container) {
		int size = container.getContainerSize();
		if (size == 5) {
			return new HopperMenu(syncId, inventory, container);
		}

		if (size % 9 != 0) {
			return null;
		}

		int rows = size / 9;
		return switch (rows) {
			case 1 -> new ChestMenu(MenuType.GENERIC_9x1, syncId, inventory, container, 1);
			case 2 -> new ChestMenu(MenuType.GENERIC_9x2, syncId, inventory, container, 2);
			case 3 -> ChestMenu.threeRows(syncId, inventory, container);
			case 4 -> new ChestMenu(MenuType.GENERIC_9x4, syncId, inventory, container, 4);
			case 5 -> new ChestMenu(MenuType.GENERIC_9x5, syncId, inventory, container, 5);
			case 6 -> ChestMenu.sixRows(syncId, inventory, container);
			default -> null;
		};
	}

	private static boolean isPhysicalStorageBlockEntity(BlockEntity blockEntity) {
		return blockEntity instanceof ChestBlockEntity
				|| blockEntity instanceof ShulkerBoxBlockEntity
				|| blockEntity instanceof HopperBlockEntity;
	}

	private static final class PhysicalStorageTarget {
		private final ServerLevel level;
		private final BlockPos pos;

		private PhysicalStorageTarget(ServerLevel level, BlockPos pos) {
			this.level = level;
			this.pos = pos;
		}
	}

	private static final class AlwaysValidContainer implements Container {
		private final Container delegate;

		private AlwaysValidContainer(Container delegate) {
			this.delegate = delegate;
		}

		@Override
		public int getContainerSize() {
			return this.delegate.getContainerSize();
		}

		@Override
		public boolean isEmpty() {
			return this.delegate.isEmpty();
		}

		@Override
		public ItemStack getItem(int slot) {
			return this.delegate.getItem(slot);
		}

		@Override
		public ItemStack removeItem(int slot, int amount) {
			return this.delegate.removeItem(slot, amount);
		}

		@Override
		public ItemStack removeItemNoUpdate(int slot) {
			return this.delegate.removeItemNoUpdate(slot);
		}

		@Override
		public void setItem(int slot, ItemStack stack) {
			this.delegate.setItem(slot, stack);
		}

		@Override
		public int getMaxStackSize() {
			return this.delegate.getMaxStackSize();
		}

		@Override
		public void setChanged() {
			this.delegate.setChanged();
		}

		@Override
		public boolean stillValid(Player player) {
			return true;
		}

		@Override
		public void startOpen(ContainerUser user) {
			this.delegate.startOpen(user);
		}

		@Override
		public void stopOpen(ContainerUser user) {
			this.delegate.stopOpen(user);
		}

		@Override
		public boolean canPlaceItem(int slot, ItemStack stack) {
			return this.delegate.canPlaceItem(slot, stack);
		}

		@Override
		public void clearContent() {
			this.delegate.clearContent();
		}
	}
}
