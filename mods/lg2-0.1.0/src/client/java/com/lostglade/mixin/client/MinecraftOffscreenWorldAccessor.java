package com.lostglade.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Minecraft.class)
public interface MinecraftOffscreenWorldAccessor {
	@Accessor("level")
	ClientLevel lg2$getLevel();

	@Accessor("level")
	@Mutable
	void lg2$setLevel(ClientLevel level);

	@Accessor("levelRenderer")
	LevelRenderer lg2$getLevelRenderer();

	@Accessor("levelRenderer")
	@Mutable
	void lg2$setLevelRenderer(LevelRenderer levelRenderer);
}
