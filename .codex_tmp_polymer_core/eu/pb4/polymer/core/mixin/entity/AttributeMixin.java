package eu.pb4.polymer.core.mixin.entity;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.serialization.Codec;
import eu.pb4.polymer.common.api.PolymerCommonUtils;
import eu.pb4.polymer.core.api.entity.PolymerEntityUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.Function;
import net.minecraft.class_1320;
import net.minecraft.class_5134;
import net.minecraft.class_6880;

@Mixin(class_1320.class)
public abstract class AttributeMixin {
    @ModifyExpressionValue(method = "<clinit>", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/Registry;holderByNameCodec()Lcom/mojang/serialization/Codec;"))
    private static Codec<class_6880<class_1320>> patchCodec(Codec<class_6880<class_1320>> codec) {
        return codec.xmap(Function.identity(), content -> { // Encode
            if (PolymerCommonUtils.isServerNetworkingThread() && PolymerEntityUtils.isPolymerEntityAttribute(content)) {
                return class_5134.field_23727;
            }
            return content;
        });
    }
}
