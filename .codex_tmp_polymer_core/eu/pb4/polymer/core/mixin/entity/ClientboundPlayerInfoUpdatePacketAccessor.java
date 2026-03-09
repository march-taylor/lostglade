package eu.pb4.polymer.core.mixin.entity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;
import net.minecraft.class_2703;

@Mixin(class_2703.class)
public interface ClientboundPlayerInfoUpdatePacketAccessor {
    @Mutable
    @Accessor
    void setEntries(List<class_2703.class_2705> entries);
}
