package com.lostglade.mixin;

import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Mob.class)
public interface MobXpRewardAccessor {
	@Accessor("xpReward")
	void lg2$setXpReward(int value);
}

