package eu.pb4.polymer.virtualentity.mixin.accessors;

import net.minecraft.class_2765;
import net.minecraft.class_3414;
import net.minecraft.class_3419;
import net.minecraft.class_6880;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(class_2765.class)
public interface ClientboundSoundEntityPacketAccessor {
    @Mutable
    @Accessor
    void setSound(class_6880<class_3414> sound);

    @Mutable
    @Accessor
    void setSource(class_3419 category);

    @Mutable
    @Accessor
    void setId(int entityId);

    @Mutable
    @Accessor
    void setVolume(float volume);

    @Mutable
    @Accessor
    void setPitch(float pitch);

    @Mutable
    @Accessor
    void setSeed(long seed);
}
