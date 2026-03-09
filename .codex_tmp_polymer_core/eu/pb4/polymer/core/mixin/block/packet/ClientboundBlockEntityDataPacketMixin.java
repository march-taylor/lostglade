package eu.pb4.polymer.core.mixin.block.packet;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import eu.pb4.polymer.core.api.block.PolymerBlockUtils;
import eu.pb4.polymer.core.impl.networking.TransformingPacketCodec;
import eu.pb4.polymer.core.mixin.block.ClientboundBlockEntityDataPacketAccessor;
import net.minecraft.class_2622;
import net.minecraft.class_9129;
import net.minecraft.class_9139;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import xyz.nucleoid.packettweaker.PacketContext;

@Mixin(class_2622.class)
public class ClientboundBlockEntityDataPacketMixin {
    @ModifyExpressionValue(method = "<clinit>", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/codec/StreamCodec;composite(Lnet/minecraft/network/codec/StreamCodec;Ljava/util/function/Function;Lnet/minecraft/network/codec/StreamCodec;Ljava/util/function/Function;Lnet/minecraft/network/codec/StreamCodec;Ljava/util/function/Function;Lcom/mojang/datafixers/util/Function3;)Lnet/minecraft/network/codec/StreamCodec;"))
    private static class_9139<class_9129, class_2622> changeNbt(class_9139<class_9129, class_2622> original) {
        return TransformingPacketCodec.encodeOnly(original, (buf, packet) -> {
            if (packet.method_11290() == null) {
                return packet;
            }
            var nbt = PolymerBlockUtils.transformBlockEntityNbt(PacketContext.get(), packet.method_11291(), packet.method_11290());
            if (packet.method_11290() == nbt) {
                return packet;
            }
            return ClientboundBlockEntityDataPacketAccessor.createBlockEntityUpdateS2CPacket(packet.method_11293(), packet.method_11291(), nbt);
        });
    }
}
