package com.lostglade.mixin;

import com.lostglade.server.ServerRaceSystem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FishingHook.class)
public abstract class FishingHookLittleDictatorPropagandaMixin {
	@Mutable
	@Final
	@Shadow
	private int luck;

	@Mutable
	@Final
	@Shadow
	private int lureSpeed;

	@Inject(method = "<init>(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/level/Level;II)V", at = @At("RETURN"))
	private void lg2$addLittleDictatorPropagandaFishing(Player player, Level level, int luck, int lureSpeed, CallbackInfo ci) {
		int bonus = ServerRaceSystem.getLittleDictatorPropagandaFishingBonus(player);
		if (bonus > 0) {
			this.luck += bonus;
			this.lureSpeed += bonus;
		}
	}
}
