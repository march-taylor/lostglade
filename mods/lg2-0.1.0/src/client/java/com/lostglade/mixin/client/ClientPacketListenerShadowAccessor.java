package com.lostglade.mixin.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientPacketListener.class)
public interface ClientPacketListenerShadowAccessor {
	@Accessor("level")
	ClientLevel lg2$getLevel();

	@Accessor("level")
	void lg2$setLevel(ClientLevel level);

	@Accessor("levelData")
	ClientLevel.ClientLevelData lg2$getLevelData();

	@Accessor("levelData")
	void lg2$setLevelData(ClientLevel.ClientLevelData levelData);
}
