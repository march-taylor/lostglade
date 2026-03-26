package com.lostglade.server;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableMultimap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.lostglade.Lg2;
import com.lostglade.config.RaceConfig;
import com.lostglade.config.RaceConfig.PlayerRaceConfig;
import com.lostglade.config.RaceConfig.RaceAbilityConfig;
import com.lostglade.config.RaceConfig.RaceAbilitySlot;
import com.lostglade.item.ModItems;
import com.lostglade.item.TubochkaItem;
import com.lostglade.mixin.MobXpRewardAccessor;
import com.lostglade.mixin.PlayerTrackedDataAccessor;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.brigadier.context.CommandContext;
import eu.pb4.polymer.core.api.entity.PolymerEntity;
import eu.pb4.polymer.core.api.entity.PolymerEntityUtils;
import eu.pb4.polymer.core.api.item.PolymerItemUtils;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import it.unimi.dsi.fastutil.Pair;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.lionarius.skinrestorer.SkinRestorer;
import net.lionarius.skinrestorer.mineskin.MineskinService;
import net.lionarius.skinrestorer.skin.SkinService;
import net.lionarius.skinrestorer.skin.SkinStorage;
import net.lionarius.skinrestorer.skin.SkinValue;
import net.lionarius.skinrestorer.skin.SkinVariant;
import net.lionarius.skinrestorer.util.PlayerUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.RemoteChatSession;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.SharedConstants;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;
import xyz.nucleoid.packettweaker.PacketContext;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.PriorityQueue;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static net.minecraft.commands.Commands.literal;

public final class ServerRaceSystem {
	private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
	private static final String DIALOG_NAMESPACE = "lg2";
	private static final String DIALOG_PATH_PREFIX = "race_menu_";
	private static final String DIALOGS_PACK_DIR = "lg2_race_dialogs";
	private static final String DIALOGS_PACK_ID = "file/" + DIALOGS_PACK_DIR;
	private static final String DIALOGS_PACK_DESCRIPTION = "LG2 generated race dialogs";
	private static final String QUICK_ACTIONS_ROUTER_DIALOG_ID = "lg2:race_quick_actions";
	private static final int DEFAULT_DIALOG_COLUMNS = 2;
	private static final int DEFAULT_ACTION_WIDTH = 220;
	private static final int EXIT_ACTION_WIDTH = 200;
	private static final String MISTER_CARTEL_49_RACE_ID = "mister_cartel_49";
	private static final String TITLE_OVERLAY_SHIFT = "\ue905";
	private static final String TITLE_OVERLAY_RESET = "\ue940\ue940\ue941\ue943";
	private static final int TITLE_OVERLAY_TARGET_ADVANCE = 168;
	private static final int TITLE_OVERLAY_SHIFT_ADVANCE = -8;
	private static final String CARTEL_PASSPORT_OVERLAY_GLYPH = "\uef10";
	private static final FontDescription CARTEL_PASSPORT_OVERLAY_FONT = new FontDescription.Resource(
			Objects.requireNonNull(Identifier.tryParse("lg2:passport_title"))
	);
	private static final FontDescription CARTEL_PASSPORT_NAME_FONT = new FontDescription.Resource(
		Objects.requireNonNull(Identifier.tryParse("lg2:passport_name"))
	);
	private static final int CARTEL_PASSPORT_NAME_CHAR_ADVANCE = 5;
	private static final int CARTEL_PASSPORT_NAME_MIN_X = 18;
	private static final int CARTEL_PASSPORT_NAME_CENTER_X = 144;
	private static final int CARTEL_PASSPORT_OVERLAY_X_OFFSET = 168;
	private static final int MISTER_CARTEL_49_STACK_LIMIT = 49;
	private static final String CARTEL_SUMMON_TAG = "lg2.cartel_summon";
	private static final String CARTEL_LAWYER_TAG = "lg2.cartel_lawyer";
	private static final String CARTEL_LAWYER_MARKER_NAME = "lg2_cartel_lawyer_marker";
	private static final double CARTEL_TARGET_RANGE = 7.0D;
	private static final int CARTEL_SPAWN_OFFSET_BLOCKS = 3;
	private static final double CARTEL_DEFAULT_COOLDOWN_SECONDS = 5.0D;
	private static final double CARTEL_DEFAULT_LIFETIME_SECONDS = 30.0D;
	private static final double CARTEL_DEFAULT_AFTER_KILL_SECONDS = 2.0D;
	private static final double CARTEL_CHASE_SPEED = 1.0D;
	private static final long CARTEL_RAIDER_NAV_INTERVAL_TICKS = 4L;
	private static final double CARTEL_DEFAULT_DEFENSE_DURATION_SECONDS = 20.0D;
	private static final double CARTEL_DEFAULT_DEFENSE_INNER_DISTANCE = 1.0D;
	private static final double CARTEL_DEFAULT_DEFENSE_FOLLOW_DISTANCE = 5.0D;
	private static final double CARTEL_DEFAULT_DEFENSE_OUTSIDE_SECONDS = 5.0D;
	private static final double CARTEL_DEFAULT_DEFENSE_HEALTH_POINTS = 0.0D;
	private static final double CARTEL_DEFAULT_DEFENSE_REFLECT_RATIO = 0.5D;
	private static final double CARTEL_DEFAULT_UNIQUE_DURATION_SECONDS = 300.0D;
	private static final double CARTEL_DEFAULT_UNIQUE_COOLDOWN_SECONDS = 300.0D;
	private static final double CARTEL_DEFAULT_SHNYAGA_TRAVKA_DROP_CHANCE = 0.25D;
	private static final double CARTEL_DEFAULT_SHNYAGA_MIN_GROWTH_SECONDS = 120.0D;
	private static final double CARTEL_DEFAULT_SHNYAGA_MAX_GROWTH_SECONDS = 240.0D;
	private static final long CARTEL_FERN_GROWTH_RETRY_TICKS = 100L;
	private static final long MISTER_CARTEL_STACK_CHECK_INTERVAL_TICKS = 8L;
	private static final double CARTEL_LAWYER_BASE_MOVE_SPEED = 0.23D;
	private static final double CARTEL_LAWYER_WALK_SPEED = CARTEL_LAWYER_BASE_MOVE_SPEED;
	private static final double CARTEL_LAWYER_RETURN_SPEED = CARTEL_LAWYER_BASE_MOVE_SPEED * 1.5D;
	private static final long CARTEL_LAWYER_MOVEMENT_LOGIC_INTERVAL_TICKS = 1L;
	private static final double CARTEL_LAWYER_STEERING_SMOOTHING = 0.35D;
	private static final EntityDimensions CARTEL_LAWYER_DIMENSIONS = EntityDimensions.fixed(0.6F, 1.8F);
	private static final int CARTEL_DISGUISE_MENU_ROWS = 3;
	private static final int CARTEL_DISGUISE_PREVIOUS_SLOT = 11;
	private static final int CARTEL_DISGUISE_HEAD_SLOT = 13;
	private static final int CARTEL_DISGUISE_NEXT_SLOT = 15;
	private static final int CARTEL_DISGUISE_PACK_PREVIOUS_SLOT = 12;
	private static final int CARTEL_DISGUISE_PACK_HEAD_SLOT = 13;
	private static final int CARTEL_DISGUISE_PACK_NEXT_SLOT = 14;
	private static final String CARTEL_LAWYER_SKIN_VALUE = "ewogICJ0aW1lc3RhbXAiIDogMTc1MjAzMzk0NjY5MSwKICAicHJvZmlsZUlkIiA6ICI0ZWE3NGM1ZGUyZGI0OGY2YjViOTk1YTVhNTYzMmU0NCIsCiAgInByb2ZpbGVOYW1lIiA6ICJNclNjYXJ5U3BhY2VDYXQiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMjRkNDQ3MDc4N2M4NWRlNWI5ODE5ODVkNDBmOTI5NzNhNmQxMmQ5ZDYxNzc0NGM3YWQzOGY4MWZmMTA3YTE5ZCIKICAgIH0KICB9Cn0=";
	private static final URI CARTEL_LAWYER_SKIN_URI = URI.create("https://textures.minecraft.net/texture/24d4470787c85de5b981985d40f92973a6d12d9d617744c7ad38f81ff107a19d");
	private static final Property CARTEL_LAWYER_FALLBACK_SKIN_PROPERTY = new Property("textures", CARTEL_LAWYER_SKIN_VALUE);

	private static final Map<String, PlayerRaceConfig> RACES_BY_NICKNAME = new LinkedHashMap<>();
	private static final Map<String, String> DIALOG_ID_BY_NICKNAME = new LinkedHashMap<>();
	private static final Map<String, String> GENERATED_DIALOG_JSON_BY_PATH = new LinkedHashMap<>();
	private static final Map<UUID, Long> CARTEL_ATTACK_COOLDOWNS = new LinkedHashMap<>();
	private static final Map<UUID, CartelSummonSession> CARTEL_SUMMON_SESSIONS = new LinkedHashMap<>();
	private static final Map<UUID, Long> CARTEL_DEFENSE_COOLDOWNS = new LinkedHashMap<>();
	private static final Map<UUID, CartelDefenseSession> CARTEL_DEFENSE_SESSIONS = new LinkedHashMap<>();
	private static final Map<UUID, Long> CARTEL_UNIQUE_COOLDOWNS = new LinkedHashMap<>();
	private static final Map<UUID, CartelDisguiseSession> CARTEL_DISGUISE_SESSIONS = new LinkedHashMap<>();
	private static final List<CartelTravkaGrowthAttempt> CARTEL_TRAVKA_GROWTH_ATTEMPTS = new ArrayList<>();
	private static final Map<CartelFernGrowthKey, CartelFernGrowthTask> CARTEL_PLANTED_FERN_GROWTHS = new LinkedHashMap<>();
	private static final PriorityQueue<CartelFernGrowthTask> CARTEL_PLANTED_FERN_GROWTH_QUEUE = new PriorityQueue<>(Comparator.comparingLong(task -> task.growAtTick));
	private static final Map<UUID, UUID> CARTEL_SUMMON_OWNER_BY_ENTITY = new LinkedHashMap<>();
	private static final Map<UUID, UUID> CARTEL_LAWYER_OWNER_BY_ENTITY = new LinkedHashMap<>();
	private static final ThreadLocal<Boolean> CARTEL_DEFENSE_REFLECTION_ACTIVE = ThreadLocal.withInitial(() -> Boolean.FALSE);
	private static CompletableFuture<Property> CARTEL_LAWYER_SKIN_FUTURE;
	private static volatile Property CARTEL_LAWYER_SKIN_PROPERTY;
	private static final class CartelSummonSession {
		private final ResourceKey<Level> dimension;
		private final UUID ownerPlayerId;
		private final UUID targetPlayerId;
		private final long normalExpireTick;
		private final long afterKillTicks;
		private final List<UUID> raiderIds = new ArrayList<>();
		private Long afterKillExpireTick;

		private CartelSummonSession(ResourceKey<Level> dimension, UUID ownerPlayerId, UUID targetPlayerId, long normalExpireTick, long afterKillTicks) {
			this.dimension = dimension;
			this.ownerPlayerId = ownerPlayerId;
			this.targetPlayerId = targetPlayerId;
			this.normalExpireTick = normalExpireTick;
			this.afterKillTicks = afterKillTicks;
		}
	}

	private static final class CartelDefenseSession {
		private final ResourceKey<Level> dimension;
		private final UUID protectedPlayerId;
		private final UUID lawyerEntityId;
		private final UUID lawyerProfileId;
		private final long endTick;
		private final double innerMinDistanceBlocks;
		private final double followMaxDistanceBlocks;
		private final long maxOutsideTicks;
		private final float reflectedDamageRatio;
		private long nextMovementLogicTick;
		private long nextWanderRetargetTick;
		private Vec3 wanderTarget;
		private Long outsideSinceTick;

		private CartelDefenseSession(
				ResourceKey<Level> dimension,
				UUID protectedPlayerId,
				UUID lawyerEntityId,
				UUID lawyerProfileId,
				long endTick,
				double innerMinDistanceBlocks,
				double followMaxDistanceBlocks,
				long maxOutsideTicks,
				float reflectedDamageRatio
		) {
			this.dimension = dimension;
			this.protectedPlayerId = protectedPlayerId;
			this.lawyerEntityId = lawyerEntityId;
			this.lawyerProfileId = lawyerProfileId;
			this.endTick = endTick;
			this.innerMinDistanceBlocks = innerMinDistanceBlocks;
			this.followMaxDistanceBlocks = followMaxDistanceBlocks;
			this.maxOutsideTicks = maxOutsideTicks;
			this.reflectedDamageRatio = reflectedDamageRatio;
		}
	}

	private static final class CartelDisguiseSession {
		private final SkinValue originalSkin;
		private final SkinValue disguisedSkin;
		private final String disguisedName;
		private final long endTick;

		private CartelDisguiseSession(SkinValue originalSkin, SkinValue disguisedSkin, String disguisedName, long endTick) {
			this.originalSkin = originalSkin;
			this.disguisedSkin = disguisedSkin;
			this.disguisedName = disguisedName;
			this.endTick = endTick;
		}
	}

	private static final class CartelTravkaGrowthAttempt {
		private final UUID playerId;
		private final ResourceKey<Level> dimension;
		private final BlockPos pos;
		private final long resolveTick;
		private final double chance;

		private CartelTravkaGrowthAttempt(UUID playerId, ResourceKey<Level> dimension, BlockPos pos, long resolveTick, double chance) {
			this.playerId = playerId;
			this.dimension = dimension;
			this.pos = pos.immutable();
			this.resolveTick = resolveTick;
			this.chance = chance;
		}
	}

	private record CartelFernGrowthKey(ResourceKey<Level> dimension, BlockPos pos) {
		private CartelFernGrowthKey {
			pos = pos.immutable();
		}
	}

	private static final class CartelFernGrowthTask {
		private final CartelFernGrowthKey key;
		private final long growAtTick;
		private final double chance;

