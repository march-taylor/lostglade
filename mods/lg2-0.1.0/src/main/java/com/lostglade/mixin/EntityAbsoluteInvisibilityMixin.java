package com.lostglade.mixin;

import com.lostglade.server.ServerAbsoluteInvisibilitySystem;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityAbsoluteInvisibilityMixin {
	@Inject(method = "spawnSprintParticle", at = @At("HEAD"), cancellable = true)
	private void lg2$cancelSprintParticlesForAbsoluteInvisibility(CallbackInfo ci) {
		if (ServerAbsoluteInvisibilitySystem.isActive((Entity) (Object) this)) {
			ci.cancel();
		}
	}

	@Inject(method = "playSound(Lnet/minecraft/sounds/SoundEvent;FF)V", at = @At("HEAD"), cancellable = true)
	private void lg2$suppressSomeSoundsForAbsoluteInvisibility(SoundEvent sound, float volume, float pitch, CallbackInfo ci) {
		if (ServerAbsoluteInvisibilitySystem.shouldSuppressSound((Entity) (Object) this)) {
			ci.cancel();
		}
	}
}
