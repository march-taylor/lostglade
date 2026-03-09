package eu.pb4.polymer.core.mixin.other;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.serialization.DynamicOps;
import eu.pb4.polymer.common.api.PolymerCommonUtils;
import eu.pb4.polymer.core.api.entity.PolymerEntityUtils;
import eu.pb4.polymer.core.api.utils.PolymerSyncedObject;
import eu.pb4.polymer.core.api.utils.PolymerUtils;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import xyz.nucleoid.packettweaker.PacketContext;

import java.util.Optional;
import net.minecraft.class_1299;
import net.minecraft.class_1320;
import net.minecraft.class_2378;
import net.minecraft.class_5134;
import net.minecraft.class_5321;
import net.minecraft.class_6880;
import net.minecraft.class_6899;
import net.minecraft.class_7923;

@Mixin(class_6899.class)
public class RegistryFixedCodecMixin {
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
                if (entry.comp_349() instanceof class_1299<?> type && PolymerEntityUtils.isPolymerEntityType(type)) {
                    return class_1299.field_33456.method_40124();
                } else if (entry.comp_349() instanceof class_1320 && PolymerEntityUtils.isPolymerEntityAttribute((class_6880<class_1320>) entry)) {
                    return class_5134.field_23727;
                } else if (PolymerSyncedObject.getSyncedObject(registry, entry.comp_349()) instanceof PolymerSyncedObject<?> polymerSyncedObject) {
                    //noinspection unchecked,DataFlowIssue
                    var x = registry.method_47983(((PolymerSyncedObject<Object>) polymerSyncedObject).getPolymerReplacement(entry.comp_349(), player));
                    if (x == null) {
                        //noinspection unchecked
                        return (class_6880<?>) registry.method_40265(0).orElse(entry);
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
