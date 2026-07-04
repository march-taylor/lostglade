package com.lostglade.block;

import com.mojang.math.Transformation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class RainbowBedDisplayHelper {
	private static final String ROOT_TAG = "lg2_rainbow_bed_display";
	private static final String POS_TAG_PREFIX = "lg2_rainbow_bed_display_pos:";
	private static final double SEARCH_RADIUS = 2.25D;

	private RainbowBedDisplayHelper() {
	}

	public static void spawnOrUpdate(ServerLevel level, BlockPos pos, BlockState state) {
		BlockPos footPos = footPos(pos, state);
		BlockState footState = level.getBlockState(footPos);
		if (!(footState.getBlock() instanceof RainbowBedBlock) || footState.getValue(BedBlock.PART) != BedPart.FOOT) {
			return;
		}

		Direction facing = footState.getValue(BedBlock.FACING);
		BlockPos headPos = footPos.relative(facing);
		BlockState headState = level.getBlockState(headPos);
		if (!(headState.getBlock() instanceof RainbowBedBlock) || headState.getValue(BedBlock.PART) != BedPart.HEAD) {
			return;
		}

		List<Display.ItemDisplay> displays = findDisplays(level, footPos);
		Display.ItemDisplay display = displays.isEmpty() ? null : displays.get(0);

		for (int i = 1; i < displays.size(); i++) {
			displays.get(i).discard();
		}

		if (display == null) {
			display = createDisplay(level, footPos);
		}
		configureDisplay(display, footPos, headPos, facing);
	}

	public static void ensureDisplay(ServerLevel level, BlockPos pos, BlockState state) {
		BlockPos footPos = footPos(pos, state);
		spawnOrUpdate(level, footPos, level.getBlockState(footPos));
	}

	public static void remove(ServerLevel level, BlockPos pos, BlockState state) {
		remove(level, footPos(pos, state));
	}

	private static void remove(ServerLevel level, BlockPos footPos) {
		for (Display.ItemDisplay display : findDisplays(level, footPos)) {
			display.discard();
		}
	}

	private static Display.ItemDisplay createDisplay(ServerLevel level, BlockPos footPos) {
		Display.ItemDisplay display = new Display.ItemDisplay(EntityType.ITEM_DISPLAY, level);
		display.addTag(ROOT_TAG);
		display.addTag(getPosTag(footPos));
		level.addFreshEntity(display);
		return display;
	}

	private static void configureDisplay(Display.ItemDisplay display, BlockPos footPos, BlockPos headPos, Direction facing) {
		Vec3 center = Vec3.atCenterOf(footPos).add(Vec3.atCenterOf(headPos)).scale(0.5D);
		display.setPos(center.x, center.y, center.z);
		// Special bed item models face opposite to the placed bed block direction,
		// so the world display needs a 180-degree correction to keep the head away from the player.
		float yRot = facing.getOpposite().toYRot();
		display.setYRot(yRot);
		display.setXRot(0.0F);
		display.setYHeadRot(yRot);
		display.setYBodyRot(yRot);
		display.setItemStack(RainbowBedItem.createDisplayStack());
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

	private static List<Display.ItemDisplay> findDisplays(ServerLevel level, BlockPos footPos) {
		String posTag = getPosTag(footPos);
		AABB box = new AABB(footPos).inflate(SEARCH_RADIUS);
		return level.getEntities(
				EntityType.ITEM_DISPLAY,
				box,
				display -> display.getTags().contains(ROOT_TAG) && display.getTags().contains(posTag)
		);
	}

	private static BlockPos footPos(BlockPos pos, BlockState state) {
		if (!(state.getBlock() instanceof RainbowBedBlock)) {
			return pos;
		}
		if (state.getValue(BedBlock.PART) == BedPart.FOOT) {
			return pos;
		}
		return pos.relative(state.getValue(BedBlock.FACING).getOpposite());
	}

	private static String getPosTag(BlockPos pos) {
		return POS_TAG_PREFIX + pos.getX() + "," + pos.getY() + "," + pos.getZ();
	}
}
