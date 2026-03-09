package eu.pb4.polymer.core.mixin.entity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;
import net.minecraft.class_2781;

@Mixin(class_2781.class)
public interface ClientboundUpdateAttributesPacketAccessor {
    @Accessor
    List<class_2781.class_2782> getAttributes();
}
