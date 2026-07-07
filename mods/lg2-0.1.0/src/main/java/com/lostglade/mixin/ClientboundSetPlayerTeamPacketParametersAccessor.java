package com.lostglade.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientboundSetPlayerTeamPacket.Parameters.class)
public interface ClientboundSetPlayerTeamPacketParametersAccessor {
	@Mutable
	@Accessor("displayName")
	void lg2$setDisplayName(Component displayName);

	@Mutable
	@Accessor("playerPrefix")
	void lg2$setPlayerPrefix(Component playerPrefix);

	@Mutable
	@Accessor("playerSuffix")
	void lg2$setPlayerSuffix(Component playerSuffix);
}
