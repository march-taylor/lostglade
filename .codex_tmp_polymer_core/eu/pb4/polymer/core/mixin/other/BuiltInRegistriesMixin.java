package eu.pb4.polymer.core.mixin.other;

import eu.pb4.polymer.core.impl.interfaces.PolymerIdMapper;
import net.minecraft.class_2248;
import net.minecraft.class_3611;
import net.minecraft.class_7923;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = class_7923.class, priority = 1500)
public class BuiltInRegistriesMixin {
    @Inject(method = "freeze", at = @At("TAIL"))
    private static void reorderEntries(CallbackInfo ci) {
        ((PolymerIdMapper<?>) class_2248.field_10651).polymer$reorderEntries();
        ((PolymerIdMapper<?>) class_3611.field_15904).polymer$reorderEntries();
    }
}
