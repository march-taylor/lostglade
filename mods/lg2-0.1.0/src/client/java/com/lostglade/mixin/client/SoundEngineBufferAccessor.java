package com.lostglade.mixin.client;

import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SoundEngine.class)
public interface SoundEngineBufferAccessor {
	@Accessor("soundBuffers")
	SoundBufferLibrary lg2$getSoundBuffers();
}
