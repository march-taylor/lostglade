package eu.pb4.polymer.core.mixin.client.debug;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import eu.pb4.polymer.core.impl.client.ClientDebugFlags;
import net.minecraft.class_10442;
import net.minecraft.class_1799;
import net.minecraft.class_9331;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(class_10442.class)
public class ItemModelResolverMixin {
    @WrapOperation(method = "appendItemLayers", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;get(Lnet/minecraft/core/component/DataComponentType;)Ljava/lang/Object;"))
    private Object replaceIdentifier(class_1799 instance, class_9331 componentType, Operation<Object> original) {
        if (ClientDebugFlags.customItemModels) return original.call(instance, componentType);
        return instance.method_7909().method_57347().method_58694(componentType);
    }
}
