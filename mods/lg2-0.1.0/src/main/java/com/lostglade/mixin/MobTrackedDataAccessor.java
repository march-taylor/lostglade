package com.lostglade.mixin;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Mob.class)
public interface MobTrackedDataAccessor {
	@Accessor("DATA_MOB_FLAGS_ID")
	static EntityDataAccessor<Byte> lg2$getDataMobFlagsId() {
		throw new AssertionError();
	}
}