package com.lostglade.mixin.client;

import com.lostglade.client.RendererBotClientAudioCapture;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SoundEngine.class)
public abstract class SoundEngineRendererBotAudioMixin {
	@Inject(method = "play", at = @At("RETURN"))
	private void lg2$captureRendererBotAudioSound(SoundInstance sound, CallbackInfoReturnable<SoundEngine.PlayResult> cir) {
		SoundEngine.PlayResult result = cir.getReturnValue();
		if (result == SoundEngine.PlayResult.STARTED || result == SoundEngine.PlayResult.STARTED_SILENTLY) {
			RendererBotClientAudioCapture.onSoundStarted((SoundEngine) (Object) this, sound);
		}
	}

	@Inject(method = "stop(Lnet/minecraft/client/resources/sounds/SoundInstance;)V", at = @At("HEAD"))
	private void lg2$stopRendererBotAudioSound(SoundInstance sound, CallbackInfo ci) {
		RendererBotClientAudioCapture.onSoundStopped(sound);
	}

	@Inject(method = "stopAll", at = @At("HEAD"))
	private void lg2$stopAllRendererBotAudioSounds(CallbackInfo ci) {
		RendererBotClientAudioCapture.onAllSoundsStopped();
	}

	@Inject(method = "stop(Lnet/minecraft/resources/Identifier;Lnet/minecraft/sounds/SoundSource;)V", at = @At("HEAD"))
	private void lg2$stopRendererBotAudioSoundsById(Identifier identifier, SoundSource source, CallbackInfo ci) {
		RendererBotClientAudioCapture.onSoundStopById(identifier, source);
	}
}
