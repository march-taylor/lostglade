package eu.pb4.polymer.core.mixin.compat.polymc;

import eu.pb4.polymer.core.impl.PolymerImpl;
import io.github.theepicblock.polymc.impl.mixin.CustomBlockBreakingCheck;
import net.minecraft.class_2248;
import net.minecraft.class_3222;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CustomBlockBreakingCheck.class)
public class polymc_CustomBlockBreakingCheckMixin {
    @Inject(method = "needsCustomBreaking", at = @At("HEAD"), cancellable = true)
    private static void polymerOverride(class_3222 player, class_2248 block, CallbackInfoReturnable<Boolean> cir) {
        if (PolymerImpl.OVERRIDE_POLYMC_MINING) {
            cir.setReturnValue(false);
        }
    }
}
