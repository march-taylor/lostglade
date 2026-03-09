package eu.pb4.polymer.core.mixin.client.item;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import eu.pb4.polymer.core.impl.client.InternalClientRegistry;
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
public abstract class CreativeModeTabsMixin {
    @Environment(EnvType.CLIENT)
    @ModifyReturnValue(method = "streamAllTabs", at = @At("RETURN"),require = 0)
    private static Stream<class_1761> polymerCore$injectClientItemGroups(Stream<class_1761> original) {
        if (InternalClientRegistry.ITEM_GROUPS.method_10204() > 0) {
            return Stream.concat(original, InternalClientRegistry.ITEM_GROUPS.stream());
        }
        return original;
    }

    @Environment(EnvType.CLIENT)
    @Inject(method = "method_51316", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/CreativeModeTab$Output;acceptAll(Ljava/util/Collection;)V", shift = At.Shift.BEFORE), locals = LocalCapture.CAPTURE_FAILSOFT, require = 0)
    private static void polymerCore$injectClientSearch(class_2378<class_1761> registry, class_1761.class_8128 displayContext, class_1761.class_7704 entries, CallbackInfo ci, Set<class_1799> set) {
        for (var group : InternalClientRegistry.ITEM_GROUPS) {
            set.addAll(group.method_45414());
        }
    }
}
