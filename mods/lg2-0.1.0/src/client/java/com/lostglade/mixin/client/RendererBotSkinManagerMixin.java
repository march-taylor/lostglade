package com.lostglade.mixin.client;

import com.lostglade.client.RendererBotClientMode;
import net.minecraft.client.resources.SkinManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(SkinManager.class)
public abstract class RendererBotSkinManagerMixin {
	@ModifyVariable(method = "createLookup", at = @At("HEAD"), ordinal = 0, argsOnly = true)
	private boolean lg2$allowInsecurePlayerTexturesForRendererBot(boolean requireSecureTextures) {
		return RendererBotClientMode.isEnabled() ? false : requireSecureTextures;
	}
}
