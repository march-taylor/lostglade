package com.lostglade.mixin.client;

import com.lostglade.client.RendererBotOffscreenWorldRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "ru.dimaskama.webcam.client.render.WebcamRenderLayer")
public abstract class WebcamRenderLayerOffscreenMixin {
	@Redirect(
			method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/AvatarRenderState;FF)V",
			at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;player:Lnet/minecraft/client/player/LocalPlayer;")
	)
	private LocalPlayer lg2$skipRealPlayerDistanceGate(Minecraft minecraft) {
		if (RendererBotOffscreenWorldRenderer.isOffscreenRenderActive()) {
			return null;
		}
		return minecraft.player;
	}
}
