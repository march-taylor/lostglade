package com.lostglade.mixin;

import com.lostglade.entity.TrojanChickenAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Chicken.class)
public abstract class ChickenTrojanRoosterMixin implements TrojanChickenAccess {
	@Unique
	private static final String LG2_STORED_BITCOINS_TAG = "lg2_stored_bitcoins";

	@Unique
	private static final String LG2_TROJAN_ROOSTER_TAG = "lg2_trojan_rooster";

	@Unique
	private int lg2$storedBitcoins;

	@Unique
	private boolean lg2$trojanRooster;

	@Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
	private void lg2$readTrojanRoosterData(ValueInput input, CallbackInfo ci) {
		this.lg2$storedBitcoins = Math.max(0, input.getIntOr(LG2_STORED_BITCOINS_TAG, 0));
		this.lg2$trojanRooster = input.getBooleanOr(LG2_TROJAN_ROOSTER_TAG, this.lg2$storedBitcoins >= 64);
	}

	@Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
	private void lg2$writeTrojanRoosterData(ValueOutput output, CallbackInfo ci) {
		if (this.lg2$storedBitcoins > 0) {
			output.putInt(LG2_STORED_BITCOINS_TAG, this.lg2$storedBitcoins);
		}
		if (this.lg2$trojanRooster) {
			output.putBoolean(LG2_TROJAN_ROOSTER_TAG, true);
		}
	}

	@Inject(method = "isFood", at = @At("HEAD"), cancellable = true)
	private void lg2$ignoreFoodForTrojanRooster(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
		if (this.lg2$trojanRooster) {
			cir.setReturnValue(false);
		}
	}

	@Override
	public int lg2$getStoredBitcoins() {
		return this.lg2$storedBitcoins;
	}

	@Override
	public void lg2$setStoredBitcoins(int amount) {
		this.lg2$storedBitcoins = Math.max(0, amount);
	}

	@Override
	public boolean lg2$isTrojanRooster() {
		return this.lg2$trojanRooster;
	}

	@Override
	public void lg2$setTrojanRooster(boolean trojanRooster) {
		this.lg2$trojanRooster = trojanRooster;
	}
}
