package eu.pb4.polymer.virtualentity.mixin;

import net.minecraft.class_2734;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(class_2734.class)
public interface SetCameraEntityS2CPacketAccessor {
    @Mutable
    @Accessor
    void setCameraId(int id);
}
