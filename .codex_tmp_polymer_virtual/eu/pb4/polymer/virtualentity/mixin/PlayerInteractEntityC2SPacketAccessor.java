package eu.pb4.polymer.virtualentity.mixin;

import net.minecraft.class_2824;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(class_2824.class)
public interface PlayerInteractEntityC2SPacketAccessor {
    @Accessor
    int getEntityId();
}
