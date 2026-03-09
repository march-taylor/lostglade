package eu.pb4.polymer.core.mixin.other;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.serialization.DynamicOps;
import eu.pb4.polymer.common.api.PolymerCommonUtils;
import eu.pb4.polymer.core.api.utils.PolymerSyncedObject;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import xyz.nucleoid.packettweaker.PacketContext;

import java.util.Optional;
import net.minecraft.class_2378;
import net.minecraft.class_5321;
import net.minecraft.class_5381;
import net.minecraft.class_6880;
import net.minecraft.class_7923;

@Mixin(class_5381.class)
public class RegistryFileCodecMixin {

    @Shadow @Final private class_5321<class_2378> registryKey;

    @ModifyVariable(
            method = "encode(Lnet/minecraft/core/Holder;Lcom/mojang/serialization/DynamicOps;Ljava/lang/Object;)Lcom/mojang/serialization/DataResult;",
            at = @At("HEAD")
    )
    private class_6880<?> polymerCore$swapEntry(class_6880<?> entry, @Local(argsOnly = true) DynamicOps<?> ops) {
        if (PolymerCommonUtils.isServerNetworkingThread()) {
            var player = PacketContext.get();
            try {
                //noinspection unchecked,rawtypes
                var registry = ((class_2378<class_2378>) (Object) class_7923.field_41167).method_29107(this.registryKey);
                //noinspection unchecked

                if (PolymerSyncedObject.getSyncedObject(registry, entry.comp_349()) instanceof PolymerSyncedObject<?> polymerSyncedObject) {
                    var obj = ((PolymerSyncedObject<Object>) polymerSyncedObject).getPolymerReplacement(entry.comp_349(), player);
                    if (obj == null) {
                        obj = entry.comp_349();
                    }

                    //noinspection unchecked,DataFlowIssue
                    var x = registry.method_47983(obj);
                    //noinspection unchecked,DataFlowIssue
                    var key = (Optional<class_5321<?>>) x.method_40230();
                    if (key.isPresent() && !key.get().method_29177().method_12836().equals("minecraft")) {
                        return class_6880.method_40223(obj);
                    }

                    return x;
                }
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }

        return entry;
    }
}
