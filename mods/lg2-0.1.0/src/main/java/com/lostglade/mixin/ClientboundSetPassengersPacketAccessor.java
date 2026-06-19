package com.lostglade.mixin;

import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientboundSetPassengersPacket.class)
public interface ClientboundSetPassengersPacketAccessor {
	@Mutable
	@Accessor("vehicle")
	void lg2$setVehicle(int vehicle);

	@Mutable
	@Accessor("passengers")
	void lg2$setPassengers(int[] passengers);
}
