package eu.pb4.polymer.core.mixin.block.packet;

import eu.pb4.polymer.core.api.block.PolymerBlockUtils;
import net.minecraft.class_2637;
import net.minecraft.class_2680;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import xyz.nucleoid.packettweaker.PacketContext;

@Mixin(value = class_2637.class, priority = 500)
public abstract class ClientboundSectionBlocksUpdatePacketMixin {
    @ModifyArg(method = "write", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/Block;getId(Lnet/minecraft/world/level/block/state/BlockState;)I"))
    private class_2680 polymer$replaceWithPolymerBlockState(class_2680 state) {
        return PolymerBlockUtils.getPolymerBlockState(state, PacketContext.get());
    }
}
