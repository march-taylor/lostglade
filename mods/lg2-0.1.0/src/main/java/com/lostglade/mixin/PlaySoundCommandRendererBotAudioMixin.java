package com.lostglade.mixin;

import com.lostglade.server.RendererBotCameraSystem;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.commands.PlaySoundCommand;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;

@Mixin(PlaySoundCommand.class)
public abstract class PlaySoundCommandRendererBotAudioMixin {
	@Inject(method = "playSound", at = @At("TAIL"))
	private static void lg2$mirrorRendererBotCommandSound(
			CommandSourceStack source,
			Collection<ServerPlayer> targets,
			Identifier soundId,
			SoundSource soundSource,
			Vec3 pos,
			float volume,
			float pitch,
			float minVolume,
			CallbackInfoReturnable<Integer> cir
	) {
		if (source == null || source.getLevel() == null || soundId == null || soundSource == null || pos == null || cir.getReturnValueI() <= 0) {
			return;
		}
		RendererBotCameraSystem.mirrorTransientSound(
				source.getLevel(),
				Holder.direct(SoundEvent.createVariableRangeEvent(soundId)),
				soundSource,
				pos.x(),
				pos.y(),
				pos.z(),
				volume,
				pitch,
				source.getLevel().getRandom().nextLong()
		);
	}
}
