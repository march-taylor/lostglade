package com.lostglade.item;

import com.lostglade.Lg2;
import com.lostglade.server.StartupRaceAbilitySystem;
import eu.pb4.polymer.core.api.item.SimplePolymerItem;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import xyz.nucleoid.packettweaker.PacketContext;

/** Education Edition-style balloon that attaches to the player it is used on. */
public final class StartupBalloonItem extends SimplePolymerItem {
	private static final Identifier MODEL_ID = Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "startup_balloon");

	public StartupBalloonItem(Item.Properties properties) {
		super(properties, Items.FIREWORK_STAR);
	}

	@Override
	public Identifier getPolymerItemModel(ItemStack stack, PacketContext context) {
		return PolymerResourcePackUtils.hasMainPack(context) ? MODEL_ID : null;
	}

	@Override
	public void modifyBasePolymerItemStack(ItemStack out, ItemStack original, PacketContext context) {
		if (!PolymerResourcePackUtils.hasMainPack(context)) {
			out.set(DataComponents.CUSTOM_NAME, Component.literal("Воздушный шарик").withStyle(style -> style.withItalic(false)));
		}
	}

	@Override
	public InteractionResult interactLivingEntity(ItemStack stack, Player user, LivingEntity target, InteractionHand hand) {
		if (!(user instanceof ServerPlayer owner) || !(target instanceof ServerPlayer targetPlayer) || user.level().isClientSide()) {
			return InteractionResult.PASS;
		}
		if (!StartupRaceAbilitySystem.attachBalloon(owner, targetPlayer)) {
			return InteractionResult.FAIL;
		}
		if (!owner.getAbilities().instabuild) {
			stack.shrink(1);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		if (!(context.getPlayer() instanceof ServerPlayer owner) || context.getLevel().isClientSide()) {
			return InteractionResult.PASS;
		}
		if (!StartupRaceAbilitySystem.releaseBalloon(owner, context.getClickedPos(), context.getClickedFace())) {
			return InteractionResult.FAIL;
		}
		if (!owner.getAbilities().instabuild) {
			context.getItemInHand().shrink(1);
		}
		return InteractionResult.SUCCESS;
	}
}
