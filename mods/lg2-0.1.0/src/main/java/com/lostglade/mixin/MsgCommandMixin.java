package com.lostglade.mixin;

import com.lostglade.server.ServerGlitchSystem;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.commands.MsgCommand;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;

@Mixin(MsgCommand.class)
public abstract class MsgCommandMixin {
	@Inject(method = "sendMessage", at = @At("HEAD"), cancellable = true)
	private static void lg2$interferePrivateMessages(
			CommandSourceStack source,
			Collection<ServerPlayer> targets,
			PlayerChatMessage message,
			CallbackInfo ci
	) {
		if (ServerGlitchSystem.handlePrivatePlayerMessageCommand(source, targets, message)) {
			ci.cancel();
		}
	}
}
