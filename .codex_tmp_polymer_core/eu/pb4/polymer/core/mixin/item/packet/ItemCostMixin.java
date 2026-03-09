package eu.pb4.polymer.core.mixin.item.packet;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import eu.pb4.polymer.common.api.PolymerCommonUtils;
import eu.pb4.polymer.core.api.item.PolymerItemUtils;
import eu.pb4.polymer.core.impl.networking.TransformingPacketCodec;
import eu.pb4.polymer.core.impl.other.ComponentChangesMap;
import net.minecraft.class_9129;
import net.minecraft.class_9139;
import net.minecraft.class_9306;
import net.minecraft.class_9329;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import xyz.nucleoid.packettweaker.PacketContext;

@Mixin(class_9306.class)
public class ItemCostMixin {
    @ModifyExpressionValue(method = "<clinit>", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/codec/StreamCodec;composite(Lnet/minecraft/network/codec/StreamCodec;Ljava/util/function/Function;Lnet/minecraft/network/codec/StreamCodec;Ljava/util/function/Function;Lnet/minecraft/network/codec/StreamCodec;Ljava/util/function/Function;Lcom/mojang/datafixers/util/Function3;)Lnet/minecraft/network/codec/StreamCodec;"))
    private static class_9139<class_9129, class_9306> polymerifyTheStack(class_9139<class_9129, class_9306> original) {
        return new TransformingPacketCodec<>(original, (buf, tradedItem) -> {
            var input = tradedItem.comp_2427();
            var stack = PolymerItemUtils.getPolymerItemStack(input, PacketContext.get());
            return stack != input ? new class_9306(stack.method_7909().method_40131(), stack.method_7947(), class_9329.method_57865(new ComponentChangesMap(stack.method_57380()))) : tradedItem;
        }, (buf, tradedItem) -> {
            if (PolymerCommonUtils.isServerNetworkingThread()) {
                var input = tradedItem.comp_2427();
                var stack = PolymerItemUtils.getRealItemStack(input, buf.method_56349());
                return stack != input ? new class_9306(stack.method_7909().method_40131(), stack.method_7947(), class_9329.method_57865(new ComponentChangesMap(stack.method_57380()))) : tradedItem;
            }
            return tradedItem;
        });
    }
}
