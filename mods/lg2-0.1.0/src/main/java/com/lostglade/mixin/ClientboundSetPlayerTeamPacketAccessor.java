package com.lostglade.mixin;

import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Optional;

@Mixin(ClientboundSetPlayerTeamPacket.class)
public interface ClientboundSetPlayerTeamPacketAccessor {
	@Accessor("parameters")
	Optional<ClientboundSetPlayerTeamPacket.Parameters> lg2$getParameters();
}
