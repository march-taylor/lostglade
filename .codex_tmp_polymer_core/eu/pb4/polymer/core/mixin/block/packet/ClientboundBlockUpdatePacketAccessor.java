package eu.pb4.polymer.core.mixin.block.packet;

import net.minecraft.class_2626;
import net.minecraft.class_2680;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(class_2626.class)
public interface ClientboundBlockUpdatePacketAccessor {
    @Accessor("blockState")
    class_2680 polymer$getState();
}
