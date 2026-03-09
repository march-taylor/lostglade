package com.lostglade.mixin;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Avatar.class)
public interface PlayerTrackedDataAccessor {
	@Accessor("DATA_PLAYER_MAIN_HAND")
	static EntityDataAccessor<HumanoidArm> lg2$getDataPlayerMainHand() {
		throw new AssertionError();
	}

	@Accessor("DATA_PLAYER_MODE_CUSTOMISATION")
	static EntityDataAccessor<Byte> lg2$getDataPlayerModeCustomisation() {
		throw new AssertionError();
	}
}
