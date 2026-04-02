package com.lostglade.mixin.client;

import com.lostglade.client.RendererBotClientMode;
import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.main.GameConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class RendererBotMinecraftAuthMixin {
	@Inject(method = "createUserApiService", at = @At("HEAD"), cancellable = true)
	private void lg2$useOfflineUserApiService(YggdrasilAuthenticationService authenticationService, GameConfig gameConfig, CallbackInfoReturnable<UserApiService> cir) {
		if (RendererBotClientMode.isEnabled()) {
			cir.setReturnValue(UserApiService.OFFLINE);
		}
	}
}
