package com.lostglade.util;

import com.lostglade.mixin.DisplayAccessor;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class ItemDisplayHitboxHelper {
	private static final String GENNADIY_HOOK_CHAIN_TAG = "lg2.gennadiy_hook_chain";
	private static final String EXIT_SIGN_DISPLAY_TAG = "lg2_exit_sign_display";
	private static final String CAMERA_DISPLAY_TAG = "lg2_camera_display";
	private static final String SERVER_DISPLAY_TAG = "lg2_server_display";
	private static final String MARK_AXE_DISPLAY_TAG = "lg2.mark_throwing_axe";
	private static final String DRONE_DISPLAY_TAG = "lg2_drone_display";
	private static final String MONITOR_DISPLAY_TAG = "lg2_monitor_display";
	private static final String TROJAN_ROOSTER_DISPLAY_TAG = "lg2_trojan_rooster_display";
	private static final String SEASON_START_DISPLAY_TAG = "lg2_season_start_display";

	private ItemDisplayHitboxHelper() {
	}

	public static void clear(Display.ItemDisplay display) {
		if (display == null) {
			return;
		}
		display.noPhysics = true;
		((DisplayAccessor) display).lg2$setDisplayWidth(0.0F);
		((DisplayAccessor) display).lg2$setDisplayHeight(0.0F);
		display.refreshDimensions();
		Vec3 position = display.position();
		display.setBoundingBox(new AABB(position.x, position.y, position.z, position.x, position.y, position.z));
	}

	public static boolean isZeroHitboxDisplay(Entity entity) {
		if (!(entity instanceof Display.ItemDisplay)) {
			return false;
		}
		return entity.getTags().contains(GENNADIY_HOOK_CHAIN_TAG)
				|| entity.getTags().contains(EXIT_SIGN_DISPLAY_TAG)
				|| entity.getTags().contains(CAMERA_DISPLAY_TAG)
				|| entity.getTags().contains(SERVER_DISPLAY_TAG)
				|| entity.getTags().contains(MARK_AXE_DISPLAY_TAG)
				|| entity.getTags().contains(DRONE_DISPLAY_TAG)
				|| entity.getTags().contains(MONITOR_DISPLAY_TAG)
				|| entity.getTags().contains(TROJAN_ROOSTER_DISPLAY_TAG)
				|| entity.getTags().contains(SEASON_START_DISPLAY_TAG);
	}
}
