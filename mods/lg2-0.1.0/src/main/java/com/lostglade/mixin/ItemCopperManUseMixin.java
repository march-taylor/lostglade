package com.lostglade.mixin;

import com.lostglade.server.ServerRaceSystem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Item.class)
public abstract class ItemCopperManUseMixin {
	@Inject(method = "use", at = @At("HEAD"), cancellable = true)
	private void lg2$allowCopperManToEatCopperIngots(
			Level level,
			Player player,
			InteractionHand hand,
			CallbackInfoReturnable<InteractionResult> cir
	) {
		if ((Object) this != Items.COPPER_INGOT || !(player instanceof ServerPlayer serverPlayer)) {
			return;
		}
		if (!ServerRaceSystem.isCopperManStockEnabled(serverPlayer)) {
			return;
		}

		ItemStack stack = player.getItemInHand(hand);
		ServerRaceSystem.ensureCopperIngotConsumableForUse(serverPlayer, stack);
		cir.setReturnValue(ItemUtils.startUsingInstantly(level, player, hand));
	}
}
