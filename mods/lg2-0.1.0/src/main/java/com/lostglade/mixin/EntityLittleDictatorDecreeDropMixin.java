package com.lostglade.mixin;

import com.lostglade.server.ServerRaceSystem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityLittleDictatorDecreeDropMixin {
	@Inject(
			method = "spawnAtLocation(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/entity/item/ItemEntity;",
			at = @At("HEAD"),
			cancellable = true
	)
	private void lg2$filterLittleDictatorEntityDrop(ServerLevel level, ItemStack stack, CallbackInfoReturnable<ItemEntity> cir) {
		if (ServerRaceSystem.shouldCancelLittleDictatorContextDrop()) {
			cir.setReturnValue(null);
		}
	}

	@Inject(
			method = "spawnAtLocation(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/item/ItemStack;F)Lnet/minecraft/world/entity/item/ItemEntity;",
			at = @At("HEAD"),
			cancellable = true
	)
	private void lg2$filterLittleDictatorEntityDropWithYOffset(ServerLevel level, ItemStack stack, float yOffset, CallbackInfoReturnable<ItemEntity> cir) {
		if (ServerRaceSystem.shouldCancelLittleDictatorContextDrop()) {
			cir.setReturnValue(null);
		}
	}

	@Inject(
			method = "spawnAtLocation(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/entity/item/ItemEntity;",
			at = @At("HEAD"),
			cancellable = true
	)
	private void lg2$filterLittleDictatorEntityDropWithOffset(ServerLevel level, ItemStack stack, Vec3 offset, CallbackInfoReturnable<ItemEntity> cir) {
		if (ServerRaceSystem.shouldCancelLittleDictatorContextDrop()) {
			cir.setReturnValue(null);
		}
	}
}
