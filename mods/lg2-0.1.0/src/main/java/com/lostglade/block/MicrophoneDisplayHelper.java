package com.lostglade.block;

import com.lostglade.util.ItemDisplayHitboxHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.AABB;

import java.util.List;

/** Visible, non-interactive representation of a placed microphone block. */
final class MicrophoneDisplayHelper {
	private static final String ROOT_TAG = "lg2_microphone_display";
	private static final String POS_TAG_PREFIX = "lg2_microphone_display_pos:";
	private static final double SEARCH_RADIUS = 0.9D;

	private MicrophoneDisplayHelper() {
	}

	static void spawnOrUpdate(ServerLevel level, BlockPos pos) {
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

		display.setPos(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
		display.setYRot(0.0F);
		display.setXRot(0.0F);
		display.setYHeadRot(0.0F);
		display.setYBodyRot(0.0F);
		display.setItemStack(MicrophoneBlockItem.createDisplayStack());
		display.setItemTransform(ItemDisplayContext.FIXED);
		display.setBillboardConstraints(Display.BillboardConstraints.FIXED);
		display.setNoGravity(true);
		display.setInvulnerable(true);
		display.setSilent(true);
		display.setShadowRadius(0.0F);
		display.setShadowStrength(0.0F);
		display.setViewRange(1.0F);
		ItemDisplayHitboxHelper.clear(display);
		if (created) {
			level.addFreshEntity(display);
		}
	}

	static void remove(ServerLevel level, BlockPos pos) {
		for (Display.ItemDisplay display : findDisplays(level, pos)) {
			display.discard();
		}
	}

	static List<Display.ItemDisplay> findDisplays(ServerLevel level, BlockPos pos) {
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
