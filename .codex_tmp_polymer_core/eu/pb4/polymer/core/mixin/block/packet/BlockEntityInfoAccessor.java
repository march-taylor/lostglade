package eu.pb4.polymer.core.mixin.block.packet;

import net.minecraft.class_2591;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData$BlockEntityInfo")
public interface BlockEntityInfoAccessor {
    @Accessor
    class_2591<?> getType();
}
