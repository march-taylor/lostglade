package eu.pb4.polymer.virtualentity.mixin;

import net.minecraft.class_2752;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(class_2752.class)
public interface EntityPassengersSetS2CPacketAccessor {
    @Mutable
    @Accessor
    void setVehicle(int id);

    @Mutable
    @Accessor
    void setPassengers(int[] passengerIds);
}
