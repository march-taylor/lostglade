package eu.pb4.polymer.core.mixin.other;

import eu.pb4.polymer.core.api.utils.PolymerSyncedObject;
import eu.pb4.polymer.core.impl.PolymerImplUtils;
import eu.pb4.polymer.core.impl.interfaces.PolymerIdMapper;
import net.minecraft.class_2361;
import net.minecraft.class_2378;
import net.minecraft.class_3610;
import net.minecraft.class_3611;
import net.minecraft.class_7923;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(class_3611.class)
public class FluidMixin {
    @Shadow @Final public static class_2361<class_3610> FLUID_STATE_REGISTRY;

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void polymer$enableMapping(CallbackInfo ci) {
        ((PolymerIdMapper<class_3610>) FLUID_STATE_REGISTRY).polymer$setChecker(
                x -> PolymerSyncedObject.getSyncedObject(class_7923.field_41173, x.method_15772()) != null,
                x -> PolymerImplUtils.isServerSideSyncableEntry((class_2378<Object>) (Object) class_7923.field_41173, x.method_15772()),
                x -> "(Fluid) " + class_7923.field_41173.method_10221(x.method_15772()));
    }
}
