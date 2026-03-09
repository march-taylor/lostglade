package eu.pb4.polymer.core.mixin.item;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import eu.pb4.polymer.common.api.PolymerCommonUtils;
import eu.pb4.polymer.core.api.item.PolymerItemUtils;
import eu.pb4.polymer.core.impl.PolymerImplUtils;
import eu.pb4.polymer.core.impl.other.PolymerTooltipType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import xyz.nucleoid.packettweaker.PacketContext;

import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.class_1799;
import net.minecraft.class_1836;


@Mixin(class_1799.class)
public class ItemStackMixin {
    @ModifyExpressionValue(method = "addDetailsToTooltip", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/TooltipFlag;isAdvanced()Z"))
    private boolean removeAdvanced(boolean original, @Local(ordinal = 0, argsOnly = true) class_1836 type) {
        return original && !(type instanceof PolymerTooltipType);
    }

    @ModifyArg(method = "<clinit>", at = @At(value = "INVOKE", target = "Lcom/mojang/serialization/MapCodec;recursive(Ljava/lang/String;Ljava/util/function/Function;)Lcom/mojang/serialization/MapCodec;"))
    private static Function<Codec<class_1799>, MapCodec<class_1799>> patchCodec(Function<Codec<class_1799>, MapCodec<class_1799>> function) {
        return (mapCodec) -> function.apply(mapCodec).xmap(content -> { // Decode
            if (PolymerCommonUtils.isServerNetworkingThread()) {
                var context = PacketContext.get();
                var lookup = context.getRegistryWrapperLookup() != null ? context.getRegistryWrapperLookup() : PolymerImplUtils.FALLBACK_LOOKUP;
                return PolymerItemUtils.getRealItemStack(content, lookup);
            }
            return content;
        }, content -> { // Encode
            if (PolymerCommonUtils.isServerNetworkingThread()) {
                var ctx = PacketContext.get();
                if (ctx.getBackingPacketListener() == null) {
                    return content;
                }
                return PolymerItemUtils.getPolymerItemStack(content, ctx);
            }
            return content;
        });
    }

    @ModifyArg(method = "<clinit>", at = @At(value = "INVOKE", target = "Lcom/mojang/serialization/Codec;lazyInitialized(Ljava/util/function/Supplier;)Lcom/mojang/serialization/Codec;", ordinal = 1))
    private static Supplier<Codec<class_1799>> patchCodec2(Supplier<Codec<class_1799>> codec) {
        return () -> codec.get().xmap(content -> { // Decode
            if (PolymerCommonUtils.isServerNetworkingThread()) {
                var context = PacketContext.get();
                var lookup = context.getRegistryWrapperLookup() != null ? context .getRegistryWrapperLookup() : PolymerImplUtils.FALLBACK_LOOKUP;
                return PolymerItemUtils.getRealItemStack(content, lookup);
            }
            return content;
        }, content -> { // Encode
            if (PolymerCommonUtils.isServerNetworkingThread()) {
                var ctx = PacketContext.get();
                if (ctx.getBackingPacketListener() == null) {
                    return content;
                }
                return PolymerItemUtils.getPolymerItemStack(content, ctx);
            }
            return content;
        });
    }
}
