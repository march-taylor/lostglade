package eu.pb4.polymer.core.mixin.item;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import eu.pb4.polymer.core.api.item.PolymerItemGroupUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.class_1761;
import net.minecraft.class_1799;
import net.minecraft.class_2378;
import net.minecraft.class_7706;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.Set;
import java.util.stream.Stream;

@Mixin(value = class_7706.class, priority = 1500)
public class CreativeModeTabsMixin {
    @Environment(EnvType.SERVER)
    @ModifyReturnValue(method = "streamAllTabs", at = @At("RETURN"))
    private static Stream<class_1761> polymerCore$injectServerItemGroups(Stream<class_1761> original) {
        if (PolymerItemGroupUtils.REGISTRY.method_10204() > 0) {
            return Stream.concat(original, PolymerItemGroupUtils.REGISTRY.stream());
        }
        return original;
    }

    @Environment(EnvType.SERVER)
    @Inject(method = "method_51316", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/CreativeModeTab$Output;acceptAll(Ljava/util/Collection;)V", shift = At.Shift.BEFORE), locals = LocalCapture.CAPTURE_FAILSOFT, require = 0)
    private static void polymerCore$injectServerSearch(class_2378<class_1761> registry, class_1761.class_8128 displayContext, class_1761.class_7704 entries, CallbackInfo ci, Set<class_1799> set) {
        for (var group : PolymerItemGroupUtils.REGISTRY) {
            set.addAll(group.method_45414());
        }
    }
}
