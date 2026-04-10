package com.lostglade.mixin;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Entity.class)
public interface EntityPassengerAccessor {
	@Accessor("vehicle")
	void lg2$setVehicle(Entity vehicle);

	@Invoker("addPassenger")
	void lg2$addPassenger(Entity passenger);

	@Invoker("removePassenger")
	void lg2$removePassenger(Entity passenger);
}
