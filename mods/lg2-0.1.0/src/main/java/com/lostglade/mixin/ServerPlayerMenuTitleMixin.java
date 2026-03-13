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
	private static final String VANILLA_SHULKER_GLYPH = "\ue915";
	private static final String INVENTORY_3ROW_EN_US = "\ue960";
	private static final String INVENTORY_3ROW_RU_RU = "\ue961";
	private static final String INVENTORY_3ROW_UK_UA = "\ue962";
	private static final String INVENTORY_3ROW_JA_JP = "\ue963";
	private static final String INVENTORY_3ROW_RPR = "\ue965";
	private static final String INVENTORY_6ROW_EN_US = "\ue966";
	private static final String INVENTORY_6ROW_RU_RU = "\ue967";
	private static final String INVENTORY_6ROW_UK_UA = "\ue968";
	private static final String INVENTORY_6ROW_JA_JP = "\ue969";
	private static final String INVENTORY_6ROW_RPR = "\ue96b";
	private static final String INVENTORY_SHULKER_EN_US = "\ue96c";
	private static final String INVENTORY_SHULKER_RU_RU = "\ue96d";
	private static final String INVENTORY_SHULKER_UK_UA = "\ue96e";
	private static final String INVENTORY_SHULKER_JA_JP = "\ue96f";
	private static final String INVENTORY_SHULKER_RPR = "\ue971";
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

		Component patchedTitle = lg2$createPatchedTitle(self, menu, provider.getDisplayName());
		if (patchedTitle == null) {
			return;
		}

		this.connection.send(new ClientboundOpenScreenPacket(menu.containerId, menu.getType(), patchedTitle));
		this.lg2$invokeInitMenu(menu);
		((PlayerContainerMenuAccessor) self).lg2$setContainerMenu(menu);
		cir.setReturnValue(OptionalInt.of(this.containerCounter));
	}

	private static Component lg2$createPatchedTitle(ServerPlayer player, AbstractContainerMenu menu, Component originalTitle) {
		if (menu == null || originalTitle == null) {
			return null;
		}

		MenuType<?> menuType = menu.getType();
		String overlayGlyph;
		String inventoryGlyph;
		if (menuType == MenuType.SHULKER_BOX) {
			overlayGlyph = VANILLA_SHULKER_GLYPH;
			inventoryGlyph = lg2$getInventoryLabelGlyph(player, menuType);
		} else if (menuType == MenuType.GENERIC_9x3) {
			overlayGlyph = VANILLA_3ROW_GLYPH;
			inventoryGlyph = lg2$getInventoryLabelGlyph(player, menuType);
		} else if (menuType == MenuType.GENERIC_9x6) {
			overlayGlyph = VANILLA_6ROW_GLYPH;
			inventoryGlyph = lg2$getInventoryLabelGlyph(player, menuType);
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

		MutableComponent prefix = Component.literal(TITLE_SHIFT + overlayGlyph + TITLE_RESET + TITLE_SHIFT + inventoryGlyph + TITLE_RESET + "\ue946\ue946")
				.withStyle(style -> style.withColor(0xFFFFFF).withItalic(false));
		MutableComponent vanillaColoredTitle = originalTitle.copy()
				.withStyle(style -> style.withColor(0x404040).withItalic(false));
		return prefix.append(vanillaColoredTitle);
	}

	private static String lg2$getInventoryLabelGlyph(ServerPlayer player, MenuType<?> menuType) {
		String locale = "en_us";
		if (player != null && player.clientInformation() != null && player.clientInformation().language() != null) {
			locale = player.clientInformation().language().toLowerCase(java.util.Locale.ROOT);
		}

		boolean isShulker = menuType == MenuType.SHULKER_BOX;
		boolean isSixRow = menuType == MenuType.GENERIC_9x6;
		if (locale.startsWith("ru")) {
			return isShulker ? INVENTORY_SHULKER_RU_RU : (isSixRow ? INVENTORY_6ROW_RU_RU : INVENTORY_3ROW_RU_RU);
		}
		if (locale.startsWith("uk")) {
			return isShulker ? INVENTORY_SHULKER_UK_UA : (isSixRow ? INVENTORY_6ROW_UK_UA : INVENTORY_3ROW_UK_UA);
		}
		if (locale.startsWith("ja")) {
			return isShulker ? INVENTORY_SHULKER_JA_JP : (isSixRow ? INVENTORY_6ROW_JA_JP : INVENTORY_3ROW_JA_JP);
		}
		if (locale.startsWith("rpr")) {
			return isShulker ? INVENTORY_SHULKER_RPR : (isSixRow ? INVENTORY_6ROW_RPR : INVENTORY_3ROW_RPR);
		}
		return isShulker ? INVENTORY_SHULKER_EN_US : (isSixRow ? INVENTORY_6ROW_EN_US : INVENTORY_3ROW_EN_US);
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
