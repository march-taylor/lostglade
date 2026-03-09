package eu.pb4.polymer.core.mixin.block.packet;

import eu.pb4.polymer.core.api.block.PolymerBlockUtils;
import net.minecraft.class_2248;
import net.minecraft.class_2623;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import xyz.nucleoid.packettweaker.PacketContext;

@Mixin(class_2623.class)
public abstract class ClientboundBlockEventPacketMixin {
    @Shadow
    public abstract class_2248 getBlock();

    @ModifyArg(method = "write", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/codec/StreamCodec;encode(Ljava/lang/Object;Ljava/lang/Object;)V"), index = 1)
    private Object polymer$replaceBlockLocal(Object block) {
        return PolymerBlockUtils.getPolymerBlock((class_2248) block, PacketContext.get());
    }
}
