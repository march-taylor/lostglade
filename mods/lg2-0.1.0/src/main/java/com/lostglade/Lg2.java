package com.lostglade;

import com.lostglade.block.ModBlocks;
import com.lostglade.config.Lg2Config;
import com.lostglade.server.ServerGlitchSystem;
import com.lostglade.item.ModItems;
import com.lostglade.server.ServerStabilitySystem;
import com.lostglade.server.ServerStructureBreakSystem;
import com.lostglade.server.ServerUpgradeUiSystem;
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
		ServerStabilitySystem.register();
		ServerGlitchSystem.register();
		ServerStructureBreakSystem.register();
		ServerUpgradeUiSystem.register();

		LOGGER.info("Initialized {}", MOD_ID);
	}
}
