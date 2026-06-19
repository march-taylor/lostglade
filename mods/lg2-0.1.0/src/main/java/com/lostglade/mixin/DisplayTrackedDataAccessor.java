package com.lostglade.mixin;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.Display;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Display.class)
public interface DisplayTrackedDataAccessor {
	@Accessor("DATA_TRANSLATION_ID")
	static EntityDataAccessor<Vector3fc> lg2$getDataTranslationId() {
		throw new AssertionError();
	}
}
