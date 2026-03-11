package com.lostglade.worldgen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

import java.util.stream.Stream;

public final class BackroomsBiomeSource extends BiomeSource {
	public static final MapCodec<BackroomsBiomeSource> CODEC = RecordCodecBuilder.mapCodec(instance ->
			instance.group(
					Biome.CODEC.fieldOf("wide_lit_biome").forGetter(source -> source.wideLitBiome),
					Biome.CODEC.fieldOf("wide_dark_biome").forGetter(source -> source.wideDarkBiome),
					Biome.CODEC.fieldOf("middle_lit_biome").forGetter(source -> source.middleLitBiome),
					Biome.CODEC.fieldOf("middle_dark_biome").forGetter(source -> source.middleDarkBiome),
					Biome.CODEC.fieldOf("narrow_lit_biome").forGetter(source -> source.narrowLitBiome),
					Biome.CODEC.fieldOf("narrow_dark_biome").forGetter(source -> source.narrowDarkBiome)
			).apply(instance, BackroomsBiomeSource::new)
	);

	private final Holder<Biome> wideLitBiome;
	private final Holder<Biome> wideDarkBiome;
	private final Holder<Biome> middleLitBiome;
	private final Holder<Biome> middleDarkBiome;
	private final Holder<Biome> narrowLitBiome;
	private final Holder<Biome> narrowDarkBiome;

	public BackroomsBiomeSource(
			Holder<Biome> wideLitBiome,
			Holder<Biome> wideDarkBiome,
			Holder<Biome> middleLitBiome,
			Holder<Biome> middleDarkBiome,
			Holder<Biome> narrowLitBiome,
			Holder<Biome> narrowDarkBiome
	) {
		this.wideLitBiome = wideLitBiome;
		this.wideDarkBiome = wideDarkBiome;
		this.middleLitBiome = middleLitBiome;
		this.middleDarkBiome = middleDarkBiome;
		this.narrowLitBiome = narrowLitBiome;
		this.narrowDarkBiome = narrowDarkBiome;
	}

	@Override
	protected MapCodec<? extends BiomeSource> codec() {
		return CODEC;
	}

	@Override
	protected Stream<Holder<Biome>> collectPossibleBiomes() {
		return Stream.of(
				this.wideLitBiome,
				this.wideDarkBiome,
				this.middleLitBiome,
				this.middleDarkBiome,
				this.narrowLitBiome,
				this.narrowDarkBiome
		).distinct();
	}

	@Override
	public Holder<Biome> getNoiseBiome(int quartX, int quartY, int quartZ, Climate.Sampler sampler) {
		int blockX = QuartPos.toBlock(quartX);
		int blockZ = QuartPos.toBlock(quartZ);
		BackroomsLayout.ZoneType zone = BackroomsLayout.getZoneAtBlock(blockX, blockZ);
		return this.getBiomeForZone(zone);
	}

	private Holder<Biome> getBiomeForZone(BackroomsLayout.ZoneType zone) {
		return switch (zone) {
			case WIDE_LIT -> this.wideLitBiome;
			case WIDE_DARK -> this.wideDarkBiome;
			case MIDDLE_LIT -> this.middleLitBiome;
			case MIDDLE_DARK -> this.middleDarkBiome;
			case NARROW_LIT -> this.narrowLitBiome;
			case NARROW_DARK -> this.narrowDarkBiome;
		};
	}
}
