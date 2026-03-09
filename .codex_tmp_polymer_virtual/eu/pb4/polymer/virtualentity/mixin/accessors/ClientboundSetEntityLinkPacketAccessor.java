package eu.pb4.polymer.virtualentity.mixin.accessors;

import net.minecraft.class_2740;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(class_2740.class)
public interface ClientboundSetEntityLinkPacketAccessor {

    @Mutable
    @Accessor
    void setSourceId(int attachedEntityId);

    @Mutable
    @Accessor
    void setDestId(int holdingEntityId);
}
