package eu.pb4.polymer.core.mixin.other;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.serialization.Codec;
import eu.pb4.polymer.common.api.PolymerCommonUtils;
import eu.pb4.polymer.core.api.entity.PolymerEntityUtils;
import eu.pb4.polymer.core.api.item.PolymerItemUtils;
import eu.pb4.polymer.core.impl.PolymerImplUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import xyz.nucleoid.packettweaker.PacketContext;

import java.util.Objects;
import java.util.function.Function;
import net.minecraft.class_2561;
import net.minecraft.class_2564;
import net.minecraft.class_2568;

@Mixin(class_2568.class)
public interface HoverEventMixin {
    @ModifyExpressionValue(method = "<clinit>", at = @At(value = "INVOKE", target = "Lcom/mojang/serialization/Codec;dispatch(Ljava/lang/String;Ljava/util/function/Function;Ljava/util/function/Function;)Lcom/mojang/serialization/Codec;"))
    private static Codec<class_2568> patchCodec(Codec<class_2568> codec) {
        return codec.xmap(Function.identity(), content -> { // Encode
            if (PolymerCommonUtils.isServerNetworkingThread()) {
                if (content.method_10892() == class_2568.class_5247.field_24344) {
                    var val = Objects.requireNonNull(((class_2568.class_10611) content).comp_3508());
                    if (PolymerEntityUtils.isPolymerEntityType(val.field_24351)) {
                        val = new class_2568.class_5248(val.field_24351, val.field_24352, val.field_24353);
                        return new class_2568.class_10613(class_2564.method_37112(val.method_27682(), class_2561.method_43470("\n")));
                    }
                }
            }
            return content;
        });
    }
}
