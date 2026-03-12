package com.lostglade.mixin;

import com.lostglade.Lg2;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public abstract class BackroomsServerLevelTimeMixin {
	private static final long BACKROOMS_FIXED_TIME = 6000L;

	@Shadow @Final
	private ServerLevelData serverLevelData;

	private static final ResourceKey<Level> BACKROOMS_LEVEL = ResourceKey.create(
			Registries.DIMENSION,
			Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "backrooms")
	);

	@Inject(method = "setDayTime", at = @At("HEAD"), cancellable = true)
	private void lg2$blockBackroomsDayTimeChanges(long dayTime, CallbackInfo ci) {
		ServerLevel level = (ServerLevel) (Object) this;
		if (!BACKROOMS_LEVEL.equals(level.dimension())) {
			return;
		}

		if (this.serverLevelData.getDayTime() != BACKROOMS_FIXED_TIME) {
			this.serverLevelData.setDayTime(BACKROOMS_FIXED_TIME);
		}
		ci.cancel();
	}

	@Inject(method = "tickTime", at = @At("TAIL"))
	private void lg2$freezeBackroomsTimeTick(CallbackInfo ci) {
		ServerLevel level = (ServerLevel) (Object) this;
		if (BACKROOMS_LEVEL.equals(level.dimension()) && this.serverLevelData.getDayTime() != BACKROOMS_FIXED_TIME) {
			this.serverLevelData.setDayTime(BACKROOMS_FIXED_TIME);
		}
	}
}
