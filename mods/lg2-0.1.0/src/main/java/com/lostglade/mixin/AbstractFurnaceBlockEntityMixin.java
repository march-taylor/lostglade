package com.lostglade.mixin;

import com.lostglade.server.ServerGlitchSystem;
import com.lostglade.server.glitch.BitcoinOvercookGlitch;
import com.lostglade.server.glitch.BitcoinOvercookFurnaceAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class AbstractFurnaceBlockEntityMixin implements BitcoinOvercookFurnaceAccess {
	@Unique
	private static final BitcoinOvercookGlitch LG2_BITCOIN_OVERCOOK = new BitcoinOvercookGlitch();

	@Unique
	private static final String LG2_BITCOIN_OVERCOOK_EXPERIENCE = "lg2_bitcoin_overcook_experience";

	@Unique
	private double lg2$bitcoinOvercookExperience;

	@Inject(
			method = "serverTick",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity;setRecipeUsed(Lnet/minecraft/world/item/crafting/RecipeHolder;)V",
					shift = At.Shift.AFTER
			),
			locals = LocalCapture.CAPTURE_FAILHARD
	)
	private static void lg2$handleBitcoinOvercook(
			ServerLevel level,
			BlockPos pos,
			BlockState state,
			AbstractFurnaceBlockEntity blockEntity,
			CallbackInfo ci,
			boolean wasLit,
			boolean changed,
			ItemStack fuelStack,
			ItemStack inputStack,
			boolean hasInput,
			boolean hasFuel,
			RecipeHolder<? extends AbstractCookingRecipe> recipeHolder,
			SingleRecipeInput recipeInput,
			int maxStackSize
	) {
		ServerGlitchSystem.onFurnaceSmelt(level, pos, state, blockEntity, recipeHolder, recipeInput);
	}

	@Inject(method = "serverTick", at = @At("TAIL"))
	private static void lg2$clearTechnicalBitcoinOvercookResult(
			ServerLevel level,
			BlockPos pos,
			BlockState state,
			AbstractFurnaceBlockEntity blockEntity,
			CallbackInfo ci
	) {
		if (!(blockEntity instanceof net.minecraft.world.level.block.entity.FurnaceBlockEntity)) {
			return;
		}

		ItemStack result = blockEntity.getItem(2);
		if (LG2_BITCOIN_OVERCOOK.isTechnicalResult(result)) {
			blockEntity.setItem(2, ItemStack.EMPTY);
		}
	}

	@Inject(method = "loadAdditional", at = @At("TAIL"))
	private void lg2$loadBitcoinOvercookExperience(ValueInput input, CallbackInfo ci) {
		this.lg2$bitcoinOvercookExperience = input.getDoubleOr(LG2_BITCOIN_OVERCOOK_EXPERIENCE, 0.0D);
	}

	@Inject(method = "saveAdditional", at = @At("TAIL"))
	private void lg2$saveBitcoinOvercookExperience(ValueOutput output, CallbackInfo ci) {
		if (this.lg2$bitcoinOvercookExperience > 1.0E-9D) {
			output.putDouble(LG2_BITCOIN_OVERCOOK_EXPERIENCE, this.lg2$bitcoinOvercookExperience);
		}
	}

	@Inject(method = "getRecipesToAwardAndPopExperience", at = @At("TAIL"))
	private void lg2$awardBitcoinOvercookExperience(ServerLevel level, Vec3 pos, CallbackInfoReturnable<java.util.List<RecipeHolder<?>>> cir) {
		double total = this.lg2$drainBitcoinOvercookExperience();
		if (total <= 1.0E-9D) {
			return;
		}

		int whole = Mth.floor(total);
		float fraction = Mth.frac((float) total);
		if (fraction > 0.0F && level.random.nextFloat() < fraction) {
			whole++;
		}

		if (whole > 0) {
			ExperienceOrb.award(level, pos, whole);
		}
	}

	@Override
	public void lg2$addBitcoinOvercookExperience(double amount) {
		if (amount > 0.0D) {
			this.lg2$bitcoinOvercookExperience += amount;
		}
	}

	@Override
	public double lg2$drainBitcoinOvercookExperience() {
		double total = this.lg2$bitcoinOvercookExperience;
		this.lg2$bitcoinOvercookExperience = 0.0D;
		return total;
	}
}
