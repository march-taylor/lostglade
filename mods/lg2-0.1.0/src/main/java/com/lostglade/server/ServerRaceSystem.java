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
import com.lostglade.item.CocaineItem;
import com.lostglade.item.CopperJetpackItem;
import com.lostglade.item.MethadoneItem;
import com.lostglade.item.ModItems;
import com.lostglade.item.TubochkaItem;
import com.lostglade.mixin.ArmorStandAccessor;
import com.lostglade.mixin.EntityPassengerAccessor;
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
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
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
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.network.protocol.game.ClientboundOpenBookPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.RemoteChatSession;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
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
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.decoration.ArmorStand;
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
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import xyz.nucleoid.packettweaker.PacketContext;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
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
import java.util.concurrent.ConcurrentHashMap;
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
	private static final String COPPER_MAN_RACE_ID = "copper_man";
	private static final String NO_RACE_ID = "no_race";
	private static final String WOMAN_RACE_ID = "woman";
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
	private static final FontDescription CARTEL_MANUAL_PAGE_FONT = new FontDescription.Resource(
			Objects.requireNonNull(Identifier.tryParse("lg2:cartel_manual_pages"))
	);
	private static final String[] CARTEL_MANUAL_PAGE_GLYPHS = {
			"\uef60",
			"\uef61",
			"\uef62",
			"\uef63",
			"\uef64",
			"\uef65"
	};
	private static final String[] CARTEL_PASSPORT_NAME_FONT_ROWS = {
			"ABCDEFGH",
			"IJKLMNOP",
			"QRSTUVWX",
			"YZabcdef",
			"ghijklmn",
			"opqrstuv",
			"wxyz0123",
			"456789_\0"
	};
	private static final String CARTEL_PASSPORT_NAME_TEXTURE_RESOURCE = "/assets/lg2/textures/font/passport_name.png";
	private static final int CARTEL_PASSPORT_NAME_BITMAP_COLUMNS = 8;
	private static final int CARTEL_PASSPORT_NAME_BITMAP_ROWS = 8;
	private static final int CARTEL_PASSPORT_NAME_RENDER_HEIGHT = 5;
	private static final int CARTEL_PASSPORT_NAME_CHAR_ADVANCE = 5;
	private static final int CARTEL_PASSPORT_NAME_MIN_X = 18;
	private static final int CARTEL_PASSPORT_NAME_CENTER_X = 131;
	private static volatile Map<Character, Integer> CARTEL_PASSPORT_NAME_ADVANCE_CACHE;
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
	private static final int COCAINE_CAULDRON_BATCH_SIZE = 16;
	private static final long MISTER_CARTEL_STACK_CHECK_INTERVAL_TICKS = 8L;
	private static final double DEFAULT_COPPER_FOOD_RESTORE_MULTIPLIER = 0.8D;
	private static final int DEFAULT_COPPER_INGOT_FOOD_POINTS = 5;
	private static final double DEFAULT_COPPER_GOLEM_NOTICE_RANGE_BLOCKS = 8.0D;
	private static final double WOMAN_FLOWER_DEFAULT_COOLDOWN_SECONDS = 5.0D;
	private static final double WOMAN_ANIMAL_BREED_DEFAULT_COOLDOWN_SECONDS = 30.0D;
	private static final float WOMAN_FLOWER_HEAL_AMOUNT = 1.0F;
	private static final double WOMAN_DEFENSE_DEFAULT_DURATION_SECONDS = 20.0D;
	private static final double WOMAN_DEFENSE_DEFAULT_RANGE_BLOCKS = 32.0D;
	private static final int WOMAN_DEFENSE_EFFECT_REFRESH_TICKS = 8;
	private static final double WOMAN_DEFENSE_LIGHT_SHAKE_STRENGTH = 0.07D;
	private static final double WOMAN_DEFENSE_MEDIUM_SHAKE_STRENGTH = 0.13D;
	private static final double WOMAN_DEFENSE_STRONG_SHAKE_STRENGTH = 0.22D;
	private static final double WOMAN_ATTACK_DEFAULT_CHARGE_RADIUS_BLOCKS = 1.5D;
	private static final double WOMAN_ATTACK_DEFAULT_RANGE_BLOCKS = 64.0D;
	private static final double WOMAN_ATTACK_DEFAULT_DAMAGE = 2.0D;
	private static final double WOMAN_ATTACK_DEFAULT_FOLLOW_SECONDS = 30.0D;
	private static final double WOMAN_ATTACK_PROJECTILE_SPEED = 1.4D;
	private static final double WOMAN_ATTACK_CHARGE_FORWARD_OFFSET = 2.0D;
	private static final int WOMAN_ATTACK_PARTICLE_COUNT = 9;
	private static final double WOMAN_ATTACK_AIR_TRIGGER_HEAD_FORWARD_OFFSET = 0.22D;
	private static final float WOMAN_ATTACK_AIR_TRIGGER_WIDTH = 1.8F;
	private static final float WOMAN_ATTACK_AIR_TRIGGER_HEIGHT = 1.8F;
	private static final long WOMAN_ATTACK_FOLLOW_NAV_INTERVAL_TICKS = 8L;
	private static final double WOMAN_ATTACK_FOLLOW_SPEED = 1.1D;
	private static final Item[] WOMAN_ATTACK_TEMPT_ITEMS = {
			Items.WHEAT,
			Items.CARROT,
			Items.POTATO,
			Items.BEETROOT,
			Items.BEETROOT_SEEDS,
			Items.WHEAT_SEEDS,
			Items.MELON_SEEDS,
			Items.PUMPKIN_SEEDS,
			Items.SUGAR,
			Items.APPLE,
			Items.SWEET_BERRIES,
			Items.GLOW_BERRIES,
			Items.SEAGRASS,
			Items.BAMBOO,
			Items.KELP,
			Items.DANDELION
	};
	private static final int COPPER_GOLEM_REACTION_INTERVAL_TICKS = 5;
	private static final double COPPER_GOLEM_FOLLOW_SPEED = 1.1D;
	private static final double COPPER_LIGHTNING_ATTRACT_RANGE_BLOCKS = 128.0D;
	private static final double COPPER_MAN_DEFENSE_DEFAULT_DURATION_SECONDS = 300.0D;
	private static final double COPPER_MAN_DEFENSE_DEFAULT_COOLDOWN_SECONDS = 900.0D;
	private static final double COPPER_MAN_JETPACK_DEFAULT_DURATION_SECONDS = 30.0D;
	private static final double COPPER_MAN_JETPACK_DEFAULT_COOLDOWN_SECONDS = 300.0D;
	private static final double COPPER_MAN_JETPACK_DEFAULT_MAX_RISE_BLOCKS = 30.0D;
	private static final double COPPER_MAN_JETPACK_ASCEND_SPEED = 0.42D;
	private static final double COPPER_MAN_JETPACK_GLIDE_DESCEND_SPEED = -0.32D;
	private static final double COPPER_MAN_JETPACK_HORIZONTAL_SPEED = 0.0275D;
	private static final double COPPER_MAN_JETPACK_VERTICAL_ACCEL = 0.255D;
	private static final double COPPER_MAN_JETPACK_DESCEND_ACCEL = 0.165D;
	private static final double COPPER_MAN_JETPACK_HORIZONTAL_BLEND = 0.4D;
	private static final double COPPER_MAN_JETPACK_ASCEND_BRAKE_RANGE_BLOCKS = 4.0D;
	private static final double COPPER_MAN_JETPACK_PARTICLE_BACK_OFFSET = 0.34D;
	private static final double COPPER_MAN_JETPACK_PARTICLE_SIDE_OFFSET = 0.2D;
	private static final double COPPER_MAN_JETPACK_PARTICLE_UP_OFFSET = 0.80D;
	private static final double COPPER_MAN_JETPACK_PARTICLE_UP_OFFSET_CROUCHING = 0.64D;
	private static final String COPPER_MAN_JETPACK_DISPLAY_TAG = "lg2_copper_jetpack_display";
	private static final String COPPER_MAN_JETPACK_DISPLAY_OWNER_TAG_PREFIX = "lg2_copper_jetpack_owner:";
	private static final long COPPER_MAN_DEFENSE_PREWARM_INTERVAL_TICKS = 10L;
	private static final long COPPER_MAN_DEFENSE_TINT_RETRY_COOLDOWN_MS = 30_000L;
	private static final long COPPER_MAN_DEFENSE_TINT_CACHE_CLEANUP_INTERVAL_TICKS = 12_000L;
	private static final long COPPER_MAN_DEFENSE_TINT_CACHE_MAX_AGE_MS = 7L * 24L * 60L * 60L * 1000L;
	private static final int COPPER_MAN_DEFENSE_TINT_CACHE_MAX_FILES = 64;
	private static final String COPPER_MAN_DEFENSE_TINT_CACHE_VERSION = "v5";
	private static final String COPPER_MAN_DEFENSE_TINT_CACHE_DIR_NAME = "generated/lg2/copper_defense_tints";
	private static final float COPPER_MAN_DEFENSE_TINT_STRENGTH = 0.84F;
	private static final float COPPER_MAN_DEFENSE_COPPER_RED = 0.6509804F;
	private static final float COPPER_MAN_DEFENSE_COPPER_GREEN = 0.37254903F;
	private static final float COPPER_MAN_DEFENSE_COPPER_BLUE = 0.30980393F;
	private static final float COPPER_INGOT_SATURATION = 0.6F;
	private static final Consumable COPPER_INGOT_CONSUMABLE = Consumable.builder()
			.consumeSeconds(1.6F)
			.animation(ItemUseAnimation.EAT)
			.sound(SoundEvents.GENERIC_EAT)
			.hasConsumeParticles(true)
			.build();
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
	private static final Map<UUID, EnumMap<RaceAbilitySlot, Long>> GENERIC_ABILITY_COOLDOWN_END_TICKS = new LinkedHashMap<>();
	private static final Map<UUID, EnumSet<RaceAbilitySlot>> GENERIC_ABILITY_INFINITE_COOLDOWNS = new LinkedHashMap<>();
	private static final Map<UUID, Long> WOMAN_FLOWER_COOLDOWNS = new LinkedHashMap<>();
	private static final Map<UUID, Long> WOMAN_ANIMAL_BREED_COOLDOWNS = new LinkedHashMap<>();
	private static final Map<UUID, WomanDefenseSession> WOMAN_DEFENSE_SESSIONS = new LinkedHashMap<>();
	private static final Map<UUID, WomanAttackChargeSession> WOMAN_ATTACK_CHARGE_SESSIONS = new LinkedHashMap<>();
	private static final List<WomanAttackProjectile> WOMAN_ATTACK_PROJECTILES = new ArrayList<>();
	private static final Map<UUID, WomanAttackFollowSession> WOMAN_ATTACK_FOLLOWS = new LinkedHashMap<>();
	private static final Map<UUID, CartelDisguiseSession> CARTEL_DISGUISE_SESSIONS = new LinkedHashMap<>();
	private static final Map<UUID, CartelManualBookRestore> CARTEL_MANUAL_BOOK_RESTORES = new LinkedHashMap<>();
	private static final Map<UUID, UUID> COPPER_GOLEM_FOLLOWERS = new LinkedHashMap<>();
	private static final Map<UUID, Long> COPPER_MAN_DEFENSE_COOLDOWNS = new LinkedHashMap<>();
	private static final Map<UUID, CopperManDefenseVisualSession> COPPER_MAN_DEFENSE_VISUAL_SESSIONS = new LinkedHashMap<>();
	private static final Map<UUID, Long> COPPER_MAN_JETPACK_COOLDOWNS = new LinkedHashMap<>();
	private static final Map<UUID, CopperManJetpackSession> COPPER_MAN_JETPACK_SESSIONS = new LinkedHashMap<>();
	private static final Map<UUID, CopperManJetpackSession> COPPER_MAN_JETPACK_SUSPENDED_FOR_DRONE = new LinkedHashMap<>();
	private static final Map<UUID, CopperManJetpackInputState> COPPER_MAN_JETPACK_INPUTS = new ConcurrentHashMap<>();
	private static final Map<UUID, UUID> COPPER_MAN_JETPACK_DISPLAY_IDS = new LinkedHashMap<>();
	private static final Map<String, Property> COPPER_MAN_DEFENSE_TINT_CACHE = new ConcurrentHashMap<>();
	private static final Map<String, Long> COPPER_MAN_DEFENSE_TINT_RETRY_AT_MS = new ConcurrentHashMap<>();
	private static final Set<String> COPPER_MAN_DEFENSE_TINT_BUILD_IN_FLIGHT = ConcurrentHashMap.newKeySet();
	private static long copperManDefenseTintCacheLastCleanupTick = Long.MIN_VALUE;
	private static final List<CartelTravkaGrowthAttempt> CARTEL_TRAVKA_GROWTH_ATTEMPTS = new ArrayList<>();
	private static final Map<CartelFernGrowthKey, CartelFernGrowthTask> CARTEL_PLANTED_FERN_GROWTHS = new LinkedHashMap<>();
	private static final PriorityQueue<CartelFernGrowthTask> CARTEL_PLANTED_FERN_GROWTH_QUEUE = new PriorityQueue<>(Comparator.comparingLong(task -> task.growAtTick));
	private static final Map<UUID, UUID> CARTEL_SUMMON_OWNER_BY_ENTITY = new LinkedHashMap<>();
	private static final Map<UUID, UUID> CARTEL_LAWYER_OWNER_BY_ENTITY = new LinkedHashMap<>();
	private static final ThreadLocal<Boolean> CARTEL_DEFENSE_REFLECTION_ACTIVE = ThreadLocal.withInitial(() -> Boolean.FALSE);
	private static final Set<CocaineCauldronKey> PROCESSED_COCAINE_CAULDRONS = new HashSet<>();
	private static long processedCocaineCauldronTick = Long.MIN_VALUE;
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

	private static final class CartelManualBookRestore {
		private final int inventorySlot;
		private final int menuSlot;
		private final long restoreAtTick;

		private CartelManualBookRestore(int inventorySlot, int menuSlot, long restoreAtTick) {
			this.inventorySlot = inventorySlot;
			this.menuSlot = menuSlot;
			this.restoreAtTick = restoreAtTick;
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
			cleanupCopperManDefenseTintCache(server, true);
			syncGeneratedDialogs(server, true);
			Lg2.LOGGER.info("Loaded {} configured personal races", RACES_BY_NICKNAME.size());
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			cleanupAllCartelRaceEntities(server, true);
			restoreAllCartelDisguises(server);
			restoreAllCopperManJetpacks(server);
			restoreAllCopperManDefenseVisuals(server);
			CartelWebcamBridge.clearAll();
			RACES_BY_NICKNAME.clear();
			DIALOG_ID_BY_NICKNAME.clear();
			GENERATED_DIALOG_JSON_BY_PATH.clear();
			CARTEL_ATTACK_COOLDOWNS.clear();
			CARTEL_SUMMON_SESSIONS.clear();
			CARTEL_DEFENSE_COOLDOWNS.clear();
			CARTEL_DEFENSE_SESSIONS.clear();
			CARTEL_UNIQUE_COOLDOWNS.clear();
			GENERIC_ABILITY_COOLDOWN_END_TICKS.clear();
			GENERIC_ABILITY_INFINITE_COOLDOWNS.clear();
			WOMAN_FLOWER_COOLDOWNS.clear();
			WOMAN_ANIMAL_BREED_COOLDOWNS.clear();
			WOMAN_DEFENSE_SESSIONS.clear();
			CARTEL_DISGUISE_SESSIONS.clear();
			COPPER_MAN_DEFENSE_COOLDOWNS.clear();
			COPPER_MAN_DEFENSE_VISUAL_SESSIONS.clear();
			COPPER_MAN_JETPACK_COOLDOWNS.clear();
			COPPER_MAN_JETPACK_SESSIONS.clear();
			COPPER_MAN_JETPACK_SUSPENDED_FOR_DRONE.clear();
			COPPER_MAN_JETPACK_INPUTS.clear();
			COPPER_MAN_JETPACK_DISPLAY_IDS.clear();
			COPPER_MAN_DEFENSE_TINT_CACHE.clear();
			COPPER_MAN_DEFENSE_TINT_RETRY_AT_MS.clear();
			COPPER_MAN_DEFENSE_TINT_BUILD_IN_FLIGHT.clear();
			copperManDefenseTintCacheLastCleanupTick = Long.MIN_VALUE;
			CARTEL_TRAVKA_GROWTH_ATTEMPTS.clear();
			CARTEL_PLANTED_FERN_GROWTHS.clear();
			CARTEL_PLANTED_FERN_GROWTH_QUEUE.clear();
			COPPER_GOLEM_FOLLOWERS.clear();
			CARTEL_SUMMON_OWNER_BY_ENTITY.clear();
			CARTEL_LAWYER_OWNER_BY_ENTITY.clear();
			CARTEL_LAWYER_SKIN_FUTURE = null;
			CARTEL_LAWYER_SKIN_PROPERTY = null;
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
				server.execute(() -> {
					getRace(handler.player).ifPresent(race ->
							Lg2.LOGGER.info("Assigned personal race '{}' to {}", race.id, handler.player.getGameProfile().name())
					);
					CartelSecretRecipeBookSystem.syncJoinedPlayer(handler.player);
					CopperManGogglesSystem.syncPlayerRecipeBook(handler.player);
					prewarmCopperManDefenseTint(server, handler.player);
				})
		);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			cleanupCartelEntitiesForDisconnect(server, handler.player);
			clearCartelDisguise(handler.player);
			CartelWebcamBridge.handlePlayerDisconnected(handler.player.getUUID());
			CARTEL_MANUAL_BOOK_RESTORES.remove(handler.player.getUUID());
			CARTEL_TRAVKA_GROWTH_ATTEMPTS.removeIf(attempt -> attempt.playerId.equals(handler.player.getUUID()));
			COPPER_GOLEM_FOLLOWERS.entrySet().removeIf(entry -> handler.player.getUUID().equals(entry.getValue()));
			clearCopperManDefenseVisual(handler.player);
			clearCopperManJetpack(handler.player);
			WOMAN_DEFENSE_SESSIONS.remove(handler.player.getUUID());
			clearWomanAttackState(handler.player.getUUID());
		});
		UseItemCallback.EVENT.register((player, world, hand) -> {
			if (!(player instanceof ServerPlayer serverPlayer) || world.isClientSide()) {
				return InteractionResult.PASS;
			}
			InteractionResult womanResult = tryReleaseWomanAttack(serverPlayer, hand);
			if (womanResult != InteractionResult.PASS) {
				return womanResult;
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
		UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			if (!(player instanceof ServerPlayer serverPlayer) || world.isClientSide()) {
				return InteractionResult.PASS;
			}
			return onUseEntity(serverPlayer, hand, entity);
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
			tickCartelManualBookRestores(server);
			tickCartelTravkaGrowthAttempts(server);
			tickCartelFernGrowths(server);
			tickCopperManStock(server);
			tickCopperManJetpack(server);
			tickCopperManDefense(server);
			tickWomanStock(server);
			tickWomanDefense(server);
			tickWomanAttack(server);
			CocaineItem.tick(server);
			MethadoneItem.tick(server);
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
		if (!hasUnlockedAbility(player, race, slot)) {
			ServerUpgradeUiSystem.playPurchaseBlockedSound(player);
			MutableComponent notPurchased = PolymerResourcePackUtils.hasMainPack(player)
					? Component.translatable("message.lg2.race.ability_not_purchased", Component.literal(ability.name))
					: Component.literal(localizeAbilityNotPurchased(player, ability.name));
			player.displayClientMessage(
					notPurchased.withStyle(style -> style.withColor(ChatFormatting.RED).withItalic(false)),
					true
			);
			return 0;
		}

		String raceId = sanitizePath(race.id);
		if (!isCustomHandledAbility(raceId, slot) && displayGenericAbilityCooldown(player, slot)) {
			return 0;
		}

		if (slot == RaceAbilitySlot.ATTACK && MISTER_CARTEL_49_RACE_ID.equals(raceId)) {
			return useMrCartelAttack(player, race, ability);
		}
		if (slot == RaceAbilitySlot.DEFENSE && MISTER_CARTEL_49_RACE_ID.equals(raceId)) {
			return useMrCartelDefense(player, race, ability);
		}
		if (slot == RaceAbilitySlot.UNIQUE_ABILITY && MISTER_CARTEL_49_RACE_ID.equals(raceId)) {
			return useMrCartelUniqueAbility(player, race, ability);
		}
		if (slot == RaceAbilitySlot.SHNYAGA && MISTER_CARTEL_49_RACE_ID.equals(raceId)) {
			return useMrCartelShnyaga(player, race, ability);
		}
		if (slot == RaceAbilitySlot.ATTACK && COPPER_MAN_RACE_ID.equals(raceId)) {
			return CopperManRepulsorSystem.toggleMode(player);
		}
		if (slot == RaceAbilitySlot.DEFENSE && COPPER_MAN_RACE_ID.equals(raceId)) {
			return useCopperManDefense(player, race, ability);
		}
		if (slot == RaceAbilitySlot.UNIQUE_ABILITY && COPPER_MAN_RACE_ID.equals(raceId)) {
			return useCopperManJetpack(player, race, ability);
		}
		if (slot == RaceAbilitySlot.SHNYAGA && COPPER_MAN_RACE_ID.equals(raceId)) {
			return CopperManGogglesSystem.toggleMode(player);
		}
		if (slot == RaceAbilitySlot.ATTACK && WOMAN_RACE_ID.equals(raceId)) {
			return useWomanAttack(player, race, ability);
		}
		if (slot == RaceAbilitySlot.DEFENSE && WOMAN_RACE_ID.equals(raceId)) {
			return useWomanDefense(player, race, ability);
		}

		startGenericAbilityCooldown(player, slot, ability);
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

	public static boolean hasUnlockedAbility(ServerPlayer player, RaceAbilitySlot slot) {
		Optional<PlayerRaceConfig> raceOptional = getRace(player);
		return raceOptional.isPresent() && hasUnlockedAbility(player, raceOptional.get(), slot);
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

	private static boolean hasUnlockedAbility(ServerPlayer player, PlayerRaceConfig race, RaceAbilitySlot slot) {
		if (slot == null) {
			return false;
		}
		RaceAbilityConfig ability = getAbility(race, slot);
		if (ability == null || !ability.enabled) {
			return false;
		}
		if (slot == RaceAbilitySlot.STOCK) {
			return true;
		}

		String upgradeId = ability.abilityId == null ? "" : ability.abilityId.trim();
		if (upgradeId.isEmpty()) {
			return false;
		}
		return ServerUpgradeUiSystem.hasUpgrade(player, upgradeId);
	}

	private static boolean isCustomHandledAbility(String raceId, RaceAbilitySlot slot) {
		if (raceId == null || slot == null) {
			return false;
		}
		if (MISTER_CARTEL_49_RACE_ID.equals(raceId)) {
			return slot == RaceAbilitySlot.ATTACK
					|| slot == RaceAbilitySlot.DEFENSE
					|| slot == RaceAbilitySlot.UNIQUE_ABILITY
					|| slot == RaceAbilitySlot.SHNYAGA;
		}
		if (COPPER_MAN_RACE_ID.equals(raceId)) {
			return slot == RaceAbilitySlot.ATTACK
					|| slot == RaceAbilitySlot.DEFENSE
					|| slot == RaceAbilitySlot.UNIQUE_ABILITY
					|| slot == RaceAbilitySlot.SHNYAGA;
		}
		return false;
	}

	private static boolean displayGenericAbilityCooldown(ServerPlayer player, RaceAbilitySlot slot) {
		if (player == null || slot == null || slot == RaceAbilitySlot.STOCK) {
			return false;
		}

		UUID playerId = player.getUUID();
		EnumSet<RaceAbilitySlot> infiniteCooldowns = GENERIC_ABILITY_INFINITE_COOLDOWNS.get(playerId);
		if (infiniteCooldowns != null && infiniteCooldowns.contains(slot)) {
			return displayInfiniteCooldown(player);
		}

		EnumMap<RaceAbilitySlot, Long> cooldownEndTicks = GENERIC_ABILITY_COOLDOWN_END_TICKS.get(playerId);
		if (cooldownEndTicks == null) {
			return false;
		}

		Long cooldownEndTick = cooldownEndTicks.get(slot);
		if (cooldownEndTick == null) {
			return false;
		}

		long nowTick = player.level().getGameTime();
		long remainingTicks = cooldownEndTick - nowTick;
		if (remainingTicks <= 0L) {
			cooldownEndTicks.remove(slot);
			if (cooldownEndTicks.isEmpty()) {
				GENERIC_ABILITY_COOLDOWN_END_TICKS.remove(playerId);
			}
			return false;
		}
		return displayRemainingCooldown(player, remainingTicks);
	}

	private static void startGenericAbilityCooldown(ServerPlayer player, RaceAbilitySlot slot, RaceAbilityConfig ability) {
		if (player == null || slot == null || slot == RaceAbilitySlot.STOCK || ability == null) {
			return;
		}

		UUID playerId = player.getUUID();
		if (isInfiniteCooldown(ability.cooldownSeconds)) {
			GENERIC_ABILITY_COOLDOWN_END_TICKS.computeIfPresent(playerId, (id, cooldowns) -> {
				cooldowns.remove(slot);
				return cooldowns.isEmpty() ? null : cooldowns;
			});
			GENERIC_ABILITY_INFINITE_COOLDOWNS
					.computeIfAbsent(playerId, id -> EnumSet.noneOf(RaceAbilitySlot.class))
					.add(slot);
			return;
		}

		EnumSet<RaceAbilitySlot> infiniteCooldowns = GENERIC_ABILITY_INFINITE_COOLDOWNS.get(playerId);
		if (infiniteCooldowns != null) {
			infiniteCooldowns.remove(slot);
			if (infiniteCooldowns.isEmpty()) {
				GENERIC_ABILITY_INFINITE_COOLDOWNS.remove(playerId);
			}
		}

		long cooldownTicks = asTicks(ability.cooldownSeconds);
		if (cooldownTicks <= 0L) {
			GENERIC_ABILITY_COOLDOWN_END_TICKS.computeIfPresent(playerId, (id, cooldowns) -> {
				cooldowns.remove(slot);
				return cooldowns.isEmpty() ? null : cooldowns;
			});
			return;
		}

		long cooldownEndTick = player.level().getGameTime() + cooldownTicks;
		GENERIC_ABILITY_COOLDOWN_END_TICKS
				.computeIfAbsent(playerId, id -> new EnumMap<>(RaceAbilitySlot.class))
				.put(slot, cooldownEndTick);
	}

	public static boolean isCopperManStockEnabled(ServerPlayer player) {
		if (player == null) {
			return false;
		}
		Optional<PlayerRaceConfig> raceOptional = getRace(player);
		if (raceOptional.isEmpty()) {
			return false;
		}
		PlayerRaceConfig race = raceOptional.get();
		return COPPER_MAN_RACE_ID.equals(sanitizePath(race.id))
				&& race.stock != null
				&& race.stock.enabled;
	}

	public static boolean ensureCopperIngotConsumableForUse(ServerPlayer player, ItemStack stack) {
		if (!isCopperManStockEnabled(player) || stack == null || stack.isEmpty() || !stack.is(Items.COPPER_INGOT)) {
			return false;
		}

		FoodProperties desiredFood = new FoodProperties(getCopperIngotFoodPoints(player), COPPER_INGOT_SATURATION, true);
		FoodProperties currentFood = stack.get(DataComponents.FOOD);
		Consumable currentConsumable = stack.get(DataComponents.CONSUMABLE);
		boolean changed = false;
		if (!Objects.equals(currentFood, desiredFood)) {
			stack.set(DataComponents.FOOD, desiredFood);
			changed = true;
		}
		if (!Objects.equals(currentConsumable, COPPER_INGOT_CONSUMABLE)) {
			stack.set(DataComponents.CONSUMABLE, COPPER_INGOT_CONSUMABLE);
			changed = true;
		}
		return changed;
	}

	public static void stripCopperIngotConsumable(ItemStack stack) {
		if (stack == null || stack.isEmpty() || !stack.is(Items.COPPER_INGOT)) {
			return;
		}
		stack.remove(DataComponents.FOOD);
		stack.remove(DataComponents.CONSUMABLE);
	}

	public static void handleCopperManJetpackInput(ServerPlayer player, net.minecraft.world.entity.player.Input input) {
		if (player == null || input == null) {
			return;
		}

		COPPER_MAN_JETPACK_INPUTS.put(
				player.getUUID(),
				new CopperManJetpackInputState(
						input.forward(),
						input.backward(),
						input.left(),
						input.right(),
						input.jump(),
						input.shift(),
						input.sprint()
				)
		);
	}

	public static void adjustCopperManFoodAfterEating(ServerPlayer player, int beforeFood, float beforeSaturation) {
		if (!isCopperManStockEnabled(player)) {
			return;
		}
		FoodDataSnapshot snapshot = getFoodDataSnapshot(player);
		if (snapshot == null) {
			return;
		}

		int gained = Math.max(0, snapshot.foodLevel - beforeFood);
		if (gained <= 0) {
			return;
		}

		double multiplier = getCopperFoodRestoreMultiplier(player);
		int adjustedGain = Math.max(0, (int) Math.round(gained * multiplier));
		int adjustedFood = Math.max(0, Math.min(20, beforeFood + adjustedGain));
		player.getFoodData().setFoodLevel(adjustedFood);

		float currentSaturation = player.getFoodData().getSaturationLevel();
		float maxAllowedSaturation = Math.max(beforeSaturation, adjustedFood);
		if (currentSaturation > maxAllowedSaturation) {
			player.getFoodData().setSaturation(maxAllowedSaturation);
		}
	}

	public static BlockPos findCopperManLightningTarget(ServerLevel level, BlockPos origin) {
		if (level == null || origin == null) {
			return null;
		}

		double bestDistance = Double.MAX_VALUE;
		ServerPlayer bestPlayer = null;
		AABB searchBox = new AABB(origin).inflate(COPPER_LIGHTNING_ATTRACT_RANGE_BLOCKS);
		for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, searchBox, ServerRaceSystem::isCopperManStockEnabled)) {
			if (!player.isAlive() || player.isSpectator() || !level.canSeeSky(player.blockPosition())) {
				continue;
			}
			double distance = player.blockPosition().distSqr(origin);
			if (distance >= bestDistance) {
				continue;
			}
			bestDistance = distance;
			bestPlayer = player;
		}

		return bestPlayer == null ? null : bestPlayer.blockPosition();
	}

	private static int useCopperManDefense(ServerPlayer caster, PlayerRaceConfig race, RaceAbilityConfig ability) {
		try {
			ServerLevel level = caster.level();
			MinecraftServer server = level.getServer();
			long nowTick = level.getGameTime();
			long cooldownTicks = asTicks(positiveOrDefault(ability.cooldownSeconds, COPPER_MAN_DEFENSE_DEFAULT_COOLDOWN_SECONDS));
			long remainingCooldownTicks = getRemainingOnlineCooldownTicks(COPPER_MAN_DEFENSE_COOLDOWNS, caster.getUUID());
			if (displayRemainingCooldown(caster, remainingCooldownTicks)) {
				return 0;
			}

			long durationTicks = Math.max(1L, asTicks(positiveOrDefault(ability.durationSeconds, COPPER_MAN_DEFENSE_DEFAULT_DURATION_SECONDS)));
			caster.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, (int) Math.min(Integer.MAX_VALUE, durationTicks), 1, false, false, true));
			startCopperManDefenseVisual(server, caster, nowTick + durationTicks);
			startOnlineCooldown(COPPER_MAN_DEFENSE_COOLDOWNS, caster.getUUID(), cooldownTicks);

			Lg2.LOGGER.info("Player {} used copper man defense '{}' from race '{}'", caster.getGameProfile().name(), ability.abilityId, race.id);
			return 1;
		} catch (Exception exception) {
			Lg2.LOGGER.error("Failed to activate copper man defense for {}", caster.getGameProfile().name(), exception);
			caster.sendSystemMessage(Component.literal("Не удалось активировать защиту."));
			return 0;
		}
	}

	private static int useCopperManJetpack(ServerPlayer caster, PlayerRaceConfig race, RaceAbilityConfig ability) {
		try {
			ServerLevel level = caster.level();
			long nowTick = level.getGameTime();
			long cooldownTicks = asTicks(positiveOrDefault(ability.cooldownSeconds, COPPER_MAN_JETPACK_DEFAULT_COOLDOWN_SECONDS));
			long remainingCooldownTicks = getRemainingOnlineCooldownTicks(COPPER_MAN_JETPACK_COOLDOWNS, caster.getUUID());
			if (displayRemainingCooldown(caster, remainingCooldownTicks)) {
				return 0;
			}

			long durationTicks = Math.max(1L, asTicks(positiveOrDefault(ability.durationSeconds, COPPER_MAN_JETPACK_DEFAULT_DURATION_SECONDS)));
			double maxRiseBlocks = positiveOrDefault(ability.jetpackMaxRiseBlocks, COPPER_MAN_JETPACK_DEFAULT_MAX_RISE_BLOCKS);
			COPPER_MAN_JETPACK_SESSIONS.put(
					caster.getUUID(),
					new CopperManJetpackSession(nowTick + durationTicks, caster.getY(), maxRiseBlocks)
			);
			COPPER_MAN_JETPACK_INPUTS.putIfAbsent(caster.getUUID(), CopperManJetpackInputState.EMPTY);
			startOnlineCooldown(COPPER_MAN_JETPACK_COOLDOWNS, caster.getUUID(), cooldownTicks);
			syncCopperManJetpackVisual(caster, true);

			Lg2.LOGGER.info("Player {} used copper man unique ability '{}' from race '{}'", caster.getGameProfile().name(), ability.abilityId, race.id);
			return 1;
		} catch (Exception exception) {
			Lg2.LOGGER.error("Failed to activate copper man jetpack for {}", caster.getGameProfile().name(), exception);
			caster.sendSystemMessage(Component.literal("Не удалось активировать реактивный ранец."));
			return 0;
		}
	}

	private static void tickCopperManJetpack(MinecraftServer server) {
		if (server == null) {
			return;
		}

		long nowTick = server.overworld().getGameTime();
		tickOnlineCooldowns(server, COPPER_MAN_JETPACK_COOLDOWNS);
		if (COPPER_MAN_JETPACK_SESSIONS.isEmpty()) {
			return;
		}

		for (Map.Entry<UUID, CopperManJetpackSession> entry : new ArrayList<>(COPPER_MAN_JETPACK_SESSIONS.entrySet())) {
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			CopperManJetpackSession session = entry.getValue();
			if (player == null || session == null) {
				COPPER_MAN_JETPACK_SESSIONS.remove(entry.getKey());
				COPPER_MAN_JETPACK_INPUTS.remove(entry.getKey());
				continue;
			}

			if (!player.isAlive() || player.isSpectator() || nowTick >= session.expireTick()) {
				clearCopperManJetpack(player);
				continue;
			}
			if (DroneSystem.isControllingDrone(player)) {
				suspendCopperManJetpackForDrone(player);
				continue;
			}

			applyCopperManJetpackMovement(player, session);
			syncCopperManJetpackVisual(player, true);
		}
	}

	private static void tickCopperManDefense(MinecraftServer server) {
		if (server == null) {
			return;
		}

		long nowTick = server.overworld().getGameTime();
		tickOnlineCooldowns(server, COPPER_MAN_DEFENSE_COOLDOWNS);
		if (copperManDefenseTintCacheLastCleanupTick == Long.MIN_VALUE
				|| nowTick - copperManDefenseTintCacheLastCleanupTick >= COPPER_MAN_DEFENSE_TINT_CACHE_CLEANUP_INTERVAL_TICKS) {
			cleanupCopperManDefenseTintCache(server, false);
		}
		if (nowTick % COPPER_MAN_DEFENSE_PREWARM_INTERVAL_TICKS == 0L) {
			prewarmCopperManDefenseTints(server);
		}
		if (COPPER_MAN_DEFENSE_VISUAL_SESSIONS.isEmpty()) {
			return;
		}

		for (Map.Entry<UUID, CopperManDefenseVisualSession> entry : new ArrayList<>(COPPER_MAN_DEFENSE_VISUAL_SESSIONS.entrySet())) {
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			CopperManDefenseVisualSession session = entry.getValue();
			if (player == null || session == null) {
				COPPER_MAN_DEFENSE_VISUAL_SESSIONS.remove(entry.getKey());
				continue;
			}

			if (!player.isAlive() || nowTick >= session.expireTick()) {
				clearCopperManDefenseVisual(player);
				continue;
			}

			ensureCopperManDefenseVisualApplied(server, player, session);
		}
	}

	private static void applyCopperManJetpackMovement(ServerPlayer player, CopperManJetpackSession session) {
		if (player == null || session == null) {
			return;
		}

		CopperManJetpackInputState input = COPPER_MAN_JETPACK_INPUTS.getOrDefault(player.getUUID(), CopperManJetpackInputState.EMPTY);
		Vec3 currentMotion = player.getDeltaMovement();
		double currentY = player.getY();
		double maxAllowedY = session.maxAllowedY();

		if (player.onGround() && !input.jump()) {
			player.fallDistance = 0.0F;
			return;
		}

		double verticalTarget;
		if (input.jump()) {
			double remaining = Math.max(0.0D, maxAllowedY - currentY);
			float brakeFactor = clamp01((float) (remaining / COPPER_MAN_JETPACK_ASCEND_BRAKE_RANGE_BLOCKS));
			verticalTarget = remaining <= 0.02D
					? 0.0D
					: Math.min(COPPER_MAN_JETPACK_ASCEND_SPEED * brakeFactor, remaining);
		} else {
			verticalTarget = player.onGround() ? 0.0D : COPPER_MAN_JETPACK_GLIDE_DESCEND_SPEED;
		}

		double verticalAccel = input.jump() ? COPPER_MAN_JETPACK_VERTICAL_ACCEL : COPPER_MAN_JETPACK_DESCEND_ACCEL;
		double nextVertical = approach(currentMotion.y, verticalTarget, verticalAccel);
		if (currentY >= maxAllowedY - 0.02D && nextVertical > 0.0D) {
			nextVertical = 0.0D;
		}

		double nextX = currentMotion.x;
		double nextZ = currentMotion.z;
		if (!player.onGround()) {
			Vec3 horizontalTarget = computeCopperManJetpackHorizontalVelocity(player, input);
			Vec3 currentHorizontal = new Vec3(currentMotion.x, 0.0D, currentMotion.z);
			Vec3 nextHorizontal = currentHorizontal.add(horizontalTarget.subtract(currentHorizontal).scale(COPPER_MAN_JETPACK_HORIZONTAL_BLEND));
			nextX = nextHorizontal.x;
			nextZ = nextHorizontal.z;
		}

		player.setDeltaMovement(nextX, nextVertical, nextZ);
		player.hurtMarked = true;
		player.fallDistance = 0.0F;
		emitCopperManJetpackParticles(player, input);
	}

	private static Vec3 computeCopperManJetpackHorizontalVelocity(ServerPlayer player, CopperManJetpackInputState input) {
		if (player == null || input == null) {
			return Vec3.ZERO;
		}

		int forwardInput = (input.forward() ? 1 : 0) - (input.backward() ? 1 : 0);
		int strafeInput = (input.right() ? 1 : 0) - (input.left() ? 1 : 0);
		if (forwardInput == 0 && strafeInput == 0) {
			return Vec3.ZERO;
		}

		Vec3 forward = Vec3.directionFromRotation(0.0F, player.getYRot());
		forward = new Vec3(forward.x, 0.0D, forward.z);
		if (forward.lengthSqr() <= 1.0E-6D) {
			return Vec3.ZERO;
		}
		forward = forward.normalize();
		Vec3 right = new Vec3(-forward.z, 0.0D, forward.x);
		Vec3 desired = forward.scale(forwardInput).add(right.scale(strafeInput));
		if (desired.lengthSqr() <= 1.0E-6D) {
			return Vec3.ZERO;
		}
		return desired.normalize().scale(COPPER_MAN_JETPACK_HORIZONTAL_SPEED);
	}

	private static void emitCopperManJetpackParticles(ServerPlayer player, CopperManJetpackInputState input) {
		if (player == null || input == null || !(player.level() instanceof ServerLevel level)) {
			return;
		}
		if (player.onGround() && !input.jump()) {
			return;
		}

		float yaw = player.getYHeadRot();
		yaw = player.yBodyRot;
		Vec3 forward = Vec3.directionFromRotation(0.0F, yaw);
		forward = new Vec3(forward.x, 0.0D, forward.z);
		if (forward.lengthSqr() <= 1.0E-6D) {
			return;
		}
		forward = forward.normalize();
		Vec3 backward = forward.scale(-1.0D);
		Vec3 right = new Vec3(-forward.z, 0.0D, forward.x);

		double upOffset = player.isCrouching()
				? COPPER_MAN_JETPACK_PARTICLE_UP_OFFSET_CROUCHING
				: COPPER_MAN_JETPACK_PARTICLE_UP_OFFSET;
		Vec3 center = player.position()
				.add(0.0D, upOffset, 0.0D)
				.add(backward.scale(COPPER_MAN_JETPACK_PARTICLE_BACK_OFFSET));
		Vec3 leftTube = center.add(right.scale(COPPER_MAN_JETPACK_PARTICLE_SIDE_OFFSET));
		Vec3 rightTube = center.add(right.scale(-COPPER_MAN_JETPACK_PARTICLE_SIDE_OFFSET));

		int particleCount = input.jump() ? 2 : 1;
		double spread = input.jump() ? 0.012D : 0.008D;
		double speed = input.jump() ? 0.018D : 0.01D;
		level.sendParticles(ParticleTypes.CLOUD, leftTube.x, leftTube.y, leftTube.z, particleCount, spread, spread, spread, speed);
		level.sendParticles(ParticleTypes.CLOUD, rightTube.x, rightTube.y, rightTube.z, particleCount, spread, spread, spread, speed);
	}

	private static void clearCopperManJetpack(ServerPlayer player) {
		if (player == null) {
			return;
		}

		COPPER_MAN_JETPACK_SESSIONS.remove(player.getUUID());
		COPPER_MAN_JETPACK_SUSPENDED_FOR_DRONE.remove(player.getUUID());
		COPPER_MAN_JETPACK_INPUTS.remove(player.getUUID());
		player.fallDistance = 0.0F;
		syncCopperManJetpackVisual(player, false);
	}

	private static void restoreAllCopperManJetpacks(MinecraftServer server) {
		if (server == null || COPPER_MAN_JETPACK_SESSIONS.isEmpty()) {
			return;
		}

		for (UUID playerId : new ArrayList<>(COPPER_MAN_JETPACK_SESSIONS.keySet())) {
			ServerPlayer player = server.getPlayerList().getPlayer(playerId);
			if (player != null) {
				syncCopperManJetpackVisual(player, false);
				player.fallDistance = 0.0F;
			}
		}
		COPPER_MAN_JETPACK_SESSIONS.clear();
		COPPER_MAN_JETPACK_SUSPENDED_FOR_DRONE.clear();
		COPPER_MAN_JETPACK_INPUTS.clear();
		COPPER_MAN_JETPACK_DISPLAY_IDS.clear();
	}

	private static void syncCopperManJetpackVisual(ServerPlayer player, boolean active) {
		if (player == null || !(player.level() instanceof ServerLevel)) {
			return;
		}

		if (!active) {
			removeCopperManJetpackDisplay(player);
			CopperManGogglesSystem.refreshVisual(player);
			return;
		}

		ensureCopperManJetpackDisplay(player);
		updateCopperManJetpackDisplay(player);
	}

	private static ItemStack buildCopperManJetpackVisualStack() {
		return CopperJetpackItem.createDisplayStack();
	}

	private static void removeCopperManJetpackDisplay(ServerPlayer player) {
		if (player == null) {
			return;
		}

		ServerLevel level = (ServerLevel) player.level();
		UUID playerId = player.getUUID();
		UUID displayId = COPPER_MAN_JETPACK_DISPLAY_IDS.remove(playerId);
		if (displayId != null) {
			Entity entity = level.getEntity(displayId);
			if (entity != null) {
				entity.discard();
			}
		}

		String ownerTag = COPPER_MAN_JETPACK_DISPLAY_OWNER_TAG_PREFIX + playerId;
		AABB cleanupBox = player.getBoundingBox().inflate(64.0D);
		for (ArmorStand stand : level.getEntitiesOfClass(
				ArmorStand.class,
				cleanupBox,
				candidate -> candidate.getTags().contains(COPPER_MAN_JETPACK_DISPLAY_TAG) && candidate.getTags().contains(ownerTag)
		)) {
			stand.discard();
		}
	}

	private static void ensureCopperManJetpackDisplay(ServerPlayer player) {
		if (player == null || !(player.level() instanceof ServerLevel level)) {
			return;
		}

		ArmorStand existing = findCopperManJetpackDisplay(player);
		if (existing != null) {
			configureCopperManJetpackDisplay(existing);
			attachCopperManJetpackDisplay(player, existing);
			COPPER_MAN_JETPACK_DISPLAY_IDS.put(player.getUUID(), existing.getUUID());
			syncCopperManJetpackDisplayEquipment(existing);
			return;
		}

		ArmorStand stand = EntityType.ARMOR_STAND.create(level, EntitySpawnReason.TRIGGERED);
		if (stand == null) {
			return;
		}
		stand.addTag(COPPER_MAN_JETPACK_DISPLAY_TAG);
		stand.addTag(COPPER_MAN_JETPACK_DISPLAY_OWNER_TAG_PREFIX + player.getUUID());
		configureCopperManJetpackDisplay(stand);
		stand.setPos(player.getX(), player.getY(), player.getZ());
		level.addFreshEntity(stand);
		attachCopperManJetpackDisplay(player, stand);
		COPPER_MAN_JETPACK_DISPLAY_IDS.put(player.getUUID(), stand.getUUID());
		syncCopperManJetpackDisplayEquipment(stand);
	}

	private static void attachCopperManJetpackDisplay(ServerPlayer player, ArmorStand stand) {
		if (player == null || stand == null) {
			return;
		}

		Entity vehicle = stand.getVehicle();
		if (vehicle == player && player.hasPassenger(stand)) {
			return;
		}
		if (vehicle != null) {
			stand.stopRiding();
		}
		forceEntityPassenger(player, stand);
	}

	private static void forceEntityPassenger(Entity vehicle, Entity passenger) {
		if (vehicle == null || passenger == null || vehicle == passenger) {
			return;
		}

		if (passenger.getVehicle() == vehicle && vehicle.hasPassenger(passenger)) {
			return;
		}

		if (passenger.isPassenger()) {
			passenger.stopRiding();
		}

		((EntityPassengerAccessor) passenger).lg2$setVehicle(vehicle);
		((EntityPassengerAccessor) vehicle).lg2$addPassenger(passenger);
		vehicle.positionRider(passenger);
		syncPassengerAttachment(vehicle);
	}

	private static void syncPassengerAttachment(Entity vehicle) {
		if (vehicle == null || !(vehicle.level() instanceof ServerLevel level)) {
			return;
		}

		ClientboundSetPassengersPacket packet = new ClientboundSetPassengersPacket(vehicle);
		for (ServerPlayer viewer : level.players()) {
			viewer.connection.send(packet);
		}
	}

	private static void configureCopperManJetpackDisplay(ArmorStand stand) {
		if (stand == null) {
			return;
		}

		stand.setInvisible(true);
		stand.setInvulnerable(true);
		stand.setSilent(true);
		stand.setNoGravity(true);
		stand.setItemSlot(EquipmentSlot.HEAD, buildCopperManJetpackVisualStack());
		((ArmorStandAccessor) stand).lg2$setSmall(false);
		((ArmorStandAccessor) stand).lg2$setMarker(true);
	}

	private static void syncCopperManJetpackDisplayEquipment(ArmorStand stand) {
		if (stand == null || !(stand.level() instanceof ServerLevel level)) {
			return;
		}

		ClientboundSetEquipmentPacket packet = new ClientboundSetEquipmentPacket(
				stand.getId(),
				List.of(com.mojang.datafixers.util.Pair.of(EquipmentSlot.HEAD, stand.getItemBySlot(EquipmentSlot.HEAD).copy()))
		);
		for (ServerPlayer viewer : level.players()) {
			viewer.connection.send(packet);
		}
	}


	private static ArmorStand findCopperManJetpackDisplay(ServerPlayer player) {
		if (player == null || !(player.level() instanceof ServerLevel level)) {
			return null;
		}

		UUID playerId = player.getUUID();
		UUID displayId = COPPER_MAN_JETPACK_DISPLAY_IDS.get(playerId);
		if (displayId != null) {
			Entity entity = level.getEntity(displayId);
			if (entity instanceof ArmorStand stand) {
				return stand;
			}
		}

		String ownerTag = COPPER_MAN_JETPACK_DISPLAY_OWNER_TAG_PREFIX + playerId;
		AABB searchBox = player.getBoundingBox().inflate(16.0D);
		List<ArmorStand> stands = level.getEntitiesOfClass(
				ArmorStand.class,
				searchBox,
				candidate -> candidate.getTags().contains(COPPER_MAN_JETPACK_DISPLAY_TAG) && candidate.getTags().contains(ownerTag)
		);
		if (stands.isEmpty()) {
			return null;
		}

		ArmorStand keep = stands.get(0);
		for (int i = 1; i < stands.size(); i++) {
			stands.get(i).discard();
		}
		return keep;
	}

	private static void updateCopperManJetpackDisplay(ServerPlayer player) {
		if (player == null || !(player.level() instanceof ServerLevel)) {
			return;
		}

		ArmorStand stand = findCopperManJetpackDisplay(player);
		if (stand == null) {
			return;
		}

		attachCopperManJetpackDisplay(player, stand);
		stand.setYRot(player.getYRot());
		stand.setYHeadRot(player.getYRot());
		stand.yBodyRot = player.getYRot();
		stand.yBodyRotO = player.getYRot();
		syncCopperManJetpackDisplayEquipment(stand);
	}

	private static void tickCopperManStock(MinecraftServer server) {
		if (server == null) {
			return;
		}

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			sanitizeCopperIngots(player);
		}

		long nowTick = server.overworld().getGameTime();
		if (nowTick % COPPER_GOLEM_REACTION_INTERVAL_TICKS != 0L) {
			return;
		}

		tickCopperGolemFollowers(server);
	}

	private static void tickWomanStock(MinecraftServer server) {
		if (server == null) {
			return;
		}
		tickOnlineCooldowns(server, WOMAN_FLOWER_COOLDOWNS);
		tickOnlineCooldowns(server, WOMAN_ANIMAL_BREED_COOLDOWNS);
	}

	private static void sanitizeCopperIngots(ServerPlayer player) {
		if (player == null) {
			return;
		}

		boolean copperMan = isCopperManStockEnabled(player);
		Inventory inventory = player.getInventory();
		boolean changed = false;
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack == null || stack.isEmpty() || !stack.is(Items.COPPER_INGOT)) {
				continue;
			}
			if (copperMan) {
				changed |= ensureCopperIngotConsumableForUse(player, stack);
			} else {
				if (stack.has(DataComponents.FOOD) || stack.has(DataComponents.CONSUMABLE)) {
					changed = true;
				}
				stripCopperIngotConsumable(stack);
			}
		}
		if (changed) {
			player.inventoryMenu.broadcastChanges();
		}
	}

	private static void tickCopperGolemFollowers(MinecraftServer server) {
		COPPER_GOLEM_FOLLOWERS.entrySet().removeIf(entry -> !updateTrackedCopperGolemFollower(server, entry.getKey(), entry.getValue()));

		List<ServerPlayer> copperPlayers = new ArrayList<>();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (isCopperManStockEnabled(player) && player.isAlive() && !player.isSpectator()) {
				copperPlayers.add(player);
			}
		}
		if (copperPlayers.isEmpty()) {
			return;
		}

		for (ServerPlayer player : copperPlayers) {
			double noticeRange = getCopperGolemNoticeRange(player);
			AABB searchBox = player.getBoundingBox().inflate(noticeRange);
			for (Entity entity : player.level().getEntities(player, searchBox, ServerRaceSystem::isCopperGolemEntity)) {
				if (!(entity instanceof Mob mob) || !mob.isAlive() || mob.isPassenger()) {
					continue;
				}
				if (mob.distanceToSqr(player) > noticeRange * noticeRange || !mob.hasLineOfSight(player)) {
					continue;
				}

				COPPER_GOLEM_FOLLOWERS.put(mob.getUUID(), player.getUUID());
				PathNavigation navigation = mob.getNavigation();
				if (navigation != null) {
					navigation.moveTo(player, COPPER_GOLEM_FOLLOW_SPEED);
				}
				mob.getLookControl().setLookAt(player, 30.0F, 30.0F);
			}
		}
	}

	private static boolean updateTrackedCopperGolemFollower(MinecraftServer server, UUID golemId, UUID playerId) {
		Entity golemEntity = findEntity(server, golemId);
		ServerPlayer player = playerId == null ? null : server.getPlayerList().getPlayer(playerId);
		if (!(golemEntity instanceof Mob mob) || player == null || !player.isAlive() || player.isSpectator()) {
			stopCopperGolemNavigation(golemEntity);
			return false;
		}
		if (!isCopperGolemEntity(mob)) {
			stopCopperGolemNavigation(mob);
			return false;
		}

		double noticeRange = getCopperGolemNoticeRange(player);
		if (mob.level() != player.level()
				|| mob.distanceToSqr(player) > noticeRange * noticeRange
				|| !mob.hasLineOfSight(player)) {
			stopCopperGolemNavigation(mob);
			return false;
		}
		return true;
	}

	private static void stopCopperGolemNavigation(Entity entity) {
		if (entity instanceof Mob mob) {
			PathNavigation navigation = mob.getNavigation();
			if (navigation != null) {
				navigation.stop();
			}
		}
	}

	private static Entity findEntity(MinecraftServer server, UUID entityId) {
		if (server == null || entityId == null) {
			return null;
		}
		for (ServerLevel level : server.getAllLevels()) {
			Entity entity = level.getEntity(entityId);
			if (entity != null) {
				return entity;
			}
		}
		return null;
	}

	private static boolean isCopperGolemEntity(Entity entity) {
		if (entity == null) {
			return false;
		}
		Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
		return id != null && sanitizePath(id.getPath()).contains("copper_golem");
	}

	private static double getCopperFoodRestoreMultiplier(ServerPlayer player) {
		Optional<PlayerRaceConfig> raceOptional = getRace(player);
		if (raceOptional.isPresent() && raceOptional.get().stock != null && raceOptional.get().stock.foodRestoreMultiplier > 0.0D) {
			return raceOptional.get().stock.foodRestoreMultiplier;
		}
		return DEFAULT_COPPER_FOOD_RESTORE_MULTIPLIER;
	}

	private static int getCopperIngotFoodPoints(ServerPlayer player) {
		Optional<PlayerRaceConfig> raceOptional = getRace(player);
		if (raceOptional.isPresent() && raceOptional.get().stock != null && raceOptional.get().stock.copperIngotFoodPoints > 0) {
			return raceOptional.get().stock.copperIngotFoodPoints;
		}
		return DEFAULT_COPPER_INGOT_FOOD_POINTS;
	}

	private static double getCopperGolemNoticeRange(ServerPlayer player) {
		Optional<PlayerRaceConfig> raceOptional = getRace(player);
		if (raceOptional.isPresent() && raceOptional.get().stock != null && raceOptional.get().stock.copperGolemNoticeRangeBlocks > 0.0D) {
			return raceOptional.get().stock.copperGolemNoticeRangeBlocks;
		}
		return DEFAULT_COPPER_GOLEM_NOTICE_RANGE_BLOCKS;
	}

	private static FoodDataSnapshot getFoodDataSnapshot(ServerPlayer player) {
		if (player == null || player.getFoodData() == null) {
			return null;
		}
		return new FoodDataSnapshot(player.getFoodData().getFoodLevel(), player.getFoodData().getSaturationLevel());
	}

	private record FoodDataSnapshot(int foodLevel, float saturationLevel) {
	}

	private record WomanDefenseSession(UUID playerId, ResourceKey<Level> dimension, long endTick, double range) {
	}

	private record WomanDefenseExposure(int severity) {
	}

	private static final class WomanAttackChargeSession {
		private final UUID playerId;
		private final ResourceKey<Level> dimension;
		private final double radius;
		private final double range;
		private final double damage;
		private final long followTicks;
		private Interaction airTriggerEntity;

		private WomanAttackChargeSession(
				UUID playerId,
				ResourceKey<Level> dimension,
				double radius,
				double range,
				double damage,
				long followTicks
		) {
			this.playerId = playerId;
			this.dimension = dimension;
			this.radius = radius;
			this.range = range;
			this.damage = damage;
			this.followTicks = followTicks;
			this.airTriggerEntity = null;
		}
	}

	private record WomanAttackFollowSession(UUID targetId, ResourceKey<Level> dimension, long endTick, double range) {
	}

	private static final class WomanAttackProjectile {
		private final UUID ownerId;
		private final ResourceKey<Level> dimension;
		private Vec3 position;
		private final Vec3 velocity;
		private double remainingRange;
		private final double originalRange;
		private final double radius;
		private final double damage;
		private final long followTicks;

		private WomanAttackProjectile(
				UUID ownerId,
				ResourceKey<Level> dimension,
				Vec3 position,
				Vec3 velocity,
				double range,
				double radius,
				double damage,
				long followTicks
		) {
			this.ownerId = ownerId;
			this.dimension = dimension;
			this.position = position;
			this.velocity = velocity;
			this.remainingRange = range;
			this.originalRange = range;
			this.radius = radius;
			this.damage = damage;
			this.followTicks = followTicks;
		}

		private UUID ownerId() {
			return this.ownerId;
		}

		private ResourceKey<Level> dimension() {
			return this.dimension;
		}

		private Vec3 position() {
			return this.position;
		}

		private void setPosition(Vec3 position) {
			this.position = position;
		}

		private Vec3 velocity() {
			return this.velocity;
		}

		private double remainingRange() {
			return this.remainingRange;
		}

		private void setRemainingRange(double remainingRange) {
			this.remainingRange = remainingRange;
		}

		private double originalRange() {
			return this.originalRange;
		}

		private double radius() {
			return this.radius;
		}

		private double damage() {
			return this.damage;
		}

		private long followTicks() {
			return this.followTicks;
		}
	}

	private record CopperManJetpackInputState(boolean forward, boolean backward, boolean left, boolean right, boolean jump, boolean shift, boolean sprint) {
		private static final CopperManJetpackInputState EMPTY = new CopperManJetpackInputState(false, false, false, false, false, false, false);
	}

	private record CopperManJetpackSession(long expireTick, double baseY, double maxRiseBlocks) {
		private double maxAllowedY() {
			return this.baseY + this.maxRiseBlocks;
		}
	}

	private record CopperManDefenseVisualSession(SkinValue originalSkin, String sourceCacheKey, long expireTick) {
	}

	private record StoredSkinProperty(String name, String value, String signature) {
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
		if (!MISTER_CARTEL_49_RACE_ID.equals(sanitizePath(race.id))
				|| race.shnyaga == null
				|| !race.shnyaga.enabled
				|| !hasUnlockedAbility(player, race, RaceAbilitySlot.SHNYAGA)) {
			return;
		}

		if (!level.getBlockState(pos).is(Blocks.FERN)) {
			return;
		}

		scheduleCartelFernGrowth(level, pos, race.shnyaga);
	}

	private static InteractionResult onUseBlock(ServerPlayer player, InteractionHand hand, BlockPos pos) {
		InteractionResult womanAttack = tryReleaseWomanAttack(player, hand);
		if (womanAttack != InteractionResult.PASS) {
			return womanAttack;
		}
		InteractionResult womanResult = tryUseWomanFlowerStock(player, hand, pos);
		if (womanResult != InteractionResult.PASS) {
			return womanResult;
		}

		ItemStack stack = player.getItemInHand(hand);
		if (!stack.is(Items.BONE_MEAL)) {
			return InteractionResult.PASS;
		}

		Optional<PlayerRaceConfig> raceOptional = getRace(player);
		if (raceOptional.isEmpty()) {
			return InteractionResult.PASS;
		}

		PlayerRaceConfig race = raceOptional.get();
		if (!MISTER_CARTEL_49_RACE_ID.equals(sanitizePath(race.id))
				|| race.shnyaga == null
				|| !race.shnyaga.enabled
				|| !hasUnlockedAbility(player, race, RaceAbilitySlot.SHNYAGA)) {
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

	private static InteractionResult onUseEntity(ServerPlayer player, InteractionHand hand, Entity entity) {
		InteractionResult womanAttack = tryReleaseWomanAttack(player, hand);
		if (womanAttack != InteractionResult.PASS) {
			return womanAttack;
		}
		return tryUseWomanAnimalBreedStock(player, hand, entity);
	}

	private static InteractionResult tryUseWomanFlowerStock(ServerPlayer player, InteractionHand hand, BlockPos pos) {
		if (player == null
				|| pos == null
				|| hand != InteractionHand.MAIN_HAND
				|| !player.getMainHandItem().isEmpty()
				|| !(player.level() instanceof ServerLevel level)) {
			return InteractionResult.PASS;
		}

		RaceAbilityConfig stock = getWomanStockAbility(player);
		if (stock == null) {
			return InteractionResult.PASS;
		}

		BlockState state = level.getBlockState(pos);
		if (!state.is(BlockTags.FLOWERS)) {
			return InteractionResult.PASS;
		}

		long remainingCooldownTicks = getRemainingOnlineCooldownTicks(WOMAN_FLOWER_COOLDOWNS, player.getUUID());
		if (displayRemainingCooldown(player, remainingCooldownTicks)) {
			return InteractionResult.SUCCESS;
		}

		consumeWomanFlower(level, pos, state, player);
		level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, 6, 0.22D, 0.22D, 0.22D, 0.02D);
		player.heal(WOMAN_FLOWER_HEAL_AMOUNT);
		startOnlineCooldown(WOMAN_FLOWER_COOLDOWNS, player.getUUID(), asTicks(getWomanFlowerCooldownSeconds(stock)));
		return InteractionResult.SUCCESS;
	}

	private static InteractionResult tryUseWomanAnimalBreedStock(ServerPlayer player, InteractionHand hand, Entity entity) {
		if (player == null
				|| entity == null
				|| hand != InteractionHand.MAIN_HAND
				|| !player.getMainHandItem().isEmpty()
				|| !(entity instanceof Animal animal)) {
			return InteractionResult.PASS;
		}

		RaceAbilityConfig stock = getWomanStockAbility(player);
		if (stock == null) {
			return InteractionResult.PASS;
		}

		if (!canWomanTriggerBreeding(animal)) {
			return InteractionResult.PASS;
		}

		long remainingCooldownTicks = getRemainingOnlineCooldownTicks(WOMAN_ANIMAL_BREED_COOLDOWNS, player.getUUID());
		if (displayRemainingCooldown(player, remainingCooldownTicks)) {
			return InteractionResult.SUCCESS;
		}

		animal.setInLove(player);
		startOnlineCooldown(WOMAN_ANIMAL_BREED_COOLDOWNS, player.getUUID(), asTicks(getWomanAnimalBreedCooldownSeconds(stock)));
		return InteractionResult.SUCCESS;
	}

	private static boolean canWomanTriggerBreeding(Animal animal) {
		if (animal == null || !animal.isAlive()) {
			return false;
		}
		if (animal.isBaby() || animal.getAge() != 0 || animal.isInLove() || !animal.canFallInLove()) {
			return false;
		}
		if (animal instanceof net.minecraft.world.entity.TamableAnimal tameable) {
			if (!tameable.isTame() || tameable.getHealth() < tameable.getMaxHealth()) {
				return false;
			}
		}
		return true;
	}

	private static void consumeWomanFlower(ServerLevel level, BlockPos pos, BlockState state, ServerPlayer player) {
		if (!(state.getBlock() instanceof DoublePlantBlock) || !state.hasProperty(DoublePlantBlock.HALF)) {
			level.destroyBlock(pos, false, player);
			return;
		}

		DoubleBlockHalf half = state.getValue(DoublePlantBlock.HALF);
		BlockPos lowerPos = half == DoubleBlockHalf.UPPER ? pos.below() : pos;
		BlockPos upperPos = lowerPos.above();
		BlockState lowerState = level.getBlockState(lowerPos);
		BlockState upperState = level.getBlockState(upperPos);
		if (lowerState.getBlock() == state.getBlock()) {
			level.destroyBlock(lowerPos, false, player);
		}
		if (upperState.getBlock() == state.getBlock()) {
			level.destroyBlock(upperPos, false, player);
		}
	}

	private static RaceAbilityConfig getWomanStockAbility(ServerPlayer player) {
		Optional<PlayerRaceConfig> raceOptional = getRace(player);
		if (raceOptional.isEmpty()) {
			return null;
		}

		PlayerRaceConfig race = raceOptional.get();
		if (!WOMAN_RACE_ID.equals(sanitizePath(race.id)) || race.stock == null || !race.stock.enabled) {
			return null;
		}
		return race.stock;
	}

	private static double getWomanFlowerCooldownSeconds(RaceAbilityConfig stock) {
		return positiveOrDefault(stock == null ? 0.0D : stock.womanFlowerCooldownSeconds, WOMAN_FLOWER_DEFAULT_COOLDOWN_SECONDS);
	}

	private static double getWomanAnimalBreedCooldownSeconds(RaceAbilityConfig stock) {
		return positiveOrDefault(stock == null ? 0.0D : stock.womanAnimalBreedCooldownSeconds, WOMAN_ANIMAL_BREED_DEFAULT_COOLDOWN_SECONDS);
	}

	private static int useWomanDefense(ServerPlayer player, PlayerRaceConfig race, RaceAbilityConfig ability) {
		if (player == null || ability == null || player.isSpectator() || !player.isAlive()) {
			return 0;
		}
		if (!(player.level() instanceof ServerLevel level)) {
			return 0;
		}

		UUID playerId = player.getUUID();
		if (WOMAN_DEFENSE_SESSIONS.containsKey(playerId)) {
			return 0;
		}

		startGenericAbilityCooldown(player, RaceAbilitySlot.DEFENSE, ability);
		long durationTicks = Math.max(1L, asTicks(getWomanDefenseDurationSeconds(ability)));
		WOMAN_DEFENSE_SESSIONS.put(
				playerId,
				new WomanDefenseSession(
						playerId,
						level.dimension(),
						level.getGameTime() + durationTicks,
						getWomanDefenseRange(ability)
				)
		);
		return 1;
	}

	private static int useWomanAttack(ServerPlayer player, PlayerRaceConfig race, RaceAbilityConfig ability) {
		if (player == null || ability == null || player.isSpectator() || !player.isAlive()) {
			return 0;
		}
		if (!(player.level() instanceof ServerLevel level)) {
			return 0;
		}

		UUID playerId = player.getUUID();
		if (WOMAN_ATTACK_CHARGE_SESSIONS.containsKey(playerId) || hasActiveWomanAttackProjectile(playerId)) {
			return 0;
		}

		startGenericAbilityCooldown(player, RaceAbilitySlot.ATTACK, ability);

		double radius = getWomanAttackChargeRadius(ability);
		double range = getWomanAttackRange(ability);
		double damage = getWomanAttackDamage(ability);
		long followTicks = asTicks(getWomanAttackFollowSeconds(ability));
		WOMAN_ATTACK_CHARGE_SESSIONS.put(
				playerId,
				new WomanAttackChargeSession(playerId, level.dimension(), radius, range, damage, followTicks)
		);
		return 1;
	}

	private static void tickWomanDefense(MinecraftServer server) {
		if (server == null || WOMAN_DEFENSE_SESSIONS.isEmpty()) {
			return;
		}

		long nowTick = server.overworld().getGameTime();
		Map<UUID, WomanDefenseExposure> exposures = new HashMap<>();
		Iterator<Map.Entry<UUID, WomanDefenseSession>> iterator = WOMAN_DEFENSE_SESSIONS.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<UUID, WomanDefenseSession> entry = iterator.next();
			WomanDefenseSession session = entry.getValue();
			ServerPlayer woman = session == null ? null : server.getPlayerList().getPlayer(session.playerId());
			if (woman == null
					|| !woman.isAlive()
					|| woman.isSpectator()
					|| !(woman.level() instanceof ServerLevel level)
					|| !level.dimension().equals(session.dimension())
					|| nowTick >= session.endTick()) {
				iterator.remove();
				continue;
			}

			double range = Math.max(0.0D, session.range());
			double rangeSqr = range * range;
			for (ServerPlayer viewer : level.players()) {
				if (viewer == null || viewer == woman || !viewer.isAlive() || viewer.isSpectator()) {
					continue;
				}
				double distanceSqr = viewer.distanceToSqr(woman);
				if (distanceSqr > rangeSqr) {
					continue;
				}
				if (!isWomanDefenseVisibleToViewer(viewer, woman)) {
					continue;
				}

				int severity = getWomanDefenseSeverity(Math.sqrt(distanceSqr));
				exposures.merge(
						viewer.getUUID(),
						new WomanDefenseExposure(severity),
						ServerRaceSystem::pickStrongerWomanDefenseExposure
				);
			}
		}

		for (Map.Entry<UUID, WomanDefenseExposure> entry : exposures.entrySet()) {
			ServerPlayer viewer = server.getPlayerList().getPlayer(entry.getKey());
			if (viewer == null || !viewer.isAlive() || viewer.isSpectator()) {
				continue;
			}
			applyWomanDefenseExposure(viewer, entry.getValue());
		}
	}

	private static WomanDefenseExposure pickStrongerWomanDefenseExposure(WomanDefenseExposure left, WomanDefenseExposure right) {
		if (left == null) {
			return right;
		}
		if (right == null) {
			return left;
		}
		return right.severity() > left.severity() ? right : left;
	}

	private static boolean isWomanDefenseVisibleToViewer(ServerPlayer viewer, ServerPlayer woman) {
		if (viewer == null || woman == null || viewer.level() != woman.level() || !viewer.hasLineOfSight(woman)) {
			return false;
		}

		Vec3 eyePosition = viewer.getEyePosition();
		Vec3 look = viewer.getViewVector(1.0F);
		if (look.lengthSqr() <= 1.0E-6D) {
			return false;
		}

		Vec3 targetPoint = clampPointToAabb(eyePosition, woman.getBoundingBox().inflate(0.15D));
		Vec3 toWoman = targetPoint.subtract(eyePosition);
		return toWoman.lengthSqr() <= 1.0E-6D || look.normalize().dot(toWoman.normalize()) >= 0.0D;
	}

	private static Vec3 clampPointToAabb(Vec3 point, AABB box) {
		if (point == null || box == null) {
			return Vec3.ZERO;
		}
		return new Vec3(
				clampToRange(point.x, box.minX, box.maxX),
				clampToRange(point.y, box.minY, box.maxY),
				clampToRange(point.z, box.minZ, box.maxZ)
		);
	}

	private static double clampToRange(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}

	private static int getWomanDefenseSeverity(double distance) {
		if (distance <= 4.0D) {
			return 4;
		}
		if (distance <= 8.0D) {
			return 3;
		}
		if (distance <= 16.0D) {
			return 2;
		}
		return 1;
	}

	private static void applyWomanDefenseExposure(ServerPlayer viewer, WomanDefenseExposure exposure) {
		if (viewer == null || exposure == null) {
			return;
		}

		int slownessAmplifier = switch (exposure.severity()) {
			case 4, 3 -> 2;
			case 2 -> 1;
			default -> 0;
		};
		refreshWomanDefenseEffect(viewer, MobEffects.SLOWNESS, WOMAN_DEFENSE_EFFECT_REFRESH_TICKS, slownessAmplifier);
		if (exposure.severity() >= 4) {
			refreshWomanDefenseEffect(viewer, MobEffects.BLINDNESS, WOMAN_DEFENSE_EFFECT_REFRESH_TICKS, 0);
		}

		double shakeStrength = switch (exposure.severity()) {
			case 4, 3 -> WOMAN_DEFENSE_STRONG_SHAKE_STRENGTH;
			case 2 -> WOMAN_DEFENSE_MEDIUM_SHAKE_STRENGTH;
			default -> WOMAN_DEFENSE_LIGHT_SHAKE_STRENGTH;
		};
		applyWomanDefenseShake(viewer, shakeStrength);
	}

	private static void refreshWomanDefenseEffect(ServerPlayer viewer, Holder<MobEffect> effect, int durationTicks, int amplifier) {
		if (viewer == null || effect == null || durationTicks <= 0) {
			return;
		}

		MobEffectInstance current = viewer.getEffect(effect);
		if (current != null && current.getAmplifier() > amplifier && current.getDuration() > durationTicks / 2) {
			return;
		}
		if (current != null && current.getAmplifier() == amplifier && current.getDuration() > durationTicks / 2) {
			return;
		}
		viewer.addEffect(new MobEffectInstance(effect, durationTicks, amplifier, false, false, true));
	}

	private static void applyWomanDefenseShake(ServerPlayer viewer, double strength) {
		if (viewer == null || strength <= 0.0D) {
			return;
		}

		Vec3 movement = viewer.getDeltaMovement();
		double angle = viewer.getRandom().nextDouble() * (Math.PI * 2.0D);
		double shakeX = Math.cos(angle) * strength;
		double shakeZ = Math.sin(angle) * strength;
		viewer.setDeltaMovement(movement.x * 0.2D + shakeX, movement.y, movement.z * 0.2D + shakeZ);
		viewer.hurtMarked = true;
		viewer.connection.send(new ClientboundSetEntityMotionPacket(viewer));
	}

	private static void tickWomanAttack(MinecraftServer server) {
		if (server == null) {
			return;
		}
		long nowTick = server.overworld().getGameTime();
		tickWomanAttackCharges(server);
		tickWomanAttackProjectiles(server);
		tickWomanAttackFollows(server, nowTick);
	}

	public static void handleMovePacket(ServerPlayer player) {
		if (player == null) {
			return;
		}
		WomanAttackChargeSession session = WOMAN_ATTACK_CHARGE_SESSIONS.get(player.getUUID());
		if (session != null) {
			syncWomanAttackAirTrigger(player, session);
		}
	}

	private static void tickWomanAttackCharges(MinecraftServer server) {
		if (WOMAN_ATTACK_CHARGE_SESSIONS.isEmpty()) {
			return;
		}

		Iterator<Map.Entry<UUID, WomanAttackChargeSession>> iterator = WOMAN_ATTACK_CHARGE_SESSIONS.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<UUID, WomanAttackChargeSession> entry = iterator.next();
			WomanAttackChargeSession session = entry.getValue();
			ServerPlayer player = session == null ? null : server.getPlayerList().getPlayer(session.playerId);
			if (player == null || !player.isAlive() || player.isSpectator() || !(player.level() instanceof ServerLevel level)) {
				removeWomanAttackAirTrigger(session);
				iterator.remove();
				continue;
			}
			if (!level.dimension().equals(session.dimension)) {
				removeWomanAttackAirTrigger(session);
				iterator.remove();
				continue;
			}

			Vec3 center = player.getEyePosition().add(player.getLookAngle().normalize().scale(WOMAN_ATTACK_CHARGE_FORWARD_OFFSET));
			emitWomanAttackSphereParticles(level, center, session.radius, WOMAN_ATTACK_PARTICLE_COUNT);
			LivingEntity touchedTarget = findWomanAttackChargeTarget(level, player, center, session.radius);
			if (touchedTarget != null) {
				iterator.remove();
				removeWomanAttackAirTrigger(session);
				applyWomanAttackHit(level, player, touchedTarget, session.damage, player.getLookAngle(), session.followTicks, session.range);
				continue;
			}

			syncWomanAttackAirTrigger(player, session);
		}
	}

	private static void tickWomanAttackProjectiles(MinecraftServer server) {
		if (WOMAN_ATTACK_PROJECTILES.isEmpty()) {
			return;
		}

		for (int i = WOMAN_ATTACK_PROJECTILES.size() - 1; i >= 0; i--) {
			WomanAttackProjectile projectile = WOMAN_ATTACK_PROJECTILES.get(i);
			ServerLevel level = projectile == null ? null : server.getLevel(projectile.dimension());
			if (projectile == null || level == null) {
				WOMAN_ATTACK_PROJECTILES.remove(i);
				continue;
			}

			ServerPlayer owner = projectile.ownerId() == null ? null : server.getPlayerList().getPlayer(projectile.ownerId());
			Vec3 start = projectile.position();
			Vec3 next = start.add(projectile.velocity());
			double stepDistance = projectile.velocity().length();
			if (projectile.remainingRange() <= 0.0D || stepDistance <= 1.0E-6D) {
				WOMAN_ATTACK_PROJECTILES.remove(i);
				continue;
			}

			BlockHitResult blockHit = level.clip(new ClipContext(start, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
			Vec3 blockLocation = blockHit.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? null : blockHit.getLocation();

			EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
					level,
					owner,
					start,
					next,
					new AABB(start, next).inflate(projectile.radius()),
					entity -> canWomanAttackHit(owner, entity),
					0.25F
			);

			Vec3 impact = null;
			boolean hitEntity = false;
			if (entityHit != null && entityHit.getEntity() != null) {
				double entityDist = start.distanceToSqr(entityHit.getLocation());
				double blockDist = blockLocation == null ? Double.POSITIVE_INFINITY : start.distanceToSqr(blockLocation);
				if (entityDist <= blockDist) {
					impact = entityHit.getLocation();
					hitEntity = true;
				}
			}
			if (!hitEntity && blockLocation != null) {
				impact = blockLocation;
			}

			Vec3 renderPos = impact == null ? next : impact;
			emitWomanAttackSphereParticles(level, renderPos, projectile.radius(), Math.max(6, WOMAN_ATTACK_PARTICLE_COUNT / 3));

			double traveled = start.distanceTo(renderPos);
			double remaining = Math.max(0.0D, projectile.remainingRange() - traveled);

			if (impact != null) {
				if (hitEntity && entityHit != null && entityHit.getEntity() instanceof LivingEntity target) {
					applyWomanAttackHit(level, owner, target, projectile);
				}
				WOMAN_ATTACK_PROJECTILES.remove(i);
				continue;
			}

			projectile.setPosition(renderPos);
			projectile.setRemainingRange(remaining);
			if (remaining <= 0.0D) {
				WOMAN_ATTACK_PROJECTILES.remove(i);
			}
		}
	}

	private static void tickWomanAttackFollows(MinecraftServer server, long nowTick) {
		if (WOMAN_ATTACK_FOLLOWS.isEmpty()) {
			return;
		}
		if (nowTick % WOMAN_ATTACK_FOLLOW_NAV_INTERVAL_TICKS != 0L) {
			return;
		}

		Iterator<Map.Entry<UUID, WomanAttackFollowSession>> iterator = WOMAN_ATTACK_FOLLOWS.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<UUID, WomanAttackFollowSession> entry = iterator.next();
			WomanAttackFollowSession session = entry.getValue();
			UUID targetId = session == null ? null : session.targetId();
			Entity targetEntity = targetId == null ? null : findEntity(server, targetId);
			if (!(targetEntity instanceof LivingEntity target) || !target.isAlive()) {
				iterator.remove();
				continue;
			}
			if (session == null || nowTick >= session.endTick()) {
				iterator.remove();
				continue;
			}

			if (!(target.level() instanceof ServerLevel level)) {
				iterator.remove();
				continue;
			}
			double range = session.range();
			AABB searchBox = target.getBoundingBox().inflate(range);
			List<Animal> animals = level.getEntitiesOfClass(Animal.class, searchBox, animal -> isWomanAttackTemptable(animal));
			for (Animal animal : animals) {
				if (!animal.isAlive()) {
					continue;
				}
				if (animal.distanceToSqr(target) > range * range) {
					continue;
				}
				PathNavigation navigation = animal.getNavigation();
				if (navigation != null) {
					navigation.moveTo(target, WOMAN_ATTACK_FOLLOW_SPEED);
				}
				animal.getLookControl().setLookAt(target, 30.0F, 30.0F);
			}
		}
	}

	private static InteractionResult tryReleaseWomanAttack(ServerPlayer player, InteractionHand hand) {
		if (player == null || hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}
		WomanAttackChargeSession session = WOMAN_ATTACK_CHARGE_SESSIONS.get(player.getUUID());
		if (session == null) {
			return InteractionResult.PASS;
		}
		if (!(player.level() instanceof ServerLevel level)) {
			WOMAN_ATTACK_CHARGE_SESSIONS.remove(player.getUUID());
			return InteractionResult.PASS;
		}
		if (!level.dimension().equals(session.dimension)) {
			removeWomanAttackAirTrigger(session);
			WOMAN_ATTACK_CHARGE_SESSIONS.remove(player.getUUID());
			return InteractionResult.PASS;
		}

		WOMAN_ATTACK_CHARGE_SESSIONS.remove(player.getUUID());
		removeWomanAttackAirTrigger(session);
		launchWomanAttackProjectile(player, level, session.radius, session.range, session.damage, session.followTicks);
		return InteractionResult.SUCCESS;
	}

	private static void clearWomanAttackState(UUID playerId) {
		if (playerId == null) {
			return;
		}
		removeWomanAttackAirTrigger(WOMAN_ATTACK_CHARGE_SESSIONS.remove(playerId));
		WOMAN_ATTACK_FOLLOWS.remove(playerId);
		for (int i = WOMAN_ATTACK_PROJECTILES.size() - 1; i >= 0; i--) {
			WomanAttackProjectile projectile = WOMAN_ATTACK_PROJECTILES.get(i);
			if (projectile != null && playerId.equals(projectile.ownerId())) {
				WOMAN_ATTACK_PROJECTILES.remove(i);
			}
		}
	}

	private static boolean hasActiveWomanAttackProjectile(UUID playerId) {
		if (playerId == null || WOMAN_ATTACK_PROJECTILES.isEmpty()) {
			return false;
		}
		for (WomanAttackProjectile projectile : WOMAN_ATTACK_PROJECTILES) {
			if (projectile != null && playerId.equals(projectile.ownerId())) {
				return true;
			}
		}
		return false;
	}

	private static void syncWomanAttackAirTrigger(ServerPlayer player, WomanAttackChargeSession session) {
		if (!shouldMaintainWomanAttackAirTrigger(player, session)) {
			removeWomanAttackAirTrigger(session);
			return;
		}
		if (hasWomanAttackAirTriggerObstruction(player, session)) {
			removeWomanAttackAirTrigger(session);
			return;
		}

		Interaction trigger = session.airTriggerEntity;
		if (trigger == null || !trigger.isAlive() || trigger.level() != player.level()) {
			trigger = new Interaction(EntityType.INTERACTION, player.level());
			trigger.setNoGravity(true);
			trigger.setSilent(true);
			trigger.setInvisible(true);
			trigger.setResponse(false);
			trigger.setWidth(WOMAN_ATTACK_AIR_TRIGGER_WIDTH);
			trigger.setHeight(WOMAN_ATTACK_AIR_TRIGGER_HEIGHT);
			player.level().addFreshEntity(trigger);
			session.airTriggerEntity = trigger;
		}

		Vec3 pos = player.getEyePosition()
				.add(player.getLookAngle().normalize().scale(WOMAN_ATTACK_AIR_TRIGGER_HEAD_FORWARD_OFFSET))
				.subtract(0.0D, WOMAN_ATTACK_AIR_TRIGGER_HEIGHT * 0.5D, 0.0D);
		trigger.setInvisible(true);
		trigger.setPos(pos.x, pos.y, pos.z);
		trigger.setDeltaMovement(Vec3.ZERO);
		trigger.setYRot(player.getYRot());
		trigger.setXRot(player.getXRot());
		player.connection.send(ClientboundEntityPositionSyncPacket.of(trigger));
	}

	private static boolean shouldMaintainWomanAttackAirTrigger(ServerPlayer player, WomanAttackChargeSession session) {
		return player != null
				&& session != null
				&& player.isAlive()
				&& !player.isSpectator()
				&& player.getMainHandItem().isEmpty()
				&& player.level().dimension().equals(session.dimension);
	}

	private static boolean hasWomanAttackAirTriggerObstruction(ServerPlayer player, WomanAttackChargeSession session) {
		if (player == null || session == null) {
			return true;
		}
		double reach = Math.max(player.blockInteractionRange(), player.entityInteractionRange()) + 0.5D;
		HitResult hit = player.pick(reach, 1.0F, false);
		if (hit instanceof EntityHitResult entityHit && entityHit.getEntity() == session.airTriggerEntity) {
			return false;
		}
		return hit.getType() != HitResult.Type.MISS;
	}

	private static void removeWomanAttackAirTrigger(WomanAttackChargeSession session) {
		if (session == null || session.airTriggerEntity == null) {
			return;
		}
		session.airTriggerEntity.stopRiding();
		session.airTriggerEntity.discard();
		session.airTriggerEntity = null;
	}

	private static void launchWomanAttackProjectile(
			ServerPlayer player,
			ServerLevel level,
			double radius,
			double range,
			double damage,
			long followTicks
	) {
		Vec3 direction = player.getLookAngle().normalize();
		Vec3 start = player.getEyePosition().add(direction.scale(WOMAN_ATTACK_CHARGE_FORWARD_OFFSET));
		Vec3 velocity = direction.scale(WOMAN_ATTACK_PROJECTILE_SPEED);
		WOMAN_ATTACK_PROJECTILES.add(new WomanAttackProjectile(player.getUUID(), level.dimension(), start, velocity, range, radius, damage, followTicks));
	}

	private static void applyWomanAttackHit(ServerLevel level, ServerPlayer owner, LivingEntity target, WomanAttackProjectile projectile) {
		if (level == null || owner == null || target == null || projectile == null) {
			return;
		}
		applyWomanAttackHit(
				level,
				owner,
				target,
				projectile.damage(),
				projectile.velocity(),
				projectile.followTicks(),
				Math.max(0.0D, projectile.originalRange())
		);
	}

	private static void applyWomanAttackHit(
			ServerLevel level,
			ServerPlayer owner,
			LivingEntity target,
			double damageValue,
			Vec3 knockDirection,
			long followTicks,
			double range
	) {
		if (level == null || owner == null || target == null || knockDirection == null) {
			return;
		}
		float damage = (float) Math.max(0.0D, damageValue);
		target.hurtServer(level, level.damageSources().magic(), damage);

		Vec3 normalizedKnock = knockDirection.lengthSqr() <= 1.0E-6D ? owner.getLookAngle() : knockDirection.normalize();
		Vec3 knock = normalizedKnock.scale(0.4D);
		target.push(knock.x, 0.1D, knock.z);

		long nowTick = level.getGameTime();
		startWomanAttackFollow(target, nowTick, followTicks, Math.max(0.0D, range));
	}

	private static void startWomanAttackFollow(LivingEntity target, long nowTick, long followTicks, double range) {
		if (target == null || followTicks <= 0L) {
			return;
		}
		long endTick = nowTick + followTicks;
		WOMAN_ATTACK_FOLLOWS.merge(
				target.getUUID(),
				new WomanAttackFollowSession(target.getUUID(), target.level().dimension(), endTick, range),
				(existing, replacement) -> new WomanAttackFollowSession(
						existing.targetId(),
						existing.dimension(),
						Math.max(existing.endTick(), replacement.endTick()),
						Math.max(existing.range(), replacement.range())
				)
		);
	}

	private static void emitWomanAttackSphereParticles(ServerLevel level, Vec3 center, double radius, int count) {
		if (level == null || center == null || radius <= 0.0D || count <= 0) {
			return;
		}

		for (int i = 0; i < count; i++) {
			double x = level.random.nextGaussian();
			double y = level.random.nextGaussian();
			double z = level.random.nextGaussian();
			double length = Math.sqrt(x * x + y * y + z * z);
			if (length <= 1.0E-6D) {
				continue;
			}
			double scale = radius * Math.cbrt(level.random.nextDouble());
			double dx = x / length * scale;
			double dy = y / length * scale;
			double dz = z / length * scale;
			level.sendParticles(ParticleTypes.HEART, center.x + dx, center.y + dy, center.z + dz, 1, 0.0D, 0.0D, 0.0D, 0.0D);
		}
	}

	private static LivingEntity findWomanAttackChargeTarget(ServerLevel level, ServerPlayer owner, Vec3 center, double radius) {
		if (level == null || owner == null || center == null || radius <= 0.0D) {
			return null;
		}

		double maxDistanceSqr = radius * radius;
		AABB searchBox = new AABB(center, center).inflate(radius);
		LivingEntity bestTarget = null;
		double bestDistanceSqr = Double.POSITIVE_INFINITY;
		for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, searchBox, entity -> canWomanAttackHit(owner, entity))) {
			double distanceSqr = distanceToAabbSqr(center, target.getBoundingBox());
			if (distanceSqr > maxDistanceSqr || distanceSqr >= bestDistanceSqr) {
				continue;
			}
			bestTarget = target;
			bestDistanceSqr = distanceSqr;
		}
		return bestTarget;
	}

	private static double distanceToAabbSqr(Vec3 point, AABB box) {
		if (point == null || box == null) {
			return Double.POSITIVE_INFINITY;
		}
		double dx = clampDistance(point.x, box.minX, box.maxX);
		double dy = clampDistance(point.y, box.minY, box.maxY);
		double dz = clampDistance(point.z, box.minZ, box.maxZ);
		return dx * dx + dy * dy + dz * dz;
	}

	private static double clampDistance(double value, double min, double max) {
		if (value < min) {
			return min - value;
		}
		if (value > max) {
			return value - max;
		}
		return 0.0D;
	}

	private static boolean canWomanAttackHit(ServerPlayer owner, Entity entity) {
		if (owner == null) {
			return false;
		}
		if (!(entity instanceof LivingEntity target)) {
			return false;
		}
		if (entity instanceof ArmorStand) {
			return false;
		}
		if (!target.isAlive() || target.isSpectator()) {
			return false;
		}
		if (target == owner) {
			return false;
		}
		if (target instanceof ServerPlayer playerTarget) {
			return !owner.isAlliedTo(playerTarget);
		}
		return true;
	}

	private static boolean isWomanAttackTemptable(Animal animal) {
		if (animal == null || !animal.isAlive()) {
			return false;
		}
		if (animal instanceof net.minecraft.world.entity.TamableAnimal) {
			return false;
		}
		for (Item item : WOMAN_ATTACK_TEMPT_ITEMS) {
			if (animal.isFood(new ItemStack(item))) {
				return true;
			}
		}
		return false;
	}

	private static double getWomanDefenseRange(RaceAbilityConfig ability) {
		return positiveOrDefault(ability == null ? 0.0D : ability.activationRangeBlocks, WOMAN_DEFENSE_DEFAULT_RANGE_BLOCKS);
	}

	private static double getWomanDefenseDurationSeconds(RaceAbilityConfig ability) {
		return positiveOrDefault(ability == null ? 0.0D : ability.durationSeconds, WOMAN_DEFENSE_DEFAULT_DURATION_SECONDS);
	}

	private static double getWomanAttackChargeRadius(RaceAbilityConfig ability) {
		return positiveOrDefault(ability == null ? 0.0D : ability.womanAttackChargeRadiusBlocks, WOMAN_ATTACK_DEFAULT_CHARGE_RADIUS_BLOCKS);
	}

	private static double getWomanAttackRange(RaceAbilityConfig ability) {
		return positiveOrDefault(ability == null ? 0.0D : ability.womanAttackRangeBlocks, WOMAN_ATTACK_DEFAULT_RANGE_BLOCKS);
	}

	private static double getWomanAttackDamage(RaceAbilityConfig ability) {
		return positiveOrDefault(ability == null ? 0.0D : ability.womanAttackDamage, WOMAN_ATTACK_DEFAULT_DAMAGE);
	}

	private static double getWomanAttackFollowSeconds(RaceAbilityConfig ability) {
		return positiveOrDefault(ability == null ? 0.0D : ability.womanAttackFollowSeconds, WOMAN_ATTACK_DEFAULT_FOLLOW_SECONDS);
	}

	public static void tryProcessCocaineCauldron(ItemEntity itemEntity) {
		if (itemEntity == null || itemEntity.level().isClientSide()) {
			return;
		}

		ItemStack triggerStack = itemEntity.getItem();
		if (triggerStack.isEmpty() || (!triggerStack.is(Items.BONE_MEAL) && !triggerStack.is(ModItems.DRIED_TRAVKA))) {
			return;
		}

		if (!(itemEntity.level() instanceof ServerLevel level)) {
			return;
		}

		BlockPos cauldronPos = findCocaineCauldronPos(itemEntity, level);
		if (cauldronPos == null) {
			return;
		}

		long gameTime = level.getGameTime();
		if (processedCocaineCauldronTick != gameTime) {
			PROCESSED_COCAINE_CAULDRONS.clear();
			processedCocaineCauldronTick = gameTime;
		}

		CocaineCauldronKey cauldronKey = new CocaineCauldronKey(level.dimension(), cauldronPos.immutable());
		if (!PROCESSED_COCAINE_CAULDRONS.add(cauldronKey)) {
			return;
		}

		BlockState state = level.getBlockState(cauldronPos);
		if (!state.is(Blocks.WATER_CAULDRON)) {
			return;
		}

		int waterLevel = state.getValue(LayeredCauldronBlock.LEVEL);
		if (waterLevel <= 0) {
			return;
		}

		AABB searchBox = createCocaineCauldronSearchBox(cauldronPos);
		List<ItemEntity> nearbyItems = level.getEntitiesOfClass(
				ItemEntity.class,
				searchBox,
				ServerRaceSystem::isCocaineIngredientEntity
		);
		if (nearbyItems.isEmpty()) {
			return;
		}

		int boneMealCount = 0;
		int driedTravkaCount = 0;
		for (ItemEntity nearby : nearbyItems) {
			ItemStack stack = nearby.getItem();
			if (stack.is(Items.BONE_MEAL)) {
				boneMealCount += stack.getCount();
			} else if (stack.is(ModItems.DRIED_TRAVKA)) {
				driedTravkaCount += stack.getCount();
			}
		}

		int craftableCount = Math.min(Math.min(boneMealCount, driedTravkaCount), waterLevel * COCAINE_CAULDRON_BATCH_SIZE);
		if (craftableCount <= 0) {
			return;
		}

		int waterLevelsConsumed = (craftableCount + COCAINE_CAULDRON_BATCH_SIZE - 1) / COCAINE_CAULDRON_BATCH_SIZE;
		consumeCocaineIngredient(nearbyItems, Items.BONE_MEAL, craftableCount);
		consumeCocaineIngredient(nearbyItems, ModItems.DRIED_TRAVKA, craftableCount);
		updateCauldronAfterCocaineCraft(level, cauldronPos, state, waterLevel - waterLevelsConsumed);
		spawnCocaineOutput(level, cauldronPos, craftableCount);
		emitCocaineCauldronParticles(level, cauldronPos, craftableCount);
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

	private static BlockPos findCocaineCauldronPos(ItemEntity itemEntity, ServerLevel level) {
		BlockPos currentPos = itemEntity.blockPosition();
		if (isValidCocaineCauldronPosition(itemEntity, level, currentPos)) {
			return currentPos;
		}

		BlockPos belowPos = currentPos.below();
		if (isValidCocaineCauldronPosition(itemEntity, level, belowPos)) {
			return belowPos;
		}

		return null;
	}

	private static boolean isValidCocaineCauldronPosition(ItemEntity itemEntity, ServerLevel level, BlockPos pos) {
		if (itemEntity == null || level == null || pos == null) {
			return false;
		}

		if (!level.getBlockState(pos).is(Blocks.WATER_CAULDRON)) {
			return false;
		}

		return itemEntity.getBoundingBox().intersects(createCocaineCauldronSearchBox(pos));
	}

	private static AABB createCocaineCauldronSearchBox(BlockPos pos) {
		return new AABB(
				pos.getX() + 0.12D,
				pos.getY() + 0.05D,
				pos.getZ() + 0.12D,
				pos.getX() + 0.88D,
				pos.getY() + 1.15D,
				pos.getZ() + 0.88D
		);
	}

	private static boolean isCocaineIngredientEntity(ItemEntity itemEntity) {
		if (itemEntity == null || itemEntity.isRemoved()) {
			return false;
		}

		ItemStack stack = itemEntity.getItem();
		return !stack.isEmpty() && (stack.is(Items.BONE_MEAL) || stack.is(ModItems.DRIED_TRAVKA));
	}

	private static void consumeCocaineIngredient(List<ItemEntity> nearbyItems, net.minecraft.world.item.Item item, int amount) {
		if (nearbyItems == null || item == null || amount <= 0) {
			return;
		}

		int remaining = amount;
		for (ItemEntity nearby : nearbyItems) {
			if (remaining <= 0) {
				return;
			}

			ItemStack stack = nearby.getItem();
			if (!stack.is(item)) {
				continue;
			}

			int consumed = Math.min(stack.getCount(), remaining);
			if (consumed >= stack.getCount()) {
				nearby.discard();
			} else {
				stack.shrink(consumed);
				nearby.setItem(stack);
			}
			remaining -= consumed;
		}
	}

	private static void updateCauldronAfterCocaineCraft(ServerLevel level, BlockPos pos, BlockState state, int remainingWaterLevel) {
		if (level == null || pos == null || state == null) {
			return;
		}

		if (remainingWaterLevel <= 0) {
			level.setBlockAndUpdate(pos, Blocks.CAULDRON.defaultBlockState());
			return;
		}

		level.setBlockAndUpdate(pos, state.setValue(LayeredCauldronBlock.LEVEL, remainingWaterLevel));
	}

	private static void spawnCocaineOutput(ServerLevel level, BlockPos pos, int count) {
		if (level == null || pos == null || count <= 0) {
			return;
		}

		ItemEntity cocaine = new ItemEntity(
				level,
				pos.getX() + 0.5D,
				pos.getY() + 0.42D,
				pos.getZ() + 0.5D,
				new ItemStack(ModItems.COCAINE, count)
		);
		cocaine.setDeltaMovement(Vec3.ZERO);
		cocaine.setDefaultPickUpDelay();
		level.addFreshEntity(cocaine);
	}

	private static void emitCocaineCauldronParticles(ServerLevel level, BlockPos pos, int craftedCount) {
		if (level == null || pos == null || craftedCount <= 0) {
			return;
		}

		int particleCount = Math.min(32, 8 + craftedCount / 2);
		level.sendParticles(
				ParticleTypes.CLOUD,
				pos.getX() + 0.5D,
				pos.getY() + 0.72D,
				pos.getZ() + 0.5D,
				particleCount,
				0.18D,
				0.12D,
				0.18D,
				0.015D
		);
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
		appendAbilityAction(actions, race, race.attack, "/race use attack");
		appendAbilityAction(actions, race, race.defense, "/race use defense");
		appendAbilityAction(actions, race, race.uniqueAbility, "/race use ability");
		appendAbilityAction(actions, race, race.shnyaga, "/race use shnyaga");

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

	private static void appendAbilityAction(List<Map<String, Object>> actions, PlayerRaceConfig race, RaceAbilityConfig ability, String command) {
		if (ability == null || !ability.enabled) {
			return;
		}

		String fallbackName = preserveBlankAbilityText(race) ? "" : "Ability";
		String label = nonBlank(ability.name, fallbackName);
		String tooltip = nonBlank(ability.description, label);

		Map<String, Object> action = new LinkedHashMap<>();
		action.put("label", textComponent(label));
		action.put("tooltip", textComponent(tooltip));
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

	private static boolean preserveBlankAbilityText(PlayerRaceConfig race) {
		return race != null && NO_RACE_ID.equals(sanitizePath(race.id));
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
			long remainingCooldownTicks = getRemainingOnlineCooldownTicks(CARTEL_DEFENSE_COOLDOWNS, caster.getUUID());
			if (displayRemainingCooldown(caster, remainingCooldownTicks)) {
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

			startOnlineCooldown(CARTEL_DEFENSE_COOLDOWNS, caster.getUUID(), cooldownTicks);

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
			long remainingCooldownTicks = getRemainingOnlineCooldownTicks(CARTEL_UNIQUE_COOLDOWNS, caster.getUUID());
			if (displayRemainingCooldown(caster, remainingCooldownTicks)) {
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

	private static int useMrCartelShnyaga(ServerPlayer caster, PlayerRaceConfig race, RaceAbilityConfig ability) {
		try {
			return openCartelShnyagaBook(caster, buildCartelShnyagaBook(caster)) ? 1 : 0;
		} catch (Exception exception) {
			Lg2.LOGGER.error("Failed to open mister cartel shnyaga manual for {}", caster.getGameProfile().name(), exception);
			return 0;
		}
	}

	private static boolean openCartelShnyagaBook(ServerPlayer player, ItemStack book) {
		if (player == null || book == null || book.isEmpty()) {
			return false;
		}

		AbstractContainerMenu menu = player.inventoryMenu;
		Inventory inventory = player.getInventory();
		int inventorySlot = inventory.getSelectedSlot();
		int menuSlot = findInventoryMenuSlot(menu, inventory, inventorySlot);
		if (menu == null || menuSlot < 0) {
			return false;
		}

		int stateId = menu.incrementStateId();
		player.connection.send(new ClientboundContainerSetSlotPacket(
				menu.containerId,
				stateId,
				menuSlot,
				book.copy()
		));
		player.connection.send(new ClientboundSetEquipmentPacket(
				player.getId(),
				List.of(com.mojang.datafixers.util.Pair.of(EquipmentSlot.MAINHAND, book.copy()))
		));
		player.connection.send(new ClientboundOpenBookPacket(InteractionHand.MAIN_HAND));
		CARTEL_MANUAL_BOOK_RESTORES.put(
				player.getUUID(),
				new CartelManualBookRestore(inventorySlot, menuSlot, player.level().getGameTime() + 1L)
		);
		return true;
	}

	private static int findInventoryMenuSlot(AbstractContainerMenu menu, Inventory inventory, int inventorySlot) {
		if (menu == null || inventory == null || inventorySlot < 0) {
			return -1;
		}

		for (int menuSlot = 0; menuSlot < menu.slots.size(); menuSlot++) {
			Slot slot = menu.getSlot(menuSlot);
			if (slot.container == inventory && slot.getContainerSlot() == inventorySlot) {
				return menuSlot;
			}
		}
		return -1;
	}

	private static void tickCartelManualBookRestores(MinecraftServer server) {
		if (server == null || CARTEL_MANUAL_BOOK_RESTORES.isEmpty()) {
			return;
		}

		long nowTick = server.overworld().getGameTime();
		CARTEL_MANUAL_BOOK_RESTORES.entrySet().removeIf(entry -> {
			CartelManualBookRestore restore = entry.getValue();
			if (restore == null || nowTick < restore.restoreAtTick) {
				return false;
			}

			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (player == null) {
				return true;
			}

			AbstractContainerMenu menu = player.inventoryMenu;
			Inventory inventory = player.getInventory();
			ItemStack actualStack = inventory.getItem(restore.inventorySlot).copy();
			int stateId = menu.incrementStateId();
			player.connection.send(new ClientboundContainerSetSlotPacket(
					menu.containerId,
					stateId,
					restore.menuSlot,
					actualStack
			));
			player.connection.send(new ClientboundSetEquipmentPacket(
					player.getId(),
					List.of(com.mojang.datafixers.util.Pair.of(EquipmentSlot.MAINHAND, player.getMainHandItem().copy()))
			));
			return true;
		});
	}

	private static ItemStack buildCartelShnyagaBook(ServerPlayer player) {
		ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
		book.set(DataComponents.WRITTEN_BOOK_CONTENT, buildCartelShnyagaBookContent(player));
		return book;
	}

	private static WrittenBookContent buildCartelShnyagaBookContent(ServerPlayer player) {
		List<Filterable<Component>> pages = new ArrayList<>();
		boolean hasPack = PolymerResourcePackUtils.hasMainPack(player);
		for (int i = 0; i < CARTEL_MANUAL_PAGE_GLYPHS.length; i++) {
			pages.add(Filterable.passThrough(buildCartelShnyagaPage(player, i, hasPack)));
		}

		return new WrittenBookContent(
				Filterable.passThrough(localizeCartelShnyagaBookTitle(player)),
				localizeCartelShnyagaBookAuthor(player),
				0,
				pages,
				true
		);
	}

	private static Component buildCartelShnyagaPage(ServerPlayer player, int pageIndex, boolean hasPack) {
		MutableComponent page = Component.empty();
		if (hasPack && pageIndex >= 0 && pageIndex < CARTEL_MANUAL_PAGE_GLYPHS.length) {
			page = page.append(Component.literal(CARTEL_MANUAL_PAGE_GLYPHS[pageIndex])
					.withStyle(style -> style.withColor(0xFFFFFF).withItalic(false).withFont(CARTEL_MANUAL_PAGE_FONT)));
			page = page.append(Component.literal("\n"));
		}

		page = page.append(Component.literal(localizeCartelShnyagaPageTitle(player, pageIndex))
				.withStyle(style -> style.withBold(true).withItalic(false).withColor(0x5B3118)));
		page = page.append(Component.literal("\n"));
		page = page.append(Component.literal(localizeCartelShnyagaPageBody(player, pageIndex))
				.withStyle(style -> style.withItalic(false).withColor(0x2E2016)));
		return page;
	}

	private static String localizeCartelShnyagaBookTitle(ServerPlayer player) {
		return switch (cartelBookLanguage(player)) {
			case RPR -> "\u0422\u0430\u0439\u043D\u043E\u0441\u0442\u0438 \u043A\u0430\u0440\u0442\u0435\u043B\u044C\u043D\u044B\u044F";
			case UK -> "\u041F\u0430\u043C'\u044F\u0442\u043A\u0430 \u041A\u0430\u0440\u0442\u0435\u043B\u044E";
			case JA -> "\u30AB\u30EB\u30C6\u30EB\u306E\u624B\u5F15\u304D";
			case EN -> "Cartel Manual";
			case RU -> "\u041F\u0430\u043C\u044F\u0442\u043A\u0430 \u041A\u0430\u0440\u0442\u0435\u043B\u044F";
		};
	}

	private static String localizeCartelShnyagaBookAuthor(ServerPlayer player) {
		return switch (cartelBookLanguage(player)) {
			case RPR -> "\u041A\u0430\u0440\u0442\u0435\u043B\u044C 49";
			case UK -> "\u041A\u0430\u0440\u0442\u0435\u043B\u044C 49";
			case JA -> "\u30AB\u30EB\u30C6\u30EB49";
			case EN -> "Cartel 49";
			case RU -> "\u041A\u0430\u0440\u0442\u0435\u043B\u044C 49";
		};
	}

	private static String localizeCartelShnyagaPageTitle(ServerPlayer player, int pageIndex) {
		return switch (cartelBookLanguage(player)) {
			case RPR -> localizeCartelShnyagaPageTitleRpr(pageIndex);
			case UK -> localizeCartelShnyagaPageTitleUk(pageIndex);
			case JA -> localizeCartelShnyagaPageTitleJa(pageIndex);
			case EN -> localizeCartelShnyagaPageTitleEn(pageIndex);
			case RU -> localizeCartelShnyagaPageTitleRu(pageIndex);
		};
	}

	private static String localizeCartelShnyagaPageBody(ServerPlayer player, int pageIndex) {
		return switch (cartelBookLanguage(player)) {
			case RPR -> localizeCartelShnyagaPageBodyRpr(pageIndex);
			case UK -> localizeCartelShnyagaPageBodyUk(pageIndex);
			case JA -> localizeCartelShnyagaPageBodyJa(pageIndex);
			case EN -> localizeCartelShnyagaPageBodyEn(pageIndex);
			case RU -> localizeCartelShnyagaPageBodyRu(pageIndex);
		};
	}

	private static CartelBookLanguage cartelBookLanguage(ServerPlayer player) {
		if (player == null || player.clientInformation() == null || player.clientInformation().language() == null) {
			return CartelBookLanguage.EN;
		}

		String normalized = player.clientInformation().language().toLowerCase(Locale.ROOT);
		if (normalized.startsWith("rpr")) {
			return CartelBookLanguage.RPR;
		}
		if (normalized.startsWith("uk")) {
			return CartelBookLanguage.UK;
		}
		if (normalized.startsWith("ru")) {
			return CartelBookLanguage.RU;
		}
		if (normalized.startsWith("ja")) {
			return CartelBookLanguage.JA;
		}
		return CartelBookLanguage.EN;
	}

	private static String localizeCartelShnyagaPageTitleRu(int pageIndex) {
		return switch (pageIndex) {
			case 0 -> "\u0421\u0435\u043A\u0440\u0435\u0442\u043D\u043E\u0441\u0442\u044C";
			case 1 -> "\u0422\u0440\u0430\u0432\u043A\u0430";
			case 2 -> "\u0421\u0443\u0448\u043A\u0430";
			case 3 -> "\u041A\u043E\u0441\u044F\u0447\u043E\u043A";
			case 4 -> "\u041A\u043E\u043A\u0430\u0438\u043D";
			case 5 -> "\u041C\u0435\u0442\u0430\u0434\u043E\u043D";
			default -> "Раздел";
		};
	}

	private static String localizeCartelShnyagaPageBodyRu(int pageIndex) {
		return switch (pageIndex) {
			case 0 -> "\u0414\u0435\u0440\u0436\u0438 \u0440\u0435\u0446\u0435\u043F\u0442\u044B \u0432 \u0442\u0430\u0439\u043D\u0435.\n\u0412\u0441\u0435 \u044D\u0442\u0438 \u0432\u0430\u0440\u043A\u0438 \u0438 \u043A\u0440\u0430\u0444\u0442\u044B\n\u0434\u043E\u0441\u0442\u0443\u043F\u043D\u044B \u043A\u0430\u0436\u0434\u043E\u043C\u0443, \u043A\u0442\u043E\n\u0443\u0437\u043D\u0430\u0435\u0442 \u0441\u0445\u0435\u043C\u0443.";
			case 1 -> "\u0421\u0430\u043C \u043F\u043E\u0441\u0430\u0434\u0438 \u043C\u0430\u043B\u044B\u0439\n\u043F\u0430\u043F\u043E\u0440\u043E\u0442\u043D\u0438\u043A.\n\u041E\u043D \u0432\u044B\u0440\u0430\u0441\u0442\u0435\u0442 \u0432 \u0431\u043E\u043B\u044C\u0448\u043E\u0439.\n\u0420\u043E\u0441\u0442 \u0438 \u043A\u043E\u0441\u0442\u043D\u0430\u044F \u043C\u0443\u043A\u0430\n\u043C\u043E\u0433\u0443\u0442 \u0443\u0440\u043E\u043D\u0438\u0442\u044C \u0422\u0440\u0430\u0432\u043A\u0443.";
			case 2 -> "\u041F\u0435\u0440\u0435\u0436\u0430\u0440\u044C \u0422\u0440\u0430\u0432\u043A\u0443 \u0432\n\u043F\u0435\u0447\u0438 \u0438\u043B\u0438 \u043A\u043E\u043F\u0442\u0438\u043B\u044C\u043D\u0435.\n\u041F\u043E\u043B\u0443\u0447\u0438\u0448\u044C \u0421\u0443\u0448\u0451\u043D\u0443\u044E\n\u0442\u0440\u0430\u0432\u043A\u0443.";
			case 3 -> "\u0412\u0435\u0440\u0441\u0442\u0430\u043A 3x3:\n\u0432\u0435\u0440\u0445 3 \u0431\u0443\u043C\u0430\u0433\u0438,\n\u0441\u0435\u0440\u0435\u0434\u0438\u043D\u0430 3 \u0441\u0443\u0448\u0451\u043D\u043E\u0439\n\u0442\u0440\u0430\u0432\u043A\u0438, \u043D\u0438\u0437 3 \u0431\u0443\u043C\u0430\u0433\u0438.\n\u0412\u044B\u0445\u043E\u0434: 3 \u041A\u043E\u0441\u044F\u0447\u043A\u0430.";
			case 4 -> "\u0412 \u043A\u043E\u0442\u0451\u043B \u0441 \u0432\u043E\u0434\u043E\u0439 \u043A\u0438\u0434\u0430\u0439\n\u0441\u0443\u0448\u0451\u043D\u0443\u044E \u0442\u0440\u0430\u0432\u043A\u0443 \u0438\n\u043A\u043E\u0441\u0442\u043D\u0443\u044E \u043C\u0443\u043A\u0443 1 \u043A 1.\n\u041A\u0430\u0436\u0434\u044B\u0435 16 \u0448\u0442\u0443\u043A\n\u0441\u044A\u0435\u0434\u0430\u044E\u0442 1/3 \u0432\u043E\u0434\u044B.";
			case 5 -> "\u0412 \u0432\u0430\u0440\u043E\u0447\u043D\u043E\u0439 \u0441\u0442\u043E\u0439\u043A\u0435:\nMundane Potion +\n1 \u041A\u043E\u043A\u0430\u0438\u043D.\n\u0412\u044B\u0445\u043E\u0434: \u041C\u0435\u0442\u0430\u0434\u043E\u043D.\n\u0420\u0435\u0446\u0435\u043F\u0442 \u0442\u043E\u0436\u0435 \u0437\u043D\u0430\u044E\u0442 \u0432\u0441\u0435.";
			default -> "";
		};
	}

	private static String localizeCartelShnyagaPageTitleEn(int pageIndex) {
		return switch (pageIndex) {
			case 0 -> "Secrecy";
			case 1 -> "Travka";
			case 2 -> "Drying";
			case 3 -> "Joint";
			case 4 -> "Cocaine";
			case 5 -> "Methadone";
			default -> "Manual";
		};
	}

	private static String localizeCartelShnyagaPageBodyEn(int pageIndex) {
		return switch (pageIndex) {
			case 0 -> "Keep every recipe secret.\nAll of these crafts are\navailable to anyone who\nlearns the method.";
			case 1 -> "Plant a small fern\nyourself.\nIt grows into a large fern.\nGrowth and bone meal\ncan drop Travka.";
			case 2 -> "Smelt Travka in a\nfurnace or smoker.\nYou get Dried Travka.";
			case 3 -> "3x3 crafting:\n3 paper on top,\n3 dried travka in the\nmiddle, 3 paper below.\nOutput: 3 Joints.";
			case 4 -> "Water cauldron:\ndried travka + bone meal\nat 1 to 1.\nEvery 16 pieces use\n1/3 of the water.";
			case 5 -> "Brewing stand:\nMundane Potion +\n1 Cocaine.\nOutput: Methadone.\nEveryone can brew it.";
			default -> "";
		};
	}

	private static String localizeCartelShnyagaPageTitleUk(int pageIndex) {
		return switch (pageIndex) {
			case 0 -> "\u0422\u0430\u0454\u043C\u043D\u0438\u0446\u044F";
			case 1 -> "\u0422\u0440\u0430\u0432\u043A\u0430";
			case 2 -> "\u0421\u0443\u0448\u043A\u0430";
			case 3 -> "\u041A\u043E\u0441\u044F\u0447\u043E\u043A";
			case 4 -> "\u041A\u043E\u043A\u0430\u0457\u043D";
			case 5 -> "\u041C\u0435\u0442\u0430\u0434\u043E\u043D";
			default -> "Розділ";
		};
	}

	private static String localizeCartelShnyagaPageBodyUk(int pageIndex) {
		return switch (pageIndex) {
			case 0 -> "\u0422\u0440\u0438\u043C\u0430\u0439 \u0440\u0435\u0446\u0435\u043F\u0442\u0438 \u0432 \u0442\u0430\u0454\u043C\u043D\u0438\u0446\u0456.\n\u0423\u0441\u0456 \u0446\u0456 \u0432\u0430\u0440\u043A\u0438 \u0439 \u043A\u0440\u0430\u0444\u0442\u0438\n\u0434\u043E\u0441\u0442\u0443\u043F\u043D\u0456 \u043A\u043E\u0436\u043D\u043E\u043C\u0443, \u0445\u0442\u043E\n\u0434\u0456\u0437\u043D\u0430\u0454\u0442\u044C\u0441\u044F \u0441\u0445\u0435\u043C\u0443.";
			case 1 -> "\u0421\u0430\u043C \u043F\u043E\u0441\u0430\u0434\u0438 \u043C\u0430\u043B\u0443\n\u043F\u0430\u043F\u043E\u0440\u043E\u0442\u044C.\n\u0412\u043E\u043D\u0430 \u0432\u0438\u0440\u043E\u0441\u0442\u0435 \u0443 \u0432\u0435\u043B\u0438\u043A\u0443.\n\u0420\u0456\u0441\u0442 \u0456 \u043A\u0456\u0441\u0442\u043A\u043E\u0432\u0435 \u0431\u043E\u0440\u043E\u0448\u043D\u043E\n\u043C\u043E\u0436\u0443\u0442\u044C \u0434\u0430\u0442\u0438 \u0422\u0440\u0430\u0432\u043A\u0443.";
			case 2 -> "\u041F\u0435\u0440\u0435\u043F\u043B\u0430\u0432 \u0422\u0440\u0430\u0432\u043A\u0443 \u0432\n\u043F\u0435\u0447\u0456 \u0430\u0431\u043E \u043A\u043E\u043F\u0442\u0438\u043B\u044C\u043D\u0456.\n\u041E\u0442\u0440\u0438\u043C\u0430\u0454\u0448 \u0421\u0443\u0448\u0435\u043D\u0443\n\u0442\u0440\u0430\u0432\u043A\u0443.";
			case 3 -> "\u0412\u0435\u0440\u0441\u0442\u0430\u043A 3x3:\n\u0432\u0435\u0440\u0445 3 \u043F\u0430\u043F\u0435\u0440\u0443,\n\u0441\u0435\u0440\u0435\u0434\u0438\u043D\u0430 3 \u0441\u0443\u0448\u0435\u043D\u043E\u0457\n\u0442\u0440\u0430\u0432\u043A\u0438, \u043D\u0438\u0437 3 \u043F\u0430\u043F\u0435\u0440\u0443.\n\u0412\u0438\u0445\u0456\u0434: 3 \u041A\u043E\u0441\u044F\u0447\u043A\u0438.";
			case 4 -> "\u0423 \u043A\u0430\u0437\u0430\u043D \u0437 \u0432\u043E\u0434\u043E\u044E \u043A\u0438\u0434\u0430\u0439\n\u0441\u0443\u0448\u0435\u043D\u0443 \u0442\u0440\u0430\u0432\u043A\u0443 \u0442\u0430\n\u043A\u0456\u0441\u0442\u043A\u043E\u0432\u0435 \u0431\u043E\u0440\u043E\u0448\u043D\u043E 1 \u0434\u043E 1.\n\u041A\u043E\u0436\u043D\u0456 16 \u0448\u0442\u0443\u043A\n\u0437\u0430\u0431\u0438\u0440\u0430\u044E\u0442\u044C 1/3 \u0432\u043E\u0434\u0438.";
			case 5 -> "\u0423 \u0432\u0430\u0440\u0438\u043B\u044C\u043D\u0456\u0439 \u0441\u0442\u0456\u0439\u0446\u0456:\nMundane Potion +\n1 \u041A\u043E\u043A\u0430\u0457\u043D.\n\u0412\u0438\u0445\u0456\u0434: \u041C\u0435\u0442\u0430\u0434\u043E\u043D.\n\u0420\u0435\u0446\u0435\u043F\u0442 \u0437\u043D\u0430\u044E\u0442\u044C \u0443\u0441\u0456.";
			default -> "";
		};
	}

	private static String localizeCartelShnyagaPageTitleJa(int pageIndex) {
		return switch (pageIndex) {
			case 0 -> "\u79D8\u5BC6";
			case 1 -> "\u30C8\u30E9\u30D5\u30AB";
			case 2 -> "\u4E7E\u71E5";
			case 3 -> "\u30B8\u30E7\u30A4\u30F3\u30C8";
			case 4 -> "\u30B3\u30AB\u30A4\u30F3";
			case 5 -> "\u30E1\u30BF\u30C9\u30F3";
			default -> "???";
		};
	}

	private static String localizeCartelShnyagaPageBodyJa(int pageIndex) {
		return switch (pageIndex) {
			case 0 -> "\u914D\u5408\u306F\u79D8\u5BC6\u306B\u3057\u308D\u3002\n\u4F5C\u308A\u65B9\u3092\u77E5\u308C\u3070\n\u8AB0\u3067\u3082\u540C\u3058\u7269\u3092\n\u4F5C\u308C\u3066\u3057\u307E\u3046\u3002";
			case 1 -> "\u5C0F\u3055\u306A\u30B7\u30C0\u3092\n\u81EA\u5206\u3067\u690D\u3048\u308B\u3002\n\u3084\u304C\u3066\u5927\u304D\u306A\u30B7\u30C0\u306B\u80B2\u3061\u3001\n\u6210\u9577\u6642\u3084\u9AA8\u7C89\u3067\n\u30C8\u30E9\u30D5\u30AB\u304C\u843D\u3061\u308B\u3002";
			case 2 -> "\u30C8\u30E9\u30D5\u30AB\u3092\n\u304B\u307E\u3069\u304B\u71FB\u88FD\u5668\u3067\u713C\u304F\u3002\n\u4E7E\u71E5\u30C8\u30E9\u30D5\u30AB\u306B\u306A\u308B\u3002";
			case 3 -> "\u4F5C\u696D\u53F03x3:\n\u4E0A\u306B\u7D193\u3001\u4E2D\u592E\u306B\n\u4E7E\u71E5\u30C8\u30E9\u30D5\u30AB3\u3001\u4E0B\u306B\u7D193\u3002\n\u7D50\u679C\u306F\u30B8\u30E7\u30A4\u30F3\u30C83\u672C\u3002";
			case 4 -> "\u6C34\u5165\u308A\u5927\u91DC\u3078\n\u4E7E\u71E5\u30C8\u30E9\u30D5\u30AB\u3068\u9AA8\u7C89\u3092\n1\u5BFE1\u3067\u5165\u308C\u308B\u3002\n16\u500B\u3054\u3068\u306B\u6C34\u3092\n1/3\u4F7F\u3046\u3002";
			case 5 -> "\u91B8\u9020\u53F0\u3067\nMundane Potion \u306B\n\u30B3\u30AB\u30A4\u30F31\u500B\u3002\n\u7D50\u679C\u306F\u30E1\u30BF\u30C9\u30F3\u3002\n\u8AB0\u3067\u3082\u4F5C\u308C\u308B\u3002";
			default -> "";
		};
	}

	private static String localizeCartelShnyagaPageTitleRpr(int pageIndex) {
		return switch (pageIndex) {
			case 0 -> "\u0422\u0430\u0439\u043D\u0430";
			case 1 -> "\u0422\u0440\u0430\u0432\u0443\u0448\u043A\u0430";
			case 2 -> "\u0421\u0443\u0448\u043A\u0430";
			case 3 -> "\u041A\u0443\u0440\u0435\u0432\u043E";
			case 4 -> "\u041F\u0440\u0430\u0445\u044A";
			case 5 -> "\u0414\u0440\u0435\u043C\u0430\u0442\u0438\u043D\u044A";
			default -> "Раздѣл";
		};
	}

	private static String localizeCartelShnyagaPageBodyRpr(int pageIndex) {
		return switch (pageIndex) {
			case 0 -> "\u0425\u0440\u0430\u043D\u0438 \u0440\u0435\u0446\u0435\u043F\u0442\u044B \u0432\u044A \u0442\u0430\u0439\u043D\u0435.\n\u0421\u0438\u0438 \u0432\u0430\u0440\u043A\u0438 \u0438 \u043A\u0440\u0430\u0444\u0442\u044B\n\u0434\u043E\u0441\u0442\u0443\u043F\u043D\u044B \u0432\u0441\u044F\u043A\u043E\u043C\u0443,\n\u043A\u0442\u043E \u0441\u0445\u0435\u043C\u0443 \u043F\u043E\u0437\u043D\u0430\u0435\u0442\u044A.";
			case 1 -> "\u0421\u0430\u043C\u044A \u043D\u0430\u0441\u0430\u0434\u0438 \u043C\u0430\u043B\u044B\u0439\n\u043F\u0430\u043F\u043E\u0440\u043E\u0442\u043D\u0438\u043A\u044A.\n\u0412\u043E\u0437\u0440\u0430\u0441\u0442\u0435\u0442\u044A \u0432\u044A \u0432\u0435\u043B\u0438\u043A\u0456\u0439.\n\u0420\u043E\u0441\u0442\u044A \u0438 \u043A\u043E\u0441\u0442\u043D\u0430\u044F \u043C\u0443\u043A\u0430\n\u043C\u043E\u0433\u0443\u0442\u044A \u0434\u0430\u0442\u044C \u0422\u0440\u0430\u0432\u0443\u0448\u043A\u0443.";
			case 2 -> "\u041F\u0435\u0440\u0435\u0436\u0430\u0440\u044C \u0422\u0440\u0430\u0432\u0443\u0448\u043A\u0443 \u0432\u044A\n\u043F\u0435\u0447\u0438 \u043B\u0438\u0431\u043E \u043A\u043E\u043F\u0442\u0438\u043B\u044C\u043D\u0435.\n\u041F\u043E\u043B\u0443\u0447\u0438\u0448\u044C \u0421\u0443\u0448\u0451\u043D\u043D\u0443\u044E\n\u0442\u0440\u0430\u0432\u0443\u0448\u043A\u0443-\u043C\u0443\u0440\u0430\u0432\u0443\u0448\u043A\u0443.";
			case 3 -> "\u0412\u0435\u0440\u0441\u0442\u0430\u043A\u044A 3x3:\n\u0441\u0432\u0435\u0440\u0445\u0443 3 \u0431\u0443\u043C\u0430\u0433\u0438,\n\u043F\u043E\u0441\u0440\u0435\u0434\u0438 3 \u0441\u0443\u0448\u0451\u043D\u043D\u043E\u0439\n\u0442\u0440\u0430\u0432\u0443\u0448\u043A\u0438, \u0441\u043D\u0438\u0437\u0443 3 \u0431\u0443\u043C\u0430\u0433\u0438.\n\u0412\u044B\u0445\u043E\u0434\u044A: 3 \u041A\u0443\u0440\u0435\u0432\u0430.";
			case 4 -> "\u0412\u043E \u043A\u043E\u0442\u0451\u043B\u044A \u0441\u044A \u0432\u043E\u0434\u043E\u044E\n\u043C\u0435\u0447\u0438 \u0441\u0443\u0448\u0451\u043D\u043D\u0443\u044E \u0442\u0440\u0430\u0432\u0443\u0448\u043A\u0443\n\u0438 \u043A\u043E\u0441\u0442\u043D\u0443\u044E \u043C\u0443\u043A\u0443 1 \u043A 1.\n\u041A\u0430\u0436\u0434\u044B\u044F 16 \u0448\u0442\u0443\u043A\u0438\n\u0441\u044A\u0435\u0434\u0430\u044E\u0442\u044A 1/3 \u0432\u043E\u0434\u044B.";
			case 5 -> "\u0412\u043E \u0432\u0430\u0440\u043E\u0447\u043D\u043E\u0439 \u0441\u0442\u043E\u0439\u043A\u0435:\nMundane Potion +\n1 \u041F\u0440\u0430\u0445\u044A.\n\u0412\u044B\u0445\u043E\u0434\u044A: \u0414\u0440\u0435\u043C\u0430\u0442\u0438\u043D\u044A.\n\u0420\u0435\u0446\u0435\u043F\u0442\u044A \u0432\u0441\u0435\u043C\u044A \u0432\u0435\u0434\u043E\u043C\u044A.";
			default -> "";
		};
	}

	private enum CartelBookLanguage {
		RPR,
		UK,
		JA,
		EN,
		RU
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
		CartelWebcamBridge.beginDisguise(caster.getUUID(), target.getUUID());
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
		startOnlineCooldown(CARTEL_UNIQUE_COOLDOWNS, caster.getUUID(), cooldownTicks);

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
		tickOnlineCooldowns(server, CARTEL_UNIQUE_COOLDOWNS);
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
				CartelWebcamBridge.endDisguise(entry.getKey());
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
			CartelWebcamBridge.endDisguise(player.getUUID());
			return;
		}
		MinecraftServer server = player.level().getServer();
		if (server != null) {
			restoreCartelDisguise(server, player, session);
		}
	}

	private static void prewarmCopperManDefenseTints(MinecraftServer server) {
		if (server == null) {
			return;
		}

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			prewarmCopperManDefenseTint(server, player);
		}
	}

	private static void prewarmCopperManDefenseTint(MinecraftServer server, ServerPlayer player) {
		if (server == null || !shouldPrewarmCopperManDefenseTint(player)) {
			return;
		}

		SkinValue currentSkin = captureCurrentSkinValue(player);
		if (currentSkin == null || currentSkin.value() == null) {
			return;
		}

		String sourceCacheKey = getCopperManDefenseTintSourceCacheKey(currentSkin.value());
		queueCopperManDefenseTintBuild(server, player.getScoreboardName(), currentSkin.value(), sourceCacheKey);
	}

	private static boolean shouldPrewarmCopperManDefenseTint(ServerPlayer player) {
		if (player == null || !player.isAlive() || player.isSpectator() || COPPER_MAN_DEFENSE_VISUAL_SESSIONS.containsKey(player.getUUID())) {
			return false;
		}

		Optional<PlayerRaceConfig> raceOptional = getRace(player);
		if (raceOptional.isEmpty()) {
			return false;
		}

		PlayerRaceConfig race = raceOptional.get();
		return COPPER_MAN_RACE_ID.equals(sanitizePath(race.id)) && hasUnlockedAbility(player, race, RaceAbilitySlot.DEFENSE);
	}

	private static void startCopperManDefenseVisual(MinecraftServer server, ServerPlayer player, long expireTick) {
		if (server == null || player == null) {
			return;
		}

		CopperManDefenseVisualSession existing = COPPER_MAN_DEFENSE_VISUAL_SESSIONS.get(player.getUUID());
		SkinValue originalSkin = existing != null && existing.originalSkin() != null ? existing.originalSkin() : captureCurrentSkinValue(player);
		if (originalSkin == null || originalSkin.value() == null) {
			return;
		}

		String sourceCacheKey = getCopperManDefenseTintSourceCacheKey(originalSkin.value());
		CopperManDefenseVisualSession session = new CopperManDefenseVisualSession(originalSkin, sourceCacheKey, expireTick);
		COPPER_MAN_DEFENSE_VISUAL_SESSIONS.put(player.getUUID(), session);
		ensureCopperManDefenseVisualApplied(server, player, session);
	}

	private static void ensureCopperManDefenseVisualApplied(MinecraftServer server, ServerPlayer player, CopperManDefenseVisualSession session) {
		if (server == null || player == null || session == null || session.originalSkin() == null || session.originalSkin().value() == null) {
			return;
		}

		SkinValue storedSkin = captureStoredSkinValue(player);
		if (storedSkin != null
				&& storedSkin.value() != null
				&& !isSameSkinProperty(storedSkin.value(), session.originalSkin().value())) {
			session = new CopperManDefenseVisualSession(
					storedSkin,
					getCopperManDefenseTintSourceCacheKey(storedSkin.value()),
					session.expireTick()
			);
			COPPER_MAN_DEFENSE_VISUAL_SESSIONS.put(player.getUUID(), session);
			if (!isPlayerUsingSkin(player, storedSkin.value())) {
				applySkin(server, player, storedSkin);
			}
		}

		Property tintedProperty = COPPER_MAN_DEFENSE_TINT_CACHE.get(session.sourceCacheKey());
		if (tintedProperty == null) {
			queueCopperManDefenseTintBuild(server, player.getScoreboardName(), session.originalSkin().value(), session.sourceCacheKey());
			return;
		}

		if (isPlayerUsingSkin(player, tintedProperty)) {
			return;
		}

		SkinVariant variant = resolveSkinVariant(tintedProperty);
		applySkin(server, player, new SkinValue("lg2_copper_defense_tint", player.getScoreboardName(), variant, tintedProperty, tintedProperty));
	}

	private static void restoreAllCopperManDefenseVisuals(MinecraftServer server) {
		if (server == null || COPPER_MAN_DEFENSE_VISUAL_SESSIONS.isEmpty()) {
			return;
		}

		for (Map.Entry<UUID, CopperManDefenseVisualSession> entry : new ArrayList<>(COPPER_MAN_DEFENSE_VISUAL_SESSIONS.entrySet())) {
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (player != null) {
				restoreCopperManDefenseVisual(server, player, entry.getValue());
			}
		}
		COPPER_MAN_DEFENSE_VISUAL_SESSIONS.clear();
	}

	private static void clearCopperManDefenseVisual(ServerPlayer player) {
		if (player == null) {
			return;
		}

		CopperManDefenseVisualSession session = COPPER_MAN_DEFENSE_VISUAL_SESSIONS.remove(player.getUUID());
		if (session == null) {
			return;
		}

		MinecraftServer server = player.level().getServer();
		if (server != null) {
			restoreCopperManDefenseVisual(server, player, session);
		}
	}

	private static void restoreCopperManDefenseVisual(MinecraftServer server, ServerPlayer player, CopperManDefenseVisualSession session) {
		if (server == null || player == null || session == null || session.originalSkin() == null) {
			return;
		}
		if (isPlayerUsingSkin(player, session.originalSkin().value())) {
			return;
		}
		applySkin(server, player, session.originalSkin());
	}

	private static String getCopperManDefenseTintSourceCacheKey(Property sourceSkin) {
		return getCopperManDefenseTintSourceCacheKey(sourceSkin, COPPER_MAN_DEFENSE_TINT_CACHE_VERSION);
	}

	private static String getCopperManDefenseTintSourceCacheKey(Property sourceSkin, String cacheVersion) {
		if (sourceSkin == null) {
			return "";
		}
		Pair<String, SkinVariant> skinData = PlayerUtils.getSkinUrl(sourceSkin);
		String skinUrl = skinData != null && skinData.first() != null && !skinData.first().isBlank()
				? skinData.first()
				: sourceSkin.value();
		String variant = skinData != null && skinData.second() != null ? skinData.second().name() : SkinVariant.CLASSIC.name();
		return skinUrl + "|" + variant + "|" + cacheVersion;
	}

	private static void queueCopperManDefenseTintBuild(MinecraftServer server, String playerName, Property sourceSkin, String sourceCacheKey) {
		if (sourceSkin == null || sourceCacheKey == null || sourceCacheKey.isBlank()) {
			return;
		}
		Property cached = COPPER_MAN_DEFENSE_TINT_CACHE.get(sourceCacheKey);
		if (cached != null) {
			return;
		}
		Property diskCached = readCopperManDefenseTintFromDisk(server, sourceCacheKey);
		if (diskCached != null) {
			COPPER_MAN_DEFENSE_TINT_CACHE.put(sourceCacheKey, diskCached);
			COPPER_MAN_DEFENSE_TINT_RETRY_AT_MS.remove(sourceCacheKey);
			return;
		}

		long nowMs = System.currentTimeMillis();
		Long retryAtMs = COPPER_MAN_DEFENSE_TINT_RETRY_AT_MS.get(sourceCacheKey);
		if (retryAtMs != null && nowMs < retryAtMs) {
			return;
		}
		if (!COPPER_MAN_DEFENSE_TINT_BUILD_IN_FLIGHT.add(sourceCacheKey)) {
			return;
		}

		CompletableFuture.runAsync(() -> {
			Property generated = null;
			try {
				generated = buildCopperManDefenseTintSkin(sourceSkin, playerName);
			} finally {
				if (generated != null) {
					COPPER_MAN_DEFENSE_TINT_CACHE.put(sourceCacheKey, generated);
					COPPER_MAN_DEFENSE_TINT_RETRY_AT_MS.remove(sourceCacheKey);
					writeCopperManDefenseTintToDisk(server, sourceCacheKey, generated);
				} else {
					COPPER_MAN_DEFENSE_TINT_RETRY_AT_MS.put(sourceCacheKey, nowMs + COPPER_MAN_DEFENSE_TINT_RETRY_COOLDOWN_MS);
				}
				COPPER_MAN_DEFENSE_TINT_BUILD_IN_FLIGHT.remove(sourceCacheKey);
			}
		});
	}

	private static Property buildCopperManDefenseTintSkin(Property sourceSkin, String playerName) {
		Path tempSkinPath = null;
		try {
			Pair<String, SkinVariant> skinData = PlayerUtils.getSkinUrl(sourceSkin);
			if (skinData == null || skinData.first() == null || skinData.first().isBlank()) {
				return null;
			}

			BufferedImage sourceSkinImage = loadSkinImage(new URI(skinData.first()));
			if (sourceSkinImage == null) {
				return null;
			}

			BufferedImage copperTintedSkin = applyCopperDefenseTint(sourceSkinImage);
			tempSkinPath = Files.createTempFile("lg2_copper_defense_", "_" + shortSha1(sourceSkin.value()) + ".png");
			ImageIO.write(copperTintedSkin, "PNG", tempSkinPath.toFile());
			SkinVariant variant = skinData.second() == null ? SkinVariant.CLASSIC : skinData.second();
			return signCopperDefenseSkin(tempSkinPath.toUri(), variant);
		} catch (Exception exception) {
			Lg2.LOGGER.debug("Failed to build copper defense skin tint for {}", playerName, exception);
			return null;
		} finally {
			if (tempSkinPath != null) {
				try {
					Files.deleteIfExists(tempSkinPath);
				} catch (IOException ignored) {
				}
			}
		}
	}

	private static Property signCopperDefenseSkin(URI skinUri, SkinVariant variant) {
		try {
			return MineskinService.INSTANCE.signSkin(skinUri, variant).orElse(null);
		} catch (Exception firstFailure) {
			try {
				MineskinService.INSTANCE.reload();
				return MineskinService.INSTANCE.signSkin(skinUri, variant).orElse(null);
			} catch (Exception secondFailure) {
				Lg2.LOGGER.debug("Failed to sign copper defense skin after Mineskin reload", secondFailure);
			}
			Lg2.LOGGER.debug("Failed to sign copper defense skin", firstFailure);
			return null;
		}
	}

	private static Property readCopperManDefenseTintFromDisk(MinecraftServer server, String sourceCacheKey) {
		if (server == null || sourceCacheKey == null || sourceCacheKey.isBlank()) {
			return null;
		}

		Path cacheFile = getCopperManDefenseTintCacheFile(server, sourceCacheKey);
		if (cacheFile == null || !Files.exists(cacheFile)) {
			return null;
		}

		try {
			String json = Files.readString(cacheFile, StandardCharsets.UTF_8);
			StoredSkinProperty stored = GSON.fromJson(json, StoredSkinProperty.class);
			if (stored == null || stored.name() == null || stored.value() == null || stored.value().isBlank()) {
				return null;
			}
			return stored.signature() == null || stored.signature().isBlank()
					? new Property(stored.name(), stored.value())
					: new Property(stored.name(), stored.value(), stored.signature());
		} catch (Exception exception) {
			Lg2.LOGGER.debug("Failed to read cached copper defense skin from {}", cacheFile, exception);
			return null;
		}
	}

	private static void writeCopperManDefenseTintToDisk(MinecraftServer server, String sourceCacheKey, Property property) {
		if (server == null || sourceCacheKey == null || sourceCacheKey.isBlank() || property == null || property.value() == null || property.value().isBlank()) {
			return;
		}

		Path cacheFile = getCopperManDefenseTintCacheFile(server, sourceCacheKey);
		if (cacheFile == null) {
			return;
		}

		try {
			Files.createDirectories(cacheFile.getParent());
			StoredSkinProperty stored = new StoredSkinProperty(property.name(), property.value(), property.signature());
			Files.writeString(cacheFile, GSON.toJson(stored), StandardCharsets.UTF_8);
		} catch (Exception exception) {
			Lg2.LOGGER.debug("Failed to write cached copper defense skin to {}", cacheFile, exception);
		}
	}

	private static Path getCopperManDefenseTintCacheFile(MinecraftServer server, String sourceCacheKey) {
		if (server == null || sourceCacheKey == null || sourceCacheKey.isBlank()) {
			return null;
		}

		Path cacheDir = server.getWorldPath(LevelResource.ROOT).resolve(COPPER_MAN_DEFENSE_TINT_CACHE_DIR_NAME);
		return cacheDir.resolve(shortSha1(sourceCacheKey) + ".json");
	}

	private static void cleanupCopperManDefenseTintCache(MinecraftServer server, boolean force) {
		if (server == null) {
			return;
		}

		long nowTick = server.overworld().getGameTime();
		if (!force
				&& copperManDefenseTintCacheLastCleanupTick != Long.MIN_VALUE
				&& nowTick - copperManDefenseTintCacheLastCleanupTick < COPPER_MAN_DEFENSE_TINT_CACHE_CLEANUP_INTERVAL_TICKS) {
			return;
		}
		copperManDefenseTintCacheLastCleanupTick = nowTick;

		Path cacheDir = server.getWorldPath(LevelResource.ROOT).resolve(COPPER_MAN_DEFENSE_TINT_CACHE_DIR_NAME);
		if (!Files.isDirectory(cacheDir)) {
			return;
		}

		long nowMs = System.currentTimeMillis();
		try (Stream<Path> stream = Files.list(cacheDir)) {
			List<Path> files = stream
					.filter(Files::isRegularFile)
					.filter(path -> path.getFileName().toString().endsWith(".json"))
					.sorted(Comparator.comparingLong(ServerRaceSystem::lastModifiedSafe).reversed())
					.toList();

			if (files.isEmpty()) {
				return;
			}

			for (int i = 0; i < files.size(); i++) {
				Path file = files.get(i);
				boolean overLimit = i >= COPPER_MAN_DEFENSE_TINT_CACHE_MAX_FILES;
				boolean expired = nowMs - lastModifiedSafe(file) > COPPER_MAN_DEFENSE_TINT_CACHE_MAX_AGE_MS;
				if (!overLimit && !expired) {
					continue;
				}
				try {
					Files.deleteIfExists(file);
				} catch (IOException exception) {
					Lg2.LOGGER.debug("Failed to delete stale copper defense tint cache {}", file, exception);
				}
			}
		} catch (IOException exception) {
			Lg2.LOGGER.debug("Failed to cleanup copper defense tint cache directory {}", cacheDir, exception);
		}
	}

	private static long lastModifiedSafe(Path path) {
		try {
			return Files.getLastModifiedTime(path).toMillis();
		} catch (IOException exception) {
			return Long.MIN_VALUE;
		}
	}

	private static BufferedImage loadSkinImage(URI uri) throws IOException {
		try (InputStream stream = uri.toURL().openStream()) {
			BufferedImage image = ImageIO.read(stream);
			if (image == null) {
				return null;
			}
			return normalizeSkinImage(toArgb(image));
		}
	}

	private static BufferedImage applyCopperDefenseTint(BufferedImage source) {
		BufferedImage tinted = toArgb(source);
		int width = tinted.getWidth();
		int height = tinted.getHeight();
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				int argb = tinted.getRGB(x, y);
				int alpha = (argb >>> 24) & 0xFF;
				if (alpha <= 0) {
					continue;
				}

				float red = ((argb >>> 16) & 0xFF) / 255.0F;
				float green = ((argb >>> 8) & 0xFF) / 255.0F;
				float blue = (argb & 0xFF) / 255.0F;
				float luminance = (red * 0.2126F) + (green * 0.7152F) + (blue * 0.0722F);
				float shading = clamp01(0.32F + (luminance * 0.95F));

				float targetRed = clamp01(COPPER_MAN_DEFENSE_COPPER_RED * (0.58F + shading));
				float targetGreen = clamp01(COPPER_MAN_DEFENSE_COPPER_GREEN * (0.52F + shading));
				float targetBlue = clamp01(COPPER_MAN_DEFENSE_COPPER_BLUE * (0.48F + (shading * 0.85F)));

				int outRed = Math.round(lerp(red, targetRed, COPPER_MAN_DEFENSE_TINT_STRENGTH) * 255.0F);
				int outGreen = Math.round(lerp(green, targetGreen, COPPER_MAN_DEFENSE_TINT_STRENGTH) * 255.0F);
				int outBlue = Math.round(lerp(blue, targetBlue, COPPER_MAN_DEFENSE_TINT_STRENGTH) * 255.0F);
				tinted.setRGB(x, y, ((alpha & 0xFF) << 24) | ((outRed & 0xFF) << 16) | ((outGreen & 0xFF) << 8) | (outBlue & 0xFF));
			}
		}
		return tinted;
	}

	private static boolean isPlayerUsingSkin(ServerPlayer player, Property expectedSkin) {
		if (player == null || expectedSkin == null) {
			return false;
		}

		Property currentSkin = PlayerUtils.getPlayerSkin(player.getGameProfile());
		return isSameSkinProperty(currentSkin, expectedSkin);
	}

	private static boolean isSameSkinProperty(Property left, Property right) {
		return left != null
				&& right != null
				&& Objects.equals(left.name(), right.name())
				&& Objects.equals(left.value(), right.value())
				&& Objects.equals(left.signature(), right.signature());
	}

	private static BufferedImage normalizeSkinImage(BufferedImage image) {
		if (image.getWidth() == 64 && image.getHeight() == 64) {
			return image;
		}

		BufferedImage normalized = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = normalized.createGraphics();
		graphics.setComposite(AlphaComposite.Src);
		graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
		graphics.drawImage(image, 0, 0, 64, 64, null);
		graphics.dispose();
		return normalized;
	}

	private static BufferedImage toArgb(BufferedImage image) {
		if (image.getType() == BufferedImage.TYPE_INT_ARGB) {
			return image;
		}

		BufferedImage converted = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = converted.createGraphics();
		graphics.setComposite(AlphaComposite.Src);
		graphics.drawImage(image, 0, 0, null);
		graphics.dispose();
		return converted;
	}

	private static float lerp(float start, float end, float delta) {
		return start + ((end - start) * delta);
	}

	private static double approach(double current, double target, double maxDelta) {
		if (current < target) {
			return Math.min(current + maxDelta, target);
		}
		if (current > target) {
			return Math.max(current - maxDelta, target);
		}
		return current;
	}

	private static float clamp01(float value) {
		return Math.max(0.0F, Math.min(1.0F, value));
	}

	private static String shortSha1(String value) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-1");
			byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
			StringBuilder builder = new StringBuilder(bytes.length * 2);
			for (byte b : bytes) {
				builder.append(String.format("%02x", b));
			}
			return builder.toString();
		} catch (NoSuchAlgorithmException exception) {
			return Integer.toHexString(value.hashCode());
		}
	}

	private record CocaineCauldronKey(ResourceKey<Level> dimension, BlockPos pos) {
	}

	private static void restoreCartelDisguise(MinecraftServer server, ServerPlayer player, CartelDisguiseSession session) {
		if (player != null) {
			CartelWebcamBridge.endDisguise(player.getUUID());
		}
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

		SkinValue stored = captureStoredSkinValue(player);
		if (stored != null) {
			return stored;
		}

		Property current = PlayerUtils.getPlayerSkin(player.getGameProfile());
		if (current == null) {
			return null;
		}
		SkinVariant variant = resolveSkinVariant(current);
		return new SkinValue("lg2_cartel_disguise", player.getScoreboardName(), variant, current, current);
	}

	private static SkinValue captureStoredSkinValue(ServerPlayer player) {
		if (player == null) {
			return null;
		}

		SkinStorage skinStorage = SkinRestorer.getSkinStorage();
		if (skinStorage == null || !skinStorage.hasSavedSkin(player.getUUID())) {
			return null;
		}

		return skinStorage.getSkin(player.getUUID());
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
		int startX = Math.max(CARTEL_PASSPORT_NAME_MIN_X, CARTEL_PASSPORT_NAME_CENTER_X - measureCartelPassportNameWidth(playerName) / 2);
		return title.copy()
			.append(Component.literal(TITLE_OVERLAY_RESET + buildHorizontalAdvance(startX))
				.withStyle(style -> style.withColor(0xFFFFFF).withItalic(false)))
			.append(Component.literal(playerName)
						.withStyle(style -> style.withColor(0x2E2016).withItalic(false).withFont(CARTEL_PASSPORT_NAME_FONT)));
	}

	private static int measureCartelPassportNameWidth(String text) {
		if (text == null || text.isEmpty()) {
			return 0;
		}
		Map<Character, Integer> advances = getCartelPassportNameAdvanceMap();
		int width = 0;
		for (int i = 0; i < text.length(); i++) {
			width += advances.getOrDefault(text.charAt(i), CARTEL_PASSPORT_NAME_CHAR_ADVANCE);
		}
		return width;
	}

	private static Map<Character, Integer> getCartelPassportNameAdvanceMap() {
		Map<Character, Integer> cached = CARTEL_PASSPORT_NAME_ADVANCE_CACHE;
		if (cached != null) {
			return cached;
		}
		synchronized (ServerRaceSystem.class) {
			if (CARTEL_PASSPORT_NAME_ADVANCE_CACHE == null) {
				CARTEL_PASSPORT_NAME_ADVANCE_CACHE = loadCartelPassportNameAdvanceMap();
			}
			return CARTEL_PASSPORT_NAME_ADVANCE_CACHE;
		}
	}

	private static Map<Character, Integer> loadCartelPassportNameAdvanceMap() {
		Map<Character, Integer> advances = new LinkedHashMap<>();
		try (InputStream stream = ServerRaceSystem.class.getResourceAsStream(CARTEL_PASSPORT_NAME_TEXTURE_RESOURCE)) {
			if (stream == null) {
				return advances;
			}
			BufferedImage image = ImageIO.read(stream);
			if (image == null) {
				return advances;
			}

			int cellWidth = image.getWidth() / CARTEL_PASSPORT_NAME_BITMAP_COLUMNS;
			int cellHeight = image.getHeight() / CARTEL_PASSPORT_NAME_BITMAP_ROWS;
			float renderScale = (float) CARTEL_PASSPORT_NAME_RENDER_HEIGHT / (float) cellHeight;

			for (int row = 0; row < CARTEL_PASSPORT_NAME_FONT_ROWS.length; row++) {
				String rowChars = CARTEL_PASSPORT_NAME_FONT_ROWS[row];
				for (int column = 0; column < rowChars.length(); column++) {
					char character = rowChars.charAt(column);
					if (character == '\0') {
						continue;
					}

					int left = cellWidth;
					int right = -1;
					for (int x = 0; x < cellWidth; x++) {
						for (int y = 0; y < cellHeight; y++) {
							int argb = image.getRGB(column * cellWidth + x, row * cellHeight + y);
							if (((argb >>> 24) & 0xFF) > 0) {
								left = Math.min(left, x);
								right = Math.max(right, x);
							}
						}
					}

					int advance;
					if (right < left) {
						advance = CARTEL_PASSPORT_NAME_CHAR_ADVANCE;
					} else {
						int glyphWidth = right - left + 1;
						advance = Math.max(1, Math.round(glyphWidth * renderScale)) + 1;
					}
					advances.put(character, advance);
				}
			}
		} catch (IOException exception) {
			Lg2.LOGGER.warn("Failed to measure passport_name font glyph widths, using fallback centering", exception);
		}
		return advances;
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
				case "passport" -> "Паспорт";
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

	private static String localizeAbilityNotPurchased(ServerPlayer player, String abilityName) {
		String locale = normalizeCartelDisguiseLocale(player);
		return switch (locale) {
			case "rpr" -> "Умѣніе «" + abilityName + "» не стяжано";
			case "uk", "uk_ua" -> "Здібність «" + abilityName + "» ще не куплена";
			case "ja", "ja_jp" -> "能力「" + abilityName + "」はまだ購入されていません";
			case "ru", "ru_ru" -> "Способность «" + abilityName + "» не куплена";
			default -> "Ability \"" + abilityName + "\" is not purchased yet";
		};
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
		tickOnlineCooldowns(server, CARTEL_DEFENSE_COOLDOWNS);
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
		level.playSound(
				null,
				lawyer.getX(),
				lawyer.getY(),
				lawyer.getZ(),
				SoundEvents.VILLAGER_AMBIENT,
				SoundSource.HOSTILE,
				1.0F,
				1.0F
		);
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

	public static boolean isCartelLawyerEntity(Entity entity) {
		return entity instanceof CartelLawyerEntity || (entity != null && entity.getTags().contains(CARTEL_LAWYER_TAG));
	}

	public static Property getCameraCartelLawyerSkinProperty() {
		return resolveCartelLawyerSkinProperty();
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
		long cooldownTicks = asTicks(positiveOrDefault(ability.cooldownSeconds, CARTEL_DEFAULT_COOLDOWN_SECONDS));
		long remainingCooldownTicks = getRemainingOnlineCooldownTicks(CARTEL_ATTACK_COOLDOWNS, caster.getUUID());
		if (displayRemainingCooldown(caster, remainingCooldownTicks)) {
			return 0;
		}
		long nowTick = level.getGameTime();

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
			return 0;
		}

		CARTEL_SUMMON_SESSIONS.put(UUID.randomUUID(), session);
		startOnlineCooldown(CARTEL_ATTACK_COOLDOWNS, caster.getUUID(), cooldownTicks);

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
		tickOnlineCooldowns(server, CARTEL_ATTACK_COOLDOWNS);
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

	public static boolean isCopperManJetpackActive(ServerPlayer player) {
		return player != null && COPPER_MAN_JETPACK_SESSIONS.containsKey(player.getUUID());
	}

	public static void suspendCopperManJetpackForDrone(ServerPlayer player) {
		if (player == null) {
			return;
		}
		UUID playerId = player.getUUID();
		CopperManJetpackSession session = COPPER_MAN_JETPACK_SESSIONS.remove(playerId);
		if (session != null) {
			COPPER_MAN_JETPACK_SUSPENDED_FOR_DRONE.put(playerId, session);
		}
		syncCopperManJetpackVisual(player, false);
	}

	public static void resumeCopperManJetpackAfterDrone(ServerPlayer player) {
		if (player == null) {
			return;
		}
		UUID playerId = player.getUUID();
		CopperManJetpackSession session = COPPER_MAN_JETPACK_SUSPENDED_FOR_DRONE.remove(playerId);
		if (session == null) {
			return;
		}

		long nowTick = player.level().getGameTime();
		if (!player.isAlive() || player.isSpectator() || nowTick >= session.expireTick()) {
			syncCopperManJetpackVisual(player, false);
			return;
		}

		COPPER_MAN_JETPACK_SESSIONS.put(playerId, session);
		syncCopperManJetpackVisual(player, true);
	}

	private static long getRemainingOnlineCooldownTicks(Map<UUID, Long> cooldowns, UUID playerId) {
		if (cooldowns == null || playerId == null) {
			return 0L;
		}
		return Math.max(0L, cooldowns.getOrDefault(playerId, 0L));
	}

	private static boolean displayRemainingCooldown(ServerPlayer player, long remainingCooldownTicks) {
		if (player == null || remainingCooldownTicks <= 0L) {
			return false;
		}

		double remaining = remainingCooldownTicks / 20.0D;
		player.displayClientMessage(
				Component.literal(String.format(Locale.ROOT, "%.1fs", remaining))
						.withStyle(ChatFormatting.RED),
				true
		);
		return true;
	}

	private static boolean displayInfiniteCooldown(ServerPlayer player) {
		if (player == null) {
			return false;
		}
		player.displayClientMessage(Component.literal("\u221E").withStyle(ChatFormatting.RED), true);
		return true;
	}

	private static void startOnlineCooldown(Map<UUID, Long> cooldowns, UUID playerId, long cooldownTicks) {
		if (cooldowns == null || playerId == null) {
			return;
		}
		if (cooldownTicks <= 0L) {
			cooldowns.remove(playerId);
			return;
		}
		cooldowns.put(playerId, cooldownTicks);
	}

	private static void tickOnlineCooldowns(MinecraftServer server, Map<UUID, Long> cooldowns) {
		if (server == null || cooldowns == null || cooldowns.isEmpty()) {
			return;
		}

		Iterator<Map.Entry<UUID, Long>> iterator = cooldowns.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<UUID, Long> entry = iterator.next();
			Long remainingTicks = entry.getValue();
			if (remainingTicks == null || remainingTicks <= 0L) {
				iterator.remove();
				continue;
			}
			if (server.getPlayerList().getPlayer(entry.getKey()) == null) {
				continue;
			}

			long nextValue = remainingTicks - 1L;
			if (nextValue <= 0L) {
				iterator.remove();
			} else {
				entry.setValue(nextValue);
			}
		}
	}

	private static double positiveOrDefault(double value, double defaultValue) {
		if (Double.isNaN(value) || value <= 0.0D) {
			return defaultValue;
		}
		return value;
	}

	private static boolean isInfiniteCooldown(double value) {
		return Double.compare(value, RaceConfig.INFINITE_COOLDOWN_SECONDS) == 0;
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
