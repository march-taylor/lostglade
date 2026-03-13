package com.lostglade.mixin;

import com.lostglade.server.ServerUpgradeUiSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.EndPortalBlock;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.Portal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityPortalAccessMixin {
	@Inject(method = "setAsInsidePortal", at = @At("HEAD"), cancellable = true)
	private void lg2$blockLockedWorldAccess(Portal portal, BlockPos pos, CallbackInfo ci) {
		if (!((Object) this instanceof ServerPlayer player) || player.level().isClientSide()) {
			return;
		}

		if (portal instanceof NetherPortalBlock) {
			if (!ServerUpgradeUiSystem.hasUpgrade(player, "world_nether")) {
				ci.cancel();
			}
		} else if (portal instanceof EndPortalBlock) {
			if (!ServerUpgradeUiSystem.hasUpgrade(player, "world_end")) {
				ci.cancel();
			}
		}
	}
}
