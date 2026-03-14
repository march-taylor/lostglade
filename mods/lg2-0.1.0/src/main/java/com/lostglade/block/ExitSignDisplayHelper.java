package com.lostglade.block;

import com.mojang.math.Transformation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class ExitSignDisplayHelper {
	private static final String ROOT_TAG = "lg2_exit_sign_display";
	private static final String POS_TAG_PREFIX = "lg2_exit_sign_display_pos:";
	private static final double SEARCH_RADIUS = 0.9D;
	private static final double STANDING_SIGN_Y_OFFSET = 0.3375D;

	private ExitSignDisplayHelper() {
	}

	public static void spawnOrUpdate(ServerLevel level, BlockPos pos, BlockState state) {
		if (!(state.getBlock() instanceof SignBlock signBlock)) {
			return;
		}

		List<Display.ItemDisplay> displays = findDisplays(level, pos);
		Display.ItemDisplay display;
		if (displays.isEmpty()) {
			display = new Display.ItemDisplay(EntityType.ITEM_DISPLAY, level);
			display.addTag(ROOT_TAG);
			display.addTag(getPosTag(pos));
			level.addFreshEntity(display);
		} else {
			display = displays.get(0);
			for (int i = 1; i < displays.size(); i++) {
				displays.get(i).discard();
			}
		}

		configureDisplay(display, signBlock, pos, state);
	}

	public static void ensureDisplay(ServerLevel level, BlockPos pos, BlockState state) {
		if (!(state.getBlock() instanceof SignBlock)) {
			return;
		}

		List<Display.ItemDisplay> displays = findDisplays(level, pos);
		if (displays.isEmpty()) {
			spawnOrUpdate(level, pos, state);
			return;
		}

		if (displays.size() > 1) {
			for (int i = 1; i < displays.size(); i++) {
				displays.get(i).discard();
			}
		}
	}

	public static void remove(ServerLevel level, BlockPos pos) {
		for (Display.ItemDisplay display : findDisplays(level, pos)) {
			display.discard();
		}
	}

	private static void configureDisplay(Display.ItemDisplay display, SignBlock signBlock, BlockPos pos, BlockState state) {
		Vec3 hitboxCenter = signBlock.getSignHitboxCenterPosition(state);
		Vec3 worldCenter = new Vec3(
				pos.getX() + hitboxCenter.x(),
				pos.getY() + hitboxCenter.y(),
				pos.getZ() + hitboxCenter.z()
		);
		if (signBlock instanceof StandingSignBlock) {
			worldCenter = worldCenter.add(0.0D, STANDING_SIGN_Y_OFFSET, 0.0D);
		}

		float yRot = signBlock.getYRotationDegrees(state) + 180.0F;
		display.setPos(worldCenter.x, worldCenter.y, worldCenter.z);
		display.setYRot(yRot);
		display.setXRot(0.0F);
		display.setYHeadRot(yRot);
		display.setYBodyRot(yRot);
		display.setItemStack(ExitSignItem.createDisplayStack());
		display.setItemTransform(ItemDisplayContext.FIXED);
		display.setBillboardConstraints(Display.BillboardConstraints.FIXED);
		display.setTransformation(Transformation.identity());
		display.setNoGravity(true);
		display.setInvulnerable(true);
		display.setSilent(true);
		display.setShadowRadius(0.0F);
		display.setShadowStrength(0.0F);
		display.setViewRange(1.0F);
	}

	private static List<Display.ItemDisplay> findDisplays(ServerLevel level, BlockPos pos) {
		String posTag = getPosTag(pos);
		AABB box = new AABB(pos).inflate(SEARCH_RADIUS);
		return level.getEntities(
				EntityType.ITEM_DISPLAY,
				box,
				display -> display.getTags().contains(ROOT_TAG) && display.getTags().contains(posTag)
		);
	}

	private static String getPosTag(BlockPos pos) {
		return POS_TAG_PREFIX + pos.getX() + "," + pos.getY() + "," + pos.getZ();
	}
}
