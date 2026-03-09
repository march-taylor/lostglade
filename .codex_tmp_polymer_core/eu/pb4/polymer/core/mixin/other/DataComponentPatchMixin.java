package eu.pb4.polymer.core.mixin.other;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.serialization.Codec;
import eu.pb4.polymer.common.api.PolymerCommonUtils;
import eu.pb4.polymer.core.api.other.PolymerComponent;
import eu.pb4.polymer.core.impl.TransformingComponent;
import eu.pb4.polymer.core.impl.networking.TransformingPacketCodec;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.nucleoid.packettweaker.PacketContext;

import java.util.function.Function;
import net.minecraft.class_9129;
import net.minecraft.class_9139;
import net.minecraft.class_9326;
import net.minecraft.class_9331;

@Mixin(class_9326.class)
public class DataComponentPatchMixin {
    @Mutable
    @Shadow @Final public static class_9139<class_9129, class_9326> STREAM_CODEC;

    @ModifyExpressionValue(method = "<clinit>", at = @At(value = "INVOKE", target = "Lcom/mojang/serialization/Codec;xmap(Ljava/util/function/Function;Ljava/util/function/Function;)Lcom/mojang/serialization/Codec;"))
    private static Codec<class_9326> patchCodec(Codec<class_9326> codec) {
        return codec.xmap(Function.identity(), content -> { // Encode
            if (PolymerCommonUtils.isServerNetworkingThread()) {
                return transformContent(content);
            }
            return content;
        });
    }

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void patchNetCodec(CallbackInfo ci) {
        STREAM_CODEC = TransformingPacketCodec.encodeOnly(STREAM_CODEC, ((byteBuf, content) -> transformContent(content)));
    }

    @Unique
    private static class_9326 transformContent(class_9326 content) {
        var player = PacketContext.get();
        var builder = class_9326.method_57841();
        for (var entry : content.method_57846()) {
            if (!PolymerComponent.canSync(entry.getKey(), entry.getValue().orElse(null), player)) {
                continue;
            } else if (entry.getValue().isPresent() && entry.getValue().get() instanceof TransformingComponent t) {
                //noinspection unchecked
                builder.method_57854((class_9331<Object>) entry.getKey(), t.polymer$getTransformed(player));
            }

            if (entry.getValue().isPresent()) {
                //noinspection unchecked
                builder.method_57854((class_9331<Object>) entry.getKey(), entry.getValue().get());
            } else {
                builder.method_57853(entry.getKey());
            }
        }
        return builder.method_57852();
    }
}
