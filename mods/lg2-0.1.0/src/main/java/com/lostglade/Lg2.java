package com.lostglade;

import com.lostglade.block.ModBlocks;
import com.lostglade.config.Lg2Config;
import com.lostglade.server.ServerGlitchSystem;
import com.lostglade.server.ServerBackroomsSystem;
import com.lostglade.server.ServerBackroomsBlockBreakSystem;
import com.lostglade.server.ServerBackroomsStalkerSystem;
import com.lostglade.item.ModItems;
import com.lostglade.server.ServerAbsoluteInvisibilitySystem;
import com.lostglade.server.ServerBossBarVisibilitySystem;
import com.lostglade.server.CameraCaptureSystem;
import com.lostglade.server.map.MapImageRenderSystem;
import com.lostglade.server.ServerStabilitySystem;
import com.lostglade.server.ServerStructureBreakSystem;
import com.lostglade.server.ServerMechanicsGateSystem;
import com.lostglade.server.ServerRespectSystem;
import com.lostglade.server.ServerTabIntegration;
import com.lostglade.server.ServerTrojanRoosterSystem;
import com.lostglade.server.ServerUnusedMobSpawnSystem;
import com.lostglade.server.ServerUpgradeUiSystem;
import com.lostglade.server.ServerVoicechatIntegration;
import com.lostglade.server.ServerWebcamIntegration;
import com.lostglade.worldgen.ModWorldGen;
import net.fabricmc.api.ModInitializer;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Lg2 implements ModInitializer {
	public static final String MOD_ID = "lg2";
	public static final String CONTENT_NAMESPACE = "lostglade";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		Lg2Config.load();
		PolymerResourcePackUtils.addModAssets(MOD_ID);

		ModItems.register();
		ModBlocks.register();
		ModWorldGen.register();
		ServerBossBarVisibilitySystem.register();
		MapImageRenderSystem.register();
		CameraCaptureSystem.register();
		ServerStabilitySystem.register();
		ServerTrojanRoosterSystem.register();
		ServerGlitchSystem.register();
		ServerAbsoluteInvisibilitySystem.register();
		ServerBackroomsSystem.register();
		ServerBackroomsBlockBreakSystem.register();
		ServerBackroomsStalkerSystem.register();
		ServerStructureBreakSystem.register();
		ServerMechanicsGateSystem.register();
		ServerRespectSystem.register();
		ServerUnusedMobSpawnSystem.register();
		ServerTabIntegration.register();
		ServerVoicechatIntegration.register();
		ServerWebcamIntegration.register();
		ServerUpgradeUiSystem.register();

		LOGGER.info("Initialized {}", MOD_ID);
	}
}
