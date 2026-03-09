package eu.pb4.polymer.core.mixin.other;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.serialization.Codec;
import eu.pb4.polymer.common.api.PolymerCommonUtils;
import eu.pb4.polymer.core.api.other.PolymerParticleType;
import eu.pb4.polymer.core.impl.networking.TransformingPacketCodec;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import xyz.nucleoid.packettweaker.PacketContext;

import java.util.function.Function;
import net.minecraft.class_2394;
import net.minecraft.class_2398;
import net.minecraft.class_9129;
import net.minecraft.class_9139;

@Mixin(class_2398.class)
public class ParticleTypesMixin {
    @ModifyExpressionValue(method = "<clinit>", at = @At(value = "INVOKE", target = "Lcom/mojang/serialization/Codec;dispatch(Ljava/lang/String;Ljava/util/function/Function;Ljava/util/function/Function;)Lcom/mojang/serialization/Codec;"))
    private static Codec<class_2394> patchCodec(Codec<class_2394> codec) {
        return codec.xmap(Function.identity(), content -> { // Encode
            if (PolymerCommonUtils.isServerNetworkingThread() && PolymerParticleType.getOverlay(content.method_10295()) instanceof PolymerParticleType<?> type) {
                //noinspection unchecked
                return ((PolymerParticleType<class_2394>) type).getPolymerParticleReplacement(content, PacketContext.get());
            }
            return content;
        });
    }

    @ModifyExpressionValue(method = "<clinit>", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/codec/StreamCodec;dispatch(Ljava/util/function/Function;Ljava/util/function/Function;)Lnet/minecraft/network/codec/StreamCodec;"))
    private static class_9139<class_9129, class_2394> patchStreamCodec(class_9139<class_9129, class_2394> codec) {
        return TransformingPacketCodec.encodeOnly(codec, (buf, content) -> { // Encode
            if (PolymerCommonUtils.isServerNetworkingThread() && PolymerParticleType.getOverlay(content.method_10295()) instanceof PolymerParticleType<?> type) {
                //noinspection unchecked
                return ((PolymerParticleType<class_2394>) type).getPolymerParticleReplacement(content, PacketContext.get());
            }
            return content;
        });
    }
}
