package com.lostglade.mixin;

import com.lostglade.server.RocketLaunchEventSystem;
import net.minecraft.world.entity.decoration.ItemFrame;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A monitor mounted on the launch body has no ordinary supporting block while
 * the body is represented by client displays.  Keep the real frame alive so
 * its media session, Bluetooth links and controls keep running in flight. */
@Mixin(ItemFrame.class)
public abstract class ItemFrameRocketMountedScreenMixin {
	@Inject(method = "survives", at = @At("HEAD"), cancellable = true)
	private void lg2$keepRocketMountedScreenAlive(CallbackInfoReturnable<Boolean> cir) {
		if (((ItemFrame) (Object) this).getTags().contains(RocketLaunchEventSystem.MOUNTED_SCREEN_TAG)) {
			cir.setReturnValue(true);
		}
	}
}