		private CartelFernGrowthTask(CartelFernGrowthKey key, long growAtTick, double chance) {
			this.key = key;
			this.growAtTick = growAtTick;
			this.chance = chance;
		}
	}

	private ServerRaceSystem() {
	}

	public static void register() {
		rebuildCache();
		registerCommands();

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			RaceConfig.load();
			rebuildCache();
			prewarmCartelLawyerSkinAsync();
			syncGeneratedDialogs(server, true);
			Lg2.LOGGER.info("Loaded {} configured personal races", RACES_BY_NICKNAME.size());
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			cleanupAllCartelRaceEntities(server, true);
			restoreAllCartelDisguises(server);
			RACES_BY_NICKNAME.clear();
			DIALOG_ID_BY_NICKNAME.clear();
			GENERATED_DIALOG_JSON_BY_PATH.clear();
			CARTEL_ATTACK_COOLDOWNS.clear();
			CARTEL_SUMMON_SESSIONS.clear();
			CARTEL_DEFENSE_COOLDOWNS.clear();
			CARTEL_DEFENSE_SESSIONS.clear();
			CARTEL_UNIQUE_COOLDOWNS.clear();
			CARTEL_DISGUISE_SESSIONS.clear();
			CARTEL_TRAVKA_GROWTH_ATTEMPTS.clear();
			CARTEL_PLANTED_FERN_GROWTHS.clear();
			CARTEL_PLANTED_FERN_GROWTH_QUEUE.clear();
			CARTEL_SUMMON_OWNER_BY_ENTITY.clear();
			CARTEL_LAWYER_OWNER_BY_ENTITY.clear();
			CARTEL_LAWYER_SKIN_FUTURE = null;
			CARTEL_LAWYER_SKIN_PROPERTY = null;
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
				getRace(handler.player).ifPresent(race ->
						Lg2.LOGGER.info("Assigned personal race '{}' to {}", race.id, handler.player.getGameProfile().name())
				)
		);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			cleanupCartelEntitiesForDisconnect(server, handler.player);
			clearCartelDisguise(handler.player);
			CARTEL_TRAVKA_GROWTH_ATTEMPTS.removeIf(attempt -> attempt.playerId.equals(handler.player.getUUID()));
		});
		UseItemCallback.EVENT.register((player, world, hand) -> {
			if (!(player instanceof ServerPlayer serverPlayer) || world.isClientSide()) {
				return InteractionResult.PASS;
			}
			return TubochkaItem.tryLightTubochka(serverPlayer) ? InteractionResult.SUCCESS : InteractionResult.PASS;
		});
		UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
			if (!(player instanceof ServerPlayer serverPlayer) || world.isClientSide()) {
				return InteractionResult.PASS;
			}
			if (TubochkaItem.tryLightTubochka(serverPlayer)) {
				return InteractionResult.SUCCESS;
			}
			return onUseBlock(serverPlayer, hand, hitResult.getBlockPos());
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			long nowTick = server.overworld().getGameTime();
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if ((nowTick + player.getId()) % MISTER_CARTEL_STACK_CHECK_INTERVAL_TICKS == 0L) {
					enforceMrCartel49StackLimit(player);
				}
			}
			tickCartelSummons(server);
			tickCartelDefense(server);
			tickCartelDisguises(server);
			tickCartelTravkaGrowthAttempts(server);
			tickCartelFernGrowths(server);
		});
	}

	public static void reload() {
		RaceConfig.load();
		rebuildCache();
	}

	private static void registerCommands() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				dispatcher.register(literal("race")
						.then(literal("menu").executes(ServerRaceSystem::openMenu))
						.then(literal("reload")
								.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
								.executes(ServerRaceSystem::reloadFromCommand)
						)
						.then(literal("use")
								.then(literal("attack").executes(context -> useAbility(context, RaceAbilitySlot.ATTACK)))
								.then(literal("defense").executes(context -> useAbility(context, RaceAbilitySlot.DEFENSE)))
								.then(literal("ability").executes(context -> useAbility(context, RaceAbilitySlot.UNIQUE_ABILITY)))
								.then(literal("shnyaga").executes(context -> useAbility(context, RaceAbilitySlot.SHNYAGA)))
						)
				)
		);
	}

	private static int reloadFromCommand(CommandContext<CommandSourceStack> context) {
		reload();
		syncGeneratedDialogs(context.getSource().getServer(), true);
		context.getSource().sendSuccess(() -> Component.literal("Race config and race dialogs reloaded"), true);
		return 1;
	}

	private static int openMenu(CommandContext<CommandSourceStack> context) {
		ServerPlayer player = context.getSource().getPlayer();
		if (player == null) {
			context.getSource().sendFailure(Component.translatable("message.lg2.race.player_only"));
			return 0;
		}

		Optional<PlayerRaceConfig> raceOptional = getRace(player);
		if (raceOptional.isEmpty()) {
			player.sendSystemMessage(Component.translatable("message.lg2.race.no_race"));
			return 0;
		}

		String dialogId = DIALOG_ID_BY_NICKNAME.get(normalizeNickname(player.getGameProfile().name()));
		if (dialogId == null || dialogId.isBlank()) {
			player.sendSystemMessage(Component.translatable("message.lg2.race.no_menu"));
			return 0;
		}

		if (!runDialogCommand(player, "show @s " + dialogId)) {
			player.sendSystemMessage(Component.translatable("message.lg2.race.no_menu"));
			Lg2.LOGGER.warn("Failed to open race dialog '{}' for {}", dialogId, player.getGameProfile().name());
			return 0;
		}

		return 1;
	}
	private static int useAbility(CommandContext<CommandSourceStack> context, RaceAbilitySlot slot) {
		ServerPlayer player = context.getSource().getPlayer();
		if (player == null) {
			context.getSource().sendFailure(Component.translatable("message.lg2.race.player_only"));
			return 0;
		}

		Optional<PlayerRaceConfig> raceOptional = getRace(player);
		if (raceOptional.isEmpty()) {
			player.sendSystemMessage(Component.translatable("message.lg2.race.no_race"));
			return 0;
		}

		PlayerRaceConfig race = raceOptional.get();
		RaceAbilityConfig ability = getAbility(race, slot);
		if (!ability.enabled) {
			player.sendSystemMessage(Component.translatable("message.lg2.race.ability_disabled", Component.literal(ability.name)));
			return 0;
		}

		if (slot == RaceAbilitySlot.ATTACK && MISTER_CARTEL_49_RACE_ID.equals(sanitizePath(race.id))) {
			return useMrCartelAttack(player, race, ability);
		}
		if (slot == RaceAbilitySlot.DEFENSE && MISTER_CARTEL_49_RACE_ID.equals(sanitizePath(race.id))) {
			return useMrCartelDefense(player, race, ability);
		}
		if (slot == RaceAbilitySlot.UNIQUE_ABILITY && MISTER_CARTEL_49_RACE_ID.equals(sanitizePath(race.id))) {
			return useMrCartelUniqueAbility(player, race, ability);
		}

		Lg2.LOGGER.info("Player {} used race ability '{}' from race '{}'", player.getGameProfile().name(), ability.abilityId, race.id);
		return 1;
	}

	public static Optional<PlayerRaceConfig> getRace(ServerPlayer player) {
		return player == null ? Optional.empty() : getRace(player.getGameProfile().name());
	}

	public static Optional<PlayerRaceConfig> getRace(String nickname) {
		if (nickname == null || nickname.isBlank()) {
			return Optional.empty();
		}
		return Optional.ofNullable(RACES_BY_NICKNAME.get(normalizeNickname(nickname)));
	}

	public static Optional<RaceAbilityConfig> getAbility(ServerPlayer player, RaceAbilitySlot slot) {
		return getRace(player).map(race -> getAbility(race, slot));
	}

	public static RaceAbilityConfig getAbility(PlayerRaceConfig race, RaceAbilitySlot slot) {
		return switch (slot) {
			case ATTACK -> race.attack;
			case DEFENSE -> race.defense;
			case UNIQUE_ABILITY -> race.uniqueAbility;
			case SHNYAGA -> race.shnyaga;
			case STOCK -> race.stock;
		};
	}

	public static Collection<PlayerRaceConfig> getAllRaces() {
		return Collections.unmodifiableCollection(RACES_BY_NICKNAME.values());
	}

	public static void onFernPlaced(ServerPlayer player, BlockPos pos) {
		if (player == null || pos == null || !(player.level() instanceof ServerLevel level)) {
			return;
		}

		clearCartelFernGrowth(level.dimension(), pos);

		Optional<PlayerRaceConfig> raceOptional = getRace(player);
		if (raceOptional.isEmpty()) {
			return;
		}

		PlayerRaceConfig race = raceOptional.get();
		if (!MISTER_CARTEL_49_RACE_ID.equals(sanitizePath(race.id)) || race.shnyaga == null || !race.shnyaga.enabled) {
			return;
		}

		if (!level.getBlockState(pos).is(Blocks.FERN)) {
			return;
		}

		scheduleCartelFernGrowth(level, pos, race.shnyaga);
	}

	private static InteractionResult onUseBlock(ServerPlayer player, InteractionHand hand, BlockPos pos) {
		ItemStack stack = player.getItemInHand(hand);
		if (!stack.is(Items.BONE_MEAL)) {
			return InteractionResult.PASS;
		}

		Optional<PlayerRaceConfig> raceOptional = getRace(player);
		if (raceOptional.isEmpty()) {
			return InteractionResult.PASS;
		}

		PlayerRaceConfig race = raceOptional.get();
		if (!MISTER_CARTEL_49_RACE_ID.equals(sanitizePath(race.id)) || race.shnyaga == null || !race.shnyaga.enabled) {
			return InteractionResult.PASS;
		}

		ServerLevel level = (ServerLevel) player.level();
		if (!level.getBlockState(pos).is(Blocks.FERN)) {
			return InteractionResult.PASS;
		}

		double chance = race.shnyaga.chance > 0.0D ? race.shnyaga.chance : CARTEL_DEFAULT_SHNYAGA_TRAVKA_DROP_CHANCE;
		CARTEL_TRAVKA_GROWTH_ATTEMPTS.add(new CartelTravkaGrowthAttempt(player.getUUID(), level.dimension(), pos, level.getGameTime() + 1L, chance));
		return InteractionResult.PASS;
	}

	private static void tickCartelTravkaGrowthAttempts(MinecraftServer server) {
		if (CARTEL_TRAVKA_GROWTH_ATTEMPTS.isEmpty()) {
			return;
		}

		for (int i = CARTEL_TRAVKA_GROWTH_ATTEMPTS.size() - 1; i >= 0; i--) {
			CartelTravkaGrowthAttempt attempt = CARTEL_TRAVKA_GROWTH_ATTEMPTS.get(i);
			ServerLevel level = server.getLevel(attempt.dimension);
			if (level == null) {
				CARTEL_TRAVKA_GROWTH_ATTEMPTS.remove(i);
				continue;
			}

			if (level.getGameTime() < attempt.resolveTick) {
				continue;
			}

			if (level.getBlockState(attempt.pos).is(Blocks.LARGE_FERN)) {
				clearCartelFernGrowth(level.dimension(), attempt.pos);
				dropTravkaFromCartelFernGrowth(level, attempt.pos, attempt.chance);
			}

			CARTEL_TRAVKA_GROWTH_ATTEMPTS.remove(i);
		}
	}

	private static void scheduleCartelFernGrowth(ServerLevel level, BlockPos pos, RaceAbilityConfig ability) {
		if (level == null || pos == null || ability == null) {
			return;
		}

		double chance = ability.chance > 0.0D ? ability.chance : CARTEL_DEFAULT_SHNYAGA_TRAVKA_DROP_CHANCE;
		double minGrowthSeconds = ability.minGrowthSeconds > 0.0D ? ability.minGrowthSeconds : CARTEL_DEFAULT_SHNYAGA_MIN_GROWTH_SECONDS;
		double maxGrowthSeconds = ability.maxGrowthSeconds > 0.0D ? ability.maxGrowthSeconds : CARTEL_DEFAULT_SHNYAGA_MAX_GROWTH_SECONDS;
		if (maxGrowthSeconds < minGrowthSeconds) {
			maxGrowthSeconds = minGrowthSeconds;
		}

		long nowTick = level.getServer() != null ? level.getServer().overworld().getGameTime() : level.getGameTime();
		long minGrowthTicks = asTicks(minGrowthSeconds);
		long maxGrowthTicks = Math.max(minGrowthTicks, asTicks(maxGrowthSeconds));
		long randomDelay = maxGrowthTicks > minGrowthTicks
				? Math.round(level.random.nextDouble() * (double) (maxGrowthTicks - minGrowthTicks))
				: 0L;
		CartelFernGrowthKey key = new CartelFernGrowthKey(level.dimension(), pos);
		CartelFernGrowthTask task = new CartelFernGrowthTask(key, nowTick + minGrowthTicks + randomDelay, chance);
		CARTEL_PLANTED_FERN_GROWTHS.put(key, task);
		CARTEL_PLANTED_FERN_GROWTH_QUEUE.add(task);
	}

	private static void tickCartelFernGrowths(MinecraftServer server) {
		if (server == null || CARTEL_PLANTED_FERN_GROWTH_QUEUE.isEmpty()) {
			return;
		}

		long nowTick = server.overworld().getGameTime();
		while (true) {
			CartelFernGrowthTask task = CARTEL_PLANTED_FERN_GROWTH_QUEUE.peek();
			if (task == null || task.growAtTick > nowTick) {
				return;
			}

			CARTEL_PLANTED_FERN_GROWTH_QUEUE.poll();
			CartelFernGrowthTask currentTask = CARTEL_PLANTED_FERN_GROWTHS.get(task.key);
			if (currentTask != task) {
				continue;
			}

			ServerLevel level = server.getLevel(task.key.dimension());
			if (level == null) {
				CARTEL_PLANTED_FERN_GROWTHS.remove(task.key);
				continue;
			}

			BlockPos pos = task.key.pos();
			if (!level.hasChunkAt(pos) || !level.hasChunkAt(pos.above())) {
				rescheduleCartelFernGrowth(task, nowTick + CARTEL_FERN_GROWTH_RETRY_TICKS);
				continue;
			}

			if (!level.getBlockState(pos).is(Blocks.FERN)) {
				CARTEL_PLANTED_FERN_GROWTHS.remove(task.key);
				continue;
			}

			if (!canCartelFernGrow(level, pos)) {
				rescheduleCartelFernGrowth(task, nowTick + CARTEL_FERN_GROWTH_RETRY_TICKS);
				continue;
			}

			growCartelFern(level, pos, task.chance);
			CARTEL_PLANTED_FERN_GROWTHS.remove(task.key);
		}
	}

	private static void rescheduleCartelFernGrowth(CartelFernGrowthTask task, long nextTick) {
		CartelFernGrowthTask replacement = new CartelFernGrowthTask(task.key, nextTick, task.chance);
		CARTEL_PLANTED_FERN_GROWTHS.put(task.key, replacement);
		CARTEL_PLANTED_FERN_GROWTH_QUEUE.add(replacement);
	}

	private static void clearCartelFernGrowth(ResourceKey<Level> dimension, BlockPos pos) {
		if (dimension == null || pos == null) {
			return;
		}
		CARTEL_PLANTED_FERN_GROWTHS.remove(new CartelFernGrowthKey(dimension, pos));
	}

	private static boolean canCartelFernGrow(ServerLevel level, BlockPos pos) {
		if (level == null || pos == null || !level.getBlockState(pos).is(Blocks.FERN)) {
			return false;
		}
		if (pos.getY() < level.getMinY() || pos.getY() >= level.getMaxY() - 1) {
			return false;
		}
		return level.getBlockState(pos.above()).canBeReplaced();
	}

	private static void growCartelFern(ServerLevel level, BlockPos pos, double chance) {
		DoublePlantBlock.placeAt(level, Blocks.LARGE_FERN.defaultBlockState(), pos, 2);
		dropTravkaFromCartelFernGrowth(level, pos, chance);
	}

	private static void dropTravkaFromCartelFernGrowth(ServerLevel level, BlockPos pos, double chance) {
		if (level == null || pos == null || level.random.nextDouble() >= chance) {
			return;
		}
		Vec3 dropPos = Vec3.atCenterOf(pos);
		ItemEntity itemEntity = new ItemEntity(level, dropPos.x, dropPos.y, dropPos.z, new ItemStack(ModItems.TRAVKA));
		itemEntity.setDefaultPickUpDelay();
		level.addFreshEntity(itemEntity);
	}

	private static void rebuildCache() {
		RACES_BY_NICKNAME.clear();
		DIALOG_ID_BY_NICKNAME.clear();
		GENERATED_DIALOG_JSON_BY_PATH.clear();

		Set<String> usedDialogPaths = new HashSet<>();
		for (PlayerRaceConfig race : RaceConfig.get().races) {
			if (race == null || !race.enabled || race.ownerNickname == null || race.ownerNickname.isBlank()) {
				continue;
			}

			String normalizedNickname = normalizeNickname(race.ownerNickname);
			String dialogPath = buildDialogPath(race, usedDialogPaths);
			String dialogId = DIALOG_NAMESPACE + ":" + dialogPath;
			DIALOG_ID_BY_NICKNAME.put(normalizedNickname, dialogId);
			GENERATED_DIALOG_JSON_BY_PATH.put(dialogPath, buildRaceDialogJson(race));

			PlayerRaceConfig previous = RACES_BY_NICKNAME.put(normalizedNickname, race);
			if (previous != null && previous != race) {
				Lg2.LOGGER.warn(
						"Duplicate personal race owner '{}' found. Race '{}' overrides '{}'",
						race.ownerNickname,
						race.id,
						previous.id
				);
			}
		}
	}

	private static String normalizeNickname(String nickname) {
		return nickname.trim().toLowerCase(Locale.ROOT);
	}

	private static boolean runDialogCommand(ServerPlayer player, String dialogCommandTail) {
		MinecraftServer server = player.level().getServer();
		if (server == null) {
			return false;
		}

		CommandSourceStack source = server.createCommandSourceStack()
				.withPermission(PermissionSet.ALL_PERMISSIONS)
				.withSuppressedOutput();
		String command = "execute as " + player.getGameProfile().name() + " run dialog " + dialogCommandTail;
		try {
			server.getCommands().performPrefixedCommand(source, command);
			return true;
		} catch (Exception exception) {
			Lg2.LOGGER.error("Failed to execute dialog command '{}'", command, exception);
			return false;
		}
	}

	private static void syncGeneratedDialogs(MinecraftServer server, boolean requestReload) {
		boolean changed = writeGeneratedDialogsPack(server);
		if (!requestReload) {
			return;
		}

		List<String> selected = new ArrayList<>(server.getPackRepository().getSelectedIds());
		if (!selected.contains(DIALOGS_PACK_ID)) {
			selected.add(DIALOGS_PACK_ID);
			changed = true;
		}

		if (!changed) {
			return;
		}

		server.reloadResources(selected).whenComplete((unused, throwable) -> {
			if (throwable != null) {
				Lg2.LOGGER.error("Failed to reload generated race dialogs datapack", throwable);
				return;
			}
			Lg2.LOGGER.info("Reloaded generated race dialogs datapack");
		});
	}

	private static boolean writeGeneratedDialogsPack(MinecraftServer server) {
		Path packRoot = server.getWorldPath(LevelResource.DATAPACK_DIR).resolve(DIALOGS_PACK_DIR);
		Path dialogDir = packRoot.resolve("data").resolve(DIALOG_NAMESPACE).resolve("dialog");
		Path quickActionsTagFile = packRoot.resolve("data").resolve("minecraft").resolve("tags").resolve("dialog").resolve("quick_actions.json");
		boolean changed = false;

		try {
			Files.createDirectories(dialogDir);
			changed |= writeIfChanged(packRoot.resolve("pack.mcmeta"), buildPackMcmetaJson());
			changed |= writeIfChanged(quickActionsTagFile, buildQuickActionsTagJson());

			Set<String> expectedFileNames = new HashSet<>();
			for (Map.Entry<String, String> entry : GENERATED_DIALOG_JSON_BY_PATH.entrySet()) {
				String fileName = entry.getKey() + ".json";
				expectedFileNames.add(fileName);
				changed |= writeIfChanged(dialogDir.resolve(fileName), entry.getValue());
			}

			try (Stream<Path> files = Files.list(dialogDir)) {
				for (Path file : files.toList()) {
					String fileName = file.getFileName().toString();
					if (!fileName.startsWith(DIALOG_PATH_PREFIX) || !fileName.endsWith(".json")) {
						continue;
					}
					if (!expectedFileNames.contains(fileName)) {
						Files.deleteIfExists(file);
						changed = true;
					}
				}
			}
		} catch (IOException exception) {
			Lg2.LOGGER.error("Failed to write generated race dialogs datapack", exception);
		}

		return changed;
	}

	private static boolean writeIfChanged(Path file, String content) throws IOException {
		String existing = Files.exists(file) ? Files.readString(file, StandardCharsets.UTF_8) : null;
		if (existing != null && existing.equals(content)) {
			return false;
		}

		Files.createDirectories(file.getParent());
		Files.writeString(file, content, StandardCharsets.UTF_8);
		return true;
	}

	private static String buildPackMcmetaJson() {
		int packFormat = SharedConstants.getCurrentVersion().packVersion(PackType.SERVER_DATA).major();
		Map<String, Object> pack = new LinkedHashMap<>();
		pack.put("pack_format", packFormat);
		pack.put("min_format", packFormat);
		pack.put("max_format", packFormat);
		pack.put("description", DIALOGS_PACK_DESCRIPTION);

		Map<String, Object> root = new LinkedHashMap<>();
		root.put("pack", pack);
		return GSON.toJson(root);
	}

	private static String buildQuickActionsTagJson() {
		List<String> values = new ArrayList<>();
		values.add(QUICK_ACTIONS_ROUTER_DIALOG_ID);

		Map<String, Object> root = new LinkedHashMap<>();
		root.put("replace", Boolean.TRUE);
		root.put("values", values);
		return GSON.toJson(root);
	}

	private static String buildRaceDialogJson(PlayerRaceConfig race) {
		Map<String, Object> root = new LinkedHashMap<>();
		root.put("type", "minecraft:multi_action");
		root.put("title", textComponent(race.displayName));
		root.put("external_title", textComponent(race.displayName));
		root.put("can_close_with_escape", Boolean.TRUE);
		root.put("pause", Boolean.FALSE);
		root.put("columns", DEFAULT_DIALOG_COLUMNS);

		List<Map<String, Object>> actions = new ArrayList<>();
		appendAbilityAction(actions, race.attack, "/race use attack");
		appendAbilityAction(actions, race.defense, "/race use defense");
		appendAbilityAction(actions, race.uniqueAbility, "/race use ability");
		appendAbilityAction(actions, race.shnyaga, "/race use shnyaga");

		if (actions.isEmpty()) {
			Map<String, Object> action = new LinkedHashMap<>();
			action.put("label", textComponent("No active abilities"));
			action.put("tooltip", textComponent("This race has no active ability buttons"));
			action.put("width", DEFAULT_ACTION_WIDTH);
			action.put("action", runCommandAction("/race use ability"));
			actions.add(action);
		}

		root.put("actions", actions);

		Map<String, Object> exitAction = new LinkedHashMap<>();
		exitAction.put("label", translateComponent("gui.back"));
		exitAction.put("width", EXIT_ACTION_WIDTH);
		root.put("exit_action", exitAction);

		return GSON.toJson(root);
	}

	private static void appendAbilityAction(List<Map<String, Object>> actions, RaceAbilityConfig ability, String command) {
		if (ability == null || !ability.enabled) {
			return;
		}

		Map<String, Object> action = new LinkedHashMap<>();
		action.put("label", textComponent(nonBlank(ability.name, "Ability")));
		action.put("tooltip", textComponent(nonBlank(ability.description, nonBlank(ability.name, "Ability"))));
		action.put("width", DEFAULT_ACTION_WIDTH);
		action.put("action", runCommandAction(command));
		actions.add(action);
	}

	private static Map<String, Object> runCommandAction(String command) {
		Map<String, Object> action = new LinkedHashMap<>();
		action.put("type", "minecraft:run_command");
		action.put("command", command);
		return action;
	}

	private static Map<String, Object> textComponent(String text) {
		Map<String, Object> component = new LinkedHashMap<>();
		component.put("text", nonBlank(text, ""));
		return component;
	}

	private static Map<String, Object> translateComponent(String key) {
		Map<String, Object> component = new LinkedHashMap<>();
		component.put("translate", key);
		return component;
	}

	private static String nonBlank(String value, String fallback) {
		if (value == null) {
			return fallback;
		}
		String normalized = value.trim();
		return normalized.isEmpty() ? fallback : normalized;
	}

	private static String buildDialogPath(PlayerRaceConfig race, Set<String> usedPaths) {
		String base = DIALOG_PATH_PREFIX + sanitizePath(race.id) + "_" + sanitizePath(race.ownerNickname);
		String candidate = base;
		int index = 2;
		while (usedPaths.contains(candidate)) {
			candidate = base + "_" + index;
			index++;
		}
		usedPaths.add(candidate);
		return candidate;
	}

	private static String sanitizePath(String value) {
		if (value == null || value.isBlank()) {
			return "race";
		}

		StringBuilder builder = new StringBuilder(value.length());
		for (int i = 0; i < value.length(); i++) {
			char ch = Character.toLowerCase(value.charAt(i));
			if ((ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9') || ch == '_') {
				builder.append(ch);
			} else {
				builder.append('_');
			}
		}

		String sanitized = builder.toString().replaceAll("_+", "_");
		sanitized = sanitized.replaceAll("^_+", "");
		sanitized = sanitized.replaceAll("_+$", "");
		return sanitized.isEmpty() ? "race" : sanitized;
	}

	private static int useMrCartelDefense(ServerPlayer caster, PlayerRaceConfig race, RaceAbilityConfig ability) {
		try {
			ServerLevel level = caster.level();
			long nowTick = level.getGameTime();
			long cooldownTicks = asTicks(positiveOrDefault(ability.cooldownSeconds, CARTEL_DEFAULT_COOLDOWN_SECONDS));
			long nextAllowedTick = CARTEL_DEFENSE_COOLDOWNS.getOrDefault(caster.getUUID(), 0L);
			if (cooldownTicks > 0 && nowTick < nextAllowedTick) {
				double remaining = (nextAllowedTick - nowTick) / 20.0D;
				caster.displayClientMessage(
						Component.literal(String.format(Locale.ROOT, "%.1fs", remaining))
								.withStyle(ChatFormatting.RED),
						true
				);
				return 0;
			}

			CartelDefenseSession existing = CARTEL_DEFENSE_SESSIONS.remove(caster.getUUID());
			if (existing != null) {
				despawnCartelLawyer(level.getServer(), existing);
			}

			long durationTicks = asTicks(positiveOrDefault(ability.durationSeconds, CARTEL_DEFAULT_DEFENSE_DURATION_SECONDS));
			double followMaxDistance = positiveOrDefault(ability.followMaxDistanceBlocks, CARTEL_DEFAULT_DEFENSE_FOLLOW_DISTANCE);
			double innerMinDistance = Math.min(
					positiveOrDefault(ability.innerMinDistanceBlocks, CARTEL_DEFAULT_DEFENSE_INNER_DISTANCE),
					Math.max(0.0D, followMaxDistance - 0.25D)
			);
			long maxOutsideTicks = asTicks(positiveOrDefault(ability.maxOutsideAreaSeconds, CARTEL_DEFAULT_DEFENSE_OUTSIDE_SECONDS));
			double lawyerHealthPoints = positiveOrDefault(ability.healthPoints, CARTEL_DEFAULT_DEFENSE_HEALTH_POINTS);
			float reflectedDamageRatio = (float) positiveOrDefault(ability.reflectedDamageRatio, CARTEL_DEFAULT_DEFENSE_REFLECT_RATIO);
			Mob lawyer = spawnCartelLawyer(level, caster, innerMinDistance, followMaxDistance, lawyerHealthPoints);
			if (lawyer == null) {
				caster.sendSystemMessage(Component.literal("Failed to create lawyer."));
				return 0;
			}
			CARTEL_LAWYER_OWNER_BY_ENTITY.put(lawyer.getUUID(), caster.getUUID());

			CartelDefenseSession session = new CartelDefenseSession(
					level.dimension(),
					caster.getUUID(),
					lawyer.getUUID(),
					lawyer.getUUID(),
					nowTick + Math.max(1L, durationTicks),
					Math.min(innerMinDistance, Math.max(0.0D, followMaxDistance - 0.25D)),
					followMaxDistance,
					Math.max(1L, maxOutsideTicks),
					reflectedDamageRatio
			);
			session.nextWanderRetargetTick = nowTick;
			session.wanderTarget = caster.position();
			CARTEL_DEFENSE_SESSIONS.put(caster.getUUID(), session);

			if (cooldownTicks > 0) {
				CARTEL_DEFENSE_COOLDOWNS.put(caster.getUUID(), nowTick + cooldownTicks);
			}

			Lg2.LOGGER.info(
					"Player {} used mister cartel defense '{}' from race '{}' and spawned lawyer {}",
					caster.getGameProfile().name(),
					ability.abilityId,
					race.id,
					lawyer.getUUID()
			);
			return 1;
		} catch (Exception exception) {
			Lg2.LOGGER.error("Failed to activate mister cartel defense for {}", caster.getGameProfile().name(), exception);
			caster.sendSystemMessage(Component.literal("Defense activation failed. Check server log."));
			return 0;
		}
	}

	private static int useMrCartelUniqueAbility(ServerPlayer caster, PlayerRaceConfig race, RaceAbilityConfig ability) {
		try {
			ServerLevel level = caster.level();
			long nowTick = level.getGameTime();
			long cooldownTicks = asTicks(positiveOrDefault(ability.cooldownSeconds, CARTEL_DEFAULT_UNIQUE_COOLDOWN_SECONDS));
			long nextAllowedTick = CARTEL_UNIQUE_COOLDOWNS.getOrDefault(caster.getUUID(), 0L);
			if (cooldownTicks > 0 && nowTick < nextAllowedTick) {
				double remaining = (nextAllowedTick - nowTick) / 20.0D;
				caster.displayClientMessage(
						Component.literal(String.format(Locale.ROOT, "%.1fs", remaining))
								.withStyle(ChatFormatting.RED),
						true
				);
				return 0;
			}

			List<ServerPlayer> candidates = collectCartelDisguiseCandidates(caster);
			if (candidates.isEmpty()) {
				caster.displayClientMessage(
						Component.literal(localizeCartelDisguiseText(caster, "no_players_online"))
								.withStyle(style -> style.withColor(ChatFormatting.RED).withItalic(false)),
						true
				);
				return 0;
			}
			openMrCartelDisguiseMenu(caster, candidates, 0, ability);
			Lg2.LOGGER.info(
					"Player {} opened mister cartel unique ability '{}' menu from race '{}'",
					caster.getGameProfile().name(),
					ability.abilityId,
					race.id
			);
			return 1;
		} catch (Exception exception) {
			Lg2.LOGGER.error("Failed to open mister cartel unique ability menu for {}", caster.getGameProfile().name(), exception);
			return 0;
		}
	}

	private static List<ServerPlayer> collectCartelDisguiseCandidates(ServerPlayer caster) {
		if (caster == null || caster.level().getServer() == null) {
			return List.of();
		}

		List<ServerPlayer> players = new ArrayList<>();
		for (ServerPlayer player : caster.level().getServer().getPlayerList().getPlayers()) {
			if (player == null || player == caster) {
				continue;
			}
			players.add(player);
		}
		return players;
	}

	private static void openMrCartelDisguiseMenu(ServerPlayer caster, List<ServerPlayer> candidates, int selectedIndex, RaceAbilityConfig ability) {
		if (caster == null) {
			return;
		}

		int normalizedIndex = candidates == null || candidates.isEmpty() ? 0 : Math.floorMod(selectedIndex, candidates.size());
		ServerPlayer selectedPlayer = candidates == null || candidates.isEmpty() ? null : candidates.get(normalizedIndex);
		caster.openMenu(new SimpleMenuProvider(
				(syncId, inventory, menuPlayer) -> new CartelDisguiseMenu(syncId, inventory, caster, ability, normalizedIndex),
				buildCartelDisguiseMenuTitle(caster, selectedPlayer)
		));
	}

	private static int activateMrCartelDisguise(ServerPlayer caster, ServerPlayer target, RaceAbilityConfig ability) {
		if (caster == null || target == null || ability == null) {
			return 0;
		}
		if (caster.getUUID().equals(target.getUUID())) {
			return 0;
		}

		MinecraftServer server = caster.level().getServer();
		if (server == null) {
			return 0;
		}

		SkinValue targetSkin = captureCurrentSkinValue(target);
		if (targetSkin == null) {
			return 0;
		}

		CartelDisguiseSession existing = CARTEL_DISGUISE_SESSIONS.get(caster.getUUID());
		SkinValue originalSkin = existing != null ? existing.originalSkin : captureCurrentSkinValue(caster);
		if (originalSkin == null) {
			return 0;
		}

		applySkin(server, caster, targetSkin);
		long nowTick = caster.level().getGameTime();
		long durationTicks = asTicks(positiveOrDefault(ability.durationSeconds, CARTEL_DEFAULT_UNIQUE_DURATION_SECONDS));
		CARTEL_DISGUISE_SESSIONS.put(
				caster.getUUID(),
				new CartelDisguiseSession(
						originalSkin,
						targetSkin,
						target.getGameProfile().name(),
						nowTick + Math.max(1L, durationTicks)
				)
		);

		long cooldownTicks = asTicks(positiveOrDefault(ability.cooldownSeconds, CARTEL_DEFAULT_UNIQUE_COOLDOWN_SECONDS));
		if (cooldownTicks > 0L) {
			CARTEL_UNIQUE_COOLDOWNS.put(caster.getUUID(), nowTick + cooldownTicks);
		}

		caster.closeContainer();
		Lg2.LOGGER.info(
				"Player {} disguised as {} using mister cartel unique ability '{}'",
				caster.getGameProfile().name(),
				target.getGameProfile().name(),
				ability.abilityId
		);
		return 1;
	}

	private static void tickCartelDisguises(MinecraftServer server) {
		long nowTick = server.overworld().getGameTime();
		if (nowTick % 40L == 0L) {
			CARTEL_UNIQUE_COOLDOWNS.entrySet().removeIf(entry -> entry.getValue() <= nowTick);
		}
		if (CARTEL_DISGUISE_SESSIONS.isEmpty()) {
			return;
		}

		CARTEL_DISGUISE_SESSIONS.entrySet().removeIf(entry -> {
			CartelDisguiseSession session = entry.getValue();
			if (session == null) {
				return true;
			}

			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (player == null) {
				return true;
			}
			if (nowTick < session.endTick) {
				return false;
			}

			restoreCartelDisguise(server, player, session);
			return true;
		});
	}

	private static void restoreAllCartelDisguises(MinecraftServer server) {
		if (server == null || CARTEL_DISGUISE_SESSIONS.isEmpty()) {
			return;
		}

		for (Map.Entry<UUID, CartelDisguiseSession> entry : new ArrayList<>(CARTEL_DISGUISE_SESSIONS.entrySet())) {
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (player != null) {
				restoreCartelDisguise(server, player, entry.getValue());
			}
		}
		CARTEL_DISGUISE_SESSIONS.clear();
	}

	private static void clearCartelDisguise(ServerPlayer player) {
		if (player == null) {
			return;
		}
		CartelDisguiseSession session = CARTEL_DISGUISE_SESSIONS.remove(player.getUUID());
		if (session == null) {
			return;
		}
		MinecraftServer server = player.level().getServer();
		if (server != null) {
			restoreCartelDisguise(server, player, session);
		}
	}

	private static void restoreCartelDisguise(MinecraftServer server, ServerPlayer player, CartelDisguiseSession session) {
		if (server == null || player == null || session == null || session.originalSkin == null) {
			return;
		}
		applySkin(server, player, session.originalSkin);
	}

	private static void applySkin(MinecraftServer server, ServerPlayer player, SkinValue skinValue) {
		if (server == null || player == null || skinValue == null) {
			return;
		}
		SkinService.applySkin(server, List.of(player), skinValue, false);
	}

	private static SkinValue captureCurrentSkinValue(ServerPlayer player) {
		if (player == null) {
			return null;
		}

		SkinStorage skinStorage = SkinRestorer.getSkinStorage();
		if (skinStorage != null && skinStorage.hasSavedSkin(player.getUUID())) {
			SkinValue stored = skinStorage.getSkin(player.getUUID());
			if (stored != null) {
				return stored;
			}
		}

		Property current = PlayerUtils.getPlayerSkin(player.getGameProfile());
		if (current == null) {
			return null;
		}
		SkinVariant variant = resolveSkinVariant(current);
		return new SkinValue("lg2_cartel_disguise", player.getScoreboardName(), variant, current, current);
	}

	private static SkinVariant resolveSkinVariant(Property property) {
		Pair<String, SkinVariant> skinData = PlayerUtils.getSkinUrl(property);
		if (skinData != null && skinData.right() != null) {
			return skinData.right();
		}
		return SkinVariant.CLASSIC;
	}

	private static Component getCartelDisguiseDisplayName(ServerPlayer player) {
		if (player == null) {
			return null;
		}
		CartelDisguiseSession session = CARTEL_DISGUISE_SESSIONS.get(player.getUUID());
		return session == null || session.disguisedName == null || session.disguisedName.isBlank()
				? null
				: Component.literal(session.disguisedName);
	}

	public static Component getChatDisplayNameOverride(ServerPlayer player) {
		return getCartelDisguiseDisplayName(player);
	}

	private static ItemStack buildCartelDisguiseArrow(ServerPlayer viewer, boolean next) {
		if (viewer != null && PolymerResourcePackUtils.hasMainPack(viewer)) {
			return ItemStack.EMPTY;
		}

		ItemStack stack = new ItemStack(Items.ARROW);
		stack.set(
				DataComponents.CUSTOM_NAME,
				Component.literal(localizeCartelDisguiseText(viewer, next ? "next" : "previous"))
						.withStyle(style -> style.withItalic(false))
		);
		return stack;
	}

	private static ItemStack buildCartelDisguiseHead(ServerPlayer viewer, ServerPlayer target) {
		ItemStack stack = new ItemStack(Items.PLAYER_HEAD);
		if (target == null) {
			stack.set(DataComponents.CUSTOM_NAME, Component.literal(" "));
			return stack;
		}

		GameProfile sourceProfile = target.getGameProfile();
		PropertyMap properties = sourceProfile != null
				? new PropertyMap(ImmutableMultimap.copyOf(sourceProfile.properties()))
				: new PropertyMap(ImmutableMultimap.of());
		applySkinRestorerSkin(target, properties);
		GameProfile profile = new GameProfile(target.getUUID(), target.getGameProfile().name(), properties);
		stack.set(DataComponents.PROFILE, ResolvableProfile.createResolved(profile));
		stack.set(
			DataComponents.CUSTOM_NAME,
			Component.literal(localizeCartelDisguiseText(viewer, "accept"))
				.withStyle(style -> style.withColor(0x80FF80).withItalic(false).withBold(true))
		);
		return stack;
	}

	private static ItemStack buildCartelDisguiseEmptyState(ServerPlayer viewer) {
		ItemStack stack = new ItemStack(Items.BARRIER);
		stack.set(DataComponents.CUSTOM_NAME, Component.literal(localizeCartelDisguiseText(viewer, "empty")));
		return stack;
	}

	private static Component buildCartelDisguiseMenuTitle(ServerPlayer viewer, ServerPlayer target) {
		if (viewer != null && PolymerResourcePackUtils.hasMainPack(viewer)) {
			return buildCartelDisguisePackTitle(target);
		}

		if (target == null) {
			return Component.literal(" ");
		}
		String playerName = target.getGameProfile().name();
		return Component.literal(buildCartelDisguiseTitlePadding(playerName) + playerName);
	}

	private static Component buildCartelDisguisePackTitle(ServerPlayer target) {
		Component title = Component.literal(buildHorizontalAdvance(CARTEL_PASSPORT_OVERLAY_X_OFFSET) + buildOverlayGlyph(CARTEL_PASSPORT_OVERLAY_GLYPH, 176))
			.withStyle(style -> style.withColor(0xFFFFFF).withItalic(false).withFont(CARTEL_PASSPORT_OVERLAY_FONT));
		if (target == null) {
			return title;
		}

		String playerName = target.getGameProfile().name();
		int startX = Math.max(CARTEL_PASSPORT_NAME_MIN_X, CARTEL_PASSPORT_NAME_CENTER_X - (playerName.length() * CARTEL_PASSPORT_NAME_CHAR_ADVANCE) / 2);
		return title.copy()
			.append(Component.literal(TITLE_OVERLAY_RESET + buildHorizontalAdvance(startX))
				.withStyle(style -> style.withColor(0xFFFFFF).withItalic(false)))
			.append(Component.literal(playerName)
						.withStyle(style -> style.withColor(0x2E2016).withItalic(false).withFont(CARTEL_PASSPORT_NAME_FONT)));
	}

	private static int getCartelDisguisePreviousSlot(ServerPlayer viewer) {
		return viewer != null && PolymerResourcePackUtils.hasMainPack(viewer)
				? CARTEL_DISGUISE_PACK_PREVIOUS_SLOT
				: CARTEL_DISGUISE_PREVIOUS_SLOT;
	}

	private static int getCartelDisguiseHeadSlot(ServerPlayer viewer) {
		return viewer != null && PolymerResourcePackUtils.hasMainPack(viewer)
				? CARTEL_DISGUISE_PACK_HEAD_SLOT
				: CARTEL_DISGUISE_HEAD_SLOT;
	}

	private static int getCartelDisguiseNextSlot(ServerPlayer viewer) {
		return viewer != null && PolymerResourcePackUtils.hasMainPack(viewer)
				? CARTEL_DISGUISE_PACK_NEXT_SLOT
				: CARTEL_DISGUISE_NEXT_SLOT;
	}

	private static void hideCartelDisguiseInventoryVisuals(ServerPlayer player, AbstractContainerMenu menu) {
		sendCartelDisguiseInventoryVisuals(player, menu, true);
		syncCartelDisguiseHeldEquipmentVisuals(player, true);
	}

	private static void restoreCartelDisguiseInventoryVisuals(ServerPlayer player, AbstractContainerMenu menu) {
		if (player != null) {
			AbstractContainerMenu targetMenu = player.containerMenu;
			if (targetMenu == null || targetMenu == menu) {
				targetMenu = player.inventoryMenu;
			}
			if (targetMenu != null) {
				sendCartelDisguiseInventoryVisuals(player, targetMenu, false);
			}
		}
		syncCartelDisguiseHeldEquipmentVisuals(player, false);
	}

	private static void sendCartelDisguiseInventoryVisuals(ServerPlayer player, AbstractContainerMenu menu, boolean hide) {
		if (player == null || menu == null) {
			return;
		}

		Inventory inventory = player.getInventory();
		PacketContext.NotNullWithPlayer context = PacketContext.create(player);
		int stateId = menu.incrementStateId();
		for (int menuSlot = 0; menuSlot < menu.slots.size(); menuSlot++) {
			Slot slot = menu.getSlot(menuSlot);
			if (slot.container != inventory) {
				continue;
			}

			int inventorySlot = slot.getContainerSlot();
			ItemStack visual = hide ? ItemStack.EMPTY : inventory.getItem(inventorySlot).copy();
			player.connection.send(new ClientboundContainerSetSlotPacket(
				menu.containerId,
				stateId,
				menuSlot,
				toCartelDisguiseClientVisualStack(visual, context)
			));
		}
	}

	private static void syncCartelDisguiseHeldEquipmentVisuals(ServerPlayer player, boolean hide) {
		if (player == null) {
			return;
		}

		PacketContext.NotNullWithPlayer context = PacketContext.create(player);
		ItemStack mainHand = hide ? ItemStack.EMPTY : toCartelDisguiseClientVisualStack(player.getMainHandItem().copy(), context);
		ItemStack offHand = hide ? ItemStack.EMPTY : toCartelDisguiseClientVisualStack(player.getOffhandItem().copy(), context);
		player.connection.send(new ClientboundSetEquipmentPacket(
			player.getId(),
			List.of(
				com.mojang.datafixers.util.Pair.of(EquipmentSlot.MAINHAND, mainHand),
				com.mojang.datafixers.util.Pair.of(EquipmentSlot.OFFHAND, offHand)
			)
		));
	}

	private static ItemStack toCartelDisguiseClientVisualStack(ItemStack stack, PacketContext.NotNullWithPlayer context) {
		if (stack == null || stack.isEmpty()) {
			return ItemStack.EMPTY;
		}

		ItemStack clientStack = PolymerItemUtils.getClientItemStack(stack, context);
		return clientStack.isEmpty() ? stack.copy() : clientStack.copy();
	}

	private static String buildCartelDisguiseTitlePadding(String playerName) {
		int nameLength = playerName == null ? 0 : playerName.length();
		int spaces = Math.max(1, 20 - (int) Math.ceil(nameLength * 0.75D));
		return " ".repeat(spaces);
	}

	private static String localizeCartelDisguiseText(ServerPlayer player, String key) {
		String locale = normalizeCartelDisguiseLocale(player);
		if (locale.startsWith("rpr")) {
			return switch (key) {
				case "passport" -> "Паспортъ";
				case "accept" -> "Приняти";
				case "previous" -> "Предыдущiй";
				case "next" -> "Слѣдующiй";
				case "empty" -> "Нѣтъ игроковъ";
				case "no_players_online" -> "На серверѣ никого нѣтъ";
				default -> "";
			};
		}
		if (locale.startsWith("uk")) {
			return switch (key) {
				case "passport" -> "Паспорт";
				case "accept" -> "Прийняти";
				case "previous" -> "Попередній";
				case "next" -> "Наступний";
				case "empty" -> "Немає гравців";
				case "no_players_online" -> "На сервері нікого немає";
				default -> "";
			};
		}
		if (locale.startsWith("ja")) {
			return switch (key) {
				case "passport" -> "パスポート";
				case "accept" -> "承認";
				case "previous" -> "前へ";
				case "next" -> "次へ";
				case "empty" -> "プレイヤーがいません";
				case "no_players_online" -> "サーバーに誰もいません";
				default -> "";
			};
		}
		if (locale.startsWith("ru")) {
			return switch (key) {
				case "passport" -> "Пасспорт";
				case "accept" -> "Принять";
				case "previous" -> "Предыдущий";
				case "next" -> "Следующий";
				case "empty" -> "Нет игроков";
				case "no_players_online" -> "На сервере никого нет";
				default -> "";
			};
		}
		return switch (key) {
			case "passport" -> "Passport";
			case "accept" -> "Accept";
			case "previous" -> "Previous";
			case "next" -> "Next";
			case "empty" -> "No players";
			case "no_players_online" -> "There is nobody on the server";
			default -> "";
		};
	}

	private static String normalizeCartelDisguiseLocale(ServerPlayer player) {
		if (player == null || player.clientInformation() == null || player.clientInformation().language() == null) {
			return "ru_ru";
		}
		return player.clientInformation().language().toLowerCase(Locale.ROOT);
	}

	private static String buildOverlayGlyph(String glyph, int glyphAdvance) {
		int compensation = TITLE_OVERLAY_TARGET_ADVANCE - TITLE_OVERLAY_SHIFT_ADVANCE - glyphAdvance;
		return TITLE_OVERLAY_RESET + TITLE_OVERLAY_SHIFT + glyph + buildHorizontalAdvance(compensation);
	}

	private static String buildHorizontalAdvance(int pixels) {
		if (pixels == 0) {
			return "";
		}

		int remaining = pixels;
		StringBuilder result = new StringBuilder();
		int[] values = remaining > 0
				? new int[]{64, 32, 16, 8, 4, 2, 1}
				: new int[]{-64, -32, -16, -8, -4, -2, -1};
		String[] glyphs = remaining > 0
				? new String[]{"\ue94d", "\ue94c", "\ue94b", "\ue94a", "\ue949", "\ue948", "\ue947"}
				: new String[]{"\ue940", "\ue941", "\ue942", "\ue943", "\ue944", "\ue945", "\ue946"};

		for (int index = 0; index < values.length; index++) {
			int step = values[index];
			while ((remaining > 0 && remaining >= step) || (remaining < 0 && remaining <= step)) {
				result.append(glyphs[index]);
				remaining -= step;
			}
		}
		return result.toString();
	}

	private static void applySkinRestorerSkin(ServerPlayer sourcePlayer, PropertyMap properties) {
		if (sourcePlayer == null || properties == null) {
			return;
		}

		try {
			SkinStorage skinStorage = SkinRestorer.getSkinStorage();
			if (skinStorage == null) {
				return;
			}

			SkinValue skinValue = skinStorage.getSkin(sourcePlayer.getUUID());
			if (skinValue == null || skinValue.value() == null) {
				return;
			}

			properties.removeAll("textures");
			properties.put("textures", skinValue.value());
		} catch (Exception exception) {
			Lg2.LOGGER.debug("Failed to resolve disguise head skin for {}", sourcePlayer.getScoreboardName(), exception);
		}
	}

	private static void tickCartelDefense(MinecraftServer server) {
		long nowTick = server.overworld().getGameTime();
		if (nowTick % 40L == 0L) {
			CARTEL_DEFENSE_COOLDOWNS.entrySet().removeIf(entry -> entry.getValue() <= nowTick);
		}
		if (CARTEL_DEFENSE_SESSIONS.isEmpty()) {
			return;
		}

		CARTEL_DEFENSE_SESSIONS.entrySet().removeIf(entry -> {
			CartelDefenseSession session = entry.getValue();
			ServerLevel level = server.getLevel(session.dimension);
			if (level == null || nowTick >= session.endTick) {
				despawnCartelLawyer(server, session);
				return true;
			}

			Entity protectedEntity = level.getEntity(session.protectedPlayerId);
			if (!(protectedEntity instanceof ServerPlayer cartel) || !cartel.isAlive()) {
				despawnCartelLawyer(server, session);
				return true;
			}

			Entity lawyerEntity = level.getEntity(session.lawyerEntityId);
			if (!(lawyerEntity instanceof Mob lawyer) || !lawyer.isAlive()) {
				despawnCartelLawyer(server, session);
				return true;
			}

			tickCartelLawyerMovement(level, cartel, lawyer, session, nowTick);
			return false;
		});
	}

	public static void handleCartelDefenseDamage(ServerLevel level, LivingEntity victim, net.minecraft.world.damagesource.DamageSource damageSource, float damage, boolean applied) {
		if (!applied || level == null || !(victim instanceof ServerPlayer protectedPlayer)) {
			return;
		}
		if (Boolean.TRUE.equals(CARTEL_DEFENSE_REFLECTION_ACTIVE.get())) {
			return;
		}

		CartelDefenseSession session = CARTEL_DEFENSE_SESSIONS.get(protectedPlayer.getUUID());
		if (session == null) {
			return;
		}

		LivingEntity attacker = resolveDamageAttacker(damageSource);
		if (attacker == null || attacker == victim || !attacker.isAlive()) {
			return;
		}

		float reflectedDamage = damage * Math.max(0.0F, session.reflectedDamageRatio);
		if (reflectedDamage <= 0.0F) {
			return;
		}

		CARTEL_DEFENSE_REFLECTION_ACTIVE.set(Boolean.TRUE);
		try {
			attacker.hurtServer(level, level.damageSources().genericKill(), reflectedDamage);
		} finally {
			CARTEL_DEFENSE_REFLECTION_ACTIVE.set(Boolean.FALSE);
		}
	}

	public static boolean shouldCancelCartelOwnerDamage(LivingEntity victim, net.minecraft.world.damagesource.DamageSource damageSource) {
		if (victim == null || damageSource == null) {
			return false;
		}

		Entity attackerEntity = damageSource.getEntity();
		if (!(attackerEntity instanceof ServerPlayer attacker)) {
			return false;
		}

		UUID ownerId = null;
		if (victim.getTags().contains(CARTEL_SUMMON_TAG)) {
			ownerId = CARTEL_SUMMON_OWNER_BY_ENTITY.get(victim.getUUID());
		} else if (victim.getTags().contains(CARTEL_LAWYER_TAG)) {
			ownerId = CARTEL_LAWYER_OWNER_BY_ENTITY.get(victim.getUUID());
		}
		return ownerId != null && ownerId.equals(attacker.getUUID());
	}

	private static LivingEntity resolveDamageAttacker(net.minecraft.world.damagesource.DamageSource damageSource) {
		if (damageSource == null) {
			return null;
		}
		if (damageSource.getEntity() instanceof LivingEntity livingEntity) {
			return livingEntity;
		}
		if (damageSource.getDirectEntity() instanceof LivingEntity livingEntity) {
			return livingEntity;
		}
		return null;
	}

	private static Mob spawnCartelLawyer(ServerLevel level, ServerPlayer cartel, double innerMinDistance, double followMaxDistance, double healthPoints) {
		Mob lawyer = createLawyerBaseMob(level);
		if (lawyer == null) {
			return null;
		}

		Vec3 spawnPos = findCartelLawyerSpawnPos(level, cartel, innerMinDistance, followMaxDistance);
		lawyer.setPos(spawnPos.x, spawnPos.y, spawnPos.z);
		lawyer.setYRot(cartel.getYRot());
		lawyer.setSilent(true);
		lawyer.setCanPickUpLoot(false);
		lawyer.addTag(CARTEL_LAWYER_TAG);
		lawyer.setCustomName(Component.literal(CARTEL_LAWYER_MARKER_NAME));
		lawyer.setCustomNameVisible(false);
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			lawyer.setDropChance(slot, 0.0F);
		}
		((MobXpRewardAccessor) (Object) lawyer).lg2$setXpReward(0);
		if (lawyer.getAttribute(Attributes.MOVEMENT_SPEED) != null) {
			lawyer.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(CARTEL_LAWYER_BASE_MOVE_SPEED);
		}
		if (lawyer.getAttribute(Attributes.KNOCKBACK_RESISTANCE) != null) {
			lawyer.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0D);
		}
		if (healthPoints <= 0.0D) {
			lawyer.setInvulnerable(true);
		} else {
			lawyer.setInvulnerable(false);
			if (lawyer.getAttribute(Attributes.MAX_HEALTH) != null) {
				lawyer.getAttribute(Attributes.MAX_HEALTH).setBaseValue(healthPoints);
			}
			lawyer.setHealth((float) healthPoints);
		}
		lawyer.refreshDimensions();

		try {
			if (lawyer instanceof CartelLawyerEntity cartelLawyer) {
				cartelLawyer.attachPolymerAppearance(buildLawyerProfile(lawyer.getUUID()));
			} else {
				PolymerEntityUtils.setPolymerEntity(lawyer, new CartelLawyerOverlay(buildLawyerProfile(lawyer.getUUID())));
			}
		} catch (Exception exception) {
			Lg2.LOGGER.error("Failed to apply lawyer player overlay", exception);
		}
		if (!level.addFreshEntity(lawyer)) {
			return null;
		}
		emitSmoke(level, lawyer.position());
		return lawyer;
	}

	private static Mob createLawyerBaseMob(ServerLevel level) {
		return new CartelLawyerEntity(level);
	}

	private static Vec3 findCartelLawyerSpawnPos(ServerLevel level, ServerPlayer cartel, double innerMinDistance, double followMaxDistance) {
		double preferredDistance = Math.max(innerMinDistance + 0.35D, Math.min(followMaxDistance - 0.35D, innerMinDistance + 0.85D));
		for (int attempt = 0; attempt < 12; attempt++) {
			double angle = (Math.PI * 2.0D * attempt) / 12.0D;
			Vec3 offset = new Vec3(Math.cos(angle) * preferredDistance, 0.0D, Math.sin(angle) * preferredDistance);
			Vec3 candidate = resolveLawyerSpawnPosition(level, cartel.position().add(offset));
			if (candidate != null) {
				return candidate;
			}
		}
		Vec3 fallback = resolveLawyerSpawnPosition(level, cartel.position().add(preferredDistance, 0.0D, 0.0D));
		return fallback != null ? fallback : cartel.position().add(preferredDistance, 0.0D, 0.0D);
	}

	private static void tickCartelLawyerMovement(ServerLevel level, ServerPlayer cartel, Mob lawyer, CartelDefenseSession session, long nowTick) {
		Vec3 cartelPos = cartel.position();
		Vec3 lawyerPos = lawyer.position();
		double distanceSqr = horizontalDistanceToSqr(lawyerPos, cartelPos);
		double minDistanceSqr = session.innerMinDistanceBlocks * session.innerMinDistanceBlocks;
		double maxDistanceSqr = session.followMaxDistanceBlocks * session.followMaxDistanceBlocks;
		if (distanceSqr > maxDistanceSqr) {
			if (session.outsideSinceTick == null) {
				session.outsideSinceTick = nowTick;
			}
			Vec3 returnTarget = projectLawyerToRing(
					level,
					cartelPos,
					lawyerPos,
					Math.max(session.innerMinDistanceBlocks + 0.35D, session.followMaxDistanceBlocks - 0.35D)
			);
			moveLawyerTowardTarget(lawyer, returnTarget, CARTEL_LAWYER_RETURN_SPEED);
			if (nowTick - session.outsideSinceTick >= session.maxOutsideTicks) {
				Vec3 returnPos = findCartelLawyerSpawnPos(level, cartel, session.innerMinDistanceBlocks, session.followMaxDistanceBlocks);
				lawyer.teleportTo(returnPos.x, returnPos.y, returnPos.z);
				lawyer.getNavigation().stop();
				session.outsideSinceTick = null;
				session.nextWanderRetargetTick = nowTick + 20L;
				session.wanderTarget = returnPos;
			}
			return;
		}

		session.outsideSinceTick = null;
		if (nowTick >= session.nextMovementLogicTick) {
			session.nextMovementLogicTick = nowTick + CARTEL_LAWYER_MOVEMENT_LOGIC_INTERVAL_TICKS;
			boolean pathCutsInnerRing = distanceSqr >= minDistanceSqr
					&& session.wanderTarget != null
					&& segmentIntersectsInnerRadius(cartelPos, lawyerPos, session.wanderTarget, session.innerMinDistanceBlocks + 0.2D);
			if (
					session.wanderTarget == null
							|| nowTick >= session.nextWanderRetargetTick
							|| lawyer.position().distanceToSqr(session.wanderTarget) <= 1.0D
							|| !isWithinLawyerBounds(cartel.position(), session, session.wanderTarget)
							|| pathCutsInnerRing
			) {
				session.wanderTarget = sampleLawyerWanderTarget(level, cartel, lawyer.position(), session.innerMinDistanceBlocks, session.followMaxDistanceBlocks);
				session.nextWanderRetargetTick = nowTick + 20L;
			}
		}

		lawyer.setTarget(null);
		Vec3 target = session.wanderTarget == null
				? findCartelLawyerSpawnPos(level, cartel, session.innerMinDistanceBlocks, session.followMaxDistanceBlocks)
				: session.wanderTarget;
		moveLawyerTowardTarget(lawyer, target, CARTEL_LAWYER_WALK_SPEED);
	}

	private static void moveLawyerTowardTarget(Mob lawyer, Vec3 target, double speed) {
		if (lawyer == null || target == null) {
			return;
		}

		lawyer.getNavigation().stop();
		Vec3 position = lawyer.position();
		Vec3 horizontal = new Vec3(target.x - position.x, 0.0D, target.z - position.z);
		double distance = horizontal.length();
		if (distance <= 0.08D) {
			Vec3 delta = lawyer.getDeltaMovement();
			lawyer.setDeltaMovement(delta.x * 0.5D, delta.y, delta.z * 0.5D);
			return;
		}

		Vec3 desiredMovement = horizontal.scale(Math.min(speed, distance) / distance);
		Vec3 delta = lawyer.getDeltaMovement();
		Vec3 currentHorizontal = new Vec3(delta.x, 0.0D, delta.z);
		Vec3 movement = currentHorizontal.scale(1.0D - CARTEL_LAWYER_STEERING_SMOOTHING).add(desiredMovement.scale(CARTEL_LAWYER_STEERING_SMOOTHING));
		if (distance <= 0.4D) {
			movement = movement.scale(0.65D);
		}
		lawyer.setDeltaMovement(movement.x, delta.y, movement.z);
		if (lawyer.horizontalCollision && lawyer.onGround()) {
			lawyer.jumpFromGround();
		}
		float yaw = (float) (Math.toDegrees(Math.atan2(movement.z, movement.x)) - 90.0D);
		lawyer.setYRot(yaw);
		lawyer.setYBodyRot(yaw);
		lawyer.setYHeadRot(yaw);
		lawyer.hurtMarked = true;
	}

	private static Vec3 sampleLawyerWanderTarget(ServerLevel level, ServerPlayer cartel, Vec3 lawyerPosition, double innerRadius, double outerRadius) {
		double minDistance = Math.max(0.35D, innerRadius + 0.35D);
		double maxDistance = Math.max(minDistance, outerRadius - 0.35D);
		for (int attempt = 0; attempt < 10; attempt++) {
			double angle = cartel.getRandom().nextDouble() * Math.PI * 2.0D;
			double distance = minDistance + cartel.getRandom().nextDouble() * Math.max(0.001D, maxDistance - minDistance);
			Vec3 desired = new Vec3(
					cartel.getX() + Math.cos(angle) * distance,
					cartel.getY(),
					cartel.getZ() + Math.sin(angle) * distance
			);
			Vec3 resolved = resolveLawyerSpawnPosition(level, desired);
			if (resolved != null && (lawyerPosition == null || !segmentIntersectsInnerRadius(cartel.position(), lawyerPosition, resolved, innerRadius + 0.2D))) {
				return resolved;
			}
		}
		return findCartelLawyerSpawnPos(level, cartel, innerRadius, outerRadius);
	}

	private static boolean isWithinLawyerBounds(Vec3 center, CartelDefenseSession session, Vec3 position) {
		double distanceSqr = horizontalDistanceToSqr(center, position);
		double minDistanceSqr = session.innerMinDistanceBlocks * session.innerMinDistanceBlocks;
		double maxDistanceSqr = session.followMaxDistanceBlocks * session.followMaxDistanceBlocks;
		return distanceSqr >= minDistanceSqr && distanceSqr <= maxDistanceSqr;
	}

	private static double horizontalDistanceToSqr(Vec3 first, Vec3 second) {
		double dx = first.x - second.x;
		double dz = first.z - second.z;
		return dx * dx + dz * dz;
	}

	private static boolean segmentIntersectsInnerRadius(Vec3 center, Vec3 start, Vec3 end, double radius) {
		double sx = start.x - center.x;
		double sz = start.z - center.z;
		double ex = end.x - center.x;
		double ez = end.z - center.z;
		double dx = ex - sx;
		double dz = ez - sz;
		double segmentLengthSqr = dx * dx + dz * dz;
		if (segmentLengthSqr <= 1.0E-6D) {
			return sx * sx + sz * sz < radius * radius;
		}
		double t = -(sx * dx + sz * dz) / segmentLengthSqr;
		t = Math.max(0.0D, Math.min(1.0D, t));
		double closestX = sx + dx * t;
		double closestZ = sz + dz * t;
		return closestX * closestX + closestZ * closestZ < radius * radius;
	}

	private static Vec3 projectLawyerToRing(ServerLevel level, Vec3 center, Vec3 currentPosition, double targetDistance) {
		Vec3 horizontal = new Vec3(currentPosition.x - center.x, 0.0D, currentPosition.z - center.z);
		if (horizontal.lengthSqr() < 1.0E-4D) {
			horizontal = new Vec3(1.0D, 0.0D, 0.0D);
		}
		Vec3 desired = center.add(horizontal.normalize().scale(Math.max(0.35D, targetDistance)));
		Vec3 resolved = resolveLawyerSpawnPosition(level, desired);
		return resolved != null ? resolved : desired;
	}

	private static Vec3 resolveLawyerSpawnPosition(ServerLevel level, Vec3 desiredCenter) {
		BlockPos origin = BlockPos.containing(desiredCenter.x, desiredCenter.y, desiredCenter.z);
		for (int dy = -1; dy <= 2; dy++) {
			BlockPos candidate = origin.offset(0, dy, 0);
			if (!level.getBlockState(candidate).canBeReplaced() || !level.getBlockState(candidate.above()).canBeReplaced()) {
				continue;
			}
			AABB box = CARTEL_LAWYER_DIMENSIONS.makeBoundingBox(candidate.getX() + 0.5D, candidate.getY(), candidate.getZ() + 0.5D);
			if (level.noCollision(box)) {
				return new Vec3(candidate.getX() + 0.5D, candidate.getY(), candidate.getZ() + 0.5D);
			}
		}
		return null;
	}

	private static void despawnCartelLawyer(MinecraftServer server, CartelDefenseSession session) {
		if (server == null || session == null) {
			return;
		}
		ServerLevel level = server.getLevel(session.dimension);
		if (level != null) {
			Entity entity = level.getEntity(session.lawyerEntityId);
			if (entity != null) {
				CARTEL_LAWYER_OWNER_BY_ENTITY.remove(entity.getUUID());
				emitSmoke(level, entity.position());
				entity.discard();
			}
			sendCartelLawyerAppearanceRemoval(level, session.lawyerProfileId);
		}
	}

	private static GameProfile buildLawyerProfile(UUID profileId) {
		var mutableProperties = ArrayListMultimap.<String, Property>create();
		mutableProperties.put("textures", resolveCartelLawyerSkinProperty());
		return new GameProfile(profileId, buildCartelLawyerProfileName(profileId), new PropertyMap(mutableProperties));
	}

	private static Property resolveCartelLawyerSkinProperty() {
		Property cached = CARTEL_LAWYER_SKIN_PROPERTY;
		if (cached != null) {
			return cached;
		}
		CompletableFuture<Property> future = prewarmCartelLawyerSkinAsync();
		if (future.isDone()) {
			Property resolved = future.getNow(CARTEL_LAWYER_FALLBACK_SKIN_PROPERTY);
			CARTEL_LAWYER_SKIN_PROPERTY = resolved;
			return resolved;
		}
		return CARTEL_LAWYER_FALLBACK_SKIN_PROPERTY;
	}

	private static synchronized CompletableFuture<Property> prewarmCartelLawyerSkinAsync() {
		if (CARTEL_LAWYER_SKIN_PROPERTY != null) {
			return CompletableFuture.completedFuture(CARTEL_LAWYER_SKIN_PROPERTY);
		}
		if (CARTEL_LAWYER_SKIN_FUTURE != null) {
			return CARTEL_LAWYER_SKIN_FUTURE;
		}

		CARTEL_LAWYER_SKIN_FUTURE = CompletableFuture.supplyAsync(ServerRaceSystem::loadCartelLawyerSkinProperty)
				.exceptionally(exception -> {
					Lg2.LOGGER.warn("Failed to prewarm cartel lawyer skin, using fallback value", exception);
					return CARTEL_LAWYER_FALLBACK_SKIN_PROPERTY;
				})
				.thenApply(property -> {
					CARTEL_LAWYER_SKIN_PROPERTY = property != null ? property : CARTEL_LAWYER_FALLBACK_SKIN_PROPERTY;
					return CARTEL_LAWYER_SKIN_PROPERTY;
				});
		return CARTEL_LAWYER_SKIN_FUTURE;
	}

	private static Property loadCartelLawyerSkinProperty() {
		try {
			Property signed = MineskinService.INSTANCE.signSkin(CARTEL_LAWYER_SKIN_URI, SkinVariant.CLASSIC).orElse(null);
			if (signed != null) {
				return signed;
			}
		} catch (Exception exception) {
			Lg2.LOGGER.warn("Failed to sign cartel lawyer skin from fixed texture URL, using fallback value", exception);
		}

		return CARTEL_LAWYER_FALLBACK_SKIN_PROPERTY;
	}

	private static String buildCartelLawyerProfileName(UUID profileId) {
		String compact = profileId == null ? UUID.randomUUID().toString().replace("-", "") : profileId.toString().replace("-", "");
		return "law" + compact.substring(0, 13);
	}

	private static String buildCartelLawyerTeamName(UUID profileId) {
		String compact = profileId == null ? UUID.randomUUID().toString().replace("-", "") : profileId.toString().replace("-", "");
		return "lg2law_" + compact.substring(0, 10);
	}

	private static void sendCartelLawyerAppearanceRemoval(ServerLevel level, UUID profileId) {
		if (level == null || profileId == null) {
			return;
		}
		PlayerTeam team = createCartelLawyerHiddenTeam(profileId, buildCartelLawyerProfileName(profileId));
		ClientboundSetPlayerTeamPacket teamPacket = ClientboundSetPlayerTeamPacket.createRemovePacket(team);
		ClientboundPlayerInfoRemovePacket packet = new ClientboundPlayerInfoRemovePacket(List.of(profileId));
		for (ServerPlayer player : level.players()) {
			player.connection.send(teamPacket);
			player.connection.send(packet);
		}
	}

	private static PlayerTeam createCartelLawyerHiddenTeam(UUID profileId, String profileName) {
		PlayerTeam team = new PlayerTeam(new Scoreboard(), buildCartelLawyerTeamName(profileId));
		team.setDisplayName(Component.empty());
		team.setPlayerPrefix(Component.empty());
		team.setPlayerSuffix(Component.empty());
		team.setNameTagVisibility(Team.Visibility.NEVER);
		team.setDeathMessageVisibility(Team.Visibility.NEVER);
		team.setCollisionRule(Team.CollisionRule.NEVER);
		team.getPlayers().add(profileName);
		return team;
	}

	private static int useMrCartelAttack(ServerPlayer caster, PlayerRaceConfig race, RaceAbilityConfig ability) {
		ServerLevel level = caster.level();
		long nowTick = level.getGameTime();
		long cooldownTicks = asTicks(positiveOrDefault(ability.cooldownSeconds, CARTEL_DEFAULT_COOLDOWN_SECONDS));
		long nextAllowedTick = CARTEL_ATTACK_COOLDOWNS.getOrDefault(caster.getUUID(), 0L);
		if (cooldownTicks > 0 && nowTick < nextAllowedTick) {
			double remaining = (nextAllowedTick - nowTick) / 20.0D;
			caster.displayClientMessage(
					Component.literal(String.format(Locale.ROOT, "%.1fs", remaining))
							.withStyle(ChatFormatting.RED),
					true
			);
			return 0;
		}

		double activationRange = positiveOrDefault(ability.activationRangeBlocks, CARTEL_TARGET_RANGE);
		LivingEntity target = findLookTarget(caster, activationRange);
		if (target == null) {
			return 0;
		}

		List<EntityType<? extends Raider>> raiderTypes = new ArrayList<>();
		raiderTypes.add(EntityType.PILLAGER);
		raiderTypes.add(EntityType.PILLAGER);
		raiderTypes.add(EntityType.VINDICATOR);
		raiderTypes.add(EntityType.VINDICATOR);
		for (int i = raiderTypes.size() - 1; i > 0; i--) {
			int swapIndex = caster.getRandom().nextInt(i + 1);
			Collections.swap(raiderTypes, i, swapIndex);
		}

		List<Direction> directions = List.of(Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST);
		long lifetimeTicks = asTicks(positiveOrDefault(ability.summonLifetimeSeconds, CARTEL_DEFAULT_LIFETIME_SECONDS));
		long afterKillTicks = asTicks(positiveOrDefault(ability.summonAfterKillSeconds, CARTEL_DEFAULT_AFTER_KILL_SECONDS));
		CartelSummonSession session = new CartelSummonSession(level.dimension(), caster.getUUID(), target.getUUID(), nowTick + Math.max(1L, lifetimeTicks), Math.max(1L, afterKillTicks));

		BlockPos center = target.blockPosition();
		for (int i = 0; i < directions.size(); i++) {
			Direction direction = directions.get(i);
			BlockPos anchor = center.relative(direction, CARTEL_SPAWN_OFFSET_BLOCKS);
			Raider raider = spawnCartelRaider(level, raiderTypes.get(i), anchor, target, caster.getUUID());
			if (raider != null) {
				session.raiderIds.add(raider.getUUID());
			}
		}

		if (session.raiderIds.isEmpty()) {
			caster.sendSystemMessage(Component.literal("Р В Р’В Р РЋРЎС™Р В Р’В Р вЂ™Р’ВµР В Р Р‹Р Р†Р вЂљРЎв„ў Р В Р’В Р РЋР’ВР В Р’В Р вЂ™Р’ВµР В Р Р‹Р В РЎвЂњР В Р Р‹Р Р†Р вЂљРЎв„ўР В Р’В Р вЂ™Р’В° Р В Р’В Р СћРІР‚ВР В Р’В Р вЂ™Р’В»Р В Р Р‹Р В Р РЏ Р В Р Р‹Р В РЎвЂњР В Р’В Р РЋРІР‚вЂќР В Р’В Р вЂ™Р’В°Р В Р’В Р В РІР‚В Р В Р’В Р В РІР‚В¦Р В Р’В Р вЂ™Р’В° Р В Р Р‹Р В РІР‚С™Р В Р’В Р вЂ™Р’В°Р В Р’В Р вЂ™Р’В·Р В Р’В Р вЂ™Р’В±Р В Р’В Р РЋРІР‚СћР В Р’В Р Р†РІР‚С›РІР‚вЂњР В Р’В Р В РІР‚В¦Р В Р’В Р РЋРІР‚ВР В Р’В Р РЋРІР‚СњР В Р’В Р РЋРІР‚СћР В Р’В Р В РІР‚В  Р В Р’В Р В РІР‚В Р В Р’В Р РЋРІР‚СћР В Р’В Р РЋРІР‚СњР В Р Р‹Р В РІР‚С™Р В Р Р‹Р РЋРІР‚СљР В Р’В Р РЋРІР‚вЂњ Р В Р Р‹Р Р†Р вЂљР’В Р В Р’В Р вЂ™Р’ВµР В Р’В Р вЂ™Р’В»Р В Р’В Р РЋРІР‚В."));
			return 0;
		}

		CARTEL_SUMMON_SESSIONS.put(UUID.randomUUID(), session);
		if (cooldownTicks > 0) {
			CARTEL_ATTACK_COOLDOWNS.put(caster.getUUID(), nowTick + cooldownTicks);
		}

		Lg2.LOGGER.info(
				"Player {} used mister cartel attack '{}' from race '{}' and summoned {} raiders around target {}",
				caster.getGameProfile().name(),
				ability.abilityId,
				race.id,
				session.raiderIds.size(),
				target.getUUID()
		);
		return 1;
	}

	private static void tickCartelSummons(MinecraftServer server) {
		long nowTick = server.overworld().getGameTime();
		if (nowTick % 40L == 0L) {
			CARTEL_ATTACK_COOLDOWNS.entrySet().removeIf(entry -> entry.getValue() <= nowTick);
		}
		if (CARTEL_SUMMON_SESSIONS.isEmpty()) {
			return;
		}

		CARTEL_SUMMON_SESSIONS.entrySet().removeIf(entry -> {
			CartelSummonSession session = entry.getValue();
			ServerLevel level = server.getLevel(session.dimension);
			if (level == null) {
				return true;
			}

			LivingEntity target = null;
			Entity targetEntity = level.getEntity(session.targetPlayerId);
			if (targetEntity instanceof LivingEntity livingEntity && livingEntity.isAlive()) {
				target = livingEntity;
			} else if (session.afterKillExpireTick == null) {
				session.afterKillExpireTick = nowTick + session.afterKillTicks;
			}
			final LivingEntity chaseTarget = target;

			boolean timedOut = nowTick >= session.normalExpireTick
					|| (session.afterKillExpireTick != null && nowTick >= session.afterKillExpireTick);

			session.raiderIds.removeIf(raiderId -> {
				Entity entity = level.getEntity(raiderId);
				if (!(entity instanceof Raider raider) || !raider.isAlive()) {
					CARTEL_SUMMON_OWNER_BY_ENTITY.remove(raiderId);
					return true;
				}

				if (timedOut) {
					despawnRaiderWithSmoke(level, raider);
					return true;
				}

				if (chaseTarget != null) {
					boolean targetChanged = raider.getTarget() != chaseTarget;
					if (targetChanged) {
						raider.setTarget(chaseTarget);
					}
					if (targetChanged || raider.getNavigation().isDone() || (nowTick + raider.getId()) % CARTEL_RAIDER_NAV_INTERVAL_TICKS == 0L) {
						raider.getNavigation().moveTo(chaseTarget, CARTEL_CHASE_SPEED);
					}
				} else {
					raider.setTarget(null);
				}
				return false;
			});

			return session.raiderIds.isEmpty();
		});
	}

	private static LivingEntity findLookTarget(ServerPlayer player, double range) {
		Vec3 eyePos = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0F);
		Vec3 maxEnd = eyePos.add(look.scale(range));
		BlockHitResult blockHit = player.level().clip(new ClipContext(eyePos, maxEnd, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		Vec3 rayEnd = blockHit.getType() == BlockHitResult.Type.MISS ? maxEnd : blockHit.getLocation();
		AABB searchBox = player.getBoundingBox().expandTowards(look.scale(range)).inflate(1.0D);

		LivingEntity best = null;
		double bestDistanceSqr = Double.MAX_VALUE;
		for (Entity entity : player.level().getEntities(player, searchBox, entity -> entity instanceof LivingEntity living && living.isAlive())) {
			if (!(entity instanceof LivingEntity living)) {
				continue;
			}
			if (entity.getTags().contains(CARTEL_SUMMON_TAG) || entity.getTags().contains(CARTEL_LAWYER_TAG)) {
				continue;
			}
			if (!player.hasLineOfSight(living)) {
				continue;
			}
			Optional<Vec3> clip = living.getBoundingBox().inflate(0.3D).clip(eyePos, rayEnd);
			if (clip.isEmpty()) {
				continue;
			}
			double distanceSqr = eyePos.distanceToSqr(clip.get());
			if (distanceSqr < bestDistanceSqr) {
				bestDistanceSqr = distanceSqr;
				best = living;
			}
		}
		return best;
	}

	private static Raider spawnCartelRaider(ServerLevel level, EntityType<? extends Raider> type, BlockPos anchor, LivingEntity target, UUID ownerPlayerId) {
		BlockPos spawnPos = resolveRaiderSpawnPos(level, type, anchor);
		if (spawnPos == null) {
			return null;
		}

		Raider raider = type.create(level, EntitySpawnReason.EVENT);
		if (raider == null) {
			return null;
		}

		float spawnYaw = level.getRandom().nextFloat() * 360.0F;
		raider.snapTo(spawnPos, spawnYaw, 0.0F);
		raider.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnPos), EntitySpawnReason.EVENT, null);
		raider.setCanPickUpLoot(false);
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			raider.setDropChance(slot, 0.0F);
		}
		((MobXpRewardAccessor) (Object) raider).lg2$setXpReward(0);
		raider.addTag(CARTEL_SUMMON_TAG);
		raider.setTarget(target);

		if (!level.addFreshEntity(raider)) {
			return null;
		}

		CARTEL_SUMMON_OWNER_BY_ENTITY.put(raider.getUUID(), ownerPlayerId);
		emitSmoke(level, raider.position());
		return raider;
	}

	private static BlockPos resolveRaiderSpawnPos(ServerLevel level, EntityType<? extends Raider> type, BlockPos anchor) {
		for (int dy = -1; dy <= 2; dy++) {
			BlockPos candidate = anchor.offset(0, dy, 0);
			if (canSpawnRaiderAt(level, type, candidate)) {
				return candidate;
			}
		}
		return null;
	}

	private static boolean canSpawnRaiderAt(ServerLevel level, EntityType<? extends Raider> type, BlockPos pos) {
		BlockPos below = pos.below();
		if (level.getBlockState(below).isAir()) {
			return false;
		}

		BlockPos headPos = pos.above();
		if (!level.getBlockState(pos).canBeReplaced() || !level.getBlockState(headPos).canBeReplaced()) {
			return false;
		}

		AABB box = type.getDimensions().makeBoundingBox(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
		return level.noCollision(box);
	}

	private static void despawnRaiderWithSmoke(ServerLevel level, Raider raider) {
		CARTEL_SUMMON_OWNER_BY_ENTITY.remove(raider.getUUID());
		emitSmoke(level, raider.position());
		raider.discard();
	}

	private static void emitSmoke(ServerLevel level, Vec3 pos) {
		level.sendParticles(ParticleTypes.SMOKE, pos.x, pos.y + 0.7D, pos.z, 16, 0.35D, 0.5D, 0.35D, 0.01D);
	}

	private static long asTicks(double seconds) {
		return Math.max(0L, Math.round(seconds * 20.0D));
	}

	private static double positiveOrDefault(double value, double defaultValue) {
		if (Double.isNaN(value) || value <= 0.0D) {
			return defaultValue;
		}
		return value;
	}

	private static void enforceMrCartel49StackLimit(ServerPlayer player) {
		Optional<PlayerRaceConfig> raceOptional = getRace(player);
		if (raceOptional.isEmpty()) {
			return;
		}

		PlayerRaceConfig race = raceOptional.get();
		if (!MISTER_CARTEL_49_RACE_ID.equals(sanitizePath(race.id)) || race.stock == null || !race.stock.enabled) {
			return;
		}

		Inventory inventory = player.getInventory();
		boolean changed = false;

		changed |= normalizeInventoryStacks(player, inventory);

		if (changed) {
			inventory.setChanged();
			player.containerMenu.broadcastChanges();
		}
	}

	private static boolean normalizeInventoryStacks(ServerPlayer player, Inventory inventory) {
		boolean changed = false;
		int size = inventory.getContainerSize();
		for (int index = 0; index < size; index++) {
			ItemStack stack = inventory.getItem(index);
			if (stack.isEmpty() || stack.getMaxStackSize() != 64 || stack.getCount() <= MISTER_CARTEL_49_STACK_LIMIT) {
				continue;
			}

			int overflow = stack.getCount() - MISTER_CARTEL_49_STACK_LIMIT;
			stack.setCount(MISTER_CARTEL_49_STACK_LIMIT);
			changed = true;

			overflow = mergeOverflowIntoStacks(inventory, stack, index, overflow);
			overflow = placeOverflowIntoEmpty(inventory, stack, overflow);

			if (overflow > 0) {
				ItemStack dropped = stack.copy();
				dropped.setCount(overflow);
				player.drop(dropped, false);
			}
		}
		return changed;
	}

	private static int mergeOverflowIntoStacks(Inventory inventory, ItemStack source, int skipIndex, int overflow) {
		int size = inventory.getContainerSize();
		for (int index = 0; index < size && overflow > 0; index++) {
			if (index == skipIndex) {
				continue;
			}
			ItemStack target = inventory.getItem(index);
			if (target.isEmpty() || target.getMaxStackSize() != 64 || target.getCount() >= MISTER_CARTEL_49_STACK_LIMIT) {
				continue;
			}
			if (!ItemStack.isSameItemSameComponents(source, target)) {
				continue;
			}

			int room = MISTER_CARTEL_49_STACK_LIMIT - target.getCount();
			if (room <= 0) {
				continue;
			}
			int moved = Math.min(room, overflow);
			target.grow(moved);
			overflow -= moved;
		}
		return overflow;
	}

	private static int placeOverflowIntoEmpty(Inventory inventory, ItemStack source, int overflow) {
		int size = inventory.getContainerSize();
		for (int index = 0; index < size && overflow > 0; index++) {
			ItemStack target = inventory.getItem(index);
			if (!target.isEmpty()) {
				continue;
			}

			int moved = Math.min(MISTER_CARTEL_49_STACK_LIMIT, overflow);
			ItemStack inserted = source.copy();
			inserted.setCount(moved);
			inventory.setItem(index, inserted);
			overflow -= moved;
		}
		return overflow;
	}

	private static void cleanupLoadedOrphanCartelRaceEntities(MinecraftServer server) {
		if (server == null || server.overworld().getGameTime() % 40L != 0L) {
			return;
		}

		Set<UUID> activeRaiderIds = new HashSet<>();
		for (CartelSummonSession session : CARTEL_SUMMON_SESSIONS.values()) {
			activeRaiderIds.addAll(session.raiderIds);
		}

		Set<UUID> activeLawyerIds = new HashSet<>();
		for (CartelDefenseSession session : CARTEL_DEFENSE_SESSIONS.values()) {
			activeLawyerIds.add(session.lawyerEntityId);
		}

		for (ServerLevel level : server.getAllLevels()) {
			List<Entity> entities = new ArrayList<>();
			for (Entity entity : level.getAllEntities()) {
				entities.add(entity);
			}

			for (Entity entity : entities) {
				if (entity == null) {
					continue;
				}
				if (entity.getTags().contains(CARTEL_SUMMON_TAG) && entity instanceof Raider raider && !activeRaiderIds.contains(raider.getUUID())) {
					CARTEL_SUMMON_OWNER_BY_ENTITY.remove(raider.getUUID());
					raider.discard();
					continue;
				}
				boolean markedLawyer = entity.getTags().contains(CARTEL_LAWYER_TAG)
						|| entity.hasCustomName() && CARTEL_LAWYER_MARKER_NAME.equals(entity.getCustomName().getString());
				if (markedLawyer && !activeLawyerIds.contains(entity.getUUID())) {
					CARTEL_LAWYER_OWNER_BY_ENTITY.remove(entity.getUUID());
					entity.discard();
					sendCartelLawyerAppearanceRemoval(level, entity.getUUID());
				}
			}
		}
	}

	private static void cleanupAllCartelRaceEntities(MinecraftServer server, boolean emitParticles) {
		if (server == null) {
			return;
		}

		for (ServerLevel level : server.getAllLevels()) {
			List<Entity> entities = new ArrayList<>();
			for (Entity entity : level.getAllEntities()) {
				entities.add(entity);
			}

			for (Entity entity : entities) {
				if (entity == null) {
					continue;
				}
				if (entity.getTags().contains(CARTEL_SUMMON_TAG) && entity instanceof Raider raider) {
					if (emitParticles) {
						despawnRaiderWithSmoke(level, raider);
					} else {
						CARTEL_SUMMON_OWNER_BY_ENTITY.remove(raider.getUUID());
						raider.discard();
					}
					continue;
				}
				boolean markedLawyer = entity.getTags().contains(CARTEL_LAWYER_TAG)
						|| entity.hasCustomName() && CARTEL_LAWYER_MARKER_NAME.equals(entity.getCustomName().getString());
				if (markedLawyer) {
					CARTEL_LAWYER_OWNER_BY_ENTITY.remove(entity.getUUID());
					if (emitParticles) {
						emitSmoke(level, entity.position());
					}
					entity.discard();
					sendCartelLawyerAppearanceRemoval(level, entity.getUUID());
				}
			}
		}

		CARTEL_SUMMON_SESSIONS.clear();
		CARTEL_DEFENSE_SESSIONS.clear();
		CARTEL_SUMMON_OWNER_BY_ENTITY.clear();
		CARTEL_LAWYER_OWNER_BY_ENTITY.clear();
	}

	private static void cleanupCartelEntitiesForDisconnect(MinecraftServer server, ServerPlayer player) {
		if (server == null || player == null) {
			return;
		}

		CartelDefenseSession defenseSession = CARTEL_DEFENSE_SESSIONS.remove(player.getUUID());
		if (defenseSession != null) {
			despawnCartelLawyer(server, defenseSession);
		}

		CARTEL_SUMMON_SESSIONS.entrySet().removeIf(entry -> {
			CartelSummonSession session = entry.getValue();
			if (!player.getUUID().equals(session.ownerPlayerId)) {
				return false;
			}

			ServerLevel level = server.getLevel(session.dimension);
			if (level != null) {
				for (UUID raiderId : session.raiderIds) {
					Entity entity = level.getEntity(raiderId);
					if (entity instanceof Raider raider && raider.isAlive()) {
						despawnRaiderWithSmoke(level, raider);
					}
					CARTEL_SUMMON_OWNER_BY_ENTITY.remove(raiderId);
				}
			}
			return true;
		});
	}

	private static final class CartelDisguiseMenu extends ChestMenu {
		private final SimpleContainer container;
		private final ServerPlayer viewer;
		private final RaceAbilityConfig ability;
		private int selectedIndex;

		private CartelDisguiseMenu(int syncId, Inventory inventory, ServerPlayer viewer, RaceAbilityConfig ability, int selectedIndex) {
			this(syncId, inventory, new SimpleContainer(CARTEL_DISGUISE_MENU_ROWS * 9), viewer, ability, selectedIndex);
		}

		private CartelDisguiseMenu(
				int syncId,
				Inventory inventory,
				SimpleContainer container,
				ServerPlayer viewer,
				RaceAbilityConfig ability,
				int selectedIndex
		) {
			super(MenuType.GENERIC_9x3, syncId, inventory, container, CARTEL_DISGUISE_MENU_ROWS);
			this.container = container;
			this.viewer = viewer;
			this.ability = ability;
			this.selectedIndex = selectedIndex;
			this.refreshContents();
		}

		@Override
		public void clicked(int slotId, int button, ClickType clickType, Player player) {
			if (slotId < 0 || !(clickType == ClickType.PICKUP || clickType == ClickType.QUICK_MOVE || clickType == ClickType.SWAP)) {
				return;
			}

			List<ServerPlayer> candidates = collectCartelDisguiseCandidates(this.viewer);
			if (candidates.isEmpty()) {
				return;
			}

			this.selectedIndex = Math.floorMod(this.selectedIndex, candidates.size());
			int previousSlot = getCartelDisguisePreviousSlot(this.viewer);
			int headSlot = getCartelDisguiseHeadSlot(this.viewer);
			int nextSlot = getCartelDisguiseNextSlot(this.viewer);
			if (slotId == previousSlot) {
				this.selectedIndex = Math.floorMod(this.selectedIndex - 1, candidates.size());
				openMrCartelDisguiseMenu(this.viewer, candidates, this.selectedIndex, this.ability);
				return;
			}
			if (slotId == nextSlot) {
				this.selectedIndex = Math.floorMod(this.selectedIndex + 1, candidates.size());
				openMrCartelDisguiseMenu(this.viewer, candidates, this.selectedIndex, this.ability);
				return;
			}
			if (slotId == headSlot) {
				activateMrCartelDisguise(this.viewer, candidates.get(this.selectedIndex), this.ability);
			}
		}

		@Override
		public ItemStack quickMoveStack(Player player, int index) {
			return ItemStack.EMPTY;
		}

		@Override
		public boolean stillValid(Player player) {
			return player.isAlive();
		}

		@Override
		public void broadcastChanges() {
			super.broadcastChanges();
			hideCartelDisguiseInventoryVisuals(this.viewer, this);
		}

		@Override
		public void broadcastFullState() {
			super.broadcastFullState();
			hideCartelDisguiseInventoryVisuals(this.viewer, this);
		}

		@Override
		public void removed(Player player) {
			super.removed(player);
			restoreCartelDisguiseInventoryVisuals(this.viewer, this);
		}

		private void refreshContents() {
			for (int slot = 0; slot < this.container.getContainerSize(); slot++) {
				this.container.setItem(slot, ItemStack.EMPTY);
			}

			List<ServerPlayer> candidates = collectCartelDisguiseCandidates(this.viewer);
			int previousSlot = getCartelDisguisePreviousSlot(this.viewer);
			int headSlot = getCartelDisguiseHeadSlot(this.viewer);
			int nextSlot = getCartelDisguiseNextSlot(this.viewer);
			if (candidates.isEmpty()) {
				this.container.setItem(headSlot, buildCartelDisguiseEmptyState(this.viewer));
				return;
			}

			this.selectedIndex = Math.floorMod(this.selectedIndex, candidates.size());
			this.container.setItem(previousSlot, buildCartelDisguiseArrow(this.viewer, false));
			this.container.setItem(headSlot, buildCartelDisguiseHead(this.viewer, candidates.get(this.selectedIndex)));
			this.container.setItem(nextSlot, buildCartelDisguiseArrow(this.viewer, true));
			if (this.viewer.containerMenu == this) {
				this.slotsChanged(this.container);
				this.broadcastChanges();
			}
		}
	}

	private static final class CartelLawyerEntity extends PathfinderMob {
		private CartelLawyerEntity(ServerLevel level) {
			super(EntityType.HUSK, level);
			this.xpReward = 0;
			this.setPersistenceRequired();
			this.setSilent(true);
			this.setCanPickUpLoot(false);
			this.setTarget(null);
			this.addTag(CARTEL_LAWYER_TAG);
			this.setCustomName(Component.literal(CARTEL_LAWYER_MARKER_NAME));
			this.setCustomNameVisible(false);
			this.refreshDimensions();
		}

		public void attachPolymerAppearance(GameProfile profile) {
			PolymerEntityUtils.setPolymerEntity(this, new CartelLawyerOverlay(profile));
		}

		@Override
		protected void registerGoals() {
		}

		@Override
		public EntityDimensions getDefaultDimensions(Pose pose) {
			return CARTEL_LAWYER_DIMENSIONS;
		}

		@Override
		public float maxUpStep() {
			return 1.0F;
		}

		@Override
		protected PathNavigation createNavigation(Level level) {
			GroundPathNavigation navigation = new GroundPathNavigation(this, level);
			navigation.setCanOpenDoors(true);
			navigation.setCanFloat(true);
			return navigation;
		}

		@Override
		public void checkDespawn() {
		}

		@Override
		protected void customServerAiStep(ServerLevel level) {
			// Lawyer movement is fully scripted in ServerRaceSystem; skip vanilla mob AI work.
		}
	}


	private static final class CartelLawyerOverlay implements PolymerEntity {
		private static final byte ALL_PLAYER_SKIN_PARTS = (byte) 0x7F;
		private final GameProfile profile;

		private CartelLawyerOverlay(GameProfile profile) {
			this.profile = profile;
		}

		@Override
		public EntityType<?> getPolymerEntityType(PacketContext context) {
			return EntityType.PLAYER;
		}

		@Override
		public void onBeforeSpawnPacket(ServerPlayer player, java.util.function.Consumer<Packet<?>> packetConsumer) {
			packetConsumer.accept(ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(
					createCartelLawyerHiddenTeam(this.profile.id(), this.profile.name()),
					true
			));
			EnumSet<ClientboundPlayerInfoUpdatePacket.Action> actions = EnumSet.of(
					ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER,
					ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED,
					ClientboundPlayerInfoUpdatePacket.Action.UPDATE_GAME_MODE,
					ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LATENCY,
					ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME,
					ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LIST_ORDER,
					ClientboundPlayerInfoUpdatePacket.Action.UPDATE_HAT
			);
			ClientboundPlayerInfoUpdatePacket packet = PolymerEntityUtils.createMutablePlayerListPacket(actions);
			ClientboundPlayerInfoUpdatePacket.Entry entry = new ClientboundPlayerInfoUpdatePacket.Entry(
					this.profile.id(),
					this.profile,
					false,
					0,
					GameType.SURVIVAL,
					null,
					true,
					0,
					(RemoteChatSession.Data) null
			);
			packet.entries().add(entry);
			packetConsumer.accept(packet);
		}

		@Override
		public void modifyRawTrackedData(List<SynchedEntityData.DataValue<?>> data, ServerPlayer player, boolean initial) {
			upsertTrackedData(data, SynchedEntityData.DataValue.create(PlayerTrackedDataAccessor.lg2$getDataPlayerMainHand(), HumanoidArm.RIGHT));
			upsertTrackedData(data, SynchedEntityData.DataValue.create(PlayerTrackedDataAccessor.lg2$getDataPlayerModeCustomisation(), ALL_PLAYER_SKIN_PARTS));
		}

		private static void upsertTrackedData(List<SynchedEntityData.DataValue<?>> data, SynchedEntityData.DataValue<?> replacement) {
			for (int i = 0; i < data.size(); i++) {
				SynchedEntityData.DataValue<?> current = data.get(i);
				if (current.id() == replacement.id()) {
					data.set(i, replacement);
					return;
				}
			}
			data.add(replacement);
		}
	}
}
