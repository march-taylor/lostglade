package com.lostglade.mixin;

import com.lostglade.item.CocaineItem;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class PlayerCocaineSprintMixin {
	@Inject(method = "canSprint", at = @At("HEAD"), cancellable = true)
	private void lg2$allowCartelCocaineSprint(CallbackInfoReturnable<Boolean> cir) {
		Player self = (Player) (Object) this;
		if (CocaineItem.canCartelSprintDespiteHunger(self)) {
			cir.setReturnValue(true);
		}
	}
}
