package com.lostglade.mixin.client;

import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Camera.class)
public interface CameraPositionInvoker {
	@Invoker("setPosition")
	void lg2$setPosition(Vec3 position);

	@Accessor("forwards")
	Vector3f lg2$getForwards();

	@Accessor("up")
	Vector3f lg2$getUp();

	@Accessor("left")
	Vector3f lg2$getLeft();
}
