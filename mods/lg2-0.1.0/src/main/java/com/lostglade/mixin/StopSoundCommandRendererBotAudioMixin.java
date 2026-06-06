package com.lostglade.mixin;

import com.lostglade.server.RendererBotCameraSystem;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.Identifier;
import net.minecraft.server.commands.StopSoundCommand;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;

@Mixin(StopSoundCommand.class)
public abstract class StopSoundCommandRendererBotAudioMixin {
	@Inject(method = "stopSound", at = @At("TAIL"))
	private static void lg2$mirrorRendererBotStopSound(
			CommandSourceStack source,
			Collection<ServerPlayer> targets,
			SoundSource soundSource,
			Identifier soundId,
			CallbackInfoReturnable<Integer> cir
	) {
		if (source == null || source.getServer() == null || cir.getReturnValueI() <= 0) {
			return;
		}
		RendererBotCameraSystem.mirrorTransientStopSound(source.getServer(), soundId, soundSource);
	}
}
