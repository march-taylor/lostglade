package com.lostglade.mixin;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Entity.class)
public interface EntityTrackedDataAccessor {
	@Accessor("DATA_SHARED_FLAGS_ID")
	static EntityDataAccessor<Byte> lg2$getDataSharedFlagsId() {
		throw new AssertionError();
	}

	@Accessor("DATA_POSE")
	static EntityDataAccessor<Pose> lg2$getDataPose() {
		throw new AssertionError();
	}
}
