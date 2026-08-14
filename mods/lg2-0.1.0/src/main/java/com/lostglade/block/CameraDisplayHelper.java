package com.lostglade.block;

import com.lostglade.item.CameraItem;
import com.lostglade.util.ItemDisplayHitboxHelper;
import com.mojang.math.Transformation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Visible, non-interactive representation of a placed camera block. */
final class CameraDisplayHelper {
	private static final String ROOT_TAG = "lg2_camera_display";
	private static final String POS_TAG_PREFIX = "lg2_camera_display_pos:";
	private static final double SEARCH_RADIUS = 0.9D;

	private CameraDisplayHelper() {
	}

	static void spawnOrUpdate(ServerLevel level, BlockPos pos, float yaw, float pitch) {
		List<Display.ItemDisplay> displays = findDisplays(level, pos);
		Display.ItemDisplay display;
		boolean created = displays.isEmpty();
		if (created) {
			display = new Display.ItemDisplay(EntityType.ITEM_DISPLAY, level);
			display.addTag(ROOT_TAG);
			display.addTag(getPosTag(pos));
		} else {
			display = displays.get(0);
			for (int i = 1; i < displays.size(); i++) {
				displays.get(i).discard();
			}
		}

		configureDisplay(display, pos, yaw, pitch);
		if (created) {
			level.addFreshEntity(display);
		}
	}

	static void remove(ServerLevel level, BlockPos pos) {
		for (Display.ItemDisplay display : findDisplays(level, pos)) {
			display.discard();
		}
	}

	private static void configureDisplay(Display.ItemDisplay display, BlockPos pos, float yaw, float pitch) {
		Vec3 origin = CameraBlock.captureBaseOrigin(pos);
		// The fixed item transform uses the model's local forward axis.  It is
		// opposite to the camera view vector, so turn only the display around.
		float displayYaw = yaw + 180.0F;
		display.setPos(origin.x, origin.y, origin.z);
		display.setYRot(displayYaw);
		display.setXRot(-pitch);
		display.setYHeadRot(displayYaw);
		display.setYBodyRot(displayYaw);
		display.setItemStack(CameraItem.createDisplayStack());
		display.setItemTransform(ItemDisplayContext.FIXED);
		display.setBillboardConstraints(Display.BillboardConstraints.FIXED);
		display.setTransformation(Transformation.identity());
		display.setNoGravity(true);
		display.setInvulnerable(true);
		display.setSilent(true);
		display.setShadowRadius(0.0F);
		display.setShadowStrength(0.0F);
		display.setViewRange(1.0F);
		ItemDisplayHitboxHelper.clear(display);
	}

	private static List<Display.ItemDisplay> findDisplays(ServerLevel level, BlockPos pos) {
		String posTag = getPosTag(pos);
		return level.getEntities(
				EntityType.ITEM_DISPLAY,
				new AABB(pos).inflate(SEARCH_RADIUS),
				display -> display.getTags().contains(ROOT_TAG) && display.getTags().contains(posTag)
		);
	}

	private static String getPosTag(BlockPos pos) {
		return POS_TAG_PREFIX + pos.getX() + "," + pos.getY() + "," + pos.getZ();
	}
}
