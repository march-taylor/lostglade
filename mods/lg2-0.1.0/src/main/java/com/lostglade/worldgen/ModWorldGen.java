package com.lostglade.worldgen;

import com.lostglade.Lg2;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

public final class ModWorldGen {
	public static final Identifier BITCOIN_ORE_FEATURE_ID = Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "bitcoin_ore_feature");
	public static final ResourceKey<PlacedFeature> BITCOIN_ORE_PLACED_KEY = ResourceKey.create(
			Registries.PLACED_FEATURE,
			Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "bitcoin_ore")
	);

	public static final Feature<?> BITCOIN_ORE_FEATURE = Registry.register(
			BuiltInRegistries.FEATURE,
			BITCOIN_ORE_FEATURE_ID,
			new BitcoinOreFeature()
	);

	private ModWorldGen() {
	}

	public static void register() {
		BiomeModifications.addFeature(
				BiomeSelectors.foundInOverworld(),
				GenerationStep.Decoration.UNDERGROUND_ORES,
				BITCOIN_ORE_PLACED_KEY
		);
	}
}
