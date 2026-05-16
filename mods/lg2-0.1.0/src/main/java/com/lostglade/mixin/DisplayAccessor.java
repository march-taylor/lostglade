package com.lostglade.mixin;

import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Display.class)
public interface DisplayAccessor {
	@Invoker("setWidth")
	void lg2$setDisplayWidth(float width);

	@Invoker("setHeight")
	void lg2$setDisplayHeight(float height);
}

