package com.lostglade.mixin;

import com.lostglade.server.RendererBotPresenceSystem;
import de.maxhenkel.voicechat.voice.common.PlayerState;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple Voice Chat normally publishes every logged-in player to the volume editor.
 * The camera account is a server-side utility player and must never be published there.
 */
@Pseudo
@Mixin(targets = "de.maxhenkel.voicechat.voice.server.PlayerStateManager")
public abstract class VoicechatPlayerStateRendererBotMixin {
	@Shadow(remap = false)
	@Final
	private ConcurrentHashMap<UUID, PlayerState> states;

	@Shadow(remap = false)
	public abstract void broadcastRemoveState(ServerPlayer player);

	@Inject(method = "broadcastState", at = @At("HEAD"), cancellable = true, remap = false)
	private void lg2$excludeRendererBotFromVoicechatPlayers(
			ServerPlayer player,
			PlayerState state,
			CallbackInfo ci
	) {
		if (!RendererBotPresenceSystem.isRendererBot(player)) {
			return;
		}

		states.remove(player.getUUID());
		broadcastRemoveState(player);
		ci.cancel();
	}
}
