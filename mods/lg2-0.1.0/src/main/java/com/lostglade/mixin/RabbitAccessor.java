package com.lostglade.mixin;

import net.minecraft.world.entity.animal.rabbit.Rabbit;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Rabbit.class)
public interface RabbitAccessor {
	@Invoker("setVariant")
	void lg2$setVariant(Rabbit.Variant variant);
}
