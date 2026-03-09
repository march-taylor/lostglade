package eu.pb4.polymer.core.mixin.item;

import net.minecraft.class_2487;
import net.minecraft.class_2512;
import net.minecraft.class_2680;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(class_2512.class)
public class NbtUtilsMixin {
    @Inject(method = "writeBlockState", at = @At("RETURN"))
    private static void polymerCore$markNbt(class_2680 state, CallbackInfoReturnable<class_2487> cir) {
        //((TypeAwareNbtCompound) cir.getReturnValue()).polymerCore$setType(TypeAwareNbtCompound.STATE_TYPE);
    }
}
