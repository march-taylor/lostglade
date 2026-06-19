package com.lostglade.mixin;

import com.lostglade.server.ServerRaceSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Block.class)
public abstract class BlockLittleDictatorDecreeLootMixin {
	@Inject(
			method = "dropResources(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;)V",
			at = @At("HEAD")
	)
	private static void lg2$beginLittleDictatorBlockLoot(
			BlockState state,
			Level level,
			BlockPos pos,
			BlockEntity blockEntity,
			Entity entity,
			ItemStack tool,
			CallbackInfo ci
	) {
		ServerRaceSystem.beginLittleDictatorBlockLootContext(entity instanceof ServerPlayer player ? player : null);
	}

	@Inject(
			method = "dropResources(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;)V",
			at = @At("RETURN")
	)
	private static void lg2$endLittleDictatorBlockLoot(
			BlockState state,
			Level level,
			BlockPos pos,
			BlockEntity blockEntity,
			Entity entity,
			ItemStack tool,
			CallbackInfo ci
	) {
		ServerRaceSystem.endLittleDictatorLootContext();
	}

	@Inject(method = "popResource", at = @At("HEAD"), cancellable = true)
	private static void lg2$filterLittleDictatorBlockDrop(Level level, BlockPos pos, ItemStack stack, CallbackInfo ci) {
		if (ServerRaceSystem.shouldCancelLittleDictatorContextDrop()) {
			ci.cancel();
		}
	}
}
