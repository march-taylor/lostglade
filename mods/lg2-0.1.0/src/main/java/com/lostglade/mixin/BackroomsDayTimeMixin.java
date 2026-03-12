package com.lostglade.mixin;

import com.lostglade.Lg2;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public abstract class BackroomsDayTimeMixin {
	private static final long BACKROOMS_FIXED_TIME = 6000L;
	private static final ResourceKey<Level> BACKROOMS_LEVEL = ResourceKey.create(
			Registries.DIMENSION,
			Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "backrooms")
	);

	@Inject(method = "getDayTime", at = @At("HEAD"), cancellable = true)
	private void lg2$forceBackroomsDayTime(CallbackInfoReturnable<Long> cir) {
		Level level = (Level) (Object) this;
		if (BACKROOMS_LEVEL.equals(level.dimension())) {
			cir.setReturnValue(BACKROOMS_FIXED_TIME);
		}
	}
}
