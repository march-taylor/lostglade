package com.lostglade.mixin;

import com.lostglade.block.ModBlocks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Sheep.class)
public abstract class SheepRainbowWoolShearMixin {
	@Inject(method = "shear", at = @At("HEAD"), cancellable = true)
	private void lg2$shearRainbowWool(ServerLevel level, SoundSource soundSource, ItemStack shears, CallbackInfo ci) {
		Sheep sheep = (Sheep) (Object) this;
		if (!sheep.readyForShearing() || !isRainbowWoolSheep(sheep)) {
			return;
		}

		level.playSound(null, sheep, SoundEvents.SHEEP_SHEAR, soundSource, 1.0F, 1.0F);
		spawnRainbowWoolDrops(level, sheep);
		sheep.setSheared(true);
		ci.cancel();
	}

	private static boolean isRainbowWoolSheep(Sheep sheep) {
		return sheep.hasCustomName() && "jeb_".equalsIgnoreCase(sheep.getCustomName().getString());
	}

	private static void spawnRainbowWoolDrops(ServerLevel level, Sheep sheep) {
		int count = 1 + sheep.getRandom().nextInt(3);
		for (int i = 0; i < count; i++) {
			ItemEntity itemEntity = sheep.spawnAtLocation(level, new ItemStack(ModBlocks.RAINBOW_WOOL), 1.0F);
			if (itemEntity == null) {
				continue;
			}

			itemEntity.setDeltaMovement(itemEntity.getDeltaMovement().add(
					(sheep.getRandom().nextFloat() - sheep.getRandom().nextFloat()) * 0.1F,
					sheep.getRandom().nextFloat() * 0.05F,
					(sheep.getRandom().nextFloat() - sheep.getRandom().nextFloat()) * 0.1F
			));
		}
	}
}
