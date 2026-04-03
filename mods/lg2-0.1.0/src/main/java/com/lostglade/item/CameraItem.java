package com.lostglade.item;

import com.lostglade.Lg2;
import com.lostglade.block.CameraBlock;
import com.lostglade.server.CameraPhotoMenuSystem;
import eu.pb4.polymer.core.api.item.PolymerBlockItem;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import xyz.nucleoid.packettweaker.PacketContext;

public final class CameraItem extends PolymerBlockItem {
	private static final Identifier MODEL_ID = Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "camera");
	private static final Identifier DISPLAY_MODEL_ID = Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "camera_display");
	private static final String DISPLAY_ROOT_TAG = "lg2_camera_display";
	private static final String DISPLAY_ONLY_TAG = "display_only";

	public CameraItem(CameraBlock block, Item.Properties settings) {
		super(block, settings, Items.SPYGLASS, true);
	}

	@Override
	public Component getName(ItemStack stack) {
		return Component.literal("Camera");
	}

	@Override
	public Item getPolymerItem(ItemStack stack, PacketContext context) {
		if (isDisplayOnly(stack) && !PolymerResourcePackUtils.hasMainPack(context)) {
			return Items.AIR;
		}
		return Items.SPYGLASS;
	}

	@Override
	public Identifier getPolymerItemModel(ItemStack itemStack, PacketContext context) {
		if (!PolymerResourcePackUtils.hasMainPack(context)) {
			return null;
		}
		return isDisplayOnly(itemStack) ? DISPLAY_MODEL_ID : MODEL_ID;
	}

	@Override
	public void modifyBasePolymerItemStack(ItemStack out, ItemStack original, PacketContext context) {
		if (isDisplayOnly(original)) {
			return;
		}
		CameraBlock.applyFallbackName(out, context);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}
		if (!player.isShiftKeyDown()) {
			return InteractionResult.PASS;
		}
		if (player instanceof ServerPlayer serverPlayer) {
			CameraPhotoMenuSystem.open(serverPlayer);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		if (context.getHand() != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}
		if (context.getLevel().getBlockState(context.getClickedPos()).getBlock() instanceof CameraBlock) {
			Player player = context.getPlayer();
			if (player != null && player.isShiftKeyDown()) {
				if (player instanceof ServerPlayer serverPlayer) {
					CameraPhotoMenuSystem.open(serverPlayer);
				}
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		}
		Player player = context.getPlayer();
		if (player != null && player.isShiftKeyDown()) {
			if (player instanceof ServerPlayer serverPlayer) {
				CameraPhotoMenuSystem.open(serverPlayer);
			}
			return InteractionResult.SUCCESS;
		}
		return super.useOn(context);
	}

	@Override
	public boolean canDestroyBlock(ItemStack stack, BlockState state, Level level, BlockPos pos, net.minecraft.world.entity.LivingEntity miningEntity) {
		return false;
	}

	public static ItemStack createDisplayStack() {
		ItemStack stack = new ItemStack(ModItems.CAMERA);
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
			var displayTag = tag.getCompoundOrEmpty(DISPLAY_ROOT_TAG);
			displayTag.putBoolean(DISPLAY_ONLY_TAG, true);
			tag.put(DISPLAY_ROOT_TAG, displayTag);
		});
		return stack;
	}

	private static boolean isDisplayOnly(ItemStack stack) {
		CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
		if (customData == null) {
			return false;
		}
		return customData.copyTag()
				.getCompoundOrEmpty(DISPLAY_ROOT_TAG)
				.getBooleanOr(DISPLAY_ONLY_TAG, false);
	}
}
