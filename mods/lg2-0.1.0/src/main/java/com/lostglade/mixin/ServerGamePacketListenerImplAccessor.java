package com.lostglade.mixin;

import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ServerGamePacketListenerImpl.class)
public interface ServerGamePacketListenerImplAccessor {
	@Invoker("markClientLoaded")
	void lg2$markClientLoaded();

	@Accessor("tickCount")
	int lg2$getTickCount();

	@Accessor("tickCount")
	void lg2$setTickCount(int tickCount);

	@Accessor("receivedMovePacketCount")
	int lg2$getReceivedMovePacketCount();

	@Accessor("knownMovePacketCount")
	void lg2$setKnownMovePacketCount(int knownMovePacketCount);

	@Accessor("receivedMovementThisTick")
	void lg2$setReceivedMovementThisTick(boolean receivedMovementThisTick);
}
