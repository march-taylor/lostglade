package com.lostglade.mixin;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.illager.Illusioner;
import net.minecraft.world.entity.monster.illager.SpellcasterIllager;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Illusioner.class)
public abstract class IllusionerMixin extends SpellcasterIllager {
	protected IllusionerMixin(EntityType<? extends SpellcasterIllager> entityType, Level level) {
		super(entityType, level);
	}

	@Inject(method = "<init>", at = @At("TAIL"))
	private void lg2$setIllusionerXpReward(EntityType<? extends Illusioner> entityType, Level level, CallbackInfo ci) {
		this.xpReward = 10;
	}
}
