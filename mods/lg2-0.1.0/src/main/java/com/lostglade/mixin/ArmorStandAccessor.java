package com.lostglade.mixin;

import net.minecraft.world.entity.decoration.ArmorStand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ArmorStand.class)
public interface ArmorStandAccessor {
	@Invoker("setSmall")
	void lg2$setSmall(boolean small);

	@Invoker("setMarker")
	void lg2$setMarker(boolean marker);
}
