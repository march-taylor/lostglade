package com.lostglade.mixin;

import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.OptionalInt;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMenuTitleMixin {
	private static final String TITLE_SHIFT = "\ue905";
	private static final String VANILLA_3ROW_GLYPH = "\ue913";
	private static final String VANILLA_6ROW_GLYPH = "\ue914";
	private static final String TITLE_RESET = "\ue940\ue940\ue941\ue943";

	@Shadow
	@Final
	public ServerGamePacketListenerImpl connection;

	@Shadow
	private int containerCounter;

	@Invoker("initMenu")
	protected abstract void lg2$invokeInitMenu(AbstractContainerMenu menu);

	@Inject(
			method = "openMenu",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;send(Lnet/minecraft/network/protocol/Packet;)V"
			),
			cancellable = true,
			locals = LocalCapture.CAPTURE_FAILHARD
	)
	private void lg2$decorateVanillaChestMenus(
			MenuProvider provider,
			CallbackInfoReturnable<OptionalInt> cir,
			AbstractContainerMenu menu
	) {
		ServerPlayer self = (ServerPlayer) (Object) this;
		if (!PolymerResourcePackUtils.hasMainPack(self)) {
			return;
		}

		Component patchedTitle = lg2$createPatchedTitle(menu, provider.getDisplayName());
		if (patchedTitle == null) {
			return;
		}

		this.connection.send(new ClientboundOpenScreenPacket(menu.containerId, menu.getType(), patchedTitle));
		this.lg2$invokeInitMenu(menu);
		((PlayerContainerMenuAccessor) self).lg2$setContainerMenu(menu);
		cir.setReturnValue(OptionalInt.of(this.containerCounter));
	}

	private static Component lg2$createPatchedTitle(AbstractContainerMenu menu, Component originalTitle) {
		if (menu == null || originalTitle == null) {
			return null;
		}

		MenuType<?> menuType = menu.getType();
		String overlayGlyph;
		if (menuType == MenuType.GENERIC_9x3 || menuType == MenuType.SHULKER_BOX) {
			overlayGlyph = VANILLA_3ROW_GLYPH;
		} else if (menuType == MenuType.GENERIC_9x6) {
			overlayGlyph = VANILLA_6ROW_GLYPH;
		} else {
			return null;
		}

		String menuClassName = menu.getClass().getName();
		if (menuClassName.startsWith("com.lostglade.")) {
			return null;
		}

		if (lg2$containsCustomGuiGlyph(originalTitle.getString())) {
			return null;
		}

		MutableComponent prefix = Component.literal(TITLE_SHIFT + overlayGlyph + TITLE_RESET)
				.withStyle(style -> style.withColor(0xFFFFFF).withItalic(false));
		MutableComponent vanillaColoredTitle = originalTitle.copy()
				.withStyle(style -> style.withColor(0x404040).withItalic(false));
		return prefix.append(vanillaColoredTitle);
	}

	private static boolean lg2$containsCustomGuiGlyph(String value) {
		if (value == null || value.isEmpty()) {
			return false;
		}

		for (int i = 0; i < value.length(); i++) {
			char character = value.charAt(i);
			if (character >= '\ue000' && character <= '\uf8ff') {
				return true;
			}
		}
		return false;
	}

}
