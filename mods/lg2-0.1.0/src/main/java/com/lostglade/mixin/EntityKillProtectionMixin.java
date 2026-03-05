package com.lostglade.mixin;

import com.lostglade.block.ModBlocks;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityKillProtectionMixin {
	@Inject(method = "remove", at = @At("HEAD"), cancellable = true)
	private void lg2$blockKilledRemovalForServerItem(Entity.RemovalReason reason, CallbackInfo ci) {
		if (reason != Entity.RemovalReason.KILLED) {
			return;
		}

		Entity self = (Entity) (Object) this;
		if (self instanceof ItemEntity itemEntity && itemEntity.getItem().is(ModBlocks.SERVER_ITEM)) {
			ci.cancel();
		}
	}
}
