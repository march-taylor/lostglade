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
import com.lostglade.item.BattleDonkeyTurretItem;
import com.lostglade.item.MethadoneItem;
import com.lostglade.item.ModItems;
import com.lostglade.item.TubochkaItem;
import com.lostglade.mixin.ArmorStandAccessor;
import com.lostglade.mixin.DisplayAccessor;
import com.lostglade.mixin.EntityTrackedDataAccessor;
import com.lostglade.mixin.EntityPassengerAccessor;
import com.lostglade.mixin.MerchantMenuAccessor;
import com.lostglade.mixin.MobXpRewardAccessor;
import com.lostglade.mixin.PlayerTrackedDataAccessor;
import com.mojang.math.Transformation;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import eu.pb4.polymer.core.api.entity.PolymerEntity;
import eu.pb4.polymer.core.api.entity.PolymerEntityUtils;
import eu.pb4.polymer.core.api.item.PolymerItemUtils;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import it.unimi.dsi.fastutil.Pair;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
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
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundOpenBookPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerRotationPacket;
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.nbt.CompoundTag;
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
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.equine.Donkey;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import xyz.nucleoid.packettweaker.PacketContext;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.Writer;
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

import static net.minecraft.commands.Commands.argument;
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
	private static final String GENNADIY_SHAROYOBOV_RACE_ID = "gennadiy_sharoyobov";
	private static final String MARK_POTROSHITEL_RACE_ID = "mark_potroshitel";
	private static final String TITLE_OVERLAY_SHIFT = "\ue905";
	private static final String TITLE_OVERLAY_RESET = "\ue940\ue940\ue941\ue943";
	private static final int TITLE_OVERLAY_TARGET_ADVANCE = 168;
	private static final int TITLE_OVERLAY_SHIFT_ADVANCE = -8;
	private static final String CARTEL_PASSPORT_OVERLAY_GLYPH = "\uef10";
	private static final String WOMAN_SHNYAGA_LETTER_OVERLAY_GLYPH = "\uef20";
	private static final FontDescription CARTEL_PASSPORT_OVERLAY_FONT = new FontDescription.Resource(
			Objects.requireNonNull(Identifier.tryParse("lg2:passport_title"))
	);
	private static final FontDescription WOMAN_SHNYAGA_LETTER_OVERLAY_FONT = new FontDescription.Resource(
			Objects.requireNonNull(Identifier.tryParse("lg2:woman_shnyaga_letter"))
	);
	private static final FontDescription CARTEL_PASSPORT_NAME_FONT = new FontDescription.Resource(
		Objects.requireNonNull(Identifier.tryParse("lg2:passport_name"))
	);
	private static final FontDescription WOMAN_SHNYAGA_NAME_FONT = new FontDescription.Resource(
			Objects.requireNonNull(Identifier.tryParse("lg2:woman_shnyaga_name"))
	);
	private static final FontDescription CARTEL_MANUAL_PAGE_FONT = new FontDescription.Resource(
			Objects.requireNonNull(Identifier.tryParse("lg2:cartel_manual_pages"))
	);
	private static final int CARTEL_MANUAL_IMAGE_COUNT = 5;
	private static final int CARTEL_MANUAL_IMAGE_COLUMNS = 2;
	private static final int CARTEL_MANUAL_IMAGE_FIRST_GLYPH = 0xEFA0;
	private static final int CARTEL_MANUAL_IMAGE_LEADING_LINES = 3;
	private static final int CARTEL_MANUAL_IMAGE_TRAILING_LINES = 3;
	private static final String CARTEL_MANUAL_IMAGE_COLUMN_JOINER = "\ueff0";
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
	private static final String WOMAN_SHNYAGA_NAME_TEXTURE_RESOURCE = "/assets/lg2/textures/font/woman_shnyaga_name.png";
	private static final String[] WOMAN_SHNYAGA_NAME_FONT_ROWS = {
			"АБВГДЕЖЗИЙКЛМНОП",
			"РСТУФХЦЧШЩЪЫЬЭЮЯ",
			"Ёабвгдежзийклмно",
			"прстуфхцчшщъыьэю",
			"яёABCDEFGHIJKLMN",
			"OPQRSTUVWXYZabcd",
			"efghijklmnopqrst",
			"uvwxyz0123456789",
			"_:-.!?\0\0\0\0\0\0\0\0\0\0"
	};
	private static final int WOMAN_SHNYAGA_NAME_BITMAP_COLUMNS = 16;
	private static final int WOMAN_SHNYAGA_NAME_BITMAP_ROWS = 9;
	private static final int WOMAN_SHNYAGA_NAME_RENDER_HEIGHT = 5;
	private static final int WOMAN_SHNYAGA_NAME_CHAR_ADVANCE = 5;
	private static final int WOMAN_SHNYAGA_NAME_SPACE_ADVANCE = 3;
	private static final int CARTEL_PASSPORT_NAME_MIN_X = 18;
	private static final int CARTEL_PASSPORT_NAME_CENTER_X = 131;
	private static volatile Map<Character, Integer> CARTEL_PASSPORT_NAME_ADVANCE_CACHE;
	private static volatile Map<Character, Integer> WOMAN_SHNYAGA_NAME_ADVANCE_CACHE;
	private static final int CARTEL_PASSPORT_OVERLAY_X_OFFSET = 168;
	// Calibrated letter overlay anchor; keep this position when replacing the texture.
	private static final int WOMAN_SHNYAGA_LETTER_OVERLAY_X_OFFSET = 189;
	private static final int WOMAN_SHNYAGA_LETTER_OVERLAY_WIDTH = 134;
	private static final double WOMAN_SHNYAGA_SENDER_LINE_CENTER_X = 36.5D;
	private static final double WOMAN_SHNYAGA_RECIPIENT_LINE_CENTER_X = 122.5D;
	private static final double WOMAN_SHNYAGA_SENDER_NAME_CORRECTION_X = 6.5D;
	private static final double WOMAN_SHNYAGA_RECIPIENT_NAME_CORRECTION_X = -2.5D;
	private static final int MISTER_CARTEL_49_STACK_LIMIT = 49;
	private static final String CARTEL_SUMMON_TAG = "lg2.cartel_summon";
	private static final String CARTEL_LAWYER_TAG = "lg2.cartel_lawyer";
	private static final String CARTEL_LAWYER_MARKER_NAME = "lg2_cartel_lawyer_marker";
	private static final Identifier CARTEL_ATTACK_FINGER_SOUND_ID = Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "cartel_attack_finger");
	private static final Holder<SoundEvent> CARTEL_ATTACK_FINGER_SOUND = Holder.direct(SoundEvent.createVariableRangeEvent(CARTEL_ATTACK_FINGER_SOUND_ID));
	private static final double CARTEL_TARGET_RANGE = 7.0D;
	private static final int CARTEL_SPAWN_OFFSET_BLOCKS = 3;
	private static final double CARTEL_SOUND_RANGE_BLOCKS = 16.0D;
	private static final float CARTEL_ATTACK_ACTIVATION_SOUND_VOLUME = 1.0F;
	private static final float CARTEL_ATTACK_ACTIVATION_SOUND_PITCH = 1.0F;
	private static final float CARTEL_SUMMON_OUTCOME_SOUND_VOLUME = 1.0F;
	private static final float CARTEL_SUMMON_OUTCOME_SOUND_PITCH = 1.0F;
	private static final float CARTEL_LAWYER_REACTION_SOUND_VOLUME = 1.0F;
	private static final float CARTEL_LAWYER_REACTION_SOUND_PITCH = 1.0F;
	private static final float CARTEL_DISGUISE_PAGE_SOUND_VOLUME = 0.75F;
	private static final float CARTEL_DISGUISE_PAGE_SOUND_PITCH = 1.0F;
	private static final double CARTEL_DEFAULT_COOLDOWN_SECONDS = 5.0D;
	private static final double CARTEL_DEFAULT_LIFETIME_SECONDS = 30.0D;
	private static final double CARTEL_DEFAULT_AFTER_KILL_SECONDS = 2.0D;
	private static final double CARTEL_DEFAULT_RAIDER_ARMOR_DIVIDER = 3.0D;
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
	private static final double WOMAN_UNIQUE_DEFAULT_DURATION_SECONDS = 600.0D;
	private static final double WOMAN_UNIQUE_DEFAULT_RANGE_BLOCKS = 32.0D;
	private static final double WOMAN_UNIQUE_DEFAULT_DROP_MIN_SECONDS = 30.0D;
	private static final double WOMAN_UNIQUE_DEFAULT_DROP_MAX_SECONDS = 60.0D;
	private static final double WOMAN_UNIQUE_DEFAULT_DROP_CHANCE = 0.4D;
	private static final double WOMAN_UNIQUE_DEFAULT_TRADE_PRICE_INCREASE = 0.3D;
	private static final double WOMAN_UNIQUE_DEFAULT_ABSORPTION_HEARTS = 2.0D;
	private static final double WOMAN_SHNYAGA_DEFAULT_TRANSFER_HEARTS = 3.0D;
	private static final double WOMAN_SHNYAGA_DEFAULT_BUFF_RANGE_BLOCKS = 16.0D;
	private static final double WOMAN_SHNYAGA_DEFAULT_REJECT_DAMAGE_HEARTS = 5.0D;
	private static final double WOMAN_SHNYAGA_DEFAULT_REJECT_DEBUFF_SECONDS = 900.0D;
	private static final int WOMAN_UNIQUE_WEAKNESS_DURATION_TICKS = 40;
	private static final int WOMAN_UNIQUE_MAIN_INVENTORY_SIZE = 36;
	private static final int WOMAN_DEFENSE_EFFECT_DURATION_TICKS = 18;
	private static final double WOMAN_DEFENSE_LIGHT_SHAKE_STRENGTH = 0.018D;
	private static final double WOMAN_DEFENSE_MEDIUM_SHAKE_STRENGTH = 0.033D;
	private static final double WOMAN_DEFENSE_STRONG_SHAKE_STRENGTH = 0.052D;
	private static final double WOMAN_DEFENSE_VERY_STRONG_SHAKE_STRENGTH = 0.068D;
	private static final double WOMAN_DEFENSE_SHAKE_MIN_MOVE_SPEED = 0.015D;
	private static final double WOMAN_DEFENSE_SHAKE_MOVEMENT_RATIO = 0.65D;
	private static final long WOMAN_DEFENSE_SHAKE_INTERVAL_TICKS = 1L;
	private static final long WOMAN_DEFENSE_PANIC_SOUND_INTERVAL_TICKS = 34L;
	private static final double WOMAN_ATTACK_DEFAULT_CHARGE_RADIUS_BLOCKS = 1.5D;
	private static final double WOMAN_ATTACK_DEFAULT_RANGE_BLOCKS = 64.0D;
	private static final double WOMAN_ATTACK_DEFAULT_DAMAGE = 2.0D;
	private static final double WOMAN_ATTACK_DEFAULT_FOLLOW_SECONDS = 30.0D;
	private static final double WOMAN_ATTACK_PROJECTILE_SPEED = 1.4D;
	private static final double WOMAN_ATTACK_CHARGE_FORWARD_OFFSET = 2.0D;
	private static final int WOMAN_ATTACK_PARTICLE_COUNT = 9;
	private static final long WOMAN_ATTACK_CHARGE_SOUND_INTERVAL_TICKS = 12L;
	private static final double WOMAN_ATTACK_AIR_TRIGGER_HEAD_FORWARD_OFFSET = 0.22D;
	private static final float WOMAN_ATTACK_AIR_TRIGGER_WIDTH = 1.8F;
	private static final float WOMAN_ATTACK_AIR_TRIGGER_HEIGHT = 1.8F;
	private static final long WOMAN_ATTACK_FOLLOW_NAV_INTERVAL_TICKS = 8L;
	private static final double WOMAN_ATTACK_FOLLOW_SPEED = 1.1D;
	private static final double WOMAN_SOUND_RANGE_BLOCKS = 16.0D;
	private static final double GENNADIY_DONKEY_DEFAULT_HEALTH_POINTS = 30.0D;
	private static final double GENNADIY_DONKEY_DEFAULT_ARMOR_DIVIDER = 2.0D;
	private static final double GENNADIY_DONKEY_DEFAULT_HEALTH_REGEN_SECONDS = 40.0D;
	private static final double GENNADIY_DONKEY_DEFAULT_COOLDOWN_SECONDS = 600.0D;
	private static final int GENNADIY_DONKEY_DEFAULT_MAX_AMMO = 128;
	private static final int GENNADIY_DONKEY_DEFAULT_AMMO_REGEN_AMOUNT = 2;
	private static final double GENNADIY_DONKEY_DEFAULT_AMMO_REGEN_SECONDS = 9.375D;
	private static final double GENNADIY_DONKEY_DEFAULT_BULLET_DAMAGE = 2.0D;
	private static final double GENNADIY_DONKEY_DEFAULT_BULLET_RANGE_BLOCKS = 16.0D;
	private static final double GENNADIY_DONKEY_DEFAULT_FOLLOW_MAX_DISTANCE_BLOCKS = 8.0D;
	private static final double GENNADIY_DEFENSE_DEFAULT_DURATION_SECONDS = 10.0D;
	private static final double GENNADIY_DEFENSE_DEFAULT_KNOCKBACK_BLOCKS_PER_DAMAGE = 1.0D;
	private static final double GENNADIY_DEFENSE_DEFAULT_MAX_KNOCKBACK_BLOCKS = 32.0D;
	private static final double GENNADIY_DEFENSE_DEFAULT_WAVE_RANGE_BLOCKS = 32.0D;
	private static final double GENNADIY_DEFENSE_DEFAULT_MIN_DAMAGE = 1.0D;
	private static final double GENNADIY_DEFENSE_DEFAULT_MAX_DAMAGE = 3.0D;
	private static final double GENNADIY_DEFENSE_EXPLOSION_SOUND_RANGE_BLOCKS = 16.0D;
	private static final float GENNADIY_DEFENSE_CHARGE_SOUND_VOLUME = 0.36F;
	private static final float GENNADIY_DEFENSE_CHARGE_SOUND_PITCH = 0.98F;
	private static final float GENNADIY_DEFENSE_EXPLOSION_SOUND_VOLUME = 2.0F;
	private static final float GENNADIY_DEFENSE_EXPLOSION_SOUND_PITCH = 0.72F;
	private static final double GENNADIY_HOOK_DEFAULT_RANGE_BLOCKS = 16.0D;
	private static final double GENNADIY_HOOK_DEFAULT_DAMAGE = 4.0D;
	private static final double GENNADIY_HOOK_DEFAULT_SLOWNESS_SECONDS = 1.5D;
	private static final double GENNADIY_HOOK_EXTEND_SPEED_BLOCKS = 1.35D;
	private static final double GENNADIY_HOOK_RETRACT_SPEED_BLOCKS = 1.0D;
	private static final double GENNADIY_HOOK_PULL_SPEED_BLOCKS = 0.82D;
	private static final double GENNADIY_HOOK_SEGMENT_SPACING_BLOCKS = 0.42D;
	private static final double GENNADIY_HOOK_CHAIN_END_OFFSET_BLOCKS = 4.0D / 16.0D;
	private static final double GENNADIY_HOOK_TIP_ATTACH_CORRECTION_BLOCKS = 3.0D / 16.0D;
	private static final Vec3 GENNADIY_HOOK_TIP_RED_LINK_ATTACH_OFFSET = new Vec3(0.0D, -5.5D / 16.0D, 8.0D / 16.0D);
	private static final double GENNADIY_HOOK_TARGET_INSET_BLOCKS = 2.0D;
	private static final double GENNADIY_HOOK_HIT_RADIUS_BLOCKS = 0.45D;
	private static final double GENNADIY_HOOK_FINISH_DISTANCE_BLOCKS = 1.45D;
	private static final int GENNADIY_HOOK_MAX_SEGMENTS = 48;
	private static final double GENNADIY_HOOK_SOUND_RANGE_BLOCKS = 32.0D;
	private static final long GENNADIY_HOOK_CHAIN_SOUND_REPEAT_TICKS = 50L;
	private static final float GENNADIY_HOOK_CHAIN_SOUND_VOLUME = 0.85F;
	private static final float GENNADIY_HOOK_CHAIN_SOUND_PITCH = 1.0F;
	private static final float GENNADIY_HOOK_IMPACT_SOUND_VOLUME = 1.875F;
	private static final float GENNADIY_HOOK_IMPACT_SOUND_PITCH = 1.0F;
	private static final float GENNADIY_HOOK_END_SOUND_VOLUME = 0.95F;
	private static final float GENNADIY_HOOK_END_SOUND_PITCH = 1.0F;
	private static final Identifier GENNADIY_HOOK_CHAIN_SOUND_ID = Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "gennadiy_hook_chain");
	private static final Identifier GENNADIY_HOOK_IMPACT_SOUND_ID = Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "gennadiy_hook_impact");
	private static final Identifier GENNADIY_HOOK_END_SOUND_ID = Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "gennadiy_hook_end");
	private static final Identifier GENNADIY_HOOK_CHAIN_FALLBACK_SOUND_ID = BuiltInRegistries.SOUND_EVENT.getKey(SoundEvents.CHAIN_STEP);
	private static final Holder<SoundEvent> GENNADIY_HOOK_CHAIN_SOUND = Holder.direct(SoundEvent.createVariableRangeEvent(GENNADIY_HOOK_CHAIN_SOUND_ID));
	private static final Holder<SoundEvent> GENNADIY_HOOK_IMPACT_SOUND = Holder.direct(SoundEvent.createVariableRangeEvent(GENNADIY_HOOK_IMPACT_SOUND_ID));
	private static final Holder<SoundEvent> GENNADIY_HOOK_END_SOUND = Holder.direct(SoundEvent.createVariableRangeEvent(GENNADIY_HOOK_END_SOUND_ID));
	private static final double GENNADIY_RAGE_DEFAULT_HEALTH_THRESHOLD_RATIO = 0.2D;
	private static final int GENNADIY_RAGE_DEFAULT_HASTE_LEVEL = 1;
	private static final double GENNADIY_RAGE_DEFAULT_MELEE_DAMAGE_BONUS_RATIO = 0.15D;
	private static final double GENNADIY_REPORT_DEFAULT_COOLDOWN_SECONDS = 1800.0D;
	private static final long GENNADIY_REPORT_ITEM_SYNC_INTERVAL_TICKS = 20L;
	private static final int GENNADIY_REPORT_CONTAINER_SCAN_CHUNK_RADIUS = 16;
	private static final int GENNADIY_REPORT_CONFIRM_LINE_WIDTH_CHARS = 42;
	private static final long GENNADIY_DEFENSE_WAVE_VISUAL_DURATION_TICKS = 18L;
	private static final float GENNADIY_DEFENSE_FORCED_PITCH = 90.0F;
	private static final String FSIT_SEAT_ENTITY_CLASS_NAME = "dev.rvbsm.fsit.entity.SeatEntity";
	private static final String FSIT_SEAT_COMPANION_CLASS_NAME = "dev.rvbsm.fsit.entity.SeatEntity$Companion";
	private static volatile Boolean FSIT_SEAT_AVAILABLE;
	private static volatile Object FSIT_SEAT_COMPANION;
	private static volatile java.lang.reflect.Method FSIT_SEAT_CREATE_METHOD;
	private static final byte ENTITY_FLAG_SHIFTING = 0x02;
	private static final byte ENTITY_FLAG_SPRINTING = 0x08;
	private static final long GENNADIY_DONKEY_SHOT_INTERVAL_TICKS = 2L;
	private static final long GENNADIY_DONKEY_MANUAL_INPUT_GRACE_TICKS = 4L;
	private static final double GENNADIY_DONKEY_WALK_SPEED = 0.18D;
	private static final double GENNADIY_DONKEY_RETURN_SPEED = 0.29D;
	private static final double GENNADIY_DONKEY_STEERING_SMOOTHING = 0.35D;
	private static final long GENNADIY_DONKEY_MOVEMENT_LOGIC_INTERVAL_TICKS = 4L;
	private static final long GENNADIY_DONKEY_AGGRO_SCAN_INTERVAL_TICKS = 5L;
	private static final double GENNADIY_DONKEY_TARGET_FORGET_RANGE_BLOCKS = 96.0D;
	private static final double GENNADIY_DONKEY_OWNER_MIN_DISTANCE_BLOCKS = 2.25D;
	private static final long GENNADIY_DONKEY_MAX_OUTSIDE_TICKS = 100L;
	private static final int GENNADIY_DONKEY_TARGET_PRIORITY_AGGRO = 1;
	private static final int GENNADIY_DONKEY_TARGET_PRIORITY_OWNER_COMBAT = 2;
	private static final int GENNADIY_DONKEY_TARGET_PRIORITY_DIRECT_HIT = 3;
	private static final String GENNADIY_DONKEY_TAG = "lg2.gennadiy_battle_donkey";
	private static final String GENNADIY_DONKEY_VISUAL_TAG = "lg2.gennadiy_battle_donkey_visual";
	private static final String GENNADIY_DONKEY_HELMET_VISUAL_TAG = "lg2.gennadiy_battle_donkey_helmet_visual";
	private static final String GENNADIY_DONKEY_TRIGGER_TAG = "lg2.gennadiy_battle_donkey_trigger";
	private static final String GENNADIY_HOOK_CHAIN_TAG = "lg2.gennadiy_hook_chain";
	private static final String GENNADIY_REPORT_DATA_TAG = "lg2_gennadiy_report";
	private static final String GENNADIY_REPORT_ID_TAG = "id";
	private static final String GENNADIY_REPORT_CREATOR_TAG = "creator";
	private static final String GENNADIY_REPORT_HOLDER_TAG = "holder";
	private static final String GENNADIY_REPORT_COOLDOWN_END_TAG = "cooldown_end_tick";
	private static final Identifier GENNADIY_REPORT_COOLDOWN_GROUP_ID = Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "gennadiy_report");
	private static final String GENNADIY_DONKEY_OWNER_TAG_PREFIX = "lg2_gennadiy_donkey_owner:";
	private static final EntityDimensions GENNADIY_DONKEY_DIMENSIONS = EntityDimensions.fixed(1.4F, 1.6F);
	private static final float GENNADIY_DONKEY_AIR_TRIGGER_WIDTH = 1.6F;
	private static final float GENNADIY_DONKEY_AIR_TRIGGER_HEIGHT = 1.6F;
	private static final double GENNADIY_DONKEY_AIR_TRIGGER_HEAD_FORWARD_OFFSET = 0.24D;
	private static final double GENNADIY_DONKEY_BULLET_VISUAL_SPEED_BLOCKS = 4.0D;
	private static final double GENNADIY_DONKEY_SHOOT_SOUND_RANGE_BLOCKS = 16.0D;
	private static final float GENNADIY_DONKEY_SHOOT_SOUND_VOLUME = 1.0F;
	private static final float GENNADIY_DONKEY_SHOOT_SOUND_PITCH = 1.0F;
	private static final float GENNADIY_DONKEY_SHOOT_FALLBACK_SOUND_VOLUME = 0.9F;
	private static final float GENNADIY_DONKEY_SHOOT_FALLBACK_SOUND_PITCH = 1.55F;
	private static final Identifier GENNADIY_DONKEY_SHOOT_SOUND_ID = Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "gennadiy_donkey_shoot");
	private static final Holder<SoundEvent> GENNADIY_DONKEY_SHOOT_SOUND = Holder.direct(SoundEvent.createVariableRangeEvent(GENNADIY_DONKEY_SHOOT_SOUND_ID));
	private static final double MARK_AXE_DEFAULT_RANGE_BLOCKS = 32.0D;
	private static final double MARK_AXE_DEFAULT_BLEEDING_ONE_DAMAGE = 1.0D;
	private static final double MARK_AXE_DEFAULT_BLEEDING_TWO_DAMAGE = 2.0D;
	private static final double MARK_AXE_DEFAULT_BLEEDING_INTERVAL_SECONDS = 3.0D;
	private static final double MARK_AXE_DEFAULT_BLEEDING_TWO_DURATION_SECONDS = 10.0D;
	private static final double MARK_AXE_DEFAULT_COOLDOWN_SECONDS = 600.0D;
	private static final double MARK_AXE_FLIGHT_SPEED_BLOCKS = 1.25D;
	private static final double MARK_AXE_FALL_SPEED_BLOCKS = 0.75D;
	private static final double MARK_AXE_HEAD_FIRST_SPIN_RADIANS = Math.PI;
	private static final double MARK_AXE_FALL_ROLL_MIN_RADIANS = Math.toRadians(180.0D);
	private static final double MARK_AXE_FALL_ROLL_MAX_RADIANS = Math.toRadians(270.0D);
	private static final double MARK_AXE_HIT_RADIUS_BLOCKS = 0.45D;
	private static final double MARK_AXE_BLOCK_STICK_INSET_BLOCKS = 0.24D;
	private static final double MARK_AXE_ENTITY_STICK_INSET_BLOCKS = 0.10D;
	private static final double MARK_AXE_ENTITY_VISUAL_WIDTH_SCALE = 0.82D;
	private static final double MARK_AXE_ENTITY_VISUAL_MIN_WIDTH = 0.26D;
	private static final double MARK_AXE_PICKUP_TRIGGER_SIZE = 0.72D;
	private static final float MARK_AXE_DISPLAY_SCALE = 1.0F;
	private static final int MARK_AXE_DISPLAY_INTERPOLATION_TICKS = 2;
	private static final int MARK_AXE_BLOOD_COLOR = 0xB40A18;
	private static final String MARK_AXE_DISPLAY_TAG = "lg2.mark_throwing_axe";
	private static final String MARK_AXE_TRIGGER_TAG = "lg2.mark_throwing_axe_trigger";
	private static final long WOMAN_SHNYAGA_WHITELIST_CHECK_INTERVAL_TICKS = 100L;
	private static final int WOMAN_SHNYAGA_EFFECT_DURATION_TICKS = 60;
	private static final String WOMAN_SHNYAGA_STATE_FILE_NAME = "lg2_woman_shnyaga_links.json";
	private static final Identifier WOMAN_SHNYAGA_WOMAN_HEALTH_MODIFIER_ID =
			Objects.requireNonNull(Identifier.tryParse("lg2:woman_shnyaga_woman_health"));
	private static final Identifier WOMAN_SHNYAGA_BOYFRIEND_HEALTH_MODIFIER_ID =
			Objects.requireNonNull(Identifier.tryParse("lg2:woman_shnyaga_boyfriend_health"));
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
	private static final double COPPER_MAN_JETPACK_AIR_CONTROL_SPEED = 0.0275D;
	private static final double COPPER_MAN_JETPACK_HORIZONTAL_INERTIA_HALF_LIFE_TICKS = 8.0D;
	private static final double COPPER_MAN_JETPACK_HORIZONTAL_INERTIA_DECAY_PER_TICK =
			Math.pow(0.5D, 1.0D / COPPER_MAN_JETPACK_HORIZONTAL_INERTIA_HALF_LIFE_TICKS);
	private static final double COPPER_MAN_JETPACK_HORIZONTAL_INERTIA_STOP_SPEED = 0.001D;
	private static final double COPPER_MAN_JETPACK_MIN_STORED_GROUND_SPEED = 0.005D;
	private static final double COPPER_MAN_JETPACK_MAX_STORED_GROUND_SPEED = 1.0D;
	private static final long COPPER_MAN_JETPACK_GROUND_INERTIA_GRACE_TICKS = 8L;
	private static final double COPPER_MAN_JETPACK_VERTICAL_ACCEL = 0.255D;
	private static final double COPPER_MAN_JETPACK_ASCEND_BRAKE_RANGE_BLOCKS = 4.0D;
	private static final double COPPER_MAN_JETPACK_PARTICLE_BACK_OFFSET = 0.34D;
	private static final double COPPER_MAN_JETPACK_PARTICLE_SIDE_OFFSET = 0.2D;
	private static final double COPPER_MAN_JETPACK_PARTICLE_UP_OFFSET = 0.80D;
	private static final double COPPER_MAN_JETPACK_PARTICLE_UP_OFFSET_CROUCHING = 0.64D;
	private static final Identifier COPPER_MAN_JETPACK_STEAM_SOUND_ID = Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "copper_jetpack_steam");
	private static final Identifier COPPER_MAN_JETPACK_FALLBACK_BUZZ_SOUND_ID = BuiltInRegistries.SOUND_EVENT.getKey(SoundEvents.MINECART_RIDING);
	private static final Identifier COPPER_MAN_JETPACK_FALLBACK_STEAM_SOUND_ID = BuiltInRegistries.SOUND_EVENT.getKey(SoundEvents.FURNACE_FIRE_CRACKLE);
	private static final Holder<SoundEvent> COPPER_MAN_JETPACK_STEAM_SOUND = Holder.direct(SoundEvent.createVariableRangeEvent(COPPER_MAN_JETPACK_STEAM_SOUND_ID));
	private static final long COPPER_MAN_JETPACK_SOUND_INTERVAL_TICKS = 8L;
	private static final double COPPER_MAN_JETPACK_SOUND_RANGE_BLOCKS = 16.0D;
	private static final float COPPER_MAN_JETPACK_SOUND_VOLUME = 2.0F;
	private static final float COPPER_MAN_JETPACK_SOUND_PITCH = 1.0F;
	private static final float COPPER_MAN_JETPACK_FALLBACK_BUZZ_VOLUME = 2.0F;
	private static final float COPPER_MAN_JETPACK_FALLBACK_BUZZ_PITCH = 0.68F;
	private static final float COPPER_MAN_JETPACK_FALLBACK_STEAM_VOLUME = 2.0F;
	private static final float COPPER_MAN_JETPACK_FALLBACK_STEAM_PITCH = 0.8F;
	private static final float COPPER_MAN_DEFENSE_ACTIVATION_SOUND_VOLUME = 2.0F;
	private static final double COPPER_MAN_DEFENSE_ACTIVATION_SOUND_RANGE_BLOCKS = 16.0D;
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
	private static final int WOMAN_SHNYAGA_PREVIOUS_SLOT = 12;
	private static final int WOMAN_SHNYAGA_HEAD_SLOT = 13;
	private static final int WOMAN_SHNYAGA_NEXT_SLOT = 14;
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
	private static final Set<UUID> WOMAN_DEFENSE_BLIND_PLAYERS = new HashSet<>();
	private static final Map<UUID, Integer> WOMAN_DEFENSE_SLOWNESS_PLAYERS = new HashMap<>();
	private static final Map<UUID, Long> WOMAN_DEFENSE_PANIC_SOUND_NEXT_TICKS = new HashMap<>();
	private static final Map<UUID, Long> WOMAN_DEFENSE_SELF_SOUND_NEXT_TICKS = new HashMap<>();
	private static final Map<UUID, Vec3> WOMAN_DEFENSE_LAST_POSITIONS = new HashMap<>();
	private static final Map<UUID, WomanUniqueSession> WOMAN_UNIQUE_SESSIONS = new LinkedHashMap<>();
	private static final Map<UUID, Integer> WOMAN_UNIQUE_SYNCED_MERCHANT_MENUS = new HashMap<>();
	private static final Map<UUID, WomanShnyagaPendingSelection> WOMAN_SHNYAGA_PENDING_SELECTIONS = new LinkedHashMap<>();
	private static final Map<UUID, LinkedHashMap<UUID, WomanShnyagaProposal>> WOMAN_SHNYAGA_PROPOSALS_BY_WOMAN = new LinkedHashMap<>();
	private static final Map<UUID, WomanShnyagaProposal> WOMAN_SHNYAGA_PROPOSALS_BY_TARGET = new LinkedHashMap<>();
	private static final Map<UUID, WomanShnyagaRejectionSession> WOMAN_SHNYAGA_REJECTION_SESSIONS = new LinkedHashMap<>();
	private static final Map<UUID, LinkedHashMap<UUID, WomanShnyagaLink>> WOMAN_SHNYAGA_LINKS_BY_WOMAN = new LinkedHashMap<>();
	private static final Map<UUID, WomanShnyagaLink> WOMAN_SHNYAGA_LINKS_BY_BOYFRIEND = new LinkedHashMap<>();
	private static final Set<UUID> WOMAN_SHNYAGA_BUFFED_BOYFRIENDS = new HashSet<>();
	private static final Map<UUID, WomanAttackChargeSession> WOMAN_ATTACK_CHARGE_SESSIONS = new LinkedHashMap<>();
	private static final List<WomanAttackProjectile> WOMAN_ATTACK_PROJECTILES = new ArrayList<>();
	private static final Map<UUID, WomanAttackFollowSession> WOMAN_ATTACK_FOLLOWS = new LinkedHashMap<>();
	private static final Map<UUID, Long> GENNADIY_DONKEY_COOLDOWNS = new LinkedHashMap<>();
	private static final Map<UUID, GennadiyDonkeyState> GENNADIY_DONKEY_STATES = new LinkedHashMap<>();
	private static final Map<UUID, UUID> GENNADIY_DONKEY_OWNER_BY_ENTITY = new LinkedHashMap<>();
	private static final List<GennadiyDonkeyBulletVisual> GENNADIY_DONKEY_BULLET_VISUALS = new ArrayList<>();
	private static final Map<UUID, GennadiyDefenseSession> GENNADIY_DEFENSE_SESSIONS = new LinkedHashMap<>();
	private static final Map<UUID, UUID> GENNADIY_DEFENSE_FSIT_SEATS = new LinkedHashMap<>();
	private static final List<GennadiyDefenseWaveVisual> GENNADIY_DEFENSE_WAVE_VISUALS = new ArrayList<>();
	private static final Map<UUID, GennadiyHookSession> GENNADIY_HOOK_SESSIONS = new LinkedHashMap<>();
	private static final Map<UUID, Integer> GENNADIY_RAGE_HASTE_PLAYERS = new HashMap<>();
	private static final Map<UUID, GennadiyReportPending> GENNADIY_REPORT_PENDING = new LinkedHashMap<>();
	private static final Map<UUID, Long> MARK_ATTACK_COOLDOWNS = new LinkedHashMap<>();
	private static final List<MarkThrownAxeSession> MARK_THROWN_AXES = new ArrayList<>();
	private static final Map<UUID, MarkBleedingSession> MARK_BLEEDING_SESSIONS = new LinkedHashMap<>();
	private static final Map<UUID, CartelDisguiseSession> CARTEL_DISGUISE_SESSIONS = new LinkedHashMap<>();
	private static final Map<UUID, CartelManualBookRestore> CARTEL_MANUAL_BOOK_RESTORES = new LinkedHashMap<>();
	private static final Map<UUID, UUID> COPPER_GOLEM_FOLLOWERS = new LinkedHashMap<>();
	private static final Map<UUID, Long> COPPER_MAN_DEFENSE_COOLDOWNS = new LinkedHashMap<>();
	private static final Map<UUID, CopperManDefenseVisualSession> COPPER_MAN_DEFENSE_VISUAL_SESSIONS = new LinkedHashMap<>();
	private static final Map<UUID, Long> COPPER_MAN_JETPACK_COOLDOWNS = new LinkedHashMap<>();
	private static final Map<UUID, CopperManJetpackSession> COPPER_MAN_JETPACK_SESSIONS = new LinkedHashMap<>();
	private static final Map<UUID, CopperManJetpackSession> COPPER_MAN_JETPACK_SUSPENDED_FOR_DRONE = new LinkedHashMap<>();
	private static final Map<UUID, CopperManJetpackInputState> COPPER_MAN_JETPACK_INPUTS = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> COPPER_MAN_JETPACK_NEXT_SOUND_TICKS = new ConcurrentHashMap<>();
	private static final Set<UUID> COPPER_MAN_JETPACK_SOUND_ACTIVE = ConcurrentHashMap.newKeySet();
	private static final Map<UUID, CopperManJetpackMovementSample> COPPER_MAN_JETPACK_MOVEMENT_SAMPLES = new ConcurrentHashMap<>();
	private static final Map<UUID, CopperManJetpackGroundInertia> COPPER_MAN_JETPACK_GROUND_INERTIA = new ConcurrentHashMap<>();
	private static final Map<UUID, CopperManJetpackFlightMomentum> COPPER_MAN_JETPACK_FLIGHT_MOMENTUM = new ConcurrentHashMap<>();
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
	private static final ThreadLocal<Boolean> GENNADIY_DEFENSE_APPLYING_EFFECT = ThreadLocal.withInitial(() -> Boolean.FALSE);
	private static final ThreadLocal<Boolean> GENNADIY_RAGE_APPLYING_DAMAGE = ThreadLocal.withInitial(() -> Boolean.FALSE);
	private static final Set<CocaineCauldronKey> PROCESSED_COCAINE_CAULDRONS = new HashSet<>();
	private static long processedCocaineCauldronTick = Long.MIN_VALUE;
	private static long womanShnyagaNextWhitelistCheckTick = Long.MIN_VALUE;
	private static CompletableFuture<Property> CARTEL_LAWYER_SKIN_FUTURE;
	private static volatile Property CARTEL_LAWYER_SKIN_PROPERTY;
	private static final class CartelSummonSession {
		private final ResourceKey<Level> dimension;
		private final UUID ownerPlayerId;
		private final UUID targetPlayerId;
		private final long normalExpireTick;
		private final long afterKillTicks;
		private final double armorDivider;
		private final List<UUID> raiderIds = new ArrayList<>();
		private Vec3 lastTargetPosition;
		private Long afterKillExpireTick;
		private boolean outcomeSoundPlayed;

		private CartelSummonSession(ResourceKey<Level> dimension, UUID ownerPlayerId, UUID targetPlayerId, long normalExpireTick, long afterKillTicks, double armorDivider, Vec3 lastTargetPosition) {
			this.dimension = dimension;
			this.ownerPlayerId = ownerPlayerId;
			this.targetPlayerId = targetPlayerId;
			this.normalExpireTick = normalExpireTick;
			this.afterKillTicks = afterKillTicks;
			this.armorDivider = armorDivider;
			this.lastTargetPosition = lastTargetPosition;
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
			loadWomanShnyagaLinks(server);
			pruneWomanShnyagaLinksAgainstWhitelist(server);
			syncWomanShnyagaLinks(server);
			prewarmCartelLawyerSkinAsync();
			cleanupCopperManDefenseTintCache(server, true);
			cleanupAllGennadiyHookChains(server);
			syncGeneratedDialogs(server, true);
			Lg2.LOGGER.info("Loaded {} configured personal races", RACES_BY_NICKNAME.size());
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			saveWomanShnyagaLinks(server);
			cleanupAllCartelRaceEntities(server, true);
			restoreAllCartelDisguises(server);
			restoreAllCopperManJetpacks(server);
			restoreAllCopperManDefenseVisuals(server);
			cleanupAllGennadiyDonkeys(server);
			cleanupAllGennadiyHookChains(server);
			dropAllMarkThrownAxes(server);
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
			WOMAN_DEFENSE_BLIND_PLAYERS.clear();
			WOMAN_DEFENSE_SLOWNESS_PLAYERS.clear();
			WOMAN_DEFENSE_PANIC_SOUND_NEXT_TICKS.clear();
			WOMAN_DEFENSE_SELF_SOUND_NEXT_TICKS.clear();
			WOMAN_DEFENSE_LAST_POSITIONS.clear();
			WOMAN_UNIQUE_SESSIONS.clear();
			WOMAN_UNIQUE_SYNCED_MERCHANT_MENUS.clear();
			WOMAN_SHNYAGA_PENDING_SELECTIONS.clear();
			WOMAN_SHNYAGA_PROPOSALS_BY_WOMAN.clear();
			WOMAN_SHNYAGA_PROPOSALS_BY_TARGET.clear();
			WOMAN_SHNYAGA_REJECTION_SESSIONS.clear();
			WOMAN_SHNYAGA_LINKS_BY_WOMAN.clear();
			WOMAN_SHNYAGA_LINKS_BY_BOYFRIEND.clear();
			WOMAN_SHNYAGA_BUFFED_BOYFRIENDS.clear();
			CARTEL_DISGUISE_SESSIONS.clear();
			COPPER_MAN_DEFENSE_COOLDOWNS.clear();
			COPPER_MAN_DEFENSE_VISUAL_SESSIONS.clear();
			COPPER_MAN_JETPACK_COOLDOWNS.clear();
			COPPER_MAN_JETPACK_SESSIONS.clear();
			COPPER_MAN_JETPACK_SUSPENDED_FOR_DRONE.clear();
			COPPER_MAN_JETPACK_INPUTS.clear();
			COPPER_MAN_JETPACK_NEXT_SOUND_TICKS.clear();
			COPPER_MAN_JETPACK_SOUND_ACTIVE.clear();
			COPPER_MAN_JETPACK_MOVEMENT_SAMPLES.clear();
			COPPER_MAN_JETPACK_GROUND_INERTIA.clear();
			COPPER_MAN_JETPACK_FLIGHT_MOMENTUM.clear();
			COPPER_MAN_JETPACK_DISPLAY_IDS.clear();
			COPPER_MAN_DEFENSE_TINT_CACHE.clear();
			COPPER_MAN_DEFENSE_TINT_RETRY_AT_MS.clear();
			COPPER_MAN_DEFENSE_TINT_BUILD_IN_FLIGHT.clear();
			GENNADIY_DONKEY_COOLDOWNS.clear();
			GENNADIY_DONKEY_STATES.clear();
			GENNADIY_DONKEY_OWNER_BY_ENTITY.clear();
			GENNADIY_DONKEY_BULLET_VISUALS.clear();
			GENNADIY_DEFENSE_SESSIONS.clear();
			GENNADIY_DEFENSE_FSIT_SEATS.clear();
			GENNADIY_DEFENSE_WAVE_VISUALS.clear();
			GENNADIY_HOOK_SESSIONS.clear();
			GENNADIY_RAGE_HASTE_PLAYERS.clear();
			GENNADIY_REPORT_PENDING.clear();
			MARK_ATTACK_COOLDOWNS.clear();
			MARK_THROWN_AXES.clear();
			MARK_BLEEDING_SESSIONS.clear();
			copperManDefenseTintCacheLastCleanupTick = Long.MIN_VALUE;
			CARTEL_TRAVKA_GROWTH_ATTEMPTS.clear();
			CARTEL_PLANTED_FERN_GROWTHS.clear();
			CARTEL_PLANTED_FERN_GROWTH_QUEUE.clear();
			COPPER_GOLEM_FOLLOWERS.clear();
			CARTEL_SUMMON_OWNER_BY_ENTITY.clear();
			CARTEL_LAWYER_OWNER_BY_ENTITY.clear();
			CARTEL_LAWYER_SKIN_FUTURE = null;
			CARTEL_LAWYER_SKIN_PROPERTY = null;
			womanShnyagaNextWhitelistCheckTick = Long.MIN_VALUE;
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
				server.execute(() -> {
					getRace(handler.player).ifPresent(race ->
							Lg2.LOGGER.info("Assigned personal race '{}' to {}", race.id, handler.player.getGameProfile().name())
					);
					CartelSecretRecipeBookSystem.syncJoinedPlayer(handler.player);
					CopperManGogglesSystem.syncPlayerRecipeBook(handler.player);
					syncWomanShnyagaLinks(server);
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
			COPPER_MAN_JETPACK_MOVEMENT_SAMPLES.remove(handler.player.getUUID());
			COPPER_MAN_JETPACK_GROUND_INERTIA.remove(handler.player.getUUID());
			COPPER_MAN_JETPACK_FLIGHT_MOMENTUM.remove(handler.player.getUUID());
			WOMAN_DEFENSE_SESSIONS.remove(handler.player.getUUID());
			if (WOMAN_DEFENSE_BLIND_PLAYERS.remove(handler.player.getUUID())) {
				handler.player.removeEffect(MobEffects.BLINDNESS);
			}
			if (WOMAN_DEFENSE_SLOWNESS_PLAYERS.remove(handler.player.getUUID()) != null) {
				handler.player.removeEffect(MobEffects.SLOWNESS);
			}
			WOMAN_DEFENSE_PANIC_SOUND_NEXT_TICKS.remove(handler.player.getUUID());
			WOMAN_DEFENSE_SELF_SOUND_NEXT_TICKS.remove(handler.player.getUUID());
			WOMAN_DEFENSE_LAST_POSITIONS.remove(handler.player.getUUID());
			clearWomanUniqueState(server, handler.player.getUUID());
			clearWomanAttackState(handler.player.getUUID());
			clearWomanShnyagaPendingState(handler.player.getUUID());
			clearGennadiyDefense(handler.player);
			cleanupGennadiyHookSession(server, GENNADIY_HOOK_SESSIONS.remove(handler.player.getUUID()));
			GENNADIY_REPORT_PENDING.remove(handler.player.getUUID());
			recallGennadiyDonkey(server, handler.player.getUUID(), false);
			syncWomanShnyagaLinks(server);
		});
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			if (!alive) {
				clearGennadiyDefense(newPlayer);
				cleanupGennadiyHookSession(newPlayer.level().getServer(), GENNADIY_HOOK_SESSIONS.remove(newPlayer.getUUID()));
				recallGennadiyDonkey(newPlayer.level().getServer(), newPlayer.getUUID(), false);
			}
		});
		ServerMessageEvents.ALLOW_CHAT_MESSAGE.register(ServerRaceSystem::onAllowChatMessage);
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
			return onUseEntity(serverPlayer, hand, entity, hitResult == null ? null : hitResult.getLocation());
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
			tickCopperManJetpackMovementSamples(server);
			tickCopperManJetpack(server);
			tickCopperManDefense(server);
			tickWomanStock(server);
			tickWomanDefense(server);
			tickWomanUnique(server);
			tickWomanShnyaga(server);
			tickWomanAttack(server);
			tickGennadiyDonkeys(server);
			tickGennadiyDefense(server);
			tickGennadiyHook(server);
			tickGennadiyRage(server);
			tickGennadiyReport(server);
			tickMarkPotroshitel(server);
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
						.then(literal("reset_cooldowns")
								.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
								.executes(ServerRaceSystem::resetAllRaceAbilityCooldownsFromCommand)
						)
						.then(literal("reset_cooldown")
								.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
								.executes(ServerRaceSystem::resetAllRaceAbilityCooldownsFromCommand)
						)
						.then(literal("use")
								.then(literal("attack").executes(context -> useAbility(context, RaceAbilitySlot.ATTACK)))
								.then(literal("defense").executes(context -> useAbility(context, RaceAbilitySlot.DEFENSE)))
								.then(literal("ability").executes(context -> useAbility(context, RaceAbilitySlot.UNIQUE_ABILITY)))
								.then(literal("shnyaga").executes(context -> useAbility(context, RaceAbilitySlot.SHNYAGA)))
						)
						.then(argument("internal_action", StringArgumentType.word())
								.suggests((context, builder) -> builder.buildFuture())
								.then(argument("response", StringArgumentType.word())
										.suggests((context, builder) -> builder.buildFuture())
										.executes(ServerRaceSystem::handleInternalRaceCommand)
								)
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

	private static int resetAllRaceAbilityCooldownsFromCommand(CommandContext<CommandSourceStack> context) {
		MinecraftServer server = context.getSource().getServer();
		resetAllRaceAbilityCooldowns(server);
		context.getSource().sendSuccess(() -> Component.literal("Все КД расовых способностей сброшены"), true);
		return 1;
	}

	public static void resetAllRaceAbilityCooldowns(MinecraftServer server) {
		CARTEL_ATTACK_COOLDOWNS.clear();
		CARTEL_DEFENSE_COOLDOWNS.clear();
		CARTEL_UNIQUE_COOLDOWNS.clear();
		GENERIC_ABILITY_COOLDOWN_END_TICKS.clear();
		GENERIC_ABILITY_INFINITE_COOLDOWNS.clear();
		WOMAN_FLOWER_COOLDOWNS.clear();
		WOMAN_ANIMAL_BREED_COOLDOWNS.clear();
		COPPER_MAN_DEFENSE_COOLDOWNS.clear();
		COPPER_MAN_JETPACK_COOLDOWNS.clear();
		GENNADIY_DONKEY_COOLDOWNS.clear();
		GENNADIY_RAGE_HASTE_PLAYERS.clear();
		MARK_ATTACK_COOLDOWNS.clear();
		unlockAllMarkThrownAxes(server);
		clearAllGennadiyReportCooldowns(server);
		CopperManGogglesSystem.resetAllAbilityCooldowns(server);
		CopperManRepulsorSystem.resetAllAbilityCooldowns(server);
	}

	private static int openMenu(CommandContext<CommandSourceStack> context) {
		ServerPlayer player = context.getSource().getPlayer();
		if (player == null) {
			context.getSource().sendFailure(Component.literal(localizeRaceMessageText(null, "player_only")));
			return 0;
		}

		Optional<PlayerRaceConfig> raceOptional = getRace(player);
		if (raceOptional.isEmpty()) {
			player.sendSystemMessage(localizedRaceMessage(player, "no_race"));
			return 0;
		}

		String dialogId = DIALOG_ID_BY_NICKNAME.get(normalizeNickname(player.getGameProfile().name()));
		if (dialogId == null || dialogId.isBlank()) {
			player.sendSystemMessage(localizedRaceMessage(player, "no_menu"));
			return 0;
		}

		if (!runDialogCommand(player, "show @s " + dialogId)) {
			player.sendSystemMessage(localizedRaceMessage(player, "no_menu"));
			Lg2.LOGGER.warn("Failed to open race dialog '{}' for {}", dialogId, player.getGameProfile().name());
			return 0;
		}

		return 1;
	}

	private static boolean onAllowChatMessage(PlayerChatMessage message, ServerPlayer sender, ChatType.Bound params) {
		return !handlePendingWomanShnyagaChatMessage(message, sender);
	}

	public static boolean handlePendingWomanShnyagaChatMessage(PlayerChatMessage message, ServerPlayer sender) {
		if (sender == null || message == null) {
			return false;
		}

		WomanShnyagaPendingSelection selection = WOMAN_SHNYAGA_PENDING_SELECTIONS.get(sender.getUUID());
		if (selection == null) {
			return false;
		}

		String content = message.signedContent() == null ? "" : message.signedContent().trim();
		handleWomanShnyagaChatSubmission(sender, selection, content);
		return true;
	}

	private static int acceptWomanShnyagaProposal(CommandContext<CommandSourceStack> context) {
		ServerPlayer player = context.getSource().getPlayer();
		if (player == null) {
			context.getSource().sendFailure(Component.literal("Player only."));
			return 0;
		}
		return handleWomanShnyagaProposalResponse(player, true);
	}

	private static int rejectWomanShnyagaProposal(CommandContext<CommandSourceStack> context) {
		ServerPlayer player = context.getSource().getPlayer();
		if (player == null) {
			context.getSource().sendFailure(Component.literal("Player only."));
			return 0;
		}
		return handleWomanShnyagaProposalResponse(player, false);
	}

	private static int handleInternalRaceCommand(CommandContext<CommandSourceStack> context) {
		String internalAction = StringArgumentType.getString(context, "internal_action").trim().toLowerCase(Locale.ROOT);
		String response = StringArgumentType.getString(context, "response").trim().toLowerCase(Locale.ROOT);
		if ("woman_shnyaga".equals(internalAction)) {
			return switch (response) {
				case "accept" -> acceptWomanShnyagaProposal(context);
				case "reject" -> rejectWomanShnyagaProposal(context);
				default -> {
					context.getSource().sendFailure(Component.literal("Unknown woman_shnyaga response."));
					yield 0;
				}
			};
		}
		if ("gennadiy_report".equals(internalAction)) {
			return switch (response) {
				case "confirm" -> confirmGennadiyReport(context);
				default -> {
					context.getSource().sendFailure(Component.literal("Unknown gennadiy_report response."));
					yield 0;
				}
			};
		}
		context.getSource().sendFailure(Component.literal("Unknown race command."));
		return 0;
	}

	private static int useAbility(CommandContext<CommandSourceStack> context, RaceAbilitySlot slot) {
		ServerPlayer player = context.getSource().getPlayer();
		if (player == null) {
			context.getSource().sendFailure(Component.literal(localizeRaceMessageText(null, "player_only")));
			return 0;
		}

		Optional<PlayerRaceConfig> raceOptional = getRace(player);
		if (raceOptional.isEmpty()) {
			player.sendSystemMessage(localizedRaceMessage(player, "no_race"));
			return 0;
		}

		PlayerRaceConfig race = raceOptional.get();
		RaceAbilityConfig ability = getAbility(race, slot);
		if (!ability.enabled) {
			player.sendSystemMessage(localizedRaceMessage(player, "ability_disabled", ability.name));
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
		if (slot == RaceAbilitySlot.UNIQUE_ABILITY && WOMAN_RACE_ID.equals(raceId)) {
			return useWomanUnique(player, race, ability);
		}
		if (slot == RaceAbilitySlot.SHNYAGA && WOMAN_RACE_ID.equals(raceId)) {
			return useWomanShnyaga(player, race, ability);
		}
		if (slot == RaceAbilitySlot.ATTACK && GENNADIY_SHAROYOBOV_RACE_ID.equals(raceId)) {
			return useGennadiyDonkeyAttack(player, race, ability);
		}
		if (slot == RaceAbilitySlot.DEFENSE && GENNADIY_SHAROYOBOV_RACE_ID.equals(raceId)) {
			return useGennadiyDefense(player, race, ability);
		}
		if (slot == RaceAbilitySlot.UNIQUE_ABILITY && GENNADIY_SHAROYOBOV_RACE_ID.equals(raceId)) {
			return useGennadiyHook(player, race, ability);
		}
		if (slot == RaceAbilitySlot.SHNYAGA && GENNADIY_SHAROYOBOV_RACE_ID.equals(raceId)) {
			return useGennadiyReport(player, race, ability);
		}
		if (slot == RaceAbilitySlot.ATTACK && MARK_POTROSHITEL_RACE_ID.equals(raceId)) {
			return useMarkPotroshitelAttack(player, race, ability);
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
		if (GENNADIY_SHAROYOBOV_RACE_ID.equals(raceId)) {
			return slot == RaceAbilitySlot.ATTACK
					|| slot == RaceAbilitySlot.DEFENSE
					|| slot == RaceAbilitySlot.UNIQUE_ABILITY
					|| slot == RaceAbilitySlot.SHNYAGA;
		}
		if (MARK_POTROSHITEL_RACE_ID.equals(raceId)) {
			return slot == RaceAbilitySlot.ATTACK;
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

	private static long getRemainingGenericAbilityCooldownTicks(ServerPlayer player, RaceAbilitySlot slot) {
		if (player == null || slot == null || slot == RaceAbilitySlot.STOCK) {
			return 0L;
		}

		EnumMap<RaceAbilitySlot, Long> cooldownEndTicks = GENERIC_ABILITY_COOLDOWN_END_TICKS.get(player.getUUID());
		if (cooldownEndTicks == null) {
			return 0L;
		}

		Long cooldownEndTick = cooldownEndTicks.get(slot);
		if (cooldownEndTick == null) {
			return 0L;
		}

		long remainingTicks = cooldownEndTick - player.level().getGameTime();
		if (remainingTicks <= 0L) {
			cooldownEndTicks.remove(slot);
			if (cooldownEndTicks.isEmpty()) {
				GENERIC_ABILITY_COOLDOWN_END_TICKS.remove(player.getUUID());
			}
			return 0L;
		}
		return remainingTicks;
	}

	private static boolean isGenericAbilityOnInfiniteCooldown(UUID playerId, RaceAbilitySlot slot) {
		if (playerId == null || slot == null || slot == RaceAbilitySlot.STOCK) {
			return false;
		}
		EnumSet<RaceAbilitySlot> infiniteCooldowns = GENERIC_ABILITY_INFINITE_COOLDOWNS.get(playerId);
		return infiniteCooldowns != null && infiniteCooldowns.contains(slot);
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
			playCopperManDefenseActivationSound(level, caster);
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

	private static void tickCopperManJetpackMovementSamples(MinecraftServer server) {
		if (server == null) {
			return;
		}

		long nowTick = server.overworld().getGameTime();
		Set<UUID> onlinePlayers = new HashSet<>();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player == null) {
				continue;
			}
			UUID playerId = player.getUUID();
			onlinePlayers.add(playerId);
			Vec3 currentPosition = player.position();
			CopperManJetpackMovementSample previous = COPPER_MAN_JETPACK_MOVEMENT_SAMPLES.put(
					playerId,
					new CopperManJetpackMovementSample(currentPosition, nowTick)
			);
			if (previous == null || previous.position() == null || previous.tick() == nowTick) {
				continue;
			}

			Vec3 movement = currentPosition.subtract(previous.position());
			Vec3 horizontalMovement = new Vec3(movement.x, 0.0D, movement.z);
			double horizontalSpeed = horizontalMovement.length();
			if (player.onGround()) {
				COPPER_MAN_JETPACK_FLIGHT_MOMENTUM.remove(playerId);
				if (horizontalSpeed >= COPPER_MAN_JETPACK_MIN_STORED_GROUND_SPEED
						&& horizontalSpeed <= COPPER_MAN_JETPACK_MAX_STORED_GROUND_SPEED) {
					COPPER_MAN_JETPACK_GROUND_INERTIA.put(
							playerId,
							new CopperManJetpackGroundInertia(horizontalMovement, nowTick)
					);
				} else if (horizontalSpeed < COPPER_MAN_JETPACK_MIN_STORED_GROUND_SPEED) {
					COPPER_MAN_JETPACK_GROUND_INERTIA.remove(playerId);
				}
			}
		}

		COPPER_MAN_JETPACK_MOVEMENT_SAMPLES.keySet().removeIf(playerId -> !onlinePlayers.contains(playerId));
		COPPER_MAN_JETPACK_GROUND_INERTIA.entrySet().removeIf(entry ->
				!onlinePlayers.contains(entry.getKey())
						|| entry.getValue() == null
						|| nowTick - entry.getValue().tick() > COPPER_MAN_JETPACK_GROUND_INERTIA_GRACE_TICKS
		);
		COPPER_MAN_JETPACK_FLIGHT_MOMENTUM.keySet().removeIf(playerId ->
				!onlinePlayers.contains(playerId) || !COPPER_MAN_JETPACK_SESSIONS.containsKey(playerId)
		);
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
				COPPER_MAN_JETPACK_NEXT_SOUND_TICKS.remove(entry.getKey());
				COPPER_MAN_JETPACK_SOUND_ACTIVE.remove(entry.getKey());
				COPPER_MAN_JETPACK_FLIGHT_MOMENTUM.remove(entry.getKey());
				continue;
			}

			if (!player.isAlive() || player.isSpectator()) {
				clearCopperManJetpack(player);
				continue;
			}
			if (nowTick >= session.expireTick()) {
				clearCopperManJetpack(player, true);
				continue;
			}
			if (DroneSystem.isControllingDrone(player)) {
				suspendCopperManJetpackForDrone(player);
				continue;
			}

			applyCopperManJetpackMovement(player, session, nowTick);
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

	private static void applyCopperManJetpackMovement(ServerPlayer player, CopperManJetpackSession session, long nowTick) {
		if (player == null || session == null) {
			return;
		}

		CopperManJetpackInputState input = COPPER_MAN_JETPACK_INPUTS.getOrDefault(player.getUUID(), CopperManJetpackInputState.EMPTY);
		Vec3 currentMotion = player.getDeltaMovement();
		double currentY = player.getY();
		double maxAllowedY = session.maxAllowedY();

		if (player.onGround() && !input.jump()) {
			COPPER_MAN_JETPACK_FLIGHT_MOMENTUM.remove(player.getUUID());
			player.fallDistance = 0.0F;
			beginCopperManJetpackLandingFade(player);
			return;
		}

		double nextVertical;
		if (input.jump()) {
			double remaining = Math.max(0.0D, maxAllowedY - currentY);
			float brakeFactor = clamp01((float) (remaining / COPPER_MAN_JETPACK_ASCEND_BRAKE_RANGE_BLOCKS));
			double verticalTarget = remaining <= 0.02D
					? 0.0D
					: Math.min(COPPER_MAN_JETPACK_ASCEND_SPEED * brakeFactor, remaining);
			nextVertical = approach(currentMotion.y, verticalTarget, COPPER_MAN_JETPACK_VERTICAL_ACCEL);
		} else {
			nextVertical = computeCopperManJetpackFallVelocity(player, currentMotion.y);
		}

		if (currentY >= maxAllowedY - 0.02D && nextVertical > 0.0D) {
			nextVertical = 0.0D;
		}

		CopperManJetpackFlightMomentum flightMomentum = resolveCopperManJetpackFlightMomentum(player, currentMotion, nowTick);
		Vec3 baseHorizontal = flightMomentum.inertia();
		Vec3 nextInertia = player.onGround()
				? baseHorizontal
				: decayCopperManJetpackHorizontalInertia(baseHorizontal);
		Vec3 airControlTarget = computeCopperManJetpackAirControlVelocity(player, input);
		Vec3 nextHorizontal = applyCopperManJetpackAirControl(nextInertia, airControlTarget, flightMomentum.capSpeed());
		if (nextInertia.lengthSqr() <= 1.0E-6D) {
			COPPER_MAN_JETPACK_FLIGHT_MOMENTUM.remove(player.getUUID());
		} else {
			COPPER_MAN_JETPACK_FLIGHT_MOMENTUM.put(player.getUUID(), new CopperManJetpackFlightMomentum(nextInertia, flightMomentum.capSpeed()));
		}
		double nextX = nextHorizontal.x;
		double nextZ = nextHorizontal.z;

		player.setDeltaMovement(nextX, nextVertical, nextZ);
		player.hurtMarked = true;
		if (player.connection != null) {
			player.connection.send(new ClientboundSetEntityMotionPacket(player));
		}
		player.fallDistance = 0.0F;
		emitCopperManJetpackParticles(player, input);
		playCopperManJetpackFlightSound(player, nowTick);
	}

	private static double computeCopperManJetpackFallVelocity(ServerPlayer player, double currentVertical) {
		if (player != null && player.onGround()) {
			return 0.0D;
		}
		return Math.max(currentVertical, COPPER_MAN_JETPACK_GLIDE_DESCEND_SPEED);
	}

	private static CopperManJetpackFlightMomentum resolveCopperManJetpackFlightMomentum(ServerPlayer player, Vec3 currentMotion, long nowTick) {
		if (player == null) {
			return new CopperManJetpackFlightMomentum(Vec3.ZERO, COPPER_MAN_JETPACK_AIR_CONTROL_SPEED);
		}

		UUID playerId = player.getUUID();
		Vec3 currentHorizontal = currentMotion == null
				? Vec3.ZERO
				: new Vec3(currentMotion.x, 0.0D, currentMotion.z);
		CopperManJetpackGroundInertia groundInertia = COPPER_MAN_JETPACK_GROUND_INERTIA.get(playerId);
		if (player.onGround()) {
			Vec3 inherited = currentHorizontal;
			if (groundInertia != null
					&& groundInertia.velocity() != null
					&& nowTick - groundInertia.tick() <= COPPER_MAN_JETPACK_GROUND_INERTIA_GRACE_TICKS
					&& groundInertia.velocity().lengthSqr() > currentHorizontal.lengthSqr()) {
				inherited = groundInertia.velocity();
			}
			return new CopperManJetpackFlightMomentum(inherited, Math.max(inherited.length(), COPPER_MAN_JETPACK_AIR_CONTROL_SPEED));
		}

		CopperManJetpackFlightMomentum flightMomentum = COPPER_MAN_JETPACK_FLIGHT_MOMENTUM.get(playerId);
		if (flightMomentum != null && flightMomentum.inertia() != null) {
			return flightMomentum;
		}
		if (groundInertia != null
				&& groundInertia.velocity() != null
				&& nowTick - groundInertia.tick() <= COPPER_MAN_JETPACK_GROUND_INERTIA_GRACE_TICKS) {
			return new CopperManJetpackFlightMomentum(
					groundInertia.velocity(),
					Math.max(groundInertia.velocity().length(), COPPER_MAN_JETPACK_AIR_CONTROL_SPEED)
			);
		}
		return new CopperManJetpackFlightMomentum(Vec3.ZERO, COPPER_MAN_JETPACK_AIR_CONTROL_SPEED);
	}

	private static Vec3 decayCopperManJetpackHorizontalInertia(Vec3 inertia) {
		if (inertia == null || inertia.lengthSqr() <= 1.0E-8D) {
			return Vec3.ZERO;
		}
		Vec3 decayed = inertia.scale(COPPER_MAN_JETPACK_HORIZONTAL_INERTIA_DECAY_PER_TICK);
		return decayed.length() <= COPPER_MAN_JETPACK_HORIZONTAL_INERTIA_STOP_SPEED ? Vec3.ZERO : decayed;
	}

	private static Vec3 computeCopperManJetpackAirControlVelocity(ServerPlayer player, CopperManJetpackInputState input) {
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
		return desired.normalize().scale(COPPER_MAN_JETPACK_AIR_CONTROL_SPEED);
	}

	private static Vec3 applyCopperManJetpackAirControl(Vec3 inertialVelocity, Vec3 airControlTarget, double capSpeed) {
		Vec3 result = inertialVelocity == null ? Vec3.ZERO : inertialVelocity;
		if (airControlTarget == null || airControlTarget.lengthSqr() <= 1.0E-6D) {
			return result;
		}

		result = result.add(airControlTarget);
		double maxSpeed = Math.max(capSpeed, COPPER_MAN_JETPACK_AIR_CONTROL_SPEED);
		double resultSpeed = result.length();
		if (resultSpeed > maxSpeed && resultSpeed > 1.0E-6D) {
			return result.scale(maxSpeed / resultSpeed);
		}
		return result;
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

	private static void playCopperManJetpackFlightSound(ServerPlayer player, long nowTick) {
		if (player == null || !(player.level() instanceof ServerLevel level)) {
			return;
		}

		UUID playerId = player.getUUID();
		long nextSoundTick = COPPER_MAN_JETPACK_NEXT_SOUND_TICKS.getOrDefault(playerId, Long.MIN_VALUE);
		if (nowTick < nextSoundTick) {
			return;
		}
		COPPER_MAN_JETPACK_NEXT_SOUND_TICKS.put(playerId, nowTick + COPPER_MAN_JETPACK_SOUND_INTERVAL_TICKS);
		COPPER_MAN_JETPACK_SOUND_ACTIVE.add(playerId);

		Vec3 origin = player.position().add(0.0D, Math.max(0.4D, player.getBbHeight() * 0.45D), 0.0D);
		long seed = level.getRandom().nextLong();
		double range = COPPER_MAN_JETPACK_SOUND_RANGE_BLOCKS;
		double rangeSqr = range * range;
		for (ServerPlayer viewer : level.players()) {
			if (viewer.distanceToSqr(origin) > rangeSqr) {
				continue;
			}

			if (PolymerResourcePackUtils.hasMainPack(viewer)) {
				sendCopperManJetpackSound(viewer, COPPER_MAN_JETPACK_STEAM_SOUND, origin, COPPER_MAN_JETPACK_SOUND_VOLUME, COPPER_MAN_JETPACK_SOUND_PITCH, seed);
			} else {
				sendCopperManJetpackSound(
						viewer,
						BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.MINECART_RIDING),
						origin,
						COPPER_MAN_JETPACK_FALLBACK_BUZZ_VOLUME,
						COPPER_MAN_JETPACK_FALLBACK_BUZZ_PITCH,
						seed
				);
				sendCopperManJetpackSound(
						viewer,
						BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.FURNACE_FIRE_CRACKLE),
						origin,
						COPPER_MAN_JETPACK_FALLBACK_STEAM_VOLUME,
						COPPER_MAN_JETPACK_FALLBACK_STEAM_PITCH,
						seed + 1L
				);
			}
		}
	}

	private static void beginCopperManJetpackLandingFade(ServerPlayer player) {
		if (player == null) {
			return;
		}
		UUID playerId = player.getUUID();
		COPPER_MAN_JETPACK_NEXT_SOUND_TICKS.remove(playerId);
		COPPER_MAN_JETPACK_SOUND_ACTIVE.remove(playerId);
	}

	private static void stopCopperManJetpackFlightSound(ServerPlayer player) {
		if (player == null || !(player.level() instanceof ServerLevel level)) {
			return;
		}
		UUID playerId = player.getUUID();
		COPPER_MAN_JETPACK_NEXT_SOUND_TICKS.remove(playerId);
		if (!COPPER_MAN_JETPACK_SOUND_ACTIVE.remove(playerId)) {
			return;
		}

		Vec3 origin = player.position().add(0.0D, Math.max(0.4D, player.getBbHeight() * 0.45D), 0.0D);
		double rangeSqr = COPPER_MAN_JETPACK_SOUND_RANGE_BLOCKS * COPPER_MAN_JETPACK_SOUND_RANGE_BLOCKS;
		for (ServerPlayer viewer : level.players()) {
			if (viewer.distanceToSqr(origin) > rangeSqr) {
				continue;
			}
			viewer.connection.send(new ClientboundStopSoundPacket(COPPER_MAN_JETPACK_STEAM_SOUND_ID, SoundSource.PLAYERS));
			viewer.connection.send(new ClientboundStopSoundPacket(COPPER_MAN_JETPACK_FALLBACK_BUZZ_SOUND_ID, SoundSource.PLAYERS));
			viewer.connection.send(new ClientboundStopSoundPacket(COPPER_MAN_JETPACK_FALLBACK_STEAM_SOUND_ID, SoundSource.PLAYERS));
		}
	}

	private static void sendCopperManJetpackSound(ServerPlayer viewer, Holder<SoundEvent> sound, Vec3 origin, float volume, float pitch, long seed) {
		if (viewer == null || viewer.connection == null || sound == null || origin == null) {
			return;
		}
		viewer.connection.send(new ClientboundSoundPacket(
				sound,
				SoundSource.PLAYERS,
				origin.x,
				origin.y,
				origin.z,
				volume,
				pitch,
				seed
		));
	}

	private static void playCopperManDefenseActivationSound(ServerLevel level, ServerPlayer caster) {
		if (level == null || caster == null) {
			return;
		}

		Vec3 origin = caster.position();
		double rangeSqr = COPPER_MAN_DEFENSE_ACTIVATION_SOUND_RANGE_BLOCKS * COPPER_MAN_DEFENSE_ACTIVATION_SOUND_RANGE_BLOCKS;
		long seed = level.getRandom().nextLong();
		Holder<SoundEvent> sound = BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.COPPER_PLACE);
		for (ServerPlayer viewer : level.players()) {
			if (viewer.distanceToSqr(origin) > rangeSqr) {
				continue;
			}
			viewer.connection.send(new ClientboundSoundPacket(
					sound,
					SoundSource.PLAYERS,
					origin.x,
					origin.y,
					origin.z,
					COPPER_MAN_DEFENSE_ACTIVATION_SOUND_VOLUME,
					1.0F,
					seed
			));
		}
	}

	private static void clearCopperManJetpack(ServerPlayer player) {
		clearCopperManJetpack(player, false);
	}

	private static void clearCopperManJetpack(ServerPlayer player, boolean fadeSound) {
		if (player == null) {
			return;
		}

		COPPER_MAN_JETPACK_SESSIONS.remove(player.getUUID());
		COPPER_MAN_JETPACK_SUSPENDED_FOR_DRONE.remove(player.getUUID());
		COPPER_MAN_JETPACK_INPUTS.remove(player.getUUID());
		COPPER_MAN_JETPACK_FLIGHT_MOMENTUM.remove(player.getUUID());
		if (fadeSound) {
			beginCopperManJetpackLandingFade(player);
		} else {
			stopCopperManJetpackFlightSound(player);
		}
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
		COPPER_MAN_JETPACK_NEXT_SOUND_TICKS.clear();
		COPPER_MAN_JETPACK_SOUND_ACTIVE.clear();
		COPPER_MAN_JETPACK_FLIGHT_MOMENTUM.clear();
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

	private static void tickGennadiyRage(MinecraftServer server) {
		if (server == null) {
			return;
		}

		Set<UUID> activeNow = new HashSet<>();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			RaceAbilityConfig stock = getGennadiyStockAbility(player);
			if (!isGennadiyRageActive(player, stock)) {
				continue;
			}

			int hasteLevel = getGennadiyRageHasteLevel(stock);
			if (hasteLevel <= 0) {
				continue;
			}

			int amplifier = hasteLevel - 1;
			UUID playerId = player.getUUID();
			Integer managedAmplifier = GENNADIY_RAGE_HASTE_PLAYERS.get(playerId);
			MobEffectInstance current = player.getEffect(MobEffects.HASTE);

			if (current != null
					&& current.getDuration() == MobEffectInstance.INFINITE_DURATION
					&& managedAmplifier == null
					&& current.getAmplifier() >= amplifier) {
				continue;
			}
			if (current != null
					&& current.getDuration() == MobEffectInstance.INFINITE_DURATION
					&& managedAmplifier != null
					&& managedAmplifier == amplifier
					&& current.getAmplifier() == amplifier) {
				activeNow.add(playerId);
				continue;
			}

			player.addEffect(new MobEffectInstance(MobEffects.HASTE, MobEffectInstance.INFINITE_DURATION, amplifier, false, false, false));
			GENNADIY_RAGE_HASTE_PLAYERS.put(playerId, amplifier);
			activeNow.add(playerId);
		}

		Iterator<Map.Entry<UUID, Integer>> iterator = GENNADIY_RAGE_HASTE_PLAYERS.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<UUID, Integer> entry = iterator.next();
			if (activeNow.contains(entry.getKey())) {
				continue;
			}
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (player != null) {
				clearGennadiyRageHaste(player, entry.getValue());
			}
			iterator.remove();
		}
	}

	private static void clearGennadiyRageHaste(ServerPlayer player, int amplifier) {
		if (player == null) {
			return;
		}
		MobEffectInstance current = player.getEffect(MobEffects.HASTE);
		if (current != null
				&& current.getAmplifier() == amplifier
				&& current.getDuration() == MobEffectInstance.INFINITE_DURATION) {
			player.removeEffect(MobEffects.HASTE);
		}
	}

	private static void tickGennadiyReport(MinecraftServer server) {
		if (server == null) {
			return;
		}
		long nowTick = server.overworld().getGameTime();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			tickGennadiyReportPlayerInventory(player, nowTick);
		}
		if (nowTick % GENNADIY_REPORT_ITEM_SYNC_INTERVAL_TICKS != 0L) {
			return;
		}
		for (ServerLevel level : server.getAllLevels()) {
			for (Entity entity : level.getAllEntities()) {
				if (entity instanceof ItemEntity itemEntity && isGennadiyReportItem(itemEntity.getItem())) {
					enforceGennadiyReportComponents(itemEntity.getItem());
				}
			}
		}
	}

	private static void tickGennadiyReportPlayerInventory(ServerPlayer player, long nowTick) {
		if (player == null) {
			return;
		}
		boolean changed = false;
		Inventory inventory = player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			changed |= tickGennadiyReportHeldStack(player, stack, nowTick);
		}
		if (player.containerMenu != null) {
			changed |= tickGennadiyReportHeldStack(player, player.containerMenu.getCarried(), nowTick);
		}
		player.getCooldowns().removeCooldown(GENNADIY_REPORT_COOLDOWN_GROUP_ID);
		displayHeldGennadiyReportCooldown(player, nowTick);
		if (changed) {
			player.inventoryMenu.broadcastChanges();
		}
	}

	private static boolean tickGennadiyReportHeldStack(ServerPlayer player, ItemStack stack, long nowTick) {
		GennadiyReportData data = readGennadiyReportData(stack);
		if (player == null || data == null) {
			return false;
		}
		boolean changed = false;
		if (!player.getUUID().equals(data.holderId())) {
			resetGennadiyReportCooldown(stack, player, nowTick);
			changed = true;
			data = readGennadiyReportData(stack);
		}
		enforceGennadiyReportComponents(stack);
		return changed;
	}

	private static void resetGennadiyReportCooldown(ItemStack stack, ServerPlayer holder, long nowTick) {
		if (stack == null || stack.isEmpty() || holder == null || readGennadiyReportData(stack) == null) {
			return;
		}
		long cooldownTicks = getGennadiyReportCooldownTicks();
		long cooldownEndTick = nowTick + cooldownTicks;
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
			CompoundTag root = tag.getCompoundOrEmpty(GENNADIY_REPORT_DATA_TAG);
			root.putString(GENNADIY_REPORT_HOLDER_TAG, holder.getUUID().toString());
			root.putLong(GENNADIY_REPORT_COOLDOWN_END_TAG, cooldownEndTick);
			tag.put(GENNADIY_REPORT_DATA_TAG, root);
		});
		enforceGennadiyReportComponents(stack);
		holder.getCooldowns().removeCooldown(GENNADIY_REPORT_COOLDOWN_GROUP_ID);
	}

	private static void displayHeldGennadiyReportCooldown(ServerPlayer player, long nowTick) {
		if (player == null || nowTick % GENNADIY_REPORT_ITEM_SYNC_INTERVAL_TICKS != 0L) {
			return;
		}
		ItemStack stack = player.getMainHandItem();
		GennadiyReportData data = readGennadiyReportData(stack);
		if (data == null) {
			stack = player.getOffhandItem();
			data = readGennadiyReportData(stack);
		}
		if (data == null) {
			return;
		}
		long remainingTicks = data.cooldownEndTick() - nowTick;
		if (remainingTicks > 0L) {
			displayRemainingCooldown(player, remainingTicks);
		}
	}

	private static void clearAllGennadiyReportCooldowns(MinecraftServer server) {
		if (server == null) {
			return;
		}

		boolean changedPlayerInventory;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			changedPlayerInventory = clearGennadiyReportCooldownsInPlayer(player);
			player.getCooldowns().removeCooldown(GENNADIY_REPORT_COOLDOWN_GROUP_ID);
			if (changedPlayerInventory) {
				player.inventoryMenu.broadcastChanges();
			}
		}
		for (ServerLevel level : server.getAllLevels()) {
			for (Entity entity : level.getAllEntities()) {
				if (entity instanceof ItemEntity itemEntity) {
					clearGennadiyReportCooldown(itemEntity.getItem());
				}
			}
			clearGennadiyReportCooldownsInLoadedContainers(level);
		}
	}

	private static boolean clearGennadiyReportCooldownsInPlayer(ServerPlayer player) {
		if (player == null) {
			return false;
		}
		boolean changed = false;
		Inventory inventory = player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			changed |= clearGennadiyReportCooldown(inventory.getItem(slot));
		}
		if (player.containerMenu != null) {
			changed |= clearGennadiyReportCooldown(player.containerMenu.getCarried());
		}
		return changed;
	}

	private static boolean clearGennadiyReportCooldownsInLoadedContainers(ServerLevel level) {
		if (level == null) {
			return false;
		}
		boolean changed = false;
		Set<Long> checkedChunks = new HashSet<>();
		for (ServerPlayer player : level.players()) {
			int centerChunkX = player.chunkPosition().x;
			int centerChunkZ = player.chunkPosition().z;
			for (int chunkX = centerChunkX - GENNADIY_REPORT_CONTAINER_SCAN_CHUNK_RADIUS; chunkX <= centerChunkX + GENNADIY_REPORT_CONTAINER_SCAN_CHUNK_RADIUS; chunkX++) {
				for (int chunkZ = centerChunkZ - GENNADIY_REPORT_CONTAINER_SCAN_CHUNK_RADIUS; chunkZ <= centerChunkZ + GENNADIY_REPORT_CONTAINER_SCAN_CHUNK_RADIUS; chunkZ++) {
					long chunkKey = net.minecraft.world.level.ChunkPos.asLong(chunkX, chunkZ);
					if (!checkedChunks.add(chunkKey)) {
						continue;
					}
					LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
					if (chunk == null) {
						continue;
					}
					for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
						if (blockEntity instanceof Container container) {
							boolean containerChanged = clearGennadiyReportCooldownsInContainer(container);
							if (containerChanged) {
								blockEntity.setChanged();
							}
							changed |= containerChanged;
						}
					}
				}
			}
		}
		return changed;
	}

	private static boolean clearGennadiyReportCooldownsInContainer(Container container) {
		if (container == null) {
			return false;
		}
		boolean changed = false;
		for (int slot = 0; slot < container.getContainerSize(); slot++) {
			changed |= clearGennadiyReportCooldown(container.getItem(slot));
		}
		return changed;
	}

	private static boolean clearGennadiyReportCooldown(ItemStack stack) {
		GennadiyReportData data = readGennadiyReportData(stack);
		if (stack == null || stack.isEmpty() || data == null) {
			return false;
		}
		boolean changed = data.cooldownEndTick() != 0L || stack.has(DataComponents.USE_COOLDOWN);
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
			CompoundTag root = tag.getCompoundOrEmpty(GENNADIY_REPORT_DATA_TAG);
			root.putLong(GENNADIY_REPORT_COOLDOWN_END_TAG, 0L);
			tag.put(GENNADIY_REPORT_DATA_TAG, root);
		});
		enforceGennadiyReportComponents(stack);
		return changed;
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

	private static final class WomanUniqueSession {
		private final UUID targetId;
		private long endTick;
		private long nextDropCheckTick;
		private final long dropMinTicks;
		private final long dropMaxTicks;
		private final double dropChance;
		private final double tradePriceIncrease;
		private boolean naturalHealingUnlocked;
		private FoodDataSnapshot lastFoodSnapshot;

		private WomanUniqueSession(
				UUID targetId,
				long endTick,
				long nextDropCheckTick,
				long dropMinTicks,
				long dropMaxTicks,
				double dropChance,
				double tradePriceIncrease,
				FoodDataSnapshot lastFoodSnapshot
		) {
			this.targetId = targetId;
			this.endTick = endTick;
			this.nextDropCheckTick = nextDropCheckTick;
			this.dropMinTicks = dropMinTicks;
			this.dropMaxTicks = dropMaxTicks;
			this.dropChance = dropChance;
			this.tradePriceIncrease = tradePriceIncrease;
			this.naturalHealingUnlocked = false;
			this.lastFoodSnapshot = lastFoodSnapshot;
		}
	}

	private record WomanShnyagaPendingSelection(UUID targetId, String targetName) {
	}

	private record WomanShnyagaRejectionSession(long endTick) {
	}

	private record WomanShnyagaProposal(
			UUID womanId,
			String womanName,
			UUID targetId,
			String targetName,
			String message,
			double transferHealthPoints,
			double buffRangeBlocks
	) {
	}

	private record WomanShnyagaLink(
			UUID womanId,
			String womanName,
			UUID boyfriendId,
			String boyfriendName,
			double transferHealthPoints,
			double buffRangeBlocks
	) {
	}

	private static final class WomanShnyagaPersistedState {
		private final List<WomanShnyagaPersistedLink> links = new ArrayList<>();
	}

	private static final class WomanShnyagaPersistedLink {
		private UUID womanId;
		private String womanName;
		private UUID boyfriendId;
		private String boyfriendName;
		private double transferHealthPoints;
		private double buffRangeBlocks;

		private WomanShnyagaPersistedLink(
				UUID womanId,
				String womanName,
				UUID boyfriendId,
				String boyfriendName,
				double transferHealthPoints,
				double buffRangeBlocks
		) {
			this.womanId = womanId;
			this.womanName = womanName;
			this.boyfriendId = boyfriendId;
			this.boyfriendName = boyfriendName;
			this.transferHealthPoints = transferHealthPoints;
			this.buffRangeBlocks = buffRangeBlocks;
		}
	}

	private static final class WomanShnyagaWhitelistEntry {
		private String uuid;
		private String name;
	}

	private record WomanShnyagaWhitelistSnapshot(Set<UUID> ids, Set<String> names, boolean active) {
		private boolean contains(UUID playerId, String playerName) {
			if (playerId != null && ids.contains(playerId)) {
				return true;
			}
			String normalizedName = normalizeWhitelistName(playerName);
			return normalizedName != null && names.contains(normalizedName);
		}
	}

	private enum WomanShnyagaBreakReason {
		NONE,
		WOMAN_KILLED_BY_BOYFRIEND,
		BOYFRIEND_REMOVED_FROM_WHITELIST,
		WOMAN_REMOVED_FROM_WHITELIST
	}

	private static final class WomanAttackChargeSession {
		private final UUID playerId;
		private final ResourceKey<Level> dimension;
		private final double radius;
		private final double range;
		private final double damage;
		private final long followTicks;
		private Interaction airTriggerEntity;
		private long nextChargeSoundTick;

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
			this.nextChargeSoundTick = 0L;
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
		private long nextFlightSoundTick;

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
			this.nextFlightSoundTick = 0L;
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

	private enum MarkAxePhase {
		FLYING,
		FALLING,
		STUCK_BLOCK,
		STUCK_ENTITY
	}

	private static final class MarkThrownAxeSession {
		private final UUID ownerId;
		private final ResourceKey<Level> dimension;
		private final ItemStack axeStack;
		private long publicPickupTick;
		private final long bleedIntervalTicks;
		private final double bleedingOneDamage;
		private final double bleedingTwoDamage;
		private final long bleedingTwoDurationTicks;
		private final double impactDamage;
		private UUID displayId;
		private UUID pickupTriggerId;
		private UUID targetId;
		private MarkAxePhase phase;
		private Vec3 position;
		private Vec3 velocity;
		private Vec3 direction;
		private Vec3 targetOffset;
		private Vec3 targetWoundOffset;
		private Vec3 targetLocalDirection;
		private double remainingHorizontalRange;
		private long nextBleedTick;
		private int ageTicks;
		private int stuckSnapTicks;
		private double baseSpinRadians;
		private double spinRadians;
		private double rollRadians;
		private int fallingTicks;
		private boolean cleaned;

		private MarkThrownAxeSession(
				UUID ownerId,
				ResourceKey<Level> dimension,
				ItemStack axeStack,
				long publicPickupTick,
				long bleedIntervalTicks,
				double bleedingOneDamage,
				double bleedingTwoDamage,
				long bleedingTwoDurationTicks,
				double impactDamage,
				Vec3 position,
				Vec3 velocity,
				double range
		) {
			this.ownerId = ownerId;
			this.dimension = dimension;
			this.axeStack = axeStack;
			this.publicPickupTick = publicPickupTick;
			this.bleedIntervalTicks = bleedIntervalTicks;
			this.bleedingOneDamage = bleedingOneDamage;
			this.bleedingTwoDamage = bleedingTwoDamage;
			this.bleedingTwoDurationTicks = Math.max(1L, bleedingTwoDurationTicks);
			this.impactDamage = impactDamage;
			this.position = position;
			this.velocity = velocity;
			this.direction = velocity.lengthSqr() <= 1.0E-6D ? Vec3.ZERO : velocity.normalize();
			this.remainingHorizontalRange = range;
			this.phase = MarkAxePhase.FLYING;
			this.baseSpinRadians = MARK_AXE_HEAD_FIRST_SPIN_RADIANS;
			this.nextBleedTick = Long.MIN_VALUE;
		}
	}

	private static final class MarkBleedingSession {
		private final UUID targetId;
		private final ResourceKey<Level> dimension;
		private final UUID attackerId;
		private final double damage;
		private final long intervalTicks;
		private final long expireTick;
		private final Vec3 woundLocalOffset;
		private long nextDamageTick;

		private MarkBleedingSession(UUID targetId, ResourceKey<Level> dimension, UUID attackerId, double damage, long intervalTicks, long nowTick, long expireTick, Vec3 woundLocalOffset) {
			this.targetId = targetId;
			this.dimension = dimension;
			this.attackerId = attackerId;
			this.damage = damage;
			this.intervalTicks = Math.max(1L, intervalTicks);
			this.expireTick = Math.max(nowTick + 1L, expireTick);
			this.woundLocalOffset = woundLocalOffset;
			this.nextDamageTick = nowTick + this.intervalTicks;
		}
	}

	private static final class GennadiyDonkeyState {
		private final UUID ownerId;
		private ResourceKey<Level> dimension;
		private UUID donkeyId;
		private UUID visualId;
		private UUID helmetVisualId;
		private UUID manualTriggerId;
		private double health;
		private double maxHealth;
		private int ammo;
		private int maxAmmo;
		private double armorDivider;
		private long healthRegenTicks;
		private int ammoRegenAmount;
		private long ammoRegenTicks;
		private double bulletDamage;
		private double bulletRange;
		private double followMaxDistance;
		private long cooldownTicks;
		private long nextHealthRegenTick;
		private long nextAmmoRegenTick;
		private long nextShotTick;
		private long lastManualInputTick;
		private long nextMovementLogicTick;
		private long nextWanderRetargetTick;
		private long nextAggroScanTick;
		private Long outsideSinceTick;
		private Vec3 wanderTarget;
		private UUID targetId;
		private int targetPriority;
		private boolean legacyVisualsCleaned;

		private GennadiyDonkeyState(UUID ownerId) {
			this.ownerId = ownerId;
			this.health = GENNADIY_DONKEY_DEFAULT_HEALTH_POINTS;
			this.maxHealth = GENNADIY_DONKEY_DEFAULT_HEALTH_POINTS;
			this.ammo = GENNADIY_DONKEY_DEFAULT_MAX_AMMO;
			this.maxAmmo = GENNADIY_DONKEY_DEFAULT_MAX_AMMO;
			this.armorDivider = GENNADIY_DONKEY_DEFAULT_ARMOR_DIVIDER;
			this.healthRegenTicks = asTicks(GENNADIY_DONKEY_DEFAULT_HEALTH_REGEN_SECONDS);
			this.ammoRegenAmount = GENNADIY_DONKEY_DEFAULT_AMMO_REGEN_AMOUNT;
			this.ammoRegenTicks = asTicks(GENNADIY_DONKEY_DEFAULT_AMMO_REGEN_SECONDS);
			this.bulletDamage = GENNADIY_DONKEY_DEFAULT_BULLET_DAMAGE;
			this.bulletRange = GENNADIY_DONKEY_DEFAULT_BULLET_RANGE_BLOCKS;
			this.followMaxDistance = GENNADIY_DONKEY_DEFAULT_FOLLOW_MAX_DISTANCE_BLOCKS;
			this.cooldownTicks = asTicks(GENNADIY_DONKEY_DEFAULT_COOLDOWN_SECONDS);
			this.nextHealthRegenTick = Long.MIN_VALUE;
			this.nextAmmoRegenTick = Long.MIN_VALUE;
			this.lastManualInputTick = Long.MIN_VALUE;
			this.legacyVisualsCleaned = false;
		}

		private boolean isActive() {
			return this.donkeyId != null;
		}
	}

	private static final class GennadiyDonkeyBulletVisual {
		private final ResourceKey<Level> dimension;
		private final Vec3 direction;
		private Vec3 position;
		private double remainingDistance;

		private GennadiyDonkeyBulletVisual(ResourceKey<Level> dimension, Vec3 start, Vec3 end) {
			this.dimension = dimension;
			Vec3 delta = end.subtract(start);
			double distance = delta.length();
			this.direction = distance <= 1.0E-6D ? Vec3.ZERO : delta.scale(1.0D / distance);
			this.position = start;
			this.remainingDistance = distance;
		}
	}

	private static final class GennadiyDefenseSession {
		private final UUID playerId;
		private final ResourceKey<Level> dimension;
		private final long endTick;
		private final double knockbackBlocksPerDamage;
		private final double maxKnockbackBlocks;
		private final double waveRange;
		private final double minDamage;
		private final double maxDamage;
		private final float lockedYaw;
		private double accumulatedDamage;
		private UUID fsitSeatId;
		private long nextChargeSoundTick = -1L;

		private GennadiyDefenseSession(
				UUID playerId,
				ResourceKey<Level> dimension,
				long endTick,
				double knockbackBlocksPerDamage,
				double maxKnockbackBlocks,
				double waveRange,
				double minDamage,
				double maxDamage,
				float lockedYaw
		) {
			this.playerId = playerId;
			this.dimension = dimension;
			this.endTick = endTick;
			this.knockbackBlocksPerDamage = knockbackBlocksPerDamage;
			this.maxKnockbackBlocks = maxKnockbackBlocks;
			this.waveRange = waveRange;
			this.minDamage = minDamage;
			this.maxDamage = Math.max(minDamage, maxDamage);
			this.lockedYaw = lockedYaw;
		}
	}

	private static final class GennadiyDefenseWaveVisual {
		private final ResourceKey<Level> dimension;
		private final Vec3 origin;
		private final double range;
		private final long startTick;
		private final long durationTicks;

		private GennadiyDefenseWaveVisual(ResourceKey<Level> dimension, Vec3 origin, double range, long startTick, long durationTicks) {
			this.dimension = dimension;
			this.origin = origin;
			this.range = range;
			this.startTick = startTick;
			this.durationTicks = Math.max(1L, durationTicks);
		}
	}

	private enum GennadiyHookPhase {
		EXTENDING,
		PULLING,
		RETRACTING
	}

	private static final class GennadiyHookSession {
		private final UUID playerId;
		private final ResourceKey<Level> dimension;
		private final Vec3 castDirection;
		private final UUID targetId;
		private final double endpointDistance;
		private final double damage;
		private final long slownessTicks;
		private final List<UUID> segmentIds = new ArrayList<>();
		private UUID hookTipId;
		private GennadiyHookPhase phase = GennadiyHookPhase.EXTENDING;
		private double visibleLength;
		private long nextChainSoundTick = -1L;

		private GennadiyHookSession(
				UUID playerId,
				ResourceKey<Level> dimension,
				Vec3 castDirection,
				UUID targetId,
				double endpointDistance,
				double damage,
				long slownessTicks
		) {
			this.playerId = playerId;
			this.dimension = dimension;
			this.castDirection = castDirection == null || castDirection.lengthSqr() <= 1.0E-6D ? new Vec3(0.0D, 0.0D, 1.0D) : castDirection.normalize();
			this.targetId = targetId;
			this.endpointDistance = Math.max(0.0D, endpointDistance);
			this.damage = Math.max(0.0D, damage);
			this.slownessTicks = Math.max(1L, slownessTicks);
		}
	}

    private record GennadiyReportPending(InteractionHand hand, long expireTick) {
    }

	private record GennadiyReportData(UUID id, UUID holderId, long cooldownEndTick) {
	}

	private record CopperManJetpackInputState(boolean forward, boolean backward, boolean left, boolean right, boolean jump, boolean shift, boolean sprint) {
		private static final CopperManJetpackInputState EMPTY = new CopperManJetpackInputState(false, false, false, false, false, false, false);
	}

	private record CopperManJetpackMovementSample(Vec3 position, long tick) {
	}

	private record CopperManJetpackGroundInertia(Vec3 velocity, long tick) {
	}

	private record CopperManJetpackFlightMomentum(Vec3 inertia, double capSpeed) {
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

	private static InteractionResult onUseEntity(ServerPlayer player, InteractionHand hand, Entity entity, Vec3 location) {
		InteractionResult markAxeInteraction = tryPickupMarkThrownAxe(player, hand, entity);
		if (markAxeInteraction != InteractionResult.PASS) {
			return markAxeInteraction;
		}
		InteractionResult donkeyInteraction = handleGennadiyDonkeyEntityUse(player, hand, entity, location);
		if (donkeyInteraction != InteractionResult.PASS) {
			return donkeyInteraction;
		}
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
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GENERIC_EAT, SoundSource.PLAYERS, 0.9F, 1.0F);
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
		playWomanAnimalBreedStockSound(player);
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

	private static RaceAbilityConfig getWomanShnyagaAbility(ServerPlayer player) {
		Optional<PlayerRaceConfig> raceOptional = getRace(player);
		if (raceOptional.isEmpty()) {
			return null;
		}

		PlayerRaceConfig race = raceOptional.get();
		if (!WOMAN_RACE_ID.equals(sanitizePath(race.id)) || race.shnyaga == null || !race.shnyaga.enabled) {
			return null;
		}
		return race.shnyaga;
	}

	private static double getWomanFlowerCooldownSeconds(RaceAbilityConfig stock) {
		return positiveOrDefault(stock == null ? 0.0D : stock.womanFlowerCooldownSeconds, WOMAN_FLOWER_DEFAULT_COOLDOWN_SECONDS);
	}

	private static double getWomanAnimalBreedCooldownSeconds(RaceAbilityConfig stock) {
		return positiveOrDefault(stock == null ? 0.0D : stock.womanAnimalBreedCooldownSeconds, WOMAN_ANIMAL_BREED_DEFAULT_COOLDOWN_SECONDS);
	}

	private static RaceAbilityConfig getGennadiyStockAbility(ServerPlayer player) {
		Optional<PlayerRaceConfig> raceOptional = getRace(player);
		if (raceOptional.isEmpty()) {
			return null;
		}

		PlayerRaceConfig race = raceOptional.get();
		if (!GENNADIY_SHAROYOBOV_RACE_ID.equals(sanitizePath(race.id)) || race.stock == null || !race.stock.enabled) {
			return null;
		}
		return race.stock;
	}

	private static boolean isGennadiyRageActive(ServerPlayer player, RaceAbilityConfig stock) {
		if (player == null || stock == null || !player.isAlive() || player.isSpectator()) {
			return false;
		}
		double maxHealth = player.getMaxHealth();
		if (maxHealth <= 0.0D) {
			return false;
		}
		double threshold = Math.min(1.0D, getGennadiyRageHealthThresholdRatio(stock));
		return threshold > 0.0D && player.getHealth() / maxHealth < threshold;
	}

	private static double getGennadiyRageHealthThresholdRatio(RaceAbilityConfig stock) {
		return positiveOrDefault(stock == null ? 0.0D : stock.gennadiyRageHealthThresholdRatio, GENNADIY_RAGE_DEFAULT_HEALTH_THRESHOLD_RATIO);
	}

	private static int getGennadiyRageHasteLevel(RaceAbilityConfig stock) {
		return positiveIntOrDefault(stock == null ? 0 : stock.gennadiyRageHasteLevel, GENNADIY_RAGE_DEFAULT_HASTE_LEVEL);
	}

	private static double getGennadiyRageMeleeDamageBonusRatio(RaceAbilityConfig stock) {
		return positiveOrDefault(stock == null ? 0.0D : stock.gennadiyRageMeleeDamageBonusRatio, GENNADIY_RAGE_DEFAULT_MELEE_DAMAGE_BONUS_RATIO);
	}

	private static void playWomanAnimalBreedStockSound(ServerPlayer player) {
		if (player == null) {
			return;
		}
		sendPersonalSound(player, SoundEvents.ALLAY_ITEM_GIVEN, SoundSource.PLAYERS, player.position(), 0.55F, 1.2F, player.level().getGameTime() ^ player.getUUID().getLeastSignificantBits());
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
		emitWomanOmenParticles(level, player.position());
		return 1;
	}

	private static int useWomanUnique(ServerPlayer caster, PlayerRaceConfig race, RaceAbilityConfig ability) {
		if (caster == null || ability == null || caster.isSpectator() || !caster.isAlive()) {
			return 0;
		}
		if (!(caster.level() instanceof ServerLevel level)) {
			return 0;
		}

		double activationRange = getWomanUniqueRange(ability);
		LivingEntity lookedAt = findLookTarget(caster, activationRange);
		if (!(lookedAt instanceof ServerPlayer target) || target == caster) {
			return 0;
		}

		long nowTick = level.getGameTime();
		long durationTicks = Math.max(1L, asTicks(getWomanUniqueDurationSeconds(ability)));
		long dropMinTicks = Math.max(1L, asTicks(getWomanUniqueDropMinSeconds(ability)));
		long dropMaxTicks = Math.max(dropMinTicks, asTicks(getWomanUniqueDropMaxSeconds(ability)));
		double dropChance = getWomanUniqueDropChance(ability);
		double tradePriceIncrease = getWomanUniqueTradePriceIncrease(ability);
		WOMAN_UNIQUE_SESSIONS.put(
				target.getUUID(),
				new WomanUniqueSession(
						target.getUUID(),
						nowTick + durationTicks,
						scheduleWomanUniqueNextDropTick(level, nowTick, dropMinTicks, dropMaxTicks),
						dropMinTicks,
						dropMaxTicks,
						dropChance,
						tradePriceIncrease,
						getFoodDataSnapshot(target)
				)
		);
		WOMAN_UNIQUE_SYNCED_MERCHANT_MENUS.remove(target.getUUID());
		syncWomanUniqueWeakness(target, durationTicks);

		float absorptionAmount = (float) (getWomanUniqueAbsorptionHearts(ability) * 2.0D);
		if (absorptionAmount > 0.0F) {
			int absorptionAmplifier = Math.max(0, (int) Math.ceil(absorptionAmount / 4.0F) - 1);
			caster.addEffect(new MobEffectInstance(
					MobEffects.ABSORPTION,
					(int) Math.min(Integer.MAX_VALUE, durationTicks),
					absorptionAmplifier,
					false,
					false,
					false
			));
			caster.setAbsorptionAmount(Math.max(caster.getAbsorptionAmount(), Math.min(caster.getMaxAbsorption(), absorptionAmount)));
		}
		target.hurtServer(level, level.damageSources().magic(), 4.0F);
		emitSmoke(level, target.position());
		playWomanUniqueActivationSound(caster, target);
		startGenericAbilityCooldown(caster, RaceAbilitySlot.UNIQUE_ABILITY, ability);

		Lg2.LOGGER.info(
				"Player {} used woman unique ability '{}' from race '{}' on {}",
				caster.getGameProfile().name(),
				ability.abilityId,
				race.id,
				target.getGameProfile().name()
		);
		return 1;
	}

	private static int useWomanShnyaga(ServerPlayer caster, PlayerRaceConfig race, RaceAbilityConfig ability) {
		if (caster == null || ability == null || caster.isSpectator() || !caster.isAlive()) {
			return 0;
		}

		List<ServerPlayer> candidates = collectWomanShnyagaCandidates(caster);
		if (candidates.isEmpty()) {
			caster.displayClientMessage(
					Component.literal(localizeWomanShnyagaText(caster, "no_players_online"))
							.withStyle(style -> style.withColor(ChatFormatting.RED).withItalic(false)),
					true
			);
			return 0;
		}

		openWomanShnyagaMenu(caster, candidates, 0, ability);
		Lg2.LOGGER.info(
				"Player {} opened woman shnyaga '{}' menu from race '{}'",
				caster.getGameProfile().name(),
				ability.abilityId,
				race.id
		);
		return 1;
	}

	private static List<ServerPlayer> collectWomanShnyagaCandidates(ServerPlayer caster) {
		if (caster == null || caster.level().getServer() == null) {
			return List.of();
		}

		List<ServerPlayer> candidates = new ArrayList<>();
		for (ServerPlayer player : caster.level().getServer().getPlayerList().getPlayers()) {
			if (player == null || player == caster || !player.isAlive() || player.isSpectator()) {
				continue;
			}
			if (hasWomanShnyagaLink(player.getUUID())) {
				continue;
			}
			WomanShnyagaProposal pending = WOMAN_SHNYAGA_PROPOSALS_BY_TARGET.get(player.getUUID());
			if (pending != null && !caster.getUUID().equals(pending.womanId())) {
				continue;
			}
			candidates.add(player);
		}
		return candidates;
	}

	private static void openWomanShnyagaMenu(ServerPlayer caster, List<ServerPlayer> candidates, int selectedIndex, RaceAbilityConfig ability) {
		if (caster == null) {
			return;
		}
		int normalizedIndex = candidates == null || candidates.isEmpty() ? 0 : Math.floorMod(selectedIndex, candidates.size());
		ServerPlayer selectedPlayer = candidates == null || candidates.isEmpty() ? null : candidates.get(normalizedIndex);
		caster.openMenu(new SimpleMenuProvider(
				(syncId, inventory, menuPlayer) -> new WomanShnyagaMenu(syncId, inventory, caster, ability, normalizedIndex),
				buildWomanShnyagaMenuTitle(caster, selectedPlayer)
		));
	}

	private static int beginWomanShnyagaPrompt(ServerPlayer caster, ServerPlayer target, RaceAbilityConfig ability) {
		if (caster == null || target == null || ability == null) {
			return 0;
		}
		if (hasWomanShnyagaLink(target.getUUID())) {
			caster.displayClientMessage(
					Component.literal(localizeWomanShnyagaText(caster, "target_already_linked"))
							.withStyle(style -> style.withColor(ChatFormatting.RED).withItalic(false)),
					true
			);
			return 0;
		}

		WomanShnyagaProposal pending = WOMAN_SHNYAGA_PROPOSALS_BY_TARGET.get(target.getUUID());
		if (pending != null && !caster.getUUID().equals(pending.womanId())) {
			caster.displayClientMessage(
					Component.literal(localizeWomanShnyagaText(caster, "target_busy"))
							.withStyle(style -> style.withColor(ChatFormatting.RED).withItalic(false)),
					true
			);
			return 0;
		}

		WOMAN_SHNYAGA_PENDING_SELECTIONS.put(
				caster.getUUID(),
				new WomanShnyagaPendingSelection(target.getUUID(), target.getGameProfile().name())
		);
		caster.closeContainer();
		caster.displayClientMessage(
				Component.literal(localizeWomanShnyagaText(caster, "write_prompt"))
						.withStyle(style -> style.withItalic(false)),
				true
		);
		return 1;
	}

	private static void handleWomanShnyagaChatSubmission(ServerPlayer sender, WomanShnyagaPendingSelection selection, String messageText) {
		if (sender == null || selection == null) {
			return;
		}
		if (messageText == null || messageText.isBlank()) {
			sender.displayClientMessage(
					Component.literal(localizeWomanShnyagaText(sender, "write_prompt"))
							.withStyle(style -> style.withColor(ChatFormatting.RED).withItalic(false)),
					true
			);
			return;
		}

		Optional<PlayerRaceConfig> raceOptional = getRace(sender);
		if (raceOptional.isEmpty()) {
			WOMAN_SHNYAGA_PENDING_SELECTIONS.remove(sender.getUUID());
			return;
		}

		PlayerRaceConfig race = raceOptional.get();
		if (!WOMAN_RACE_ID.equals(sanitizePath(race.id)) || race.shnyaga == null || !race.shnyaga.enabled || !hasUnlockedAbility(sender, race, RaceAbilitySlot.SHNYAGA)) {
			WOMAN_SHNYAGA_PENDING_SELECTIONS.remove(sender.getUUID());
			return;
		}

		MinecraftServer server = sender.level().getServer();
		if (server == null) {
			WOMAN_SHNYAGA_PENDING_SELECTIONS.remove(sender.getUUID());
			return;
		}

		ServerPlayer target = server.getPlayerList().getPlayer(selection.targetId());
		if (target == null || !target.isAlive() || target.isSpectator()) {
			WOMAN_SHNYAGA_PENDING_SELECTIONS.remove(sender.getUUID());
			sender.displayClientMessage(
					Component.literal(localizeWomanShnyagaText(sender, "target_unavailable"))
							.withStyle(style -> style.withColor(ChatFormatting.RED).withItalic(false)),
					true
			);
			return;
		}
		if (hasWomanShnyagaLink(target.getUUID())) {
			WOMAN_SHNYAGA_PENDING_SELECTIONS.remove(sender.getUUID());
			sender.displayClientMessage(
					Component.literal(localizeWomanShnyagaText(sender, "target_already_linked"))
							.withStyle(style -> style.withColor(ChatFormatting.RED).withItalic(false)),
					true
			);
			return;
		}

		WomanShnyagaProposal existingForTarget = WOMAN_SHNYAGA_PROPOSALS_BY_TARGET.get(target.getUUID());
		if (existingForTarget != null && !sender.getUUID().equals(existingForTarget.womanId())) {
			WOMAN_SHNYAGA_PENDING_SELECTIONS.remove(sender.getUUID());
			sender.displayClientMessage(
					Component.literal(localizeWomanShnyagaText(sender, "target_busy"))
							.withStyle(style -> style.withColor(ChatFormatting.RED).withItalic(false)),
					true
			);
			return;
		}

		WOMAN_SHNYAGA_PENDING_SELECTIONS.remove(sender.getUUID());
		sender.displayClientMessage(Component.empty(), true);
		if (existingForTarget != null) {
			removeWomanShnyagaProposal(existingForTarget);
		}
		WomanShnyagaProposal proposal = new WomanShnyagaProposal(
				sender.getUUID(),
				sender.getGameProfile().name(),
				target.getUUID(),
				target.getGameProfile().name(),
				messageText,
				getWomanShnyagaTransferHealthPoints(race.shnyaga),
				getWomanShnyagaBuffRange(race.shnyaga)
		);
		WOMAN_SHNYAGA_PROPOSALS_BY_WOMAN
				.computeIfAbsent(sender.getUUID(), ignored -> new LinkedHashMap<>())
				.put(target.getUUID(), proposal);
		WOMAN_SHNYAGA_PROPOSALS_BY_TARGET.put(target.getUUID(), proposal);
		displayWomanShnyagaStatus(sender, "proposal_sent", ChatFormatting.LIGHT_PURPLE);
		target.sendSystemMessage(buildWomanShnyagaProposalMessage(target, proposal));
		playWomanShnyagaMessageSentSound(sender, target);
	}

	private static int handleWomanShnyagaProposalResponse(ServerPlayer target, boolean accept) {
		if (target == null) {
			return 0;
		}
		WomanShnyagaProposal proposal = WOMAN_SHNYAGA_PROPOSALS_BY_TARGET.get(target.getUUID());
		if (proposal == null) {
			displayWomanShnyagaStatus(target, "no_pending_proposal", ChatFormatting.RED);
			return 0;
		}

		MinecraftServer server = target.level().getServer();
		ServerPlayer woman = server == null ? null : server.getPlayerList().getPlayer(proposal.womanId());
		if (server == null || woman == null || !woman.isAlive() || woman.isSpectator()) {
			removeWomanShnyagaProposal(proposal);
			displayWomanShnyagaStatus(target, "proposal_expired", ChatFormatting.RED);
			return 0;
		}
		if (hasWomanShnyagaLink(target.getUUID())) {
			removeWomanShnyagaProposal(proposal);
			displayWomanShnyagaStatus(target, "proposal_expired", ChatFormatting.RED);
			return 0;
		}

		Optional<PlayerRaceConfig> womanRaceOptional = getRace(woman);
		RaceAbilityConfig shnyaga = getWomanShnyagaAbility(woman);
		if (womanRaceOptional.isEmpty() || shnyaga == null || !hasUnlockedAbility(woman, womanRaceOptional.get(), RaceAbilitySlot.SHNYAGA)) {
			removeWomanShnyagaProposal(proposal);
			displayWomanShnyagaStatus(target, "proposal_expired", ChatFormatting.RED);
			return 0;
		}
		long remainingCooldownTicks = getRemainingGenericAbilityCooldownTicks(woman, RaceAbilitySlot.SHNYAGA);
		if (remainingCooldownTicks > 0L || isGenericAbilityOnInfiniteCooldown(woman.getUUID(), RaceAbilitySlot.SHNYAGA)) {
			displayWomanShnyagaStatus(target, "proposal_on_cooldown", ChatFormatting.RED);
			return 0;
		}

		removeWomanShnyagaProposal(proposal);
		startGenericAbilityCooldown(woman, RaceAbilitySlot.SHNYAGA, shnyaga);

		if (accept) {
			createWomanShnyagaLink(server, woman, target, proposal);
			return 1;
		}

		applyWomanShnyagaRejection(woman, target);
		return 1;
	}

	private static boolean hasWomanShnyagaLink(UUID playerId) {
		if (playerId == null) {
			return false;
		}
		LinkedHashMap<UUID, WomanShnyagaLink> womanLinks = WOMAN_SHNYAGA_LINKS_BY_WOMAN.get(playerId);
		return (womanLinks != null && !womanLinks.isEmpty()) || WOMAN_SHNYAGA_LINKS_BY_BOYFRIEND.containsKey(playerId);
	}

	private static WomanShnyagaLink getWomanShnyagaLink(UUID womanId, UUID boyfriendId) {
		if (womanId == null || boyfriendId == null) {
			return null;
		}
		LinkedHashMap<UUID, WomanShnyagaLink> womanLinks = WOMAN_SHNYAGA_LINKS_BY_WOMAN.get(womanId);
		if (womanLinks != null) {
			WomanShnyagaLink byWoman = womanLinks.get(boyfriendId);
			if (byWoman != null) {
				return byWoman;
			}
		}
		WomanShnyagaLink byBoyfriend = WOMAN_SHNYAGA_LINKS_BY_BOYFRIEND.get(boyfriendId);
		if (byBoyfriend != null && womanId.equals(byBoyfriend.womanId())) {
			return byBoyfriend;
		}
		return null;
	}

	private static ServerPlayer resolveWomanShnyagaAttackerPlayer(ServerPlayer victim, DamageSource damageSource) {
		if (damageSource == null) {
			if (victim == null) {
				return null;
			}
			LivingEntity killCredit = victim.getKillCredit();
			return killCredit instanceof ServerPlayer killCreditPlayer ? killCreditPlayer : null;
		}
		Entity attacker = damageSource.getEntity();
		if (attacker instanceof ServerPlayer attackerPlayer) {
			return attackerPlayer;
		}
		Entity direct = damageSource.getDirectEntity();
		if (direct instanceof ServerPlayer directPlayer) {
			return directPlayer;
		}
		if (direct instanceof Projectile projectile && projectile.getOwner() instanceof ServerPlayer projectileOwner) {
			return projectileOwner;
		}
		if (victim == null) {
			return null;
		}
		LivingEntity killCredit = victim.getKillCredit();
		if (killCredit instanceof ServerPlayer killCreditPlayer) {
			return killCreditPlayer;
		}
		return null;
	}

	private static void removeWomanShnyagaProposal(WomanShnyagaProposal proposal) {
		if (proposal == null) {
			return;
		}
		WOMAN_SHNYAGA_PROPOSALS_BY_WOMAN.computeIfPresent(proposal.womanId(), (womanId, proposals) -> {
			proposals.remove(proposal.targetId(), proposal);
			return proposals.isEmpty() ? null : proposals;
		});
		WOMAN_SHNYAGA_PROPOSALS_BY_TARGET.remove(proposal.targetId(), proposal);
	}

	private static void clearWomanShnyagaPendingState(UUID playerId) {
		if (playerId == null) {
			return;
		}
		WOMAN_SHNYAGA_PENDING_SELECTIONS.remove(playerId);
		LinkedHashMap<UUID, WomanShnyagaProposal> proposalsByWoman = WOMAN_SHNYAGA_PROPOSALS_BY_WOMAN.remove(playerId);
		if (proposalsByWoman != null) {
			for (WomanShnyagaProposal proposal : new ArrayList<>(proposalsByWoman.values())) {
				WOMAN_SHNYAGA_PROPOSALS_BY_TARGET.remove(proposal.targetId(), proposal);
			}
		}
		removeWomanShnyagaProposal(WOMAN_SHNYAGA_PROPOSALS_BY_TARGET.get(playerId));
	}

	private static void createWomanShnyagaLink(MinecraftServer server, ServerPlayer woman, ServerPlayer boyfriend, WomanShnyagaProposal proposal) {
		if (server == null || woman == null || boyfriend == null || proposal == null) {
			return;
		}

		WomanShnyagaLink link = new WomanShnyagaLink(
				proposal.womanId(),
				proposal.womanName(),
				proposal.targetId(),
				proposal.targetName(),
				proposal.transferHealthPoints(),
				proposal.buffRangeBlocks()
		);
		WOMAN_SHNYAGA_LINKS_BY_WOMAN
				.computeIfAbsent(link.womanId(), ignored -> new LinkedHashMap<>())
				.put(link.boyfriendId(), link);
		WOMAN_SHNYAGA_LINKS_BY_BOYFRIEND.put(link.boyfriendId(), link);
		saveWomanShnyagaLinks(server);
		syncWomanShnyagaLinks(server);

		sendPersonalSound(woman, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, woman.position(), 0.75F, 1.15F, woman.level().getGameTime() ^ woman.getUUID().getLeastSignificantBits());
		sendPersonalSound(boyfriend, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, boyfriend.position(), 0.75F, 1.15F, boyfriend.level().getGameTime() ^ boyfriend.getUUID().getLeastSignificantBits());
		displayWomanShnyagaStatus(woman, "accepted_woman", ChatFormatting.LIGHT_PURPLE, link.boyfriendName());
		displayWomanShnyagaStatus(boyfriend, "accepted_target", ChatFormatting.LIGHT_PURPLE, link.womanName());
	}

	private static void applyWomanShnyagaRejection(ServerPlayer woman, ServerPlayer target) {
		if (woman == null || target == null) {
			return;
		}
		RaceAbilityConfig shnyaga = getWomanShnyagaAbility(woman);
		float damage = (float) getWomanShnyagaRejectDamagePoints(shnyaga);
		if (damage > 0.0F && woman.level() instanceof ServerLevel level) {
			woman.hurtServer(level, level.damageSources().generic(), damage);
		}
		long debuffTicks = Math.max(1L, asTicks(getWomanShnyagaRejectDebuffSeconds(shnyaga)));
		long nowTick = woman.level().getGameTime();
		WOMAN_SHNYAGA_REJECTION_SESSIONS.put(woman.getUUID(), new WomanShnyagaRejectionSession(nowTick + debuffTicks));
		applyWomanShnyagaRejectionEffects(woman, debuffTicks);
		ServerUpgradeUiSystem.playPurchaseBlockedSound(woman);
		ServerUpgradeUiSystem.playPurchaseBlockedSound(target);
		displayWomanShnyagaStatus(woman, "rejected_woman", ChatFormatting.RED, target.getName().getString());
		displayWomanShnyagaStatus(target, "rejected_target", ChatFormatting.AQUA, woman.getName().getString());
	}

	private static void playWomanShnyagaMessageSentSound(ServerPlayer sender, ServerPlayer target) {
		long seed = sender == null ? 0L : sender.level().getGameTime() ^ sender.getUUID().getMostSignificantBits();
		if (sender != null) {
			sendPersonalSound(sender, SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, sender.position(), 0.45F, 1.25F, seed);
		}
		if (target != null) {
			sendPersonalSound(target, SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, target.position(), 0.55F, 1.05F, seed ^ target.getUUID().getLeastSignificantBits());
		}
	}

	private static Component buildWomanShnyagaProposalMessage(ServerPlayer viewer, WomanShnyagaProposal proposal) {
		MutableComponent root = Component.empty();
		root.append(Component.literal(localizeWomanShnyagaText(viewer, "warning_line")).withStyle(style -> style.withColor(ChatFormatting.RED).withBold(true).withItalic(false)));
		root.append(Component.literal(" "));
		root.append(Component.literal(proposal.womanName()).withStyle(style -> style.withColor(ChatFormatting.LIGHT_PURPLE).withItalic(false)));
		root.append(Component.literal(" " + localizeWomanShnyagaText(viewer, "warning_suffix")).withStyle(style -> style.withColor(ChatFormatting.RED).withItalic(false)));
		root.append(Component.literal("\n\n"));
		root.append(Component.literal("\u00AB" + proposal.message() + "\u00BB").withStyle(style -> style.withColor(ChatFormatting.WHITE).withItalic(false)));
		root.append(Component.literal("\n\n"));
		root.append(
				Component.literal(localizeWomanShnyagaText(viewer, "accept_button"))
						.withStyle(style -> style
								.withColor(0xFF7FD4)
								.withUnderlined(true)
								.withItalic(false)
								.withClickEvent(new ClickEvent.RunCommand("/race woman_shnyaga accept")))
		);
		root.append(Component.literal(" "));
		root.append(
				Component.literal(localizeWomanShnyagaText(viewer, "reject_button"))
						.withStyle(style -> style
								.withColor(0x59B7FF)
								.withUnderlined(true)
								.withItalic(false)
								.withClickEvent(new ClickEvent.RunCommand("/race woman_shnyaga reject")))
		);
		return root;
	}

	private static void displayWomanShnyagaStatus(ServerPlayer player, String key, ChatFormatting color) {
		displayWomanShnyagaStatus(player, key, color, new Object[0]);
	}

	private static void displayWomanShnyagaStatus(ServerPlayer player, String key, ChatFormatting color, Object... args) {
		if (player == null || key == null || color == null) {
			return;
		}
		String text = localizeWomanShnyagaText(player, key);
		if (args != null && args.length > 0) {
			text = String.format(Locale.ROOT, text, args);
		}
		player.displayClientMessage(
				Component.literal(text)
						.withStyle(style -> style.withColor(color).withItalic(false)),
				true
		);
	}

	private static void tickWomanShnyaga(MinecraftServer server) {
		if (server == null) {
			return;
		}
		tickWomanShnyagaRejections(server);
		tickWomanShnyagaPendingSelections(server);
		tickWomanShnyagaDeathBreaks(server);
		long nowTick = server.overworld().getGameTime();
		if (!WOMAN_SHNYAGA_LINKS_BY_WOMAN.isEmpty() && nowTick >= womanShnyagaNextWhitelistCheckTick) {
			pruneWomanShnyagaLinksAgainstWhitelist(server);
			womanShnyagaNextWhitelistCheckTick = nowTick + WOMAN_SHNYAGA_WHITELIST_CHECK_INTERVAL_TICKS;
		}
		syncWomanShnyagaLinks(server);
	}

	private static void tickWomanShnyagaRejections(MinecraftServer server) {
		if (server == null || WOMAN_SHNYAGA_REJECTION_SESSIONS.isEmpty()) {
			return;
		}

		long nowTick = server.overworld().getGameTime();
		Iterator<Map.Entry<UUID, WomanShnyagaRejectionSession>> iterator = WOMAN_SHNYAGA_REJECTION_SESSIONS.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<UUID, WomanShnyagaRejectionSession> entry = iterator.next();
			WomanShnyagaRejectionSession session = entry.getValue();
			if (session == null || nowTick >= session.endTick()) {
				iterator.remove();
				continue;
			}

			ServerPlayer woman = server.getPlayerList().getPlayer(entry.getKey());
			if (woman == null || !woman.isAlive() || woman.isSpectator()) {
				continue;
			}

			applyWomanShnyagaRejectionEffects(woman, session.endTick() - nowTick);
		}
	}

	private static void applyWomanShnyagaRejectionEffects(ServerPlayer woman, long remainingTicks) {
		if (woman == null || remainingTicks <= 0L) {
			return;
		}

		int durationTicks = (int) Math.min((long) Integer.MAX_VALUE, Math.max(1L, remainingTicks));
		woman.addEffect(new MobEffectInstance(MobEffects.HUNGER, durationTicks, 0, false, false, false));
		woman.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, durationTicks, 0, false, false, false));
	}

	private static void tickWomanShnyagaDeathBreaks(MinecraftServer server) {
		if (server == null || WOMAN_SHNYAGA_LINKS_BY_WOMAN.isEmpty()) {
			return;
		}

		List<WomanShnyagaLink> toRemove = new ArrayList<>();
		for (Map.Entry<UUID, LinkedHashMap<UUID, WomanShnyagaLink>> entry : WOMAN_SHNYAGA_LINKS_BY_WOMAN.entrySet()) {
			ServerPlayer woman = server.getPlayerList().getPlayer(entry.getKey());
			if (woman == null || woman.isAlive()) {
				continue;
			}

			ServerPlayer attackerPlayer = resolveWomanShnyagaAttackerPlayer(woman, woman.getLastDamageSource());
			if (attackerPlayer == null) {
				continue;
			}

			WomanShnyagaLink link = getWomanShnyagaLink(woman.getUUID(), attackerPlayer.getUUID());
			if (link != null) {
				toRemove.add(link);
			}
		}

		for (WomanShnyagaLink link : toRemove) {
			dissolveWomanShnyagaLink(server, link, WomanShnyagaBreakReason.WOMAN_KILLED_BY_BOYFRIEND);
		}
	}

	private static void tickWomanShnyagaPendingSelections(MinecraftServer server) {
		if (server == null || WOMAN_SHNYAGA_PENDING_SELECTIONS.isEmpty()) {
			return;
		}

		Iterator<Map.Entry<UUID, WomanShnyagaPendingSelection>> iterator = WOMAN_SHNYAGA_PENDING_SELECTIONS.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<UUID, WomanShnyagaPendingSelection> entry = iterator.next();
			ServerPlayer woman = server.getPlayerList().getPlayer(entry.getKey());
			WomanShnyagaPendingSelection selection = entry.getValue();
			if (woman == null || selection == null || !woman.isAlive() || woman.isSpectator()) {
				iterator.remove();
				continue;
			}

			ServerPlayer target = server.getPlayerList().getPlayer(selection.targetId());
			if (target == null || !target.isAlive() || target.isSpectator()) {
				woman.displayClientMessage(Component.empty(), true);
				displayWomanShnyagaStatus(woman, "target_unavailable", ChatFormatting.RED);
				iterator.remove();
				continue;
			}

			woman.displayClientMessage(
					Component.literal(localizeWomanShnyagaText(woman, "write_prompt"))
							.withStyle(style -> style.withItalic(false)),
					true
			);
		}
	}

	private static void syncWomanShnyagaLinks(MinecraftServer server) {
		if (server == null) {
			return;
		}

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			LinkedHashMap<UUID, WomanShnyagaLink> womanLinks = WOMAN_SHNYAGA_LINKS_BY_WOMAN.get(player.getUUID());
			WomanShnyagaLink boyfriendLink = WOMAN_SHNYAGA_LINKS_BY_BOYFRIEND.get(player.getUUID());
			double womanHealthBonus = 0.0D;
			if (womanLinks != null) {
				for (WomanShnyagaLink womanLink : womanLinks.values()) {
					womanHealthBonus += Math.max(0.0D, womanLink.transferHealthPoints());
				}
			}
			syncWomanShnyagaHealthModifier(
					player,
					WOMAN_SHNYAGA_WOMAN_HEALTH_MODIFIER_ID,
					womanHealthBonus
			);
			syncWomanShnyagaHealthModifier(
					player,
					WOMAN_SHNYAGA_BOYFRIEND_HEALTH_MODIFIER_ID,
					boyfriendLink == null ? 0.0D : -boyfriendLink.transferHealthPoints()
			);
		}

		Set<UUID> buffedNow = new HashSet<>();
		for (LinkedHashMap<UUID, WomanShnyagaLink> womanLinks : WOMAN_SHNYAGA_LINKS_BY_WOMAN.values()) {
			for (WomanShnyagaLink link : womanLinks.values()) {
				ServerPlayer woman = server.getPlayerList().getPlayer(link.womanId());
				ServerPlayer boyfriend = server.getPlayerList().getPlayer(link.boyfriendId());
				if (woman == null || boyfriend == null || !woman.isAlive() || !boyfriend.isAlive() || woman.isSpectator() || boyfriend.isSpectator()) {
					continue;
				}
				if (woman.level() != boyfriend.level()) {
					continue;
				}
				double maxDistance = link.buffRangeBlocks();
				if (boyfriend.distanceToSqr(woman) > maxDistance * maxDistance) {
					continue;
				}

				refreshWomanShnyagaBuff(boyfriend, MobEffects.STRENGTH, 0);
				refreshWomanShnyagaBuff(boyfriend, MobEffects.SPEED, 0);
				refreshWomanShnyagaBuff(boyfriend, MobEffects.REGENERATION, 0);
				refreshWomanShnyagaBuff(boyfriend, MobEffects.HASTE, 1);
				buffedNow.add(boyfriend.getUUID());
			}
		}

		Set<UUID> toClear = new HashSet<>(WOMAN_SHNYAGA_BUFFED_BOYFRIENDS);
		toClear.removeAll(buffedNow);
		for (UUID boyfriendId : toClear) {
			ServerPlayer boyfriend = server.getPlayerList().getPlayer(boyfriendId);
			if (boyfriend != null) {
				clearWomanShnyagaBuffs(boyfriend);
			}
		}
		WOMAN_SHNYAGA_BUFFED_BOYFRIENDS.clear();
		WOMAN_SHNYAGA_BUFFED_BOYFRIENDS.addAll(buffedNow);
	}

	private static void refreshWomanShnyagaBuff(ServerPlayer player, Holder<MobEffect> effect, int amplifier) {
		if (player == null || effect == null) {
			return;
		}
		MobEffectInstance current = player.getEffect(effect);
		if (current != null
				&& current.getAmplifier() == amplifier
				&& current.getDuration() == MobEffectInstance.INFINITE_DURATION) {
			return;
		}
		player.addEffect(new MobEffectInstance(effect, MobEffectInstance.INFINITE_DURATION, amplifier, false, false, false));
	}

	private static void clearWomanShnyagaBuffs(ServerPlayer player) {
		if (player == null) {
			return;
		}
		player.removeEffect(MobEffects.STRENGTH);
		player.removeEffect(MobEffects.SPEED);
		player.removeEffect(MobEffects.REGENERATION);
		player.removeEffect(MobEffects.HASTE);
	}

	private static void syncWomanShnyagaHealthModifier(ServerPlayer player, Identifier modifierId, double amount) {
		if (player == null || modifierId == null) {
			return;
		}
		AttributeInstance attribute = player.getAttribute(Attributes.MAX_HEALTH);
		if (attribute == null) {
			return;
		}

		AttributeModifier current = attribute.getModifier(modifierId);
		if (Math.abs(amount) <= 1.0E-6D) {
			if (current != null) {
				attribute.removeModifier(modifierId);
			}
		} else if (current == null
				|| current.operation() != AttributeModifier.Operation.ADD_VALUE
				|| Double.compare(current.amount(), amount) != 0) {
			if (current != null) {
				attribute.removeModifier(modifierId);
			}
			attribute.addTransientModifier(new AttributeModifier(modifierId, amount, AttributeModifier.Operation.ADD_VALUE));
		}
		if (player.getHealth() > player.getMaxHealth()) {
			player.setHealth(player.getMaxHealth());
		}
	}

	private static void pruneWomanShnyagaLinksAgainstWhitelist(MinecraftServer server) {
		if (server == null || WOMAN_SHNYAGA_LINKS_BY_WOMAN.isEmpty()) {
			return;
		}
		WomanShnyagaWhitelistSnapshot whitelist = readWomanShnyagaWhitelist(server);
		if (whitelist == null || !whitelist.active()) {
			return;
		}

		LinkedHashMap<WomanShnyagaLink, WomanShnyagaBreakReason> toRemove = new LinkedHashMap<>();
		for (LinkedHashMap<UUID, WomanShnyagaLink> womanLinks : WOMAN_SHNYAGA_LINKS_BY_WOMAN.values()) {
			for (WomanShnyagaLink link : womanLinks.values()) {
				if (!whitelist.contains(link.womanId(), link.womanName())) {
					toRemove.put(link, WomanShnyagaBreakReason.WOMAN_REMOVED_FROM_WHITELIST);
					continue;
				}
				if (!whitelist.contains(link.boyfriendId(), link.boyfriendName())) {
					toRemove.put(link, WomanShnyagaBreakReason.BOYFRIEND_REMOVED_FROM_WHITELIST);
				}
			}
		}
		for (Map.Entry<WomanShnyagaLink, WomanShnyagaBreakReason> entry : toRemove.entrySet()) {
			dissolveWomanShnyagaLink(server, entry.getKey(), entry.getValue());
		}
	}

	private static WomanShnyagaWhitelistSnapshot readWomanShnyagaWhitelist(MinecraftServer server) {
		if (server == null) {
			return null;
		}
		Set<UUID> ids = new HashSet<>();
		Set<String> names = new HashSet<>();

		try {
			server.getPlayerList().reloadWhiteList();
		} catch (Exception exception) {
			Lg2.LOGGER.warn("Failed to reload whitelist for woman shnyaga state", exception);
		}

		try {
			for (net.minecraft.server.players.UserWhiteListEntry entry : server.getPlayerList().getWhiteList().getEntries()) {
				if (entry == null || entry.getUser() == null) {
					continue;
				}
				net.minecraft.server.players.NameAndId user = entry.getUser();
				if (user.id() != null) {
					ids.add(user.id());
				}
				addWomanShnyagaWhitelistName(names, user.name());
			}
		} catch (Exception exception) {
			Lg2.LOGGER.warn("Failed to read loaded whitelist for woman shnyaga state", exception);
		}

		Path whitelistPath = server.getPlayerList().getWhiteList().getFile().toPath();
		boolean hasWhitelistFile = Files.exists(whitelistPath);
		if (!hasWhitelistFile) {
			whitelistPath = server.getWorldPath(LevelResource.ROOT).getParent().resolve("whitelist.json");
			hasWhitelistFile = Files.exists(whitelistPath);
		}
		if (hasWhitelistFile) {
			readWomanShnyagaWhitelistFile(whitelistPath, ids, names);
		}
		return new WomanShnyagaWhitelistSnapshot(
				ids,
				names,
				server.getPlayerList().isUsingWhitelist() || hasWhitelistFile
		);
	}

	private static void readWomanShnyagaWhitelistFile(Path whitelistPath, Set<UUID> ids, Set<String> names) {
		if (whitelistPath == null || ids == null || names == null) {
			return;
		}
		try (Reader reader = Files.newBufferedReader(whitelistPath)) {
			WomanShnyagaWhitelistEntry[] entries = GSON.fromJson(reader, WomanShnyagaWhitelistEntry[].class);
			if (entries == null) {
				return;
			}
			for (WomanShnyagaWhitelistEntry entry : entries) {
				if (entry == null) {
					continue;
				}
				if (entry.uuid != null && !entry.uuid.isBlank()) {
					try {
						ids.add(UUID.fromString(entry.uuid.trim()));
					} catch (IllegalArgumentException ignored) {
					}
				}
				addWomanShnyagaWhitelistName(names, entry.name);
			}
		} catch (IOException exception) {
			Lg2.LOGGER.warn("Failed to read whitelist file {} for woman shnyaga state", whitelistPath, exception);
		}
	}

	private static void addWomanShnyagaWhitelistName(Set<String> names, String name) {
		String normalizedName = normalizeWhitelistName(name);
		if (normalizedName != null) {
			names.add(normalizedName);
		}
	}

	private static String normalizeWhitelistName(String name) {
		if (name == null) {
			return null;
		}
		String normalized = name.trim();
		return normalized.isEmpty() ? null : normalized.toLowerCase(Locale.ROOT);
	}

	private static void dissolveWomanShnyagaLink(MinecraftServer server, WomanShnyagaLink link, WomanShnyagaBreakReason reason) {
		if (link == null) {
			return;
		}
		WOMAN_SHNYAGA_LINKS_BY_WOMAN.computeIfPresent(link.womanId(), (womanId, links) -> {
			links.remove(link.boyfriendId(), link);
			return links.isEmpty() ? null : links;
		});
		WOMAN_SHNYAGA_LINKS_BY_BOYFRIEND.remove(link.boyfriendId(), link);
		saveWomanShnyagaLinks(server);
		syncWomanShnyagaLinks(server);

		ServerPlayer woman = server == null ? null : server.getPlayerList().getPlayer(link.womanId());
		ServerPlayer boyfriend = server == null ? null : server.getPlayerList().getPlayer(link.boyfriendId());
		if (boyfriend != null && boyfriend.isAlive()) {
			boyfriend.setHealth(Math.min(boyfriend.getMaxHealth(), boyfriend.getHealth() + (float) link.transferHealthPoints()));
			clearWomanShnyagaBuffs(boyfriend);
		}
		if (woman != null && woman.isAlive() && woman.getHealth() > woman.getMaxHealth()) {
			woman.setHealth(woman.getMaxHealth());
		}

		if (server == null || reason == WomanShnyagaBreakReason.NONE) {
			return;
		}
		if (reason == WomanShnyagaBreakReason.WOMAN_KILLED_BY_BOYFRIEND) {
			if (woman != null) {
				displayWomanShnyagaStatus(woman, "link_broken_kill_woman", ChatFormatting.RED, link.boyfriendName());
			}
			if (boyfriend != null) {
				displayWomanShnyagaStatus(boyfriend, "link_broken_kill_target", ChatFormatting.AQUA, link.womanName());
			}
			return;
		}
		if (reason == WomanShnyagaBreakReason.BOYFRIEND_REMOVED_FROM_WHITELIST) {
			if (woman != null) {
				displayWomanShnyagaStatus(woman, "link_broken_whitelist_woman", ChatFormatting.RED, link.boyfriendName());
			}
			if (boyfriend != null) {
				displayWomanShnyagaStatus(boyfriend, "link_broken_whitelist_target", ChatFormatting.RED, link.womanName());
			}
			return;
		}
		if (reason == WomanShnyagaBreakReason.WOMAN_REMOVED_FROM_WHITELIST) {
			if (boyfriend != null) {
				displayWomanShnyagaStatus(boyfriend, "link_broken_whitelist_target_woman_removed", ChatFormatting.RED, link.womanName());
			}
		}
	}

	private static void saveWomanShnyagaLinks(MinecraftServer server) {
		if (server == null) {
			return;
		}
		Path path = getWomanShnyagaStatePath(server);
		WomanShnyagaPersistedState state = new WomanShnyagaPersistedState();
		for (LinkedHashMap<UUID, WomanShnyagaLink> womanLinks : WOMAN_SHNYAGA_LINKS_BY_WOMAN.values()) {
			for (WomanShnyagaLink link : womanLinks.values()) {
				state.links.add(new WomanShnyagaPersistedLink(
						link.womanId(),
						link.womanName(),
						link.boyfriendId(),
						link.boyfriendName(),
						link.transferHealthPoints(),
						link.buffRangeBlocks()
				));
			}
		}
		try {
			Files.createDirectories(path.getParent());
			try (Writer writer = Files.newBufferedWriter(path)) {
				GSON.toJson(state, writer);
			}
		} catch (IOException exception) {
			Lg2.LOGGER.error("Failed to save woman shnyaga state to {}", path, exception);
		}
	}

	private static void loadWomanShnyagaLinks(MinecraftServer server) {
		WOMAN_SHNYAGA_LINKS_BY_WOMAN.clear();
		WOMAN_SHNYAGA_LINKS_BY_BOYFRIEND.clear();
		if (server == null) {
			return;
		}

		Path path = getWomanShnyagaStatePath(server);
		if (!Files.exists(path)) {
			return;
		}

		try (Reader reader = Files.newBufferedReader(path)) {
			WomanShnyagaPersistedState state = GSON.fromJson(reader, WomanShnyagaPersistedState.class);
			if (state == null || state.links == null) {
				return;
			}
			for (WomanShnyagaPersistedLink stored : state.links) {
				if (stored == null || stored.womanId == null || stored.boyfriendId == null || stored.womanId.equals(stored.boyfriendId)) {
					continue;
				}
				WomanShnyagaLink link = new WomanShnyagaLink(
						stored.womanId,
						nonBlank(stored.womanName, ""),
						stored.boyfriendId,
						nonBlank(stored.boyfriendName, ""),
						Math.max(0.0D, stored.transferHealthPoints),
						Math.max(0.0D, stored.buffRangeBlocks)
				);
				if (WOMAN_SHNYAGA_LINKS_BY_BOYFRIEND.containsKey(link.boyfriendId())) {
					continue;
				}
				WOMAN_SHNYAGA_LINKS_BY_WOMAN
						.computeIfAbsent(link.womanId(), ignored -> new LinkedHashMap<>())
						.put(link.boyfriendId(), link);
				WOMAN_SHNYAGA_LINKS_BY_BOYFRIEND.put(link.boyfriendId(), link);
			}
		} catch (Exception exception) {
			Lg2.LOGGER.error("Failed to load woman shnyaga state from {}", path, exception);
		}
	}

	private static Path getWomanShnyagaStatePath(MinecraftServer server) {
		return server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(WOMAN_SHNYAGA_STATE_FILE_NAME);
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
		WomanAttackChargeSession session = new WomanAttackChargeSession(playerId, level.dimension(), radius, range, damage, followTicks);
		WOMAN_ATTACK_CHARGE_SESSIONS.put(playerId, session);
		Vec3 center = player.getEyePosition().add(player.getLookAngle().normalize().scale(WOMAN_ATTACK_CHARGE_FORWARD_OFFSET));
		playWomanAttackChargeSound(level, session, center, level.getGameTime());
		return 1;
	}

	private static void tickWomanUnique(MinecraftServer server) {
		if (server == null || WOMAN_UNIQUE_SESSIONS.isEmpty()) {
			return;
		}

		long nowTick = server.overworld().getGameTime();
		Iterator<Map.Entry<UUID, WomanUniqueSession>> iterator = WOMAN_UNIQUE_SESSIONS.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<UUID, WomanUniqueSession> entry = iterator.next();
			UUID targetId = entry.getKey();
			WomanUniqueSession session = entry.getValue();
			ServerPlayer target = server.getPlayerList().getPlayer(targetId);
			if (target == null || nowTick >= session.endTick) {
				cleanupWomanUniqueState(server, targetId);
				iterator.remove();
				continue;
			}

			updateWomanUniqueHealingUnlockByFood(target, session);
			if (target.isAlive() && !target.isSpectator()) {
				syncWomanUniqueWeakness(target, session.endTick - nowTick);
				processWomanUniqueDrop(target, session, nowTick);
			}
			syncWomanUniqueMerchantOffers(target, session);
		}
	}

	private static void clearWomanUniqueState(MinecraftServer server, UUID targetId) {
		if (targetId == null) {
			return;
		}
		WOMAN_UNIQUE_SESSIONS.remove(targetId);
		cleanupWomanUniqueState(server, targetId);
	}

	private static void cleanupWomanUniqueState(MinecraftServer server, UUID targetId) {
		if (targetId == null) {
			return;
		}
		WOMAN_UNIQUE_SYNCED_MERCHANT_MENUS.remove(targetId);
		if (server == null) {
			return;
		}
		ServerPlayer target = server.getPlayerList().getPlayer(targetId);
		if (target != null) {
			restoreWomanUniqueMerchantOffers(target);
		}
	}

	private static void updateWomanUniqueHealingUnlockByFood(ServerPlayer target, WomanUniqueSession session) {
		if (target == null || session == null || session.naturalHealingUnlocked) {
			return;
		}

		FoodDataSnapshot currentSnapshot = getFoodDataSnapshot(target);
		if (currentSnapshot == null) {
			return;
		}

		FoodDataSnapshot previousSnapshot = session.lastFoodSnapshot;
		if (previousSnapshot != null
				&& (currentSnapshot.foodLevel() > previousSnapshot.foodLevel()
				|| currentSnapshot.saturationLevel() > previousSnapshot.saturationLevel() + 1.0E-4F)) {
			session.naturalHealingUnlocked = true;
		}
		session.lastFoodSnapshot = currentSnapshot;
	}

	private static void syncWomanUniqueWeakness(ServerPlayer target, long remainingTicks) {
		if (target == null || remainingTicks <= 0L) {
			return;
		}

		int durationTicks = (int) Math.min((long) Integer.MAX_VALUE, Math.max(1L, remainingTicks));
		MobEffectInstance current = target.getEffect(MobEffects.WEAKNESS);
		if (current != null
				&& current.getAmplifier() > 0
				&& current.getDuration() > Math.min(20, durationTicks)) {
			return;
		}
		if (current != null
				&& current.getAmplifier() == 0
				&& current.getDuration() >= Math.max(0, durationTicks - 20)) {
			return;
		}
		target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, durationTicks, 0, false, false, false));
	}

	public static boolean shouldBlockWomanUniqueNaturalHealing(ServerPlayer player) {
		if (player == null) {
			return false;
		}
		WomanUniqueSession session = WOMAN_UNIQUE_SESSIONS.get(player.getUUID());
		return session != null && !session.naturalHealingUnlocked;
	}

	public static void onPlayerConsumedFood(ServerPlayer player) {
		if (player == null) {
			return;
		}
		WomanUniqueSession session = WOMAN_UNIQUE_SESSIONS.get(player.getUUID());
		if (session == null) {
			return;
		}
		session.naturalHealingUnlocked = true;
		FoodDataSnapshot snapshot = getFoodDataSnapshot(player);
		if (snapshot != null) {
			session.lastFoodSnapshot = snapshot;
		}
	}

	private static long scheduleWomanUniqueNextDropTick(ServerLevel level, long nowTick, long minTicks, long maxTicks) {
		long min = Math.max(1L, minTicks);
		long max = Math.max(min, maxTicks);
		if (level == null || max == min) {
			return nowTick + min;
		}
		long delta = max - min;
		long offset = Math.round(level.random.nextDouble() * (double) delta);
		return nowTick + min + offset;
	}

	private static void processWomanUniqueDrop(ServerPlayer target, WomanUniqueSession session, long nowTick) {
		if (target == null || session == null || nowTick < session.nextDropCheckTick) {
			return;
		}

		if (target.getRandom().nextDouble() < session.dropChance) {
			dropRandomWomanUniqueInventoryItem(target);
		}
		session.nextDropCheckTick = scheduleWomanUniqueNextDropTick(target.level(), nowTick, session.dropMinTicks, session.dropMaxTicks);
	}

	private static boolean dropRandomWomanUniqueInventoryItem(ServerPlayer player) {
		if (player == null) {
			return false;
		}

		Inventory inventory = player.getInventory();
		int size = Math.min(WOMAN_UNIQUE_MAIN_INVENTORY_SIZE, inventory.getContainerSize());
		List<Integer> candidates = new ArrayList<>();
		for (int slot = 0; slot < size; slot++) {
			if (!inventory.getItem(slot).isEmpty()) {
				candidates.add(slot);
			}
		}
		if (candidates.isEmpty()) {
			return false;
		}

		int slot = candidates.get(player.getRandom().nextInt(candidates.size()));
		ItemStack dropped = inventory.removeItemNoUpdate(slot);
		if (dropped.isEmpty()) {
			return false;
		}
		inventory.setChanged();
		player.containerMenu.broadcastChanges();
		player.drop(dropped, false);
		return true;
	}

	private static void syncWomanUniqueMerchantOffers(ServerPlayer target, WomanUniqueSession session) {
		if (target == null || session == null) {
			return;
		}
		if (!(target.containerMenu instanceof MerchantMenu menu)) {
			WOMAN_UNIQUE_SYNCED_MERCHANT_MENUS.remove(target.getUUID());
			return;
		}

		Integer syncedContainerId = WOMAN_UNIQUE_SYNCED_MERCHANT_MENUS.get(target.getUUID());
		if (syncedContainerId != null && syncedContainerId == menu.containerId) {
			return;
		}

		Merchant merchant = ((MerchantMenuAccessor) menu).lg2$getTrader();
		if (merchant == null) {
			return;
		}
		MerchantOffers originalOffers = merchant.getOffers();
		if (originalOffers == null) {
			return;
		}
		MerchantOffers adjustedOffers = buildWomanUniqueAdjustedOffers(originalOffers, session.tradePriceIncrease);
		menu.setOffers(adjustedOffers);
		target.sendMerchantOffers(
				menu.containerId,
				adjustedOffers,
				menu.getTraderLevel(),
				menu.getTraderXp(),
				menu.showProgressBar(),
				menu.canRestock()
		);
		WOMAN_UNIQUE_SYNCED_MERCHANT_MENUS.put(target.getUUID(), menu.containerId);
	}

	private static void restoreWomanUniqueMerchantOffers(ServerPlayer target) {
		if (target == null || !(target.containerMenu instanceof MerchantMenu menu)) {
			return;
		}

		Merchant merchant = ((MerchantMenuAccessor) menu).lg2$getTrader();
		if (merchant == null) {
			return;
		}
		MerchantOffers originalOffers = merchant.getOffers();
		if (originalOffers == null) {
			return;
		}
		MerchantOffers restored = originalOffers.copy();
		menu.setOffers(restored);
		target.sendMerchantOffers(
				menu.containerId,
				restored,
				menu.getTraderLevel(),
				menu.getTraderXp(),
				menu.showProgressBar(),
				menu.canRestock()
		);
	}

	private static MerchantOffers buildWomanUniqueAdjustedOffers(MerchantOffers originalOffers, double tradePriceIncrease) {
		MerchantOffers adjusted = originalOffers == null ? new MerchantOffers() : originalOffers.copy();
		if (tradePriceIncrease <= 0.0D) {
			return adjusted;
		}

		for (MerchantOffer offer : adjusted) {
			if (offer == null) {
				continue;
			}
			int baseCount = offer.getBaseCostA().getCount();
			if (baseCount <= 0) {
				continue;
			}
			int extra = Math.max(1, (int) Math.ceil(baseCount * tradePriceIncrease));
			offer.addToSpecialPriceDiff(extra);
		}
		return adjusted;
	}

	private static void tickWomanDefense(MinecraftServer server) {
		if (server == null) {
			return;
		}
		if (WOMAN_DEFENSE_SESSIONS.isEmpty() && WOMAN_DEFENSE_BLIND_PLAYERS.isEmpty() && WOMAN_DEFENSE_SLOWNESS_PLAYERS.isEmpty()) {
			WOMAN_DEFENSE_SELF_SOUND_NEXT_TICKS.clear();
			return;
		}

		long nowTick = server.overworld().getGameTime();
		Map<UUID, WomanDefenseExposure> exposures = new HashMap<>();
		Map<UUID, Integer> slownessPlayers = new HashMap<>();
		Set<UUID> blindPlayers = new HashSet<>();
		Set<UUID> exposedPlayers = new HashSet<>();
		Set<UUID> activeDefenseWomen = new HashSet<>();
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
			activeDefenseWomen.add(woman.getUUID());
			emitWomanDefenseOmenAura(level, woman, nowTick);
			playWomanDefenseSelfSound(woman, nowTick);
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
			exposedPlayers.add(entry.getKey());
			applyWomanDefenseExposure(viewer, entry.getValue());
			playWomanDefensePanicSound(viewer, entry.getValue().severity(), nowTick);
			slownessPlayers.put(entry.getKey(), getWomanDefenseSlownessAmplifier(entry.getValue().severity()));
			if (entry.getValue().severity() >= 4) {
				blindPlayers.add(entry.getKey());
			}
		}
		WOMAN_DEFENSE_PANIC_SOUND_NEXT_TICKS.keySet().removeIf(playerId -> !exposedPlayers.contains(playerId));
		WOMAN_DEFENSE_SELF_SOUND_NEXT_TICKS.keySet().removeIf(playerId -> !activeDefenseWomen.contains(playerId));
		syncWomanDefenseSlowness(server, slownessPlayers);
		syncWomanDefenseBlindness(server, blindPlayers);
		WOMAN_DEFENSE_LAST_POSITIONS.entrySet().removeIf(entry -> !exposedPlayers.contains(entry.getKey()));
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

		double shakeStrength = switch (exposure.severity()) {
			case 4 -> WOMAN_DEFENSE_VERY_STRONG_SHAKE_STRENGTH;
			case 3 -> WOMAN_DEFENSE_STRONG_SHAKE_STRENGTH;
			case 2 -> WOMAN_DEFENSE_MEDIUM_SHAKE_STRENGTH;
			default -> WOMAN_DEFENSE_LIGHT_SHAKE_STRENGTH;
		};
		applyWomanDefenseShake(viewer, shakeStrength);
	}

	private static int getWomanDefenseSlownessAmplifier(int severity) {
		return switch (severity) {
			case 4, 3 -> 2;
			case 2 -> 1;
			default -> 0;
		};
	}

	private static void playWomanDefensePanicSound(ServerPlayer viewer, int severity, long nowTick) {
		if (viewer == null || viewer.connection == null) {
			return;
		}

		long nextTick = WOMAN_DEFENSE_PANIC_SOUND_NEXT_TICKS.getOrDefault(viewer.getUUID(), Long.MIN_VALUE);
		if (nowTick < nextTick) {
			return;
		}

		float volume = switch (severity) {
			case 4 -> 0.85F;
			case 3 -> 0.7F;
			case 2 -> 0.58F;
			default -> 0.45F;
		};
		float pitch = switch (severity) {
			case 4 -> 0.62F;
			case 3 -> 0.72F;
			case 2 -> 0.82F;
			default -> 0.92F;
		};
		sendPersonalSound(viewer, SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, viewer.position(), volume, pitch, nowTick ^ viewer.getUUID().getLeastSignificantBits());
		WOMAN_DEFENSE_PANIC_SOUND_NEXT_TICKS.put(viewer.getUUID(), nowTick + WOMAN_DEFENSE_PANIC_SOUND_INTERVAL_TICKS);
	}

	private static void playWomanDefenseSelfSound(ServerPlayer woman, long nowTick) {
		if (woman == null || woman.connection == null) {
			return;
		}

		long nextTick = WOMAN_DEFENSE_SELF_SOUND_NEXT_TICKS.getOrDefault(woman.getUUID(), Long.MIN_VALUE);
		if (nowTick < nextTick) {
			return;
		}

		sendPersonalSound(woman, SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, woman.position(), 0.36F, 0.98F, nowTick ^ woman.getUUID().getMostSignificantBits());
		WOMAN_DEFENSE_SELF_SOUND_NEXT_TICKS.put(woman.getUUID(), nowTick + WOMAN_DEFENSE_PANIC_SOUND_INTERVAL_TICKS);
	}

	private static void refreshWomanDefenseEffect(ServerPlayer viewer, Holder<MobEffect> effect, int durationTicks, int amplifier, boolean showIcon) {
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
		viewer.addEffect(new MobEffectInstance(effect, durationTicks, amplifier, false, false, showIcon));
	}

	private static void syncWomanDefenseBlindness(MinecraftServer server, Set<UUID> desiredBlindPlayers) {
		if (server == null) {
			return;
		}

		WOMAN_DEFENSE_BLIND_PLAYERS.removeIf(playerId -> {
			if (desiredBlindPlayers.contains(playerId)) {
				return false;
			}
			ServerPlayer player = server.getPlayerList().getPlayer(playerId);
			if (player != null) {
				player.removeEffect(MobEffects.BLINDNESS);
			}
			return true;
		});

		for (UUID playerId : desiredBlindPlayers) {
			ServerPlayer player = server.getPlayerList().getPlayer(playerId);
			if (player == null || !player.isAlive() || player.isSpectator()) {
				continue;
			}

			MobEffectInstance current = player.getEffect(MobEffects.BLINDNESS);
			if (current == null
					|| current.getAmplifier() != 0
					|| current.getDuration() != MobEffectInstance.INFINITE_DURATION) {
				player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, MobEffectInstance.INFINITE_DURATION, 0, false, false, false));
			}
			WOMAN_DEFENSE_BLIND_PLAYERS.add(playerId);
		}
	}

	private static void syncWomanDefenseSlowness(MinecraftServer server, Map<UUID, Integer> desiredSlownessPlayers) {
		if (server == null) {
			return;
		}

		WOMAN_DEFENSE_SLOWNESS_PLAYERS.entrySet().removeIf(entry -> {
			Integer desiredAmplifier = desiredSlownessPlayers.get(entry.getKey());
			if (desiredAmplifier != null && desiredAmplifier.equals(entry.getValue())) {
				return false;
			}
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (player != null) {
				MobEffectInstance current = player.getEffect(MobEffects.SLOWNESS);
				if (current != null
						&& current.getAmplifier() == entry.getValue()
						&& current.getDuration() == MobEffectInstance.INFINITE_DURATION) {
					player.removeEffect(MobEffects.SLOWNESS);
				}
			}
			return true;
		});

		for (Map.Entry<UUID, Integer> entry : desiredSlownessPlayers.entrySet()) {
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (player == null || !player.isAlive() || player.isSpectator()) {
				continue;
			}

			int amplifier = Math.max(0, entry.getValue());
			MobEffectInstance current = player.getEffect(MobEffects.SLOWNESS);
			if (current == null
					|| current.getAmplifier() != amplifier
					|| current.getDuration() != MobEffectInstance.INFINITE_DURATION) {
				player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, MobEffectInstance.INFINITE_DURATION, amplifier, false, false, false));
			}
			WOMAN_DEFENSE_SLOWNESS_PLAYERS.put(entry.getKey(), amplifier);
		}
	}

	private static void applyWomanDefenseShake(ServerPlayer viewer, double strength) {
		if (viewer == null || strength <= 0.0D) {
			return;
		}
		long tick = viewer.level().getGameTime() + viewer.getUUID().getLeastSignificantBits();
		if (Math.floorMod(tick, WOMAN_DEFENSE_SHAKE_INTERVAL_TICKS) != 0L) {
			return;
		}

		Vec3 currentPosition = viewer.position();
		Vec3 previousPosition = WOMAN_DEFENSE_LAST_POSITIONS.put(viewer.getUUID(), currentPosition);
		if (previousPosition == null) {
			return;
		}

		Vec3 horizontalMovement = new Vec3(currentPosition.x - previousPosition.x, 0.0D, currentPosition.z - previousPosition.z);
		double moveSpeed = horizontalMovement.length();
		if (moveSpeed < WOMAN_DEFENSE_SHAKE_MIN_MOVE_SPEED) {
			return;
		}
		Vec3 look = viewer.getLookAngle();
		Vec3 basis = new Vec3(look.x, 0.0D, look.z);
		if (basis.lengthSqr() <= 1.0E-6D) {
			basis = horizontalMovement;
		}
		if (basis.lengthSqr() <= 1.0E-6D) {
			return;
		}
		Vec3 lateral = new Vec3(-basis.normalize().z, 0.0D, basis.normalize().x);
		double maxOffset = Math.min(strength, moveSpeed * WOMAN_DEFENSE_SHAKE_MOVEMENT_RATIO);
		double lateralOffset = Math.floorMod(tick / WOMAN_DEFENSE_SHAKE_INTERVAL_TICKS, 2L) == 0L ? maxOffset : -maxOffset;
		viewer.push(lateral.x * lateralOffset, 0.0D, lateral.z * lateralOffset);
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
		syncGennadiyDonkeyManualTrigger(player);
		syncGennadiyDonkeyManualVisual(player);
		syncGennadiyDefenseHeadLock(player);
		WomanAttackChargeSession session = WOMAN_ATTACK_CHARGE_SESSIONS.get(player.getUUID());
		if (session != null) {
			syncWomanAttackAirTrigger(player, session);
		}
	}

	public static void handlePlayerInputPacket(ServerPlayer player) {
		syncGennadiyDefenseHeadLock(player);
	}

	public static boolean handleGennadiyDefenseMovementPacket(ServerPlayer player) {
		if (player == null || !isGennadiyDefenseActive(player)) {
			return false;
		}
		syncGennadiyDefenseHeadLock(player);
		return true;
	}

	public static void handleVehicleMovePacket(ServerPlayer player) {
		if (player == null) {
			return;
		}
		syncGennadiyDonkeyManualTrigger(player);
		syncGennadiyDonkeyManualVisual(player);
		syncGennadiyDefenseHeadLock(player);
	}

	public static void handleVehicleMovePacket(ServerPlayer player, float vehicleYaw) {
		if (player == null) {
			return;
		}
		syncGennadiyDonkeyManualTrigger(player);
		syncGennadiyDonkeyManualVisual(player, vehicleYaw, true);
		syncGennadiyDefenseHeadLock(player);
	}

	private static void syncGennadiyDonkeyManualVisual(ServerPlayer player) {
		syncGennadiyDonkeyManualVisual(player, 0.0F, false);
	}

	private static void syncGennadiyDonkeyManualVisual(ServerPlayer player, float vehicleYaw, boolean usePacketYaw) {
		if (player == null || !(player.level() instanceof ServerLevel level)) {
			return;
		}
		GennadiyDonkeyState state = GENNADIY_DONKEY_STATES.get(player.getUUID());
		if (state == null || !state.isActive() || state.donkeyId == null) {
			return;
		}
		Entity vehicle = player.getVehicle();
		if (!(vehicle instanceof Donkey donkey) || !state.donkeyId.equals(donkey.getUUID())) {
			return;
		}
		updateGennadiyDonkeyVisual(level, player, donkey, state, usePacketYaw, vehicleYaw, usePacketYaw ? player : null);
	}

	private static void tickWomanAttackCharges(MinecraftServer server) {
		if (WOMAN_ATTACK_CHARGE_SESSIONS.isEmpty()) {
			return;
		}

		long nowTick = server.overworld().getGameTime();
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
			playWomanAttackChargeSound(level, session, center, nowTick);
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

		long nowTick = server.overworld().getGameTime();
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
			playWomanAttackFlightSound(level, projectile, start, nowTick);

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
		double reach = Math.max(player.blockInteractionRange(), player.entityInteractionRange());
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

	private static void playWomanAttackChargeSound(ServerLevel level, WomanAttackChargeSession session, Vec3 origin, long nowTick) {
		if (level == null || session == null || origin == null || nowTick < session.nextChargeSoundTick) {
			return;
		}
		playWomanPositionedSound(level, origin, SoundEvents.AMETHYST_BLOCK_CHIME, 2.2F, 1.45F);
		session.nextChargeSoundTick = nowTick + WOMAN_ATTACK_CHARGE_SOUND_INTERVAL_TICKS;
	}

	private static void playWomanAttackReleaseSound(ServerLevel level, Vec3 origin) {
		playWomanPositionedSound(level, origin, SoundEvents.BREEZE_SHOOT, 0.33F, 1.35F);
	}

	private static void playWomanAttackFlightSound(ServerLevel level, WomanAttackProjectile projectile, Vec3 origin, long nowTick) {
		if (level == null || projectile == null || origin == null || nowTick < projectile.nextFlightSoundTick) {
			return;
		}
		playWomanPositionedSound(level, origin, SoundEvents.AMETHYST_BLOCK_CHIME, 2.2F, 1.45F);
		projectile.nextFlightSoundTick = nowTick + WOMAN_ATTACK_CHARGE_SOUND_INTERVAL_TICKS;
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
		playWomanAttackReleaseSound(level, start);
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
		target.hurtServer(level, womanAttackDamageSource(level, owner), damage);

		Vec3 normalizedKnock = knockDirection.lengthSqr() <= 1.0E-6D ? owner.getLookAngle() : knockDirection.normalize();
		Vec3 knock = normalizedKnock.scale(0.4D);
		target.push(knock.x, 0.1D, knock.z);

		long nowTick = level.getGameTime();
		startWomanAttackFollow(target, nowTick, followTicks, Math.max(0.0D, range));
	}

	private static DamageSource womanAttackDamageSource(ServerLevel level, ServerPlayer owner) {
		return new DamageSource(
				level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(DamageTypes.GENERIC),
				owner,
				owner
		);
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

	private static int useMarkPotroshitelAttack(ServerPlayer player, PlayerRaceConfig race, RaceAbilityConfig ability) {
		if (player == null || ability == null || player.isSpectator() || !player.isAlive()) {
			return 0;
		}
		if (!(player.level() instanceof ServerLevel level)) {
			return 0;
		}

		ItemStack held = player.getMainHandItem();
		if (!isMarkThrowableAxe(held)) {
			player.displayClientMessage(
					Component.literal("Возьмите в руку топор, чтобы бросить его.").withStyle(ChatFormatting.RED),
					true
			);
			return 0;
		}

		long remainingCooldownTicks = getRemainingOnlineCooldownTicks(MARK_ATTACK_COOLDOWNS, player.getUUID());
		if (displayRemainingCooldown(player, remainingCooldownTicks)) {
			return 0;
		}

		long cooldownTicks = Math.max(1L, asTicks(positiveOrDefault(ability.cooldownSeconds, MARK_AXE_DEFAULT_COOLDOWN_SECONDS)));
		double range = getMarkAxeRange(ability);
		long bleedIntervalTicks = Math.max(1L, asTicks(getMarkAxeBleedingIntervalSeconds(ability)));
		double bleedOneDamage = getMarkAxeBleedingOneDamage(ability);
		double bleedTwoDamage = getMarkAxeBleedingTwoDamage(ability);
		long bleedTwoDurationTicks = Math.max(1L, asTicks(getMarkAxeBleedingTwoDurationSeconds(ability)));
		ItemStack thrownStack = held.copyWithCount(1);
		double fullChargeDamage = Math.max(1.0D, player.getAttributeValue(Attributes.ATTACK_DAMAGE));

		held.shrink(1);
		if (held.isEmpty()) {
			player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		}
		player.containerMenu.broadcastChanges();

		Vec3 direction = player.getLookAngle();
		if (direction.lengthSqr() <= 1.0E-6D) {
			direction = Vec3.directionFromRotation(player.getXRot(), player.getYRot());
		}
		direction = direction.normalize();
		Vec3 start = player.getEyePosition().add(direction.scale(0.55D));
		Vec3 velocity = direction.scale(MARK_AXE_FLIGHT_SPEED_BLOCKS);
		MarkThrownAxeSession session = new MarkThrownAxeSession(
				player.getUUID(),
				level.dimension(),
				thrownStack,
				level.getGameTime() + cooldownTicks,
				bleedIntervalTicks,
				bleedOneDamage,
				bleedTwoDamage,
				bleedTwoDurationTicks,
				fullChargeDamage,
				start,
				velocity,
				range
		);

		Display.ItemDisplay display = createMarkAxeDisplay(level, session);
		if (display == null) {
			giveMarkAxeStackBack(player, thrownStack);
			return 0;
		}
		MARK_THROWN_AXES.add(session);
		startOnlineCooldown(MARK_ATTACK_COOLDOWNS, player.getUUID(), cooldownTicks);
		return 1;
	}

	private static boolean isMarkThrowableAxe(ItemStack stack) {
		return stack != null && !stack.isEmpty() && stack.getItem() instanceof AxeItem;
	}

	private static Display.ItemDisplay createMarkAxeDisplay(ServerLevel level, MarkThrownAxeSession session) {
		if (level == null || session == null || session.axeStack == null || session.axeStack.isEmpty()) {
			return null;
		}
		Display.ItemDisplay display = new Display.ItemDisplay(EntityType.ITEM_DISPLAY, level);
		display.addTag(MARK_AXE_DISPLAY_TAG);
		display.setNoGravity(true);
		display.noPhysics = true;
		display.setInvulnerable(true);
		display.setSilent(true);
		display.setItemStack(session.axeStack.copyWithCount(1));
		configureMarkAxeDisplay(display);
		clearMarkAxeDisplayHitbox(display);
		level.addFreshEntity(display);
		session.displayId = display.getUUID();
		updateMarkAxeDisplay(display, session, session.direction);
		return display;
	}

	private static void configureMarkAxeDisplay(Display.ItemDisplay display) {
		if (display == null) {
			return;
		}
		display.setBillboardConstraints(Display.BillboardConstraints.FIXED);
		display.setItemTransform(ItemDisplayContext.FIXED);
		display.setViewRange(48.0F);
		display.setTransformationInterpolationDelay(0);
		display.setTransformationInterpolationDuration(MARK_AXE_DISPLAY_INTERPOLATION_TICKS);
		display.setPosRotInterpolationDuration(MARK_AXE_DISPLAY_INTERPOLATION_TICKS);
	}

	private static void clearMarkAxeDisplayHitbox(Display.ItemDisplay display) {
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

	private static void updateMarkAxeDisplay(Display.ItemDisplay display, MarkThrownAxeSession session, Vec3 visualDirection) {
		if (display == null || session == null || session.position == null) {
			return;
		}
		Vec3 direction = visualDirection == null || visualDirection.lengthSqr() <= 1.0E-6D ? session.direction : visualDirection.normalize();
		float yaw = yawFromDirection(direction);
		float pitch = pitchFromDirection(direction);
		display.setPos(session.position.x, session.position.y, session.position.z);
		display.setYRot(yaw);
		display.setXRot(pitch);
		display.yRotO = yaw;
		display.xRotO = pitch;
		boolean snapStuck = session.phase == MarkAxePhase.STUCK_BLOCK || session.stuckSnapTicks > 0;
		display.setTransformationInterpolationDuration(snapStuck ? 0 : MARK_AXE_DISPLAY_INTERPOLATION_TICKS);
		display.setPosRotInterpolationDuration(snapStuck ? 0 : MARK_AXE_DISPLAY_INTERPOLATION_TICKS);
		display.setTransformation(new Transformation(
				new Vector3f(0.0F, 0.0F, 0.0F),
				buildMarkAxeDisplayRotation(session),
				new Vector3f(MARK_AXE_DISPLAY_SCALE, MARK_AXE_DISPLAY_SCALE, MARK_AXE_DISPLAY_SCALE),
				new Quaternionf()
		));
		if (session.stuckSnapTicks > 0) {
			session.stuckSnapTicks--;
		}
		clearMarkAxeDisplayHitbox(display);
		display.hurtMarked = true;
	}

	private static Quaternionf buildMarkAxeDisplayRotation(MarkThrownAxeSession session) {
		double baseSpin = session == null ? MARK_AXE_HEAD_FIRST_SPIN_RADIANS : session.baseSpinRadians;
		if (session != null && session.phase == MarkAxePhase.FLYING) {
			return new Quaternionf()
					.rotateY((float) baseSpin)
					.rotateX((float) (Math.PI * 0.5D))
					.rotateZ((float) session.spinRadians);
		}
		double roll = session == null ? 0.0D : session.rollRadians;
		return new Quaternionf()
				.rotateY((float) baseSpin)
				.rotateX((float) (Math.PI * 0.5D))
				.rotateZ((float) roll);
	}

	private static void tickMarkPotroshitel(MinecraftServer server) {
		if (server == null) {
			return;
		}
		tickOnlineCooldowns(server, MARK_ATTACK_COOLDOWNS);
		tickMarkThrownAxes(server);
		tickMarkBleedingSessions(server);
	}

	private static void tickMarkThrownAxes(MinecraftServer server) {
		if (server == null || MARK_THROWN_AXES.isEmpty()) {
			return;
		}

		Iterator<MarkThrownAxeSession> iterator = MARK_THROWN_AXES.iterator();
		while (iterator.hasNext()) {
			MarkThrownAxeSession session = iterator.next();
			ServerLevel level = session == null || session.dimension == null ? null : server.getLevel(session.dimension);
			if (session == null || level == null || session.cleaned || session.axeStack == null || session.axeStack.isEmpty()) {
				cleanupMarkThrownAxe(server, session, false);
				iterator.remove();
				continue;
			}
			if (!tickMarkThrownAxeSession(server, level, session)) {
				cleanupMarkThrownAxe(server, session, true);
				iterator.remove();
			}
		}
	}

	private static boolean tickMarkThrownAxeSession(MinecraftServer server, ServerLevel level, MarkThrownAxeSession session) {
		Display.ItemDisplay display = resolveMarkAxeDisplay(server, level, session);
		if (display == null) {
			return false;
		}
		session.ageTicks++;

		return switch (session.phase) {
			case FLYING -> tickMarkAxeFlying(server, level, session, display);
			case FALLING -> tickMarkAxeFalling(server, level, session, display);
			case STUCK_BLOCK -> tickMarkAxeStuckBlock(server, level, session, display);
			case STUCK_ENTITY -> tickMarkAxeStuckEntity(server, level, session, display);
		};
	}

	private static Display.ItemDisplay resolveMarkAxeDisplay(MinecraftServer server, ServerLevel level, MarkThrownAxeSession session) {
		Entity existing = findEntity(server, session.displayId);
		if (existing instanceof Display.ItemDisplay display && display.isAlive()) {
			configureMarkAxeDisplay(display);
			clearMarkAxeDisplayHitbox(display);
			return display;
		}
		if (session.phase == MarkAxePhase.FLYING || session.phase == MarkAxePhase.FALLING) {
			return null;
		}
		return createMarkAxeDisplay(level, session);
	}

	private static boolean tickMarkAxeFlying(MinecraftServer server, ServerLevel level, MarkThrownAxeSession session, Display.ItemDisplay display) {
		Vec3 start = session.position;
		Vec3 velocity = session.velocity;
		double stepDistance = velocity.length();
		if (stepDistance <= 1.0E-6D) {
			switchMarkAxeToFalling(session);
			return true;
		}
		if (session.remainingHorizontalRange <= 0.0D) {
			switchMarkAxeToFalling(session);
			return true;
		}

		double step = Math.min(stepDistance, session.remainingHorizontalRange);
		Vec3 next = start.add(velocity.normalize().scale(step));
		MarkAxeImpact impact = findMarkAxeImpact(level, session, start, next, display);
		if (impact != null) {
			handleMarkAxeImpact(server, level, session, display, impact);
			return true;
		}

		session.position = next;
		session.remainingHorizontalRange -= step;
		session.direction = velocity.normalize();
		session.spinRadians += 1.05D;
		updateMarkAxeDisplay(display, session, session.direction);
		if (session.remainingHorizontalRange <= 1.0E-4D) {
			switchMarkAxeToFalling(session);
			updateMarkAxeDisplay(display, session, session.direction);
		}
		return true;
	}

	private static boolean tickMarkAxeFalling(MinecraftServer server, ServerLevel level, MarkThrownAxeSession session, Display.ItemDisplay display) {
		Vec3 start = session.position;
		Vec3 velocity = new Vec3(0.0D, -MARK_AXE_FALL_SPEED_BLOCKS, 0.0D);
		Vec3 next = start.add(velocity);
		MarkAxeImpact impact = findMarkAxeImpact(level, session, start, next, display);
		if (impact != null) {
			handleMarkAxeImpact(server, level, session, display, impact);
			return true;
		}

		session.position = next;
		session.velocity = velocity;
		session.fallingTicks++;
		session.direction = new Vec3(0.0D, -1.0D, 0.0D);
		session.baseSpinRadians = MARK_AXE_HEAD_FIRST_SPIN_RADIANS;
		session.spinRadians = 0.0D;
		updateMarkAxeDisplay(display, session, session.direction);

		if (session.position.y < level.getMinY() - 8.0D) {
			return false;
		}
		return true;
	}

	private static void switchMarkAxeToFalling(MarkThrownAxeSession session) {
		if (session == null) {
			return;
		}
		session.phase = MarkAxePhase.FALLING;
		session.velocity = new Vec3(0.0D, -MARK_AXE_FALL_SPEED_BLOCKS, 0.0D);
		session.baseSpinRadians = MARK_AXE_HEAD_FIRST_SPIN_RADIANS;
		session.rollRadians = randomMarkAxeFallingRollRadians();
		session.fallingTicks = 0;
		session.direction = new Vec3(0.0D, -1.0D, 0.0D);
		session.spinRadians = 0.0D;
	}

	private static double randomMarkAxeFallingRollRadians() {
		return java.util.concurrent.ThreadLocalRandom.current().nextDouble(
				MARK_AXE_FALL_ROLL_MIN_RADIANS,
				MARK_AXE_FALL_ROLL_MAX_RADIANS
		);
	}

	private static MarkAxeImpact findMarkAxeImpact(ServerLevel level, MarkThrownAxeSession session, Vec3 start, Vec3 end, Display.ItemDisplay display) {
		if (level == null || session == null || start == null || end == null) {
			return null;
		}
		ServerPlayer owner = level.getServer().getPlayerList().getPlayer(session.ownerId);
		BlockHitResult blockHit = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
		Vec3 blockLocation = blockHit.getType() == HitResult.Type.MISS ? null : blockHit.getLocation();
		Vec3 entityEnd = blockLocation == null ? end : blockLocation;
		EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
				level,
				display,
				start,
				entityEnd,
				new AABB(start, entityEnd).inflate(MARK_AXE_HIT_RADIUS_BLOCKS),
				entity -> canMarkAxeHit(owner, entity),
				0.25F
		);
		if (entityHit != null && entityHit.getEntity() instanceof LivingEntity target) {
			return new MarkAxeImpact(resolveMarkAxeEntityVisualImpact(target, start, entityEnd, entityHit.getLocation()), target, null);
		}
		if (blockLocation != null) {
			return new MarkAxeImpact(blockLocation, null, blockHit);
		}
		return null;
	}

	private static boolean canMarkAxeHit(ServerPlayer owner, Entity entity) {
		if (!(entity instanceof LivingEntity target) || entity instanceof ArmorStand) {
			return false;
		}
		if (!target.isAlive() || target.isSpectator() || target == owner) {
			return false;
		}
		if (entity.getTags().contains(MARK_AXE_DISPLAY_TAG) || entity.getTags().contains(MARK_AXE_TRIGGER_TAG)) {
			return false;
		}
		return !(target instanceof ServerPlayer playerTarget) || owner == null || !owner.isAlliedTo(playerTarget);
	}

	private static void handleMarkAxeImpact(
			MinecraftServer server,
			ServerLevel level,
			MarkThrownAxeSession session,
			Display.ItemDisplay display,
			MarkAxeImpact impact
	) {
		if (impact.target() != null) {
			stickMarkAxeInEntity(server, level, session, display, impact.location(), impact.target());
			return;
		}
		stickMarkAxeInBlock(server, level, session, display, impact.location());
	}

	private static void stickMarkAxeInBlock(MinecraftServer server, ServerLevel level, MarkThrownAxeSession session, Display.ItemDisplay display, Vec3 impactLocation) {
		detachMarkAxeDisplayPassenger(display);
		boolean wasFalling = session.phase == MarkAxePhase.FALLING;
		Vec3 direction = wasFalling || session.velocity == null || session.velocity.lengthSqr() <= 1.0E-6D ? session.direction : session.velocity.normalize();
		direction = direction == null || direction.lengthSqr() <= 1.0E-6D ? new Vec3(0.0D, -1.0D, 0.0D) : direction.normalize();
		double impactRoll = wasFalling && Math.abs(direction.y) > 0.85D ? session.rollRadians : randomMarkAxeFallingRollRadians();
		session.position = impactLocation.subtract(direction.scale(MARK_AXE_BLOCK_STICK_INSET_BLOCKS));
		session.direction = direction;
		session.velocity = Vec3.ZERO;
		session.phase = MarkAxePhase.STUCK_BLOCK;
		if (!wasFalling) {
			session.baseSpinRadians = MARK_AXE_HEAD_FIRST_SPIN_RADIANS;
		}
		session.spinRadians = 0.0D;
		session.rollRadians = impactRoll;
		session.stuckSnapTicks = 1;
		updateMarkAxeDisplay(display, session, direction);
		syncMarkAxePickupTrigger(server, level, session);
	}

	private static void stickMarkAxeInEntity(MinecraftServer server, ServerLevel level, MarkThrownAxeSession session, Display.ItemDisplay display, Vec3 hitLocation, LivingEntity target) {
		ServerPlayer owner = server.getPlayerList().getPlayer(session.ownerId);
		boolean wasFalling = session.phase == MarkAxePhase.FALLING;
		Vec3 direction = wasFalling || session.velocity == null || session.velocity.lengthSqr() <= 1.0E-6D ? session.direction : session.velocity.normalize();
		direction = direction == null || direction.lengthSqr() <= 1.0E-6D ? new Vec3(0.0D, -1.0D, 0.0D) : direction.normalize();
		double impactRoll = randomMarkAxeFallingRollRadians();
		Vec3 woundLocation = adjustMarkAxeEntityHitLocation(target, hitLocation);
		float damage = computeMarkAxeImpactDamage(level, owner, target, session);
		DamageSource source = markAxeImpactDamageSource(level, owner);
		target.invulnerableTime = 0;
		target.hurtServer(level, source, damage);
		target.invulnerableTime = 0;

		if (!target.isAlive()) {
			releaseMarkAxeAfterLethalEntityHit(session, display, direction, woundLocation);
			return;
		}

		session.position = woundLocation.subtract(direction.scale(MARK_AXE_ENTITY_STICK_INSET_BLOCKS));
		session.direction = direction;
		session.velocity = Vec3.ZERO;
		session.phase = MarkAxePhase.STUCK_ENTITY;
		session.baseSpinRadians = MARK_AXE_HEAD_FIRST_SPIN_RADIANS;
		session.spinRadians = 0.0D;
		session.rollRadians = impactRoll;
		session.stuckSnapTicks = 1;
		session.targetId = target.getUUID();
		float targetYaw = getMarkAxeEntityAttachmentYaw(target);
		session.targetOffset = rotateMarkAxeWorldToLocal(session.position.subtract(getMarkAxeEntityAttachmentAnchor(target)), targetYaw);
		session.targetWoundOffset = rotateMarkAxeWorldToLocal(woundLocation.subtract(getMarkAxeEntityAttachmentAnchor(target)), targetYaw);
		session.targetLocalDirection = rotateMarkAxeWorldToLocal(direction, targetYaw);
		session.nextBleedTick = level.getGameTime() + session.bleedIntervalTicks;
		updateMarkAxeDisplay(display, session, direction);
		syncMarkAxePickupTrigger(server, level, session);
		emitMarkBloodDrip(level, woundLocation);
	}

	private static boolean tickMarkAxeStuckBlock(MinecraftServer server, ServerLevel level, MarkThrownAxeSession session, Display.ItemDisplay display) {
		updateMarkAxeDisplay(display, session, session.direction);
		syncMarkAxePickupTrigger(server, level, session);
		return true;
	}

	private static boolean tickMarkAxeStuckEntity(MinecraftServer server, ServerLevel level, MarkThrownAxeSession session, Display.ItemDisplay display) {
		Entity entity = findEntity(server, session.targetId);
		if (!(entity instanceof LivingEntity target) || target.level() != level || !target.isAlive()) {
			releaseMarkAxeFromDeadTarget(server, level, session, display);
			return true;
		}

		Vec3 offset = session.targetOffset == null ? new Vec3(0.0D, target.getBbHeight() * 0.55D, 0.0D) : session.targetOffset;
		float targetYaw = getMarkAxeEntityAttachmentYaw(target);
		session.position = getMarkAxeEntityAttachmentAnchor(target).add(rotateMarkAxeLocalToWorld(offset, targetYaw));
		Vec3 localDirection = session.targetLocalDirection == null ? session.direction : session.targetLocalDirection;
		Vec3 direction = rotateMarkAxeLocalToWorld(localDirection, targetYaw);
		if (direction.lengthSqr() > 1.0E-6D) {
			session.direction = direction.normalize();
		}
		updateMarkAxeDisplay(display, session, session.direction);
		syncMarkAxePickupTrigger(server, level, session);
		long nowTick = level.getGameTime();
		if ((session.ageTicks & 1) == 0) {
			emitMarkBloodDrip(level, getMarkAxeEntityWoundPosition(target, session));
		}
		if (nowTick >= session.nextBleedTick) {
			applyMarkBleedingDamage(level, target, session.ownerId, session.bleedingOneDamage);
			session.nextBleedTick = nowTick + session.bleedIntervalTicks;
		}
		return true;
	}

	private static void releaseMarkAxeAfterLethalEntityHit(MarkThrownAxeSession session, Display.ItemDisplay display, Vec3 direction, Vec3 woundLocation) {
		if (session == null) {
			return;
		}
		detachMarkAxeDisplayPassenger(display);
		Vec3 normalizedDirection = direction == null || direction.lengthSqr() <= 1.0E-6D ? new Vec3(0.0D, -1.0D, 0.0D) : direction.normalize();
		session.targetId = null;
		session.targetOffset = null;
		session.targetWoundOffset = null;
		session.targetLocalDirection = null;
		session.position = woundLocation == null ? session.position : woundLocation.subtract(normalizedDirection.scale(MARK_AXE_ENTITY_STICK_INSET_BLOCKS));
		if (session.position == null) {
			session.position = Vec3.ZERO;
		}
		session.direction = normalizedDirection;
		session.velocity = new Vec3(normalizedDirection.x * 0.08D, -0.12D, normalizedDirection.z * 0.08D);
		session.stuckSnapTicks = 0;
		switchMarkAxeToFalling(session);
		updateMarkAxeDisplay(display, session, session.direction);
	}

	private static void detachMarkAxeDisplayPassenger(Display.ItemDisplay display) {
		if (display == null || !display.isPassenger()) {
			return;
		}
		Entity vehicle = display.getVehicle();
		display.stopRiding();
		if (vehicle != null) {
			syncPassengerAttachment(vehicle);
		}
	}

	private static void releaseMarkAxeFromDeadTarget(MinecraftServer server, ServerLevel level, MarkThrownAxeSession session, Display.ItemDisplay display) {
		if (session == null) {
			return;
		}
		detachMarkAxeDisplayPassenger(display);
		session.targetId = null;
		session.targetOffset = null;
		session.targetWoundOffset = null;
		session.targetLocalDirection = null;
		session.spinRadians = 0.0D;
		session.stuckSnapTicks = 0;
		Entity trigger = findEntity(server, session.pickupTriggerId);
		if (trigger != null) {
			trigger.discard();
		}
		session.pickupTriggerId = null;
		Vec3 previousDirection = session.direction == null || session.direction.lengthSqr() <= 1.0E-6D
				? new Vec3(0.0D, -1.0D, 0.0D)
				: session.direction.normalize();
		session.velocity = new Vec3(previousDirection.x * 0.08D, -0.12D, previousDirection.z * 0.08D);
		switchMarkAxeToFalling(session);
		updateMarkAxeDisplay(display, session, session.direction);
	}

	private static Vec3 getMarkAxeEntityAttachmentAnchor(LivingEntity target) {
		return target == null ? Vec3.ZERO : target.position();
	}

	private static float getMarkAxeEntityAttachmentYaw(LivingEntity target) {
		return target == null ? 0.0F : target.yBodyRot;
	}

	private static Vec3 rotateMarkAxeLocalToWorld(Vec3 local, float yawDegrees) {
		if (local == null) {
			return Vec3.ZERO;
		}
		double yaw = Math.toRadians(yawDegrees);
		double sin = Math.sin(yaw);
		double cos = Math.cos(yaw);
		return new Vec3(
				local.x * cos - local.z * sin,
				local.y,
				local.x * sin + local.z * cos
		);
	}

	private static Vec3 rotateMarkAxeWorldToLocal(Vec3 world, float yawDegrees) {
		if (world == null) {
			return Vec3.ZERO;
		}
		double yaw = Math.toRadians(yawDegrees);
		double sin = Math.sin(yaw);
		double cos = Math.cos(yaw);
		return new Vec3(
				world.x * cos + world.z * sin,
				world.y,
				-world.x * sin + world.z * cos
		);
	}

	private static Vec3 getMarkAxeEntityWoundPosition(LivingEntity target, MarkThrownAxeSession session) {
		if (target == null) {
			return Vec3.ZERO;
		}
		Vec3 woundOffset = session == null ? null : session.targetWoundOffset;
		if (woundOffset == null) {
			woundOffset = session == null ? null : session.targetOffset;
		}
		if (woundOffset == null) {
			return target.position().add(0.0D, Math.max(0.4D, target.getBbHeight() * 0.55D), 0.0D);
		}
		return getMarkAxeEntityAttachmentAnchor(target).add(
				rotateMarkAxeLocalToWorld(woundOffset, getMarkAxeEntityAttachmentYaw(target))
		);
	}

	private static Vec3 resolveMarkAxeEntityVisualImpact(LivingEntity target, Vec3 start, Vec3 end, Vec3 fallback) {
		if (target == null || start == null || end == null) {
			return fallback;
		}
		AABB visualBox = getMarkAxeEntityVisualHitBox(target);
		Optional<Vec3> visualHit = visualBox.clip(start, end);
		if (visualHit.isPresent()) {
			return visualHit.get();
		}
		return closestPointInMarkAxeBox(visualBox, fallback == null ? end : fallback);
	}

	private static AABB getMarkAxeEntityVisualHitBox(LivingEntity target) {
		AABB box = target.getBoundingBox();
		double halfWidth = Math.max(MARK_AXE_ENTITY_VISUAL_MIN_WIDTH * 0.5D, target.getBbWidth() * MARK_AXE_ENTITY_VISUAL_WIDTH_SCALE * 0.5D);
		double bottomInset = Math.min(0.12D, target.getBbHeight() * 0.08D);
		double topInset = Math.min(0.08D, target.getBbHeight() * 0.04D);
		double minY = box.minY + bottomInset;
		double maxY = Math.max(minY + 0.12D, box.maxY - topInset);
		return new AABB(
				target.getX() - halfWidth,
				minY,
				target.getZ() - halfWidth,
				target.getX() + halfWidth,
				maxY,
				target.getZ() + halfWidth
		);
	}

	private static Vec3 closestPointInMarkAxeBox(AABB box, Vec3 point) {
		if (box == null || point == null) {
			return point;
		}
		return new Vec3(
				Math.max(box.minX, Math.min(box.maxX, point.x)),
				Math.max(box.minY, Math.min(box.maxY, point.y)),
				Math.max(box.minZ, Math.min(box.maxZ, point.z))
		);
	}

	private static Vec3 adjustMarkAxeEntityHitLocation(LivingEntity target, Vec3 hitLocation) {
		if (target == null || hitLocation == null) {
			return hitLocation;
		}
		return closestPointInMarkAxeBox(getMarkAxeEntityVisualHitBox(target), hitLocation);
	}

	private record MarkAxeImpact(Vec3 location, LivingEntity target, BlockHitResult blockHit) {
	}

	private static float computeMarkAxeImpactDamage(ServerLevel level, ServerPlayer owner, LivingEntity target, MarkThrownAxeSession session) {
		if (level == null || owner == null || target == null || session == null) {
			return (float) Math.max(0.0D, session == null ? 0.0D : session.impactDamage);
		}
		float baseCriticalDamage = (float) Math.max(0.0D, session.impactDamage);
		float enchantedDamage = EnchantmentHelper.modifyDamage(level, session.axeStack, target, markAxeImpactDamageSource(level, owner), baseCriticalDamage);
		return Math.max(baseCriticalDamage, enchantedDamage);
	}

	private static DamageSource markAxeImpactDamageSource(ServerLevel level, ServerPlayer owner) {
		if (owner == null) {
			return new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(DamageTypes.THROWN));
		}
		return new DamageSource(
				level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(DamageTypes.PLAYER_ATTACK),
				owner,
				owner
		);
	}

	private static DamageSource markBleedingDamageSource(ServerLevel level, ServerPlayer owner) {
		if (owner == null) {
			return new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(DamageTypes.GENERIC));
		}
		return new DamageSource(
				level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(DamageTypes.GENERIC),
				owner,
				owner
		);
	}

	private static void applyMarkBleedingDamage(ServerLevel level, LivingEntity target, UUID ownerId, double damageValue) {
		if (level == null || target == null || !target.isAlive() || damageValue <= 0.0D) {
			return;
		}
		ServerPlayer owner = ownerId == null ? null : level.getServer().getPlayerList().getPlayer(ownerId);
		target.invulnerableTime = 0;
		target.hurtServer(level, markBleedingDamageSource(level, owner), (float) damageValue);
		target.invulnerableTime = 0;
	}

	private static void tickMarkBleedingSessions(MinecraftServer server) {
		if (server == null || MARK_BLEEDING_SESSIONS.isEmpty()) {
			return;
		}
		Iterator<Map.Entry<UUID, MarkBleedingSession>> iterator = MARK_BLEEDING_SESSIONS.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<UUID, MarkBleedingSession> entry = iterator.next();
			MarkBleedingSession session = entry.getValue();
			ServerLevel level = session == null || session.dimension == null ? null : server.getLevel(session.dimension);
			Entity entity = session == null ? null : findEntity(server, session.targetId);
			if (!(entity instanceof LivingEntity target) || level == null || target.level() != level || !target.isAlive()) {
				iterator.remove();
				continue;
			}
			long nowTick = level.getGameTime();
			if (nowTick >= session.expireTick) {
				iterator.remove();
				continue;
			}
			Vec3 woundPosition = getMarkBleedingWoundPosition(target, session);
			if ((nowTick & 1L) == 0L) {
				emitMarkBloodDrip(level, woundPosition);
			}
			if (nowTick < session.nextDamageTick) {
				continue;
			}
			applyMarkBleedingDamage(level, target, session.attackerId, session.damage);
			session.nextDamageTick = nowTick + session.intervalTicks;
		}
	}

	private static void startMarkBleedingTwo(ServerLevel level, LivingEntity target, MarkThrownAxeSession axeSession) {
		if (level == null || target == null || axeSession == null || !target.isAlive() || axeSession.bleedingTwoDamage <= 0.0D) {
			return;
		}
		long nowTick = level.getGameTime();
		Vec3 woundLocalOffset = axeSession.targetWoundOffset == null ? axeSession.targetOffset : axeSession.targetWoundOffset;
		if (woundLocalOffset == null && axeSession.position != null) {
			woundLocalOffset = rotateMarkAxeWorldToLocal(
					axeSession.position.subtract(getMarkAxeEntityAttachmentAnchor(target)),
					getMarkAxeEntityAttachmentYaw(target)
			);
		}
		MARK_BLEEDING_SESSIONS.put(
				target.getUUID(),
				new MarkBleedingSession(
						target.getUUID(),
						level.dimension(),
						axeSession.ownerId,
						axeSession.bleedingTwoDamage,
						axeSession.bleedIntervalTicks,
						nowTick,
						nowTick + axeSession.bleedingTwoDurationTicks,
						woundLocalOffset
				)
		);
	}

	private static Vec3 getMarkBleedingWoundPosition(LivingEntity target, MarkBleedingSession session) {
		if (target == null) {
			return Vec3.ZERO;
		}
		if (session != null && session.woundLocalOffset != null) {
			return getMarkAxeEntityAttachmentAnchor(target).add(
					rotateMarkAxeLocalToWorld(session.woundLocalOffset, getMarkAxeEntityAttachmentYaw(target))
			);
		}
		return target.position().add(0.0D, Math.max(0.4D, target.getBbHeight() * 0.55D), 0.0D);
	}

	private static void emitMarkBloodDrip(ServerLevel level, Vec3 origin) {
		if (level == null || origin == null) {
			return;
		}
		for (int i = 0; i < 3; i++) {
			double y = origin.y - i * 0.18D;
			level.sendParticles(new DustParticleOptions(MARK_AXE_BLOOD_COLOR, 0.85F), origin.x, y, origin.z, 1, 0.015D, 0.015D, 0.015D, 0.0D);
		}
	}

	private static void syncMarkAxePickupTrigger(MinecraftServer server, ServerLevel level, MarkThrownAxeSession session) {
		if (server == null || level == null || session == null || session.position == null) {
			return;
		}
		Interaction trigger = null;
		Entity existing = findEntity(server, session.pickupTriggerId);
		if (existing instanceof Interaction interaction && interaction.isAlive() && interaction.level() == level) {
			trigger = interaction;
		}
		if (trigger == null) {
			trigger = new Interaction(EntityType.INTERACTION, level);
			trigger.addTag(MARK_AXE_TRIGGER_TAG);
			trigger.setNoGravity(true);
			trigger.setSilent(true);
			trigger.setInvisible(true);
			trigger.setResponse(true);
			trigger.setWidth((float) MARK_AXE_PICKUP_TRIGGER_SIZE);
			trigger.setHeight((float) MARK_AXE_PICKUP_TRIGGER_SIZE);
			level.addFreshEntity(trigger);
			session.pickupTriggerId = trigger.getUUID();
		}
		Vec3 pos = session.position.subtract(0.0D, MARK_AXE_PICKUP_TRIGGER_SIZE * 0.5D, 0.0D);
		trigger.setInvisible(true);
		trigger.setPos(pos.x, pos.y, pos.z);
		trigger.setDeltaMovement(Vec3.ZERO);
		trigger.setYRot(yawFromDirection(session.direction));
		trigger.setXRot(pitchFromDirection(session.direction));
		trigger.hurtMarked = true;
	}

	private static InteractionResult tryPickupMarkThrownAxe(ServerPlayer player, InteractionHand hand, Entity entity) {
		if (player == null || entity == null || MARK_THROWN_AXES.isEmpty()) {
			return InteractionResult.PASS;
		}
		MarkThrownAxeSession session = findMarkThrownAxeByEntity(entity.getUUID());
		if (session == null || session.phase == MarkAxePhase.FLYING || session.phase == MarkAxePhase.FALLING) {
			return InteractionResult.PASS;
		}
		if (!(player.level() instanceof ServerLevel level)) {
			return InteractionResult.SUCCESS;
		}
		long nowTick = level.getGameTime();
		boolean publicPickup = nowTick >= session.publicPickupTick;
		boolean ownerPickup = player.getUUID().equals(session.ownerId);
		if (!publicPickup && !ownerPickup) {
			player.displayClientMessage(Component.literal("Этот топор может забрать только Марк.").withStyle(ChatFormatting.RED), true);
			return InteractionResult.SUCCESS;
		}

		if (session.phase == MarkAxePhase.STUCK_ENTITY) {
			Entity targetEntity = findEntity(level.getServer(), session.targetId);
			if (targetEntity instanceof LivingEntity target && target.isAlive()) {
				startMarkBleedingTwo(level, target, session);
			}
		}

		giveMarkAxeStackBack(player, session.axeStack);
		if (ownerPickup && !publicPickup) {
			MARK_ATTACK_COOLDOWNS.remove(session.ownerId);
		}
		cleanupMarkThrownAxe(level.getServer(), session, false);
		MARK_THROWN_AXES.remove(session);
		return InteractionResult.SUCCESS;
	}

	private static MarkThrownAxeSession findMarkThrownAxeByEntity(UUID entityId) {
		if (entityId == null) {
			return null;
		}
		for (MarkThrownAxeSession session : MARK_THROWN_AXES) {
			if (session == null) {
				continue;
			}
			if (entityId.equals(session.displayId) || entityId.equals(session.pickupTriggerId)) {
				return session;
			}
		}
		return null;
	}

	private static void giveMarkAxeStackBack(ServerPlayer player, ItemStack stack) {
		if (player == null || stack == null || stack.isEmpty()) {
			return;
		}
		ItemStack remaining = stack.copy();
		boolean added = player.getInventory().add(remaining);
		if (!added || !remaining.isEmpty()) {
			player.drop(remaining, false);
		}
		player.containerMenu.broadcastChanges();
	}

	private static void dropMarkAxeItem(ServerLevel level, Vec3 position, ItemStack stack) {
		if (level == null || position == null || stack == null || stack.isEmpty()) {
			return;
		}
		ItemEntity itemEntity = new ItemEntity(level, position.x, position.y, position.z, stack.copy());
		itemEntity.setDefaultPickUpDelay();
		level.addFreshEntity(itemEntity);
	}

	private static void cleanupMarkThrownAxe(MinecraftServer server, MarkThrownAxeSession session, boolean dropItem) {
		if (session == null || session.cleaned) {
			return;
		}
		session.cleaned = true;
		if (dropItem && server != null) {
			ServerLevel level = session.dimension == null ? null : server.getLevel(session.dimension);
			if (level != null) {
				dropMarkAxeItem(level, session.position, session.axeStack);
			}
		}
		Entity display = findEntity(server, session.displayId);
		if (display != null) {
			display.discard();
		}
		Entity trigger = findEntity(server, session.pickupTriggerId);
		if (trigger != null) {
			trigger.discard();
		}
	}

	private static void dropAllMarkThrownAxes(MinecraftServer server) {
		if (server == null || MARK_THROWN_AXES.isEmpty()) {
			return;
		}
		for (MarkThrownAxeSession session : new ArrayList<>(MARK_THROWN_AXES)) {
			cleanupMarkThrownAxe(server, session, true);
		}
		MARK_THROWN_AXES.clear();
	}

	private static void unlockAllMarkThrownAxes(MinecraftServer server) {
		if (server == null || MARK_THROWN_AXES.isEmpty()) {
			return;
		}
		long nowTick = server.overworld().getGameTime();
		for (MarkThrownAxeSession session : MARK_THROWN_AXES) {
			if (session != null) {
				session.publicPickupTick = nowTick;
			}
		}
	}

	private static double getMarkAxeRange(RaceAbilityConfig ability) {
		return positiveOrDefault(ability == null ? 0.0D : ability.markAxeRangeBlocks, MARK_AXE_DEFAULT_RANGE_BLOCKS);
	}

	private static double getMarkAxeBleedingOneDamage(RaceAbilityConfig ability) {
		return positiveOrDefault(ability == null ? 0.0D : ability.markAxeBleedingOneDamage, MARK_AXE_DEFAULT_BLEEDING_ONE_DAMAGE);
	}

	private static double getMarkAxeBleedingTwoDamage(RaceAbilityConfig ability) {
		return positiveOrDefault(ability == null ? 0.0D : ability.markAxeBleedingTwoDamage, MARK_AXE_DEFAULT_BLEEDING_TWO_DAMAGE);
	}

	private static double getMarkAxeBleedingIntervalSeconds(RaceAbilityConfig ability) {
		return positiveOrDefault(ability == null ? 0.0D : ability.markAxeBleedingIntervalSeconds, MARK_AXE_DEFAULT_BLEEDING_INTERVAL_SECONDS);
	}

	private static double getMarkAxeBleedingTwoDurationSeconds(RaceAbilityConfig ability) {
		return positiveOrDefault(ability == null ? 0.0D : ability.markAxeBleedingTwoDurationSeconds, MARK_AXE_DEFAULT_BLEEDING_TWO_DURATION_SECONDS);
	}

	private static double getWomanDefenseRange(RaceAbilityConfig ability) {
		return positiveOrDefault(ability == null ? 0.0D : ability.activationRangeBlocks, WOMAN_DEFENSE_DEFAULT_RANGE_BLOCKS);
	}

	private static double getWomanDefenseDurationSeconds(RaceAbilityConfig ability) {
		return positiveOrDefault(ability == null ? 0.0D : ability.durationSeconds, WOMAN_DEFENSE_DEFAULT_DURATION_SECONDS);
	}

	private static double getWomanUniqueRange(RaceAbilityConfig ability) {
		return positiveOrDefault(ability == null ? 0.0D : ability.activationRangeBlocks, WOMAN_UNIQUE_DEFAULT_RANGE_BLOCKS);
	}

	private static double getWomanUniqueDurationSeconds(RaceAbilityConfig ability) {
		return positiveOrDefault(ability == null ? 0.0D : ability.durationSeconds, WOMAN_UNIQUE_DEFAULT_DURATION_SECONDS);
	}

	private static double getWomanUniqueDropMinSeconds(RaceAbilityConfig ability) {
		return positiveOrDefault(ability == null ? 0.0D : ability.womanUniqueDropMinSeconds, WOMAN_UNIQUE_DEFAULT_DROP_MIN_SECONDS);
	}

	private static double getWomanUniqueDropMaxSeconds(RaceAbilityConfig ability) {
		return positiveOrDefault(ability == null ? 0.0D : ability.womanUniqueDropMaxSeconds, WOMAN_UNIQUE_DEFAULT_DROP_MAX_SECONDS);
	}

	private static double getWomanUniqueDropChance(RaceAbilityConfig ability) {
		double configured = ability == null ? 0.0D : ability.womanUniqueDropChance;
		return configured > 0.0D ? Math.min(1.0D, configured) : WOMAN_UNIQUE_DEFAULT_DROP_CHANCE;
	}

	private static double getWomanUniqueTradePriceIncrease(RaceAbilityConfig ability) {
		return positiveOrDefault(ability == null ? 0.0D : ability.womanUniqueTradePriceIncrease, WOMAN_UNIQUE_DEFAULT_TRADE_PRICE_INCREASE);
	}

	private static double getWomanUniqueAbsorptionHearts(RaceAbilityConfig ability) {
		return positiveOrDefault(ability == null ? 0.0D : ability.womanUniqueAbsorptionHearts, WOMAN_UNIQUE_DEFAULT_ABSORPTION_HEARTS);
	}

	private static double getWomanShnyagaTransferHearts(RaceAbilityConfig ability) {
		return positiveOrDefault(ability == null ? 0.0D : ability.womanShnyagaTransferHearts, WOMAN_SHNYAGA_DEFAULT_TRANSFER_HEARTS);
	}

	private static double getWomanShnyagaTransferHealthPoints(RaceAbilityConfig ability) {
		return getWomanShnyagaTransferHearts(ability) * 2.0D;
	}

	private static double getWomanShnyagaBuffRange(RaceAbilityConfig ability) {
		return positiveOrDefault(ability == null ? 0.0D : ability.womanShnyagaBuffRangeBlocks, WOMAN_SHNYAGA_DEFAULT_BUFF_RANGE_BLOCKS);
	}

	private static double getWomanShnyagaRejectDamageHearts(RaceAbilityConfig ability) {
		return positiveOrDefault(ability == null ? 0.0D : ability.womanShnyagaRejectDamageHearts, WOMAN_SHNYAGA_DEFAULT_REJECT_DAMAGE_HEARTS);
	}

	private static double getWomanShnyagaRejectDamagePoints(RaceAbilityConfig ability) {
		return getWomanShnyagaRejectDamageHearts(ability) * 2.0D;
	}

	private static double getWomanShnyagaRejectDebuffSeconds(RaceAbilityConfig ability) {
		return positiveOrDefault(ability == null ? 0.0D : ability.womanShnyagaRejectDebuffSeconds, WOMAN_SHNYAGA_DEFAULT_REJECT_DEBUFF_SECONDS);
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

	private static double getGennadiyDefenseDurationSeconds(RaceAbilityConfig ability) {
		return positiveOrDefault(ability == null ? 0.0D : ability.gennadiyDefenseDurationSeconds, GENNADIY_DEFENSE_DEFAULT_DURATION_SECONDS);
	}

	private static double getGennadiyDefenseKnockbackBlocksPerDamage(RaceAbilityConfig ability) {
		return positiveOrDefault(ability == null ? 0.0D : ability.gennadiyDefenseKnockbackBlocksPerDamage, GENNADIY_DEFENSE_DEFAULT_KNOCKBACK_BLOCKS_PER_DAMAGE);
	}

	private static double getGennadiyDefenseMaxKnockbackBlocks(RaceAbilityConfig ability) {
		return positiveOrDefault(ability == null ? 0.0D : ability.gennadiyDefenseMaxKnockbackBlocks, GENNADIY_DEFENSE_DEFAULT_MAX_KNOCKBACK_BLOCKS);
	}

	private static double getGennadiyDefenseWaveRange(RaceAbilityConfig ability) {
		return positiveOrDefault(ability == null ? 0.0D : ability.gennadiyDefenseWaveRangeBlocks, GENNADIY_DEFENSE_DEFAULT_WAVE_RANGE_BLOCKS);
	}

	private static double getGennadiyDefenseMinDamage(RaceAbilityConfig ability) {
		return positiveOrDefault(ability == null ? 0.0D : ability.gennadiyDefenseMinDamage, GENNADIY_DEFENSE_DEFAULT_MIN_DAMAGE);
	}

	private static double getGennadiyDefenseMaxDamage(RaceAbilityConfig ability) {
		return positiveOrDefault(ability == null ? 0.0D : ability.gennadiyDefenseMaxDamage, GENNADIY_DEFENSE_DEFAULT_MAX_DAMAGE);
	}

	private static double getGennadiyHookRange(RaceAbilityConfig ability) {
		return positiveOrDefault(ability == null ? 0.0D : ability.gennadiyHookRangeBlocks, GENNADIY_HOOK_DEFAULT_RANGE_BLOCKS);
	}

	private static double getGennadiyHookDamage(RaceAbilityConfig ability) {
		return positiveOrDefault(ability == null ? 0.0D : ability.gennadiyHookDamage, GENNADIY_HOOK_DEFAULT_DAMAGE);
	}

	private static double getGennadiyHookSlownessSeconds(RaceAbilityConfig ability) {
		return positiveOrDefault(ability == null ? 0.0D : ability.gennadiyHookSlownessSeconds, GENNADIY_HOOK_DEFAULT_SLOWNESS_SECONDS);
	}

	private static int useGennadiyDefense(ServerPlayer player, PlayerRaceConfig race, RaceAbilityConfig ability) {
		if (player == null || ability == null || player.isSpectator() || !player.isAlive()) {
			return 0;
		}
		if (!(player.level() instanceof ServerLevel level)) {
			return 0;
		}
		if (GENNADIY_DEFENSE_SESSIONS.containsKey(player.getUUID()) || displayGenericAbilityCooldown(player, RaceAbilitySlot.DEFENSE)) {
			return 0;
		}

		long durationTicks = Math.max(1L, asTicks(getGennadiyDefenseDurationSeconds(ability)));
		double minDamage = getGennadiyDefenseMinDamage(ability);
		double maxDamage = Math.max(minDamage, getGennadiyDefenseMaxDamage(ability));
		GennadiyDefenseSession session = new GennadiyDefenseSession(
				player.getUUID(),
				level.dimension(),
				level.getGameTime() + durationTicks,
				getGennadiyDefenseKnockbackBlocksPerDamage(ability),
				getGennadiyDefenseMaxKnockbackBlocks(ability),
				getGennadiyDefenseWaveRange(ability),
				minDamage,
				maxDamage,
				player.getYRot()
		);
		GENNADIY_DEFENSE_SESSIONS.put(player.getUUID(), session);
		applyGennadiyDefenseStasis(player, durationTicks);
		emitGennadiyDefenseSmokeAura(level, player, level.getGameTime());
		playGennadiyDefenseChargeSound(player, level.getGameTime());
		session.nextChargeSoundTick = level.getGameTime() + WOMAN_DEFENSE_PANIC_SOUND_INTERVAL_TICKS;
		startGenericAbilityCooldown(player, RaceAbilitySlot.DEFENSE, ability);
		Lg2.LOGGER.info("Player {} activated gennadiy defense '{}' from race '{}'", player.getGameProfile().name(), ability.abilityId, race.id);
		return 1;
	}

	private static int useGennadiyHook(ServerPlayer player, PlayerRaceConfig race, RaceAbilityConfig ability) {
		if (player == null || ability == null || player.isSpectator() || !player.isAlive()) {
			return 0;
		}
		if (!(player.level() instanceof ServerLevel level)) {
			return 0;
		}
		if (displayGenericAbilityCooldown(player, RaceAbilitySlot.UNIQUE_ABILITY)) {
			return 0;
		}

		cleanupGennadiyHookSession(level.getServer(), GENNADIY_HOOK_SESSIONS.remove(player.getUUID()));
		Vec3 origin = getGennadiyHookOrigin(player);
		Vec3 direction = player.getLookAngle();
		if (direction.lengthSqr() <= 1.0E-6D) {
			return 0;
		}
		direction = direction.normalize();

		double range = getGennadiyHookRange(ability);
		Vec3 maxEnd = origin.add(direction.scale(range));
		BlockHitResult blockHit = level.clip(new ClipContext(origin, maxEnd, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		Vec3 rayEnd = blockHit.getType() == HitResult.Type.MISS ? maxEnd : blockHit.getLocation();
		EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
				level,
				player,
				origin,
				rayEnd,
				new AABB(origin, rayEnd).inflate(GENNADIY_HOOK_HIT_RADIUS_BLOCKS),
				entity -> canGennadiyHookHit(player, entity),
				(float) GENNADIY_HOOK_HIT_RADIUS_BLOCKS
		);

		LivingEntity target = null;
		Vec3 endpoint = rayEnd;
		if (entityHit != null && entityHit.getEntity() instanceof LivingEntity hitTarget) {
			target = hitTarget;
			endpoint = entityHit.getLocation();
		}

		double endpointDistance = Math.min(range, Math.max(0.0D, endpoint.distanceTo(origin)));
		if (endpointDistance <= 0.05D) {
			return 0;
		}

		GennadiyHookSession session = new GennadiyHookSession(
				player.getUUID(),
				level.dimension(),
				direction,
				target == null ? null : target.getUUID(),
				endpointDistance,
				getGennadiyHookDamage(ability),
				asTicks(getGennadiyHookSlownessSeconds(ability))
		);
		GENNADIY_HOOK_SESSIONS.put(player.getUUID(), session);
		startGenericAbilityCooldown(player, RaceAbilitySlot.UNIQUE_ABILITY, ability);
		playGennadiyHookChainSound(level, origin);
		session.nextChainSoundTick = level.getGameTime() + GENNADIY_HOOK_CHAIN_SOUND_REPEAT_TICKS;
		Lg2.LOGGER.info("Player {} fired gennadiy hook '{}' from race '{}'", player.getGameProfile().name(), ability.abilityId, race.id);
		return 1;
	}

	private static boolean canGennadiyHookHit(ServerPlayer caster, Entity entity) {
		if (caster == null || !(entity instanceof LivingEntity target)) {
			return false;
		}
		if (!target.isAlive() || target.isSpectator() || target == caster || target instanceof ArmorStand) {
			return false;
		}
		if (target.getTags().contains(GENNADIY_DONKEY_TAG)) {
			return false;
		}
		return !(target instanceof ServerPlayer targetPlayer) || !caster.isAlliedTo(targetPlayer);
	}

	private static Vec3 getGennadiyHookOrigin(ServerPlayer player) {
		return player.getEyePosition().subtract(0.0D, 0.12D, 0.0D);
	}

	private static int useGennadiyReport(ServerPlayer player, PlayerRaceConfig race, RaceAbilityConfig ability) {
		if (player == null || ability == null || player.isSpectator() || !player.isAlive()) {
			return 0;
		}
		MinecraftServer server = player.level().getServer();
		if (hasActiveGennadiyReport(server)) {
			displayGennadiyReportActionbar(player, "Уже есть активный REPORT. Уничтожьте текущий предмет, чтобы создать новый.");
			return 0;
		}

		InteractionHand hand = selectGennadiyReportHand(player);
		if (hand == null) {
			displayGennadiyReportActionbar(player, "Возьмите предмет в руку, чтобы создать REPORT.");
			return 0;
		}

		ItemStack stack = player.getItemInHand(hand);
        // expireTick is kept in the record shape for compatibility with already-loaded old outer classes.
		// REPORT confirmation itself intentionally lives until disconnect or the next proposal.
		GENNADIY_REPORT_PENDING.put(player.getUUID(), new GennadiyReportPending(hand, Long.MAX_VALUE));
		Component itemName = stack.getHoverName().copy().withStyle(style -> style.withColor(ChatFormatting.DARK_RED).withItalic(false));
		MutableComponent message = centeredGennadiyReportLiteral("Вы уверены, что хотите наложить", ChatFormatting.RED)
				.append(Component.literal("\n"))
				.append(centeredGennadiyReportLineWithItem(
						"проклятие REPORT на ",
						itemName,
						stack.getHoverName().getString(),
						"?"
				))
				.append(Component.literal("\n"))
				.append(centeredGennadiyReportLiteral("В мире может существовать только один REPORT.", ChatFormatting.RED))
				.append(Component.literal("\n"))
				.append(centeredGennadiyReportLiteral("Новый можно создать только после", ChatFormatting.RED))
				.append(Component.literal("\n"))
				.append(centeredGennadiyReportLiteral("уничтожения старого.", ChatFormatting.RED));
		MutableComponent button = Component.literal("[Подтвердить]")
				.withStyle(style -> style
						.withColor(ChatFormatting.DARK_RED)
						.withBold(true)
						.withClickEvent(new ClickEvent.RunCommand("/race gennadiy_report confirm")));
		player.sendSystemMessage(message.append(Component.literal("\n")).append(centeredGennadiyReportComponent(button, "[Подтвердить]")));
		Lg2.LOGGER.info("Player {} requested gennadiy REPORT curse for race '{}'", player.getGameProfile().name(), race.id);
		return 1;
	}

	private static int confirmGennadiyReport(CommandContext<CommandSourceStack> context) {
		ServerPlayer player = context.getSource().getPlayer();
		if (player == null) {
			context.getSource().sendFailure(Component.literal("Player only."));
			return 0;
		}

		GennadiyReportPending pending = GENNADIY_REPORT_PENDING.remove(player.getUUID());
		if (pending == null) {
			displayGennadiyReportActionbar(player, "Сначала выберите предмет для REPORT.");
			return 0;
		}
		if (hasActiveGennadiyReport(player.level().getServer())) {
			displayGennadiyReportActionbar(player, "Уже есть активный REPORT. Уничтожьте текущий предмет, чтобы создать новый.");
			return 0;
		}

		ItemStack selected = player.getItemInHand(pending.hand());
		if (selected.isEmpty()) {
			displayGennadiyReportActionbar(player, "Возьмите предмет в руку, чтобы создать REPORT.");
			return 0;
		}

		long nowTick = player.level().getGameTime();
		long cooldownTicks = getGennadiyReportCooldownTicks();
		ItemStack reportStack = selected.copyWithCount(1);
		applyGennadiyReport(reportStack, player, nowTick, cooldownTicks);
		if (selected.getCount() > 1) {
			ItemStack remainder = selected.copy();
			remainder.setCount(selected.getCount() - 1);
			player.setItemInHand(pending.hand(), reportStack);
			if (!player.getInventory().add(remainder)) {
				player.drop(remainder, false);
			}
		} else {
			player.setItemInHand(pending.hand(), reportStack);
		}

		player.getCooldowns().removeCooldown(GENNADIY_REPORT_COOLDOWN_GROUP_ID);
		sendPersonalSound(player, SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, player.position(), 1.0F, 1.0F, nowTick ^ player.getUUID().getLeastSignificantBits());
		return 1;
	}

	private static InteractionHand selectGennadiyReportHand(ServerPlayer player) {
		if (player == null) {
			return null;
		}
		if (!player.getItemInHand(InteractionHand.MAIN_HAND).isEmpty()) {
			return InteractionHand.MAIN_HAND;
		}
		return player.getItemInHand(InteractionHand.OFF_HAND).isEmpty() ? null : InteractionHand.OFF_HAND;
	}

	private static void displayGennadiyReportActionbar(ServerPlayer player, String text) {
		if (player == null || text == null || text.isBlank()) {
			return;
		}
		player.displayClientMessage(Component.literal(text).withStyle(ChatFormatting.RED), true);
	}

	private static MutableComponent centeredGennadiyReportLiteral(String text, ChatFormatting color) {
		return Component.literal(gennadiyReportCenterPadding(text) + text).withStyle(color);
	}

	private static MutableComponent centeredGennadiyReportLineWithItem(String prefix, Component itemName, String itemText, String suffix) {
		String visibleText = prefix + itemText + suffix;
		return Component.literal(gennadiyReportCenterPadding(visibleText) + prefix)
				.withStyle(ChatFormatting.RED)
				.append(itemName)
				.append(Component.literal(suffix).withStyle(ChatFormatting.RED));
	}

	private static MutableComponent centeredGennadiyReportComponent(Component component, String visibleText) {
		return Component.literal(gennadiyReportCenterPadding(visibleText)).append(component);
	}

	private static String gennadiyReportCenterPadding(String visibleText) {
		int length = visibleText == null ? 0 : visibleText.length();
		int padding = Math.max(0, (GENNADIY_REPORT_CONFIRM_LINE_WIDTH_CHARS - length) / 2);
		return " ".repeat(padding);
	}

	private static void applyGennadiyReport(ItemStack stack, ServerPlayer creator, long nowTick, long cooldownTicks) {
		if (stack == null || stack.isEmpty()) {
			return;
		}
		UUID reportId = UUID.randomUUID();
		long cooldownEndTick = nowTick + Math.max(1L, cooldownTicks);
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
			CompoundTag root = tag.getCompoundOrEmpty(GENNADIY_REPORT_DATA_TAG);
			root.putString(GENNADIY_REPORT_ID_TAG, reportId.toString());
			root.putString(GENNADIY_REPORT_CREATOR_TAG, creator.getUUID().toString());
			root.putString(GENNADIY_REPORT_HOLDER_TAG, creator.getUUID().toString());
			root.putLong(GENNADIY_REPORT_COOLDOWN_END_TAG, cooldownEndTick);
			tag.put(GENNADIY_REPORT_DATA_TAG, root);
		});
		enforceGennadiyReportComponents(stack);
	}

	public static boolean isGennadiyReportItem(ItemStack stack) {
		return readGennadiyReportData(stack) != null;
	}

	private static GennadiyReportData readGennadiyReportData(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return null;
		}
		CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
		if (customData == null) {
			return null;
		}
		CompoundTag root = customData.copyTag().getCompoundOrEmpty(GENNADIY_REPORT_DATA_TAG);
		String idText = root.getStringOr(GENNADIY_REPORT_ID_TAG, "");
		if (idText.isBlank()) {
			return null;
		}
		try {
			UUID id = UUID.fromString(idText);
			UUID holderId = parseOptionalUuid(root.getStringOr(GENNADIY_REPORT_HOLDER_TAG, ""));
			return new GennadiyReportData(id, holderId, root.getLongOr(GENNADIY_REPORT_COOLDOWN_END_TAG, 0L));
		} catch (IllegalArgumentException ignored) {
			return null;
		}
	}

	private static UUID parseOptionalUuid(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return UUID.fromString(value);
		} catch (IllegalArgumentException ignored) {
			return null;
		}
	}

	private static void enforceGennadiyReportComponents(ItemStack stack) {
		if (stack == null || stack.isEmpty() || readGennadiyReportData(stack) == null) {
			return;
		}
		stack.set(DataComponents.CUSTOM_NAME, Component.literal("REPORT").withStyle(style -> style.withColor(ChatFormatting.RED).withItalic(false)));
		stack.remove(DataComponents.USE_COOLDOWN);
		stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, Boolean.TRUE);
	}

	private static boolean hasActiveGennadiyReport(MinecraftServer server) {
		if (server == null) {
			return false;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (findGennadiyReportInPlayer(player) != null) {
				return true;
			}
		}
		for (ServerLevel level : server.getAllLevels()) {
			for (Entity entity : level.getAllEntities()) {
				if (entity instanceof ItemEntity itemEntity && isGennadiyReportItem(itemEntity.getItem())) {
					enforceGennadiyReportComponents(itemEntity.getItem());
					return true;
				}
			}
			if (hasGennadiyReportInLoadedContainers(level)) {
				return true;
			}
		}
		return false;
	}

	private static ItemStack findGennadiyReportInPlayer(ServerPlayer player) {
		if (player == null) {
			return null;
		}
		Inventory inventory = player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (isGennadiyReportItem(stack)) {
				enforceGennadiyReportComponents(stack);
				return stack;
			}
		}
		ItemStack carried = player.containerMenu == null ? ItemStack.EMPTY : player.containerMenu.getCarried();
		if (isGennadiyReportItem(carried)) {
			enforceGennadiyReportComponents(carried);
			return carried;
		}
		return null;
	}

	private static boolean hasGennadiyReportInLoadedContainers(ServerLevel level) {
		if (level == null) {
			return false;
		}
		Set<Long> checkedChunks = new HashSet<>();
		for (ServerPlayer player : level.players()) {
			int centerChunkX = player.chunkPosition().x;
			int centerChunkZ = player.chunkPosition().z;
			for (int chunkX = centerChunkX - GENNADIY_REPORT_CONTAINER_SCAN_CHUNK_RADIUS; chunkX <= centerChunkX + GENNADIY_REPORT_CONTAINER_SCAN_CHUNK_RADIUS; chunkX++) {
				for (int chunkZ = centerChunkZ - GENNADIY_REPORT_CONTAINER_SCAN_CHUNK_RADIUS; chunkZ <= centerChunkZ + GENNADIY_REPORT_CONTAINER_SCAN_CHUNK_RADIUS; chunkZ++) {
					long chunkKey = net.minecraft.world.level.ChunkPos.asLong(chunkX, chunkZ);
					if (!checkedChunks.add(chunkKey)) {
						continue;
					}
					LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
					if (chunk == null) {
						continue;
					}
					for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
						if (blockEntity instanceof Container container && containsGennadiyReport(container)) {
							return true;
						}
					}
				}
			}
		}
		return false;
	}

	private static boolean containsGennadiyReport(Container container) {
		if (container == null) {
			return false;
		}
		for (int slot = 0; slot < container.getContainerSize(); slot++) {
			ItemStack stack = container.getItem(slot);
			if (isGennadiyReportItem(stack)) {
				enforceGennadiyReportComponents(stack);
				return true;
			}
		}
		return false;
	}

	private static long getGennadiyReportCooldownTicks() {
		return Math.max(1L, asTicks(getGennadiyReportCooldownSeconds()));
	}

	private static double getGennadiyReportCooldownSeconds() {
		for (PlayerRaceConfig race : RaceConfig.get().races) {
			if (race != null && GENNADIY_SHAROYOBOV_RACE_ID.equals(sanitizePath(race.id))) {
				return getGennadiyReportCooldownSeconds(race.shnyaga);
			}
		}
		return GENNADIY_REPORT_DEFAULT_COOLDOWN_SECONDS;
	}

	private static double getGennadiyReportCooldownSeconds(RaceAbilityConfig ability) {
		return positiveOrDefault(ability == null ? 0.0D : ability.gennadiyReportCooldownSeconds, GENNADIY_REPORT_DEFAULT_COOLDOWN_SECONDS);
	}

	private static int useGennadiyDonkeyAttack(ServerPlayer player, PlayerRaceConfig race, RaceAbilityConfig ability) {
		if (player == null || ability == null || player.isSpectator() || !player.isAlive()) {
			return 0;
		}
		if (!(player.level() instanceof ServerLevel level)) {
			return 0;
		}

		UUID ownerId = player.getUUID();
		GennadiyDonkeyState state = getGennadiyDonkeyState(ownerId);
		syncGennadiyDonkeyConfig(state, ability);
		if (isGennadiyDonkeyActive(level.getServer(), state)) {
			recallGennadiyDonkey(level.getServer(), ownerId, false);
			return 1;
		}

		long remainingCooldownTicks = getRemainingOnlineCooldownTicks(GENNADIY_DONKEY_COOLDOWNS, ownerId);
		if (displayRemainingCooldown(player, remainingCooldownTicks)) {
			return 0;
		}

		applyGennadiyDonkeyStoredRegen(state, level.getGameTime());
		if (state.health <= 0.0D) {
			state.health = state.maxHealth;
		}
		if (state.ammo < 0) {
			state.ammo = state.maxAmmo;
		}

		Donkey donkey = spawnGennadiyDonkey(level, player, state);
		if (donkey == null) {
			player.sendSystemMessage(Component.literal("Не удалось призвать боевого ослика."));
			return 0;
		}

		state.dimension = level.dimension();
		state.donkeyId = donkey.getUUID();
		state.visualId = null;
		state.helmetVisualId = null;
		state.manualTriggerId = null;
		state.targetId = null;
		state.wanderTarget = null;
		state.outsideSinceTick = null;
		state.nextShotTick = 0L;
		state.lastManualInputTick = Long.MIN_VALUE;
		state.nextMovementLogicTick = 0L;
		state.nextWanderRetargetTick = 0L;
		state.legacyVisualsCleaned = false;
		GENNADIY_DONKEY_OWNER_BY_ENTITY.put(donkey.getUUID(), ownerId);
		ensureGennadiyDonkeyVisual(level, donkey, state);
		emitSmoke(level, donkey.position());
		Lg2.LOGGER.info("Player {} summoned battle donkey '{}'", player.getGameProfile().name(), ability.abilityId);
		return 1;
	}

	private static GennadiyDonkeyState getGennadiyDonkeyState(UUID ownerId) {
		return GENNADIY_DONKEY_STATES.computeIfAbsent(ownerId, GennadiyDonkeyState::new);
	}

	private static void syncGennadiyDonkeyConfig(GennadiyDonkeyState state, RaceAbilityConfig ability) {
		if (state == null) {
			return;
		}
		double oldMaxHealth = state.maxHealth;
		state.maxHealth = positiveOrDefault(ability == null ? 0.0D : ability.gennadiyDonkeyHealthPoints, GENNADIY_DONKEY_DEFAULT_HEALTH_POINTS);
		state.armorDivider = positiveOrDefault(ability == null ? 0.0D : ability.gennadiyDonkeyArmorDivider, GENNADIY_DONKEY_DEFAULT_ARMOR_DIVIDER);
		state.healthRegenTicks = Math.max(1L, asTicks(positiveOrDefault(ability == null ? 0.0D : ability.gennadiyDonkeyHealthRegenSeconds, GENNADIY_DONKEY_DEFAULT_HEALTH_REGEN_SECONDS)));
		state.cooldownTicks = Math.max(1L, asTicks(positiveOrDefault(ability == null ? 0.0D : ability.cooldownSeconds, GENNADIY_DONKEY_DEFAULT_COOLDOWN_SECONDS)));
		state.maxAmmo = Math.max(1, positiveIntOrDefault(ability == null ? 0 : ability.gennadiyDonkeyMaxAmmo, GENNADIY_DONKEY_DEFAULT_MAX_AMMO));
		state.ammoRegenAmount = Math.max(1, positiveIntOrDefault(ability == null ? 0 : ability.gennadiyDonkeyAmmoRegenAmount, GENNADIY_DONKEY_DEFAULT_AMMO_REGEN_AMOUNT));
		state.ammoRegenTicks = Math.max(1L, asTicks(positiveOrDefault(ability == null ? 0.0D : ability.gennadiyDonkeyAmmoRegenSeconds, GENNADIY_DONKEY_DEFAULT_AMMO_REGEN_SECONDS)));
		state.bulletDamage = positiveOrDefault(ability == null ? 0.0D : ability.gennadiyDonkeyBulletDamage, GENNADIY_DONKEY_DEFAULT_BULLET_DAMAGE);
		state.bulletRange = positiveOrDefault(ability == null ? 0.0D : ability.gennadiyDonkeyBulletRangeBlocks, GENNADIY_DONKEY_DEFAULT_BULLET_RANGE_BLOCKS);
		state.followMaxDistance = positiveOrDefault(ability == null ? 0.0D : ability.gennadiyDonkeyFollowMaxDistanceBlocks, GENNADIY_DONKEY_DEFAULT_FOLLOW_MAX_DISTANCE_BLOCKS);
		if (oldMaxHealth <= 0.0D || state.health <= 0.0D) {
			state.health = state.maxHealth;
		} else {
			state.health = Math.min(state.maxHealth, state.health);
		}
		state.ammo = Math.max(0, Math.min(state.maxAmmo, state.ammo));
	}

	private static int positiveIntOrDefault(int value, int fallback) {
		return value > 0 ? value : fallback;
	}

	private static Donkey spawnGennadiyDonkey(ServerLevel level, ServerPlayer owner, GennadiyDonkeyState state) {
		if (level == null || owner == null || state == null) {
			return null;
		}
		Donkey donkey = EntityType.DONKEY.create(level, EntitySpawnReason.TRIGGERED);
		if (donkey == null) {
			return null;
		}
		Vec3 spawnPos = findGennadiyDonkeySpawnPos(level, owner);
		donkey.setPos(spawnPos.x, spawnPos.y, spawnPos.z);
		donkey.setYRot(owner.getYRot());
		donkey.setYBodyRot(owner.getYRot());
		donkey.setYHeadRot(owner.getYRot());
		donkey.setTamed(true);
		donkey.setOwner(owner);
		donkey.setChest(false);
		donkey.setCanPickUpLoot(false);
		donkey.setPersistenceRequired();
		donkey.addTag(GENNADIY_DONKEY_TAG);
		donkey.addTag(GENNADIY_DONKEY_OWNER_TAG_PREFIX + owner.getUUID());
		donkey.setCustomName(null);
		donkey.setCustomNameVisible(false);
		donkey.setItemSlot(EquipmentSlot.SADDLE, new ItemStack(Items.SADDLE));
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			donkey.setDropChance(slot, 0.0F);
		}
		((MobXpRewardAccessor) (Object) donkey).lg2$setXpReward(0);
		if (donkey.getAttribute(Attributes.MAX_HEALTH) != null) {
			donkey.getAttribute(Attributes.MAX_HEALTH).setBaseValue(state.maxHealth);
		}
		if (donkey.getAttribute(Attributes.MOVEMENT_SPEED) != null) {
			donkey.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.18D);
		}
		if (donkey.getAttribute(Attributes.KNOCKBACK_RESISTANCE) != null) {
			donkey.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0.45D);
		}
		donkey.setHealth((float) Math.max(1.0D, Math.min(state.maxHealth, state.health)));
		donkey.refreshDimensions();
		return level.addFreshEntity(donkey) ? donkey : null;
	}

	private static Vec3 findGennadiyDonkeySpawnPos(ServerLevel level, ServerPlayer owner) {
		Vec3 forward = owner.getLookAngle();
		Vec3 horizontal = new Vec3(forward.x, 0.0D, forward.z);
		if (horizontal.lengthSqr() <= 1.0E-6D) {
			horizontal = Vec3.directionFromRotation(0.0F, owner.getYRot());
		}
		horizontal = horizontal.normalize();
		for (int attempt = 0; attempt < 12; attempt++) {
			double angle = (Math.PI * 2.0D * attempt) / 12.0D;
			Vec3 offset = attempt == 0
					? horizontal.scale(1.8D)
					: new Vec3(Math.cos(angle) * 2.0D, 0.0D, Math.sin(angle) * 2.0D);
			Vec3 resolved = resolveGennadiyDonkeySpawnPosition(level, owner.position().add(offset));
			if (resolved != null) {
				return resolved;
			}
		}
		Vec3 fallback = owner.position().add(horizontal.scale(1.8D));
		return new Vec3(fallback.x, owner.getY(), fallback.z);
	}

	private static Vec3 resolveGennadiyDonkeySpawnPosition(ServerLevel level, Vec3 desiredCenter) {
		BlockPos origin = BlockPos.containing(desiredCenter.x, desiredCenter.y, desiredCenter.z);
		for (int dy = -1; dy <= 2; dy++) {
			BlockPos candidate = origin.offset(0, dy, 0);
			if (!level.getBlockState(candidate).canBeReplaced() || !level.getBlockState(candidate.above()).canBeReplaced()) {
				continue;
			}
			AABB box = GENNADIY_DONKEY_DIMENSIONS.makeBoundingBox(candidate.getX() + 0.5D, candidate.getY(), candidate.getZ() + 0.5D);
			if (level.noCollision(box)) {
				return new Vec3(candidate.getX() + 0.5D, candidate.getY(), candidate.getZ() + 0.5D);
			}
		}
		return null;
	}

	private static void tickGennadiyDonkeys(MinecraftServer server) {
		if (server == null) {
			return;
		}
		tickOnlineCooldowns(server, GENNADIY_DONKEY_COOLDOWNS);
		long nowTick = server.overworld().getGameTime();
		tickGennadiyDonkeyBulletVisuals(server);
		for (Map.Entry<UUID, GennadiyDonkeyState> entry : new ArrayList<>(GENNADIY_DONKEY_STATES.entrySet())) {
			UUID ownerId = entry.getKey();
			GennadiyDonkeyState state = entry.getValue();
			if (state == null) {
				continue;
			}
			if (!state.isActive()) {
				applyGennadiyDonkeyStoredRegen(state, nowTick);
				continue;
			}
			ServerLevel level = state.dimension == null ? null : server.getLevel(state.dimension);
			Entity entity = level == null ? null : level.getEntity(state.donkeyId);
			ServerPlayer owner = server.getPlayerList().getPlayer(ownerId);
			if (!(entity instanceof Donkey donkey)) {
				handleGennadiyDonkeyKilledOrMissing(server, ownerId, state);
				continue;
			}
			if (!donkey.isAlive()) {
				handleGennadiyDonkeyKilledOrMissing(server, ownerId, state);
				continue;
			}
			if (owner == null || !owner.isAlive() || owner.isSpectator()) {
				recallGennadiyDonkey(server, ownerId, false);
				continue;
			}
			if (!owner.level().dimension().equals(state.dimension)) {
				recallGennadiyDonkey(server, ownerId, false);
				continue;
			}
			if (donkey.hasCustomName()) {
				donkey.setCustomName(null);
				donkey.setCustomNameVisible(false);
			}
			state.health = donkey.getHealth();
			updateGennadiyDonkeyArmor(owner, donkey, state);
			ensureGennadiyDonkeyVisual(level, donkey, state);
			syncGennadiyDonkeyManualTrigger(owner);
			tickGennadiyDonkeyCombat(level, owner, donkey, state, nowTick);
			tickGennadiyDonkeyMovement(level, owner, donkey, state, nowTick);
			updateGennadiyDonkeyVisual(level, owner, donkey, state);
		}
	}

	private static void applyGennadiyDonkeyStoredRegen(GennadiyDonkeyState state, long nowTick) {
		if (state == null || state.isActive()) {
			return;
		}
		if (state.health < state.maxHealth && state.healthRegenTicks > 0L) {
			if (state.nextHealthRegenTick == Long.MIN_VALUE) {
				state.nextHealthRegenTick = nowTick + state.healthRegenTicks;
			}
			while (state.health < state.maxHealth && nowTick >= state.nextHealthRegenTick) {
				state.health = Math.min(state.maxHealth, state.health + 1.0D);
				state.nextHealthRegenTick += state.healthRegenTicks;
			}
		}
		if (state.health >= state.maxHealth) {
			state.nextHealthRegenTick = Long.MIN_VALUE;
		}
		if (state.ammo < state.maxAmmo && state.ammoRegenTicks > 0L) {
			if (state.nextAmmoRegenTick == Long.MIN_VALUE) {
				state.nextAmmoRegenTick = nowTick + state.ammoRegenTicks;
			}
			while (state.ammo < state.maxAmmo && nowTick >= state.nextAmmoRegenTick) {
				state.ammo = Math.min(state.maxAmmo, state.ammo + state.ammoRegenAmount);
				state.nextAmmoRegenTick += state.ammoRegenTicks;
			}
		}
		if (state.ammo >= state.maxAmmo) {
			state.nextAmmoRegenTick = Long.MIN_VALUE;
		}
	}

	private static boolean isGennadiyDonkeyActive(MinecraftServer server, GennadiyDonkeyState state) {
		if (server == null || state == null || !state.isActive() || state.dimension == null) {
			return false;
		}
		ServerLevel level = server.getLevel(state.dimension);
		Entity entity = level == null ? null : level.getEntity(state.donkeyId);
		return entity instanceof Donkey donkey && donkey.isAlive();
	}

	private static void handleGennadiyDonkeyKilledOrMissing(MinecraftServer server, UUID ownerId, GennadiyDonkeyState state) {
		if (server == null || ownerId == null || state == null) {
			return;
		}
		removeGennadiyDonkeyVisual(server, state);
		removeGennadiyDonkeyManualTrigger(server, state);
		if (state.donkeyId != null) {
			GENNADIY_DONKEY_OWNER_BY_ENTITY.remove(state.donkeyId);
		}
		state.donkeyId = null;
		state.visualId = null;
		state.manualTriggerId = null;
		state.targetId = null;
		state.wanderTarget = null;
		state.outsideSinceTick = null;
		state.health = state.maxHealth;
		state.ammo = state.maxAmmo;
		state.nextHealthRegenTick = Long.MIN_VALUE;
		state.nextAmmoRegenTick = Long.MIN_VALUE;
		startOnlineCooldown(GENNADIY_DONKEY_COOLDOWNS, ownerId, Math.max(1L, state.cooldownTicks));
	}

	private static void recallGennadiyDonkey(MinecraftServer server, UUID ownerId, boolean killed) {
		if (server == null || ownerId == null) {
			return;
		}
		GennadiyDonkeyState state = GENNADIY_DONKEY_STATES.get(ownerId);
		if (state == null || !state.isActive()) {
			return;
		}
		ServerLevel level = state.dimension == null ? null : server.getLevel(state.dimension);
		Entity entity = level == null ? null : level.getEntity(state.donkeyId);
		if (entity instanceof Donkey donkey && donkey.isAlive() && !killed) {
			state.health = Math.max(1.0D, donkey.getHealth());
			emitSmoke(level, donkey.position());
			donkey.discard();
		} else if (entity != null) {
			entity.discard();
		}
		removeGennadiyDonkeyVisual(server, state);
		removeGennadiyDonkeyManualTrigger(server, state);
		if (state.donkeyId != null) {
			GENNADIY_DONKEY_OWNER_BY_ENTITY.remove(state.donkeyId);
		}
		long nowTick = server.overworld().getGameTime();
		state.donkeyId = null;
		state.visualId = null;
		state.manualTriggerId = null;
		state.targetId = null;
		state.wanderTarget = null;
		state.outsideSinceTick = null;
		state.nextHealthRegenTick = state.health < state.maxHealth ? nowTick + state.healthRegenTicks : Long.MIN_VALUE;
		state.nextAmmoRegenTick = state.ammo < state.maxAmmo ? nowTick + state.ammoRegenTicks : Long.MIN_VALUE;
	}

	private static void cleanupAllGennadiyDonkeys(MinecraftServer server) {
		if (server == null) {
			return;
		}
		for (GennadiyDonkeyState state : new ArrayList<>(GENNADIY_DONKEY_STATES.values())) {
			if (state == null) {
				continue;
			}
			ServerLevel level = state.dimension == null ? null : server.getLevel(state.dimension);
			if (level != null && state.donkeyId != null) {
				Entity entity = level.getEntity(state.donkeyId);
				if (entity != null) {
					entity.discard();
				}
			}
			removeGennadiyDonkeyVisual(server, state);
			removeGennadiyDonkeyManualTrigger(server, state);
		}
	}

	private static void updateGennadiyDonkeyArmor(ServerPlayer owner, Donkey donkey, GennadiyDonkeyState state) {
		if (owner == null || donkey == null || state == null) {
			return;
		}
		AttributeInstance armor = donkey.getAttribute(Attributes.ARMOR);
		if (armor == null) {
			return;
		}
		boolean manual = donkey.hasPassenger(owner);
		double value = manual ? Math.max(0.0D, owner.getArmorValue() / Math.max(1.0D, state.armorDivider)) : 0.0D;
		if (Math.abs(armor.getBaseValue() - value) > 1.0E-4D) {
			armor.setBaseValue(value);
		}
	}

	private static void tickGennadiyDonkeyCombat(ServerLevel level, ServerPlayer owner, Donkey donkey, GennadiyDonkeyState state, long nowTick) {
		if (level == null || owner == null || donkey == null || state == null) {
			return;
		}
		if (!donkey.hasPassenger(owner) && state.ammo < 2) {
			clearGennadiyDonkeyTarget(state);
			return;
		}
		LivingEntity target = resolveGennadiyDonkeyTarget(level.getServer(), owner, donkey, state);
		if (target == null) {
			clearGennadiyDonkeyTarget(state);
			if (!donkey.hasPassenger(owner)) {
				target = findGennadiyDonkeyAggressor(level, owner, donkey, state, nowTick);
				if (target != null) {
					setGennadiyDonkeyTarget(owner.getUUID(), target, GENNADIY_DONKEY_TARGET_PRIORITY_AGGRO);
				}
			}
		}
		if (donkey.hasPassenger(owner)) {
			if (state.lastManualInputTick + GENNADIY_DONKEY_MANUAL_INPUT_GRACE_TICKS >= nowTick && nowTick >= state.nextShotTick) {
				fireGennadiyDonkeyTwinShot(level, owner, donkey, state, owner.getLookAngle().normalize());
			}
			return;
		}
		if (target == null || nowTick < state.nextShotTick || state.ammo < 2) {
			return;
		}
		Vec3 origin = getGennadiyDonkeyTurretOrigin(donkey);
		Vec3 targetCenter = target.position().add(0.0D, Math.max(0.25D, target.getBbHeight() * 0.55D), 0.0D);
		Vec3 direction = targetCenter.subtract(origin);
		if (direction.lengthSqr() <= 1.0E-6D || direction.length() > state.bulletRange) {
			return;
		}
		fireGennadiyDonkeyTwinShot(level, owner, donkey, state, direction.normalize());
	}

	private static LivingEntity findGennadiyDonkeyAggressor(ServerLevel level, ServerPlayer owner, Donkey donkey, GennadiyDonkeyState state, long nowTick) {
		if (level == null || owner == null || donkey == null || state == null || state.ammo < 2 || nowTick < state.nextAggroScanTick) {
			return null;
		}
		state.nextAggroScanTick = nowTick + GENNADIY_DONKEY_AGGRO_SCAN_INTERVAL_TICKS;
		double scanRange = Math.max(GENNADIY_DONKEY_OWNER_MIN_DISTANCE_BLOCKS, Math.max(state.bulletRange, state.followMaxDistance) + 16.0D);
		AABB area = donkey.getBoundingBox().inflate(scanRange);
		LivingEntity best = null;
		double bestDistanceSqr = Double.MAX_VALUE;
		for (Mob mob : level.getEntitiesOfClass(Mob.class, area, mob -> mob != null && mob.getTarget() == donkey)) {
			if (!canGennadiyDonkeyTarget(owner, donkey, mob)) {
				continue;
			}
			double distanceSqr = mob.distanceToSqr(donkey);
			if (distanceSqr < bestDistanceSqr) {
				best = mob;
				bestDistanceSqr = distanceSqr;
			}
		}
		return best;
	}

	private static LivingEntity resolveGennadiyDonkeyTarget(MinecraftServer server, ServerPlayer owner, Donkey donkey, GennadiyDonkeyState state) {
		if (server == null || owner == null || donkey == null || state == null || state.targetId == null) {
			return null;
		}
		if (state.ammo < 2) {
			clearGennadiyDonkeyTarget(state);
			return null;
		}
		Entity entity = findEntity(server, state.targetId);
		if (!(entity instanceof LivingEntity target) || !canGennadiyDonkeyTarget(owner, donkey, target)) {
			return null;
		}
		if (target.level() != donkey.level()) {
			return null;
		}
		double maxRange = Math.max(GENNADIY_DONKEY_TARGET_FORGET_RANGE_BLOCKS, state.bulletRange + state.followMaxDistance + 16.0D);
		if (target.distanceToSqr(donkey) > maxRange * maxRange) {
			return null;
		}
		return target;
	}

	private static boolean canGennadiyDonkeyTarget(ServerPlayer owner, Donkey donkey, Entity entity) {
		if (owner == null || donkey == null || !(entity instanceof LivingEntity target)) {
			return false;
		}
		if (!target.isAlive() || target.isSpectator() || target == owner || target == donkey) {
			return false;
		}
		if (target instanceof ArmorStand) {
			return false;
		}
		if (target instanceof ServerPlayer playerTarget && owner.isAlliedTo(playerTarget)) {
			return false;
		}
		return true;
	}

	private static void fireGennadiyDonkeyTwinShot(ServerLevel level, ServerPlayer owner, Donkey donkey, GennadiyDonkeyState state, Vec3 direction) {
		if (level == null || owner == null || donkey == null || state == null || direction == null || direction.lengthSqr() <= 1.0E-6D) {
			return;
		}
		if (state.ammo < 2) {
			return;
		}
		Vec3 normalized = direction.normalize();
		Vec3 origin = getGennadiyDonkeyTurretOrigin(donkey);
		Vec3 right = new Vec3(-normalized.z, 0.0D, normalized.x);
		if (right.lengthSqr() <= 1.0E-6D) {
			right = Vec3.directionFromRotation(0.0F, donkey.getYRot() + 90.0F);
		}
		right = right.normalize();
		Vec3 leftMuzzle = origin.add(right.scale(-0.28D)).add(normalized.scale(0.55D));
		Vec3 rightMuzzle = origin.add(right.scale(0.28D)).add(normalized.scale(0.55D));
		Map<LivingEntity, Double> damageByTarget = new LinkedHashMap<>();
		LivingEntity leftTarget = fireGennadiyDonkeyBullet(level, owner, donkey, state, leftMuzzle, normalized);
		if (leftTarget != null) {
			damageByTarget.merge(leftTarget, Math.max(0.0D, state.bulletDamage), Double::sum);
		}
		LivingEntity rightTarget = fireGennadiyDonkeyBullet(level, owner, donkey, state, rightMuzzle, normalized);
		if (rightTarget != null) {
			damageByTarget.merge(rightTarget, Math.max(0.0D, state.bulletDamage), Double::sum);
		}
		for (Map.Entry<LivingEntity, Double> entry : damageByTarget.entrySet()) {
			damageGennadiyDonkeyBulletTarget(level, donkey, origin, entry.getKey(), entry.getValue());
		}
		state.ammo = Math.max(0, state.ammo - 2);
		if (state.ammo < 2) {
			clearGennadiyDonkeyTarget(state);
		}
		state.nextShotTick = level.getGameTime() + GENNADIY_DONKEY_SHOT_INTERVAL_TICKS;
		playGennadiyDonkeyShootSound(level, origin);
	}

	private static Vec3 getGennadiyDonkeyTurretOrigin(Donkey donkey) {
		return donkey.position().add(0.0D, Math.max(1.25D, donkey.getBbHeight() * 0.86D), 0.0D);
	}

	private static LivingEntity fireGennadiyDonkeyBullet(ServerLevel level, ServerPlayer owner, Donkey donkey, GennadiyDonkeyState state, Vec3 start, Vec3 direction) {
		Vec3 end = start.add(direction.scale(state.bulletRange));
		BlockHitResult blockHit = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, donkey));
		Vec3 blockEnd = blockHit.getType() == HitResult.Type.MISS ? end : blockHit.getLocation();
		EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
				level,
				donkey,
				start,
				blockEnd,
				new AABB(start, blockEnd).inflate(0.35D),
				entity -> canGennadiyBulletHit(owner, donkey, entity),
				0.25F
		);
		Vec3 impact = blockEnd;
		LivingEntity hitTarget = null;
		if (entityHit != null && entityHit.getEntity() instanceof LivingEntity target) {
			impact = entityHit.getLocation();
			hitTarget = target;
		}
		spawnGennadiyBulletVisual(level, start, impact);
		return hitTarget;
	}

	private static void damageGennadiyDonkeyBulletTarget(ServerLevel level, Donkey donkey, Vec3 sourcePos, LivingEntity target, double damage) {
		if (level == null || donkey == null || sourcePos == null || target == null || !target.isAlive() || damage <= 0.0D) {
			return;
		}
		Arrow arrow = new Arrow(level, donkey, new ItemStack(Items.ARROW), new ItemStack(Items.CROSSBOW));
		arrow.setPos(sourcePos);
		target.invulnerableTime = 0;
		target.hurtServer(level, level.damageSources().arrow(arrow, donkey), (float) damage);
		target.invulnerableTime = 0;
		arrow.discard();
	}

	private static boolean canGennadiyBulletHit(ServerPlayer owner, Donkey donkey, Entity entity) {
		return canGennadiyDonkeyTarget(owner, donkey, entity);
	}

	private static void spawnGennadiyBulletVisual(ServerLevel level, Vec3 start, Vec3 end) {
		if (level == null || start == null || end == null) {
			return;
		}
		Vec3 delta = end.subtract(start);
		double length = delta.length();
		if (length <= 1.0E-6D) {
			spawnGennadiyBulletImpactParticles(level, start);
			return;
		}
		Vec3 direction = delta.scale(1.0D / length);
		GENNADIY_DONKEY_BULLET_VISUALS.add(new GennadiyDonkeyBulletVisual(level.dimension(), start, end));
		spawnGennadiyBulletMuzzleParticles(level, start, direction);
	}

	private static void tickGennadiyDonkeyBulletVisuals(MinecraftServer server) {
		if (server == null || GENNADIY_DONKEY_BULLET_VISUALS.isEmpty()) {
			return;
		}
		Iterator<GennadiyDonkeyBulletVisual> iterator = GENNADIY_DONKEY_BULLET_VISUALS.iterator();
		while (iterator.hasNext()) {
			GennadiyDonkeyBulletVisual bullet = iterator.next();
			ServerLevel level = bullet.dimension == null ? null : server.getLevel(bullet.dimension);
			if (level == null || bullet.remainingDistance <= 1.0E-6D || bullet.direction.lengthSqr() <= 1.0E-6D) {
				iterator.remove();
				continue;
			}
			double step = Math.min(GENNADIY_DONKEY_BULLET_VISUAL_SPEED_BLOCKS, bullet.remainingDistance);
			Vec3 previous = bullet.position;
			bullet.position = bullet.position.add(bullet.direction.scale(step));
			bullet.remainingDistance -= step;
			spawnGennadiyBulletHeadParticles(level, previous, bullet.position, bullet.direction);
			if (bullet.remainingDistance <= 1.0E-6D) {
				spawnGennadiyBulletImpactParticles(level, bullet.position);
				iterator.remove();
			}
		}
	}

	private static void spawnGennadiyBulletMuzzleParticles(ServerLevel level, Vec3 position, Vec3 direction) {
		Vec3 smoke = position.subtract(direction.scale(0.12D));
		level.sendParticles(ParticleTypes.SMOKE, smoke.x, smoke.y, smoke.z, 1, 0.02D, 0.02D, 0.02D, 0.003D);
		level.sendParticles(ParticleTypes.CRIT, position.x, position.y, position.z, 1, 0.01D, 0.01D, 0.01D, 0.008D);
	}

	private static void spawnGennadiyBulletHeadParticles(ServerLevel level, Vec3 previous, Vec3 current, Vec3 direction) {
		Vec3 tail = current.subtract(direction.scale(0.18D));
		level.sendParticles(new DustParticleOptions(0xC27A2C, 0.45F), current.x, current.y, current.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
		level.sendParticles(ParticleTypes.SMOKE, tail.x, tail.y, tail.z, 1, 0.006D, 0.006D, 0.006D, 0.0D);
	}

	private static void spawnGennadiyBulletImpactParticles(ServerLevel level, Vec3 position) {
		level.sendParticles(ParticleTypes.CRIT, position.x, position.y, position.z, 3, 0.04D, 0.04D, 0.04D, 0.03D);
		level.sendParticles(ParticleTypes.SMOKE, position.x, position.y, position.z, 2, 0.035D, 0.035D, 0.035D, 0.0D);
	}

	private static void tickGennadiyHook(MinecraftServer server) {
		if (server == null || GENNADIY_HOOK_SESSIONS.isEmpty()) {
			return;
		}

		Iterator<Map.Entry<UUID, GennadiyHookSession>> iterator = GENNADIY_HOOK_SESSIONS.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<UUID, GennadiyHookSession> entry = iterator.next();
			GennadiyHookSession session = entry.getValue();
			ServerPlayer player = session == null ? null : server.getPlayerList().getPlayer(session.playerId);
			ServerLevel level = session == null || session.dimension == null ? null : server.getLevel(session.dimension);
			if (session == null
					|| player == null
					|| level == null
					|| player.level() != level
					|| !player.isAlive()
					|| player.isSpectator()) {
				cleanupGennadiyHookSession(server, session);
				iterator.remove();
				continue;
			}

			if (!tickGennadiyHookSession(server, level, player, session)) {
				cleanupGennadiyHookSession(server, session);
				iterator.remove();
			}
		}
	}

	private static boolean tickGennadiyHookSession(MinecraftServer server, ServerLevel level, ServerPlayer player, GennadiyHookSession session) {
		Vec3 origin = getGennadiyHookOrigin(player);
		maybeRefreshGennadiyHookChainSound(level, session, origin);
		if (session.phase == GennadiyHookPhase.EXTENDING) {
			session.visibleLength = Math.min(session.endpointDistance, session.visibleLength + GENNADIY_HOOK_EXTEND_SPEED_BLOCKS);
			updateGennadiyHookSegments(server, level, session, origin, session.castDirection, session.visibleLength);
			if (session.visibleLength + 1.0E-4D < session.endpointDistance) {
				return true;
			}
			Vec3 hookEnd = origin.add(session.castDirection.scale(session.endpointDistance));
			Entity latchedEntity = session.targetId == null ? null : findEntity(server, session.targetId);
			if (!(latchedEntity instanceof LivingEntity latchedTarget)
					|| !canGennadiyHookHit(player, latchedTarget)
					|| latchedTarget.level() != level) {
				session.phase = GennadiyHookPhase.RETRACTING;
			} else {
				session.phase = GennadiyHookPhase.PULLING;
				Vec3 latchedCenter = latchedTarget.position().add(0.0D, getGennadiyHookTargetCenterYOffset(latchedTarget), 0.0D);
				playGennadiyHookImpactSound(level, latchedCenter);
			}
			return true;
		}

		if (session.phase == GennadiyHookPhase.RETRACTING) {
			session.visibleLength = Math.max(0.0D, session.visibleLength - GENNADIY_HOOK_RETRACT_SPEED_BLOCKS);
			updateGennadiyHookSegments(server, level, session, origin, session.castDirection, session.visibleLength);
			if (session.visibleLength > 0.05D) {
				return true;
			}
			stopGennadiyHookChainSound(level, origin);
			playGennadiyHookEndSound(level, origin);
			return false;
		}

		Entity entity = findEntity(server, session.targetId);
		if (!(entity instanceof LivingEntity target) || !canGennadiyHookHit(player, target) || target.level() != level) {
			session.phase = GennadiyHookPhase.RETRACTING;
			return true;
		}

		double targetCenterYOffset = getGennadiyHookTargetCenterYOffset(target);
		Vec3 targetCenter = target.position().add(0.0D, targetCenterYOffset, 0.0D);
		Vec3 toTarget = targetCenter.subtract(origin);
		double distance = toTarget.length();
		if (distance <= GENNADIY_HOOK_FINISH_DISTANCE_BLOCKS) {
			finishGennadiyHookPull(level, player, target, session);
			return false;
		}
		if (distance <= 1.0E-6D) {
			return false;
		}

		Vec3 directionToTarget = toTarget.scale(1.0D / distance);
		double pull = getGennadiyHookPullDistance(distance);
		double nextDistance = Math.max(GENNADIY_HOOK_FINISH_DISTANCE_BLOCKS, distance - pull);
		Vec3 nextTargetCenter = origin.add(directionToTarget.scale(Math.max(0.0D, nextDistance - GENNADIY_HOOK_TARGET_INSET_BLOCKS)));
		moveGennadiyHookTargetToCenter(target, nextTargetCenter, targetCenterYOffset);
		if (nextDistance <= GENNADIY_HOOK_FINISH_DISTANCE_BLOCKS + 1.0E-4D) {
			finishGennadiyHookPull(level, player, target, session);
			return false;
		}
		updateGennadiyHookSegments(server, level, session, origin, directionToTarget, nextDistance);
		return true;
	}

	private static double getGennadiyHookTargetCenterYOffset(LivingEntity target) {
		return target == null ? 0.25D : Math.max(0.25D, target.getBbHeight() * 0.55D);
	}

	private static double getGennadiyHookPullDistance(double distance) {
		return Math.min(GENNADIY_HOOK_PULL_SPEED_BLOCKS, Math.max(0.25D, distance * 0.28D));
	}

	private static void maybeRefreshGennadiyHookChainSound(ServerLevel level, GennadiyHookSession session, Vec3 origin) {
		if (level == null || session == null || origin == null || session.nextChainSoundTick < 0L) {
			return;
		}
		if (level.getGameTime() < session.nextChainSoundTick) {
			return;
		}
		stopGennadiyHookChainSound(level, origin);
		playGennadiyHookChainSound(level, origin);
		session.nextChainSoundTick = level.getGameTime() + GENNADIY_HOOK_CHAIN_SOUND_REPEAT_TICKS;
	}

	private static void moveGennadiyHookTargetToCenter(LivingEntity target, Vec3 targetCenter, double centerYOffset) {
		if (target == null || targetCenter == null) {
			return;
		}
		double x = targetCenter.x;
		double y = targetCenter.y - centerYOffset;
		double z = targetCenter.z;
		target.setDeltaMovement(Vec3.ZERO);
		if (target instanceof ServerPlayer targetPlayer) {
			targetPlayer.connection.teleport(x, y, z, targetPlayer.getYRot(), targetPlayer.getXRot());
		} else {
			target.teleportTo(x, y, z);
		}
		target.hurtMarked = true;
		target.resetFallDistance();
		if (target instanceof ServerPlayer targetPlayer) {
			targetPlayer.connection.send(new ClientboundSetEntityMotionPacket(targetPlayer));
		}
		sendGennadiyHookTargetTeleportToViewers(target);
	}

	private static void sendGennadiyHookTargetTeleportToViewers(LivingEntity target) {
		if (target == null || !(target.level() instanceof ServerLevel level)) {
			return;
		}
		PositionMoveRotation rotation = new PositionMoveRotation(
				target.position(),
				Vec3.ZERO,
				target.getYRot(),
				target.getXRot()
		);
		ClientboundTeleportEntityPacket teleportPacket = ClientboundTeleportEntityPacket.teleport(
				target.getId(),
				rotation,
				Collections.emptySet(),
				target.onGround()
		);
		ClientboundSetEntityMotionPacket motionPacket = new ClientboundSetEntityMotionPacket(target);
		for (ServerPlayer viewer : level.players()) {
			if (viewer == null || viewer.connection == null || viewer == target) {
				continue;
			}
			viewer.connection.send(teleportPacket);
			viewer.connection.send(motionPacket);
		}
	}

	private static void finishGennadiyHookPull(ServerLevel level, ServerPlayer player, LivingEntity target, GennadiyHookSession session) {
		if (level == null || player == null || target == null || session == null) {
			return;
		}
		if (session.damage > 0.0D) {
			target.hurtServer(level, gennadiyHookDamageSource(level, player), (float) session.damage);
		}
		target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, (int) Math.min(Integer.MAX_VALUE, session.slownessTicks), 2, false, true, true));
		Vec3 center = target.position().add(0.0D, Math.max(0.25D, target.getBbHeight() * 0.55D), 0.0D);
		level.sendParticles(
				ParticleTypes.CRIT,
				center.x,
				center.y,
				center.z,
				18,
				Math.max(0.15D, target.getBbWidth() * 0.35D),
				Math.max(0.18D, target.getBbHeight() * 0.25D),
				Math.max(0.15D, target.getBbWidth() * 0.35D),
				0.16D
		);
		stopGennadiyHookChainSound(level, getGennadiyHookOrigin(player));
		stopGennadiyHookChainSound(level, center);
		playGennadiyHookEndSound(level, center);
	}

	private static DamageSource gennadiyHookDamageSource(ServerLevel level, ServerPlayer player) {
		return new DamageSource(
				level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(DamageTypes.GENERIC),
				player,
				player
		);
	}

	private static void updateGennadiyHookSegments(MinecraftServer server, ServerLevel level, GennadiyHookSession session, Vec3 origin, Vec3 direction, double length) {
		if (server == null || level == null || session == null || origin == null || direction == null) {
			return;
		}
		if (direction.lengthSqr() <= 1.0E-6D || length <= 0.02D) {
			trimGennadiyHookSegments(server, session, 0);
			removeGennadiyHookTip(server, session);
			return;
		}
		Vec3 normalized = direction.normalize();
		double chainBodyLength = Math.max(0.0D, length - GENNADIY_HOOK_SEGMENT_SPACING_BLOCKS);
		int desiredCount = Math.max(0, Math.min(GENNADIY_HOOK_MAX_SEGMENTS, (int) Math.ceil(chainBodyLength / GENNADIY_HOOK_SEGMENT_SPACING_BLOCKS)));
		trimGennadiyHookSegments(server, session, desiredCount);
		while (session.segmentIds.size() < desiredCount) {
			session.segmentIds.add(null);
		}

		float yaw = yawFromDirection(normalized);
		float pitch = pitchFromDirection(normalized);
		double lastSegmentDistance = 0.0D;
		for (int i = 0; i < desiredCount; i++) {
			double distance = Math.min(chainBodyLength, (i + 0.5D) * GENNADIY_HOOK_SEGMENT_SPACING_BLOCKS);
			lastSegmentDistance = distance;
			Vec3 position = origin.add(normalized.scale(distance));
			Display.ItemDisplay display = resolveGennadiyHookSegment(server, level, session, i);
			if (display == null) {
				continue;
			}
			display.setPos(position.x, position.y, position.z);
			clearGennadiyHookChainHitbox(display);
			display.setYRot(yaw);
			display.setXRot(pitch);
			display.yRotO = yaw;
			display.xRotO = pitch;
			display.hurtMarked = true;
		}
		double tipAttachDistance = desiredCount > 0
				? lastSegmentDistance + GENNADIY_HOOK_CHAIN_END_OFFSET_BLOCKS
				: length;
		updateGennadiyHookTip(server, level, session, origin, normalized, tipAttachDistance, yaw, pitch);
	}

	private static Display.ItemDisplay resolveGennadiyHookSegment(MinecraftServer server, ServerLevel level, GennadiyHookSession session, int index) {
		UUID id = index < session.segmentIds.size() ? session.segmentIds.get(index) : null;
		Entity existing = findEntity(server, id);
		if (existing instanceof Display.ItemDisplay display && display.isAlive()) {
			configureGennadiyHookDisplay(display);
			clearGennadiyHookChainHitbox(display);
			return display;
		}
		Display.ItemDisplay display = new Display.ItemDisplay(EntityType.ITEM_DISPLAY, level);
		display.addTag(GENNADIY_HOOK_CHAIN_TAG);
		display.addTag(GENNADIY_DONKEY_OWNER_TAG_PREFIX + session.playerId);
		display.setNoGravity(true);
		display.noPhysics = true;
		display.setInvulnerable(true);
		display.setSilent(true);
		configureGennadiyHookDisplay(display);
		display.setItemStack(BattleDonkeyTurretItem.createHookChainDisplayStack());
		clearGennadiyHookChainHitbox(display);
		level.addFreshEntity(display);
		session.segmentIds.set(index, display.getUUID());
		return display;
	}

	private static void updateGennadiyHookTip(
			MinecraftServer server,
			ServerLevel level,
			GennadiyHookSession session,
			Vec3 origin,
			Vec3 direction,
			double tipAttachDistance,
			float chainYaw,
			float chainPitch
	) {
		if (server == null || level == null || session == null || origin == null || direction == null || tipAttachDistance <= 0.02D) {
			removeGennadiyHookTip(server, session);
			return;
		}
		Display.ItemDisplay display = resolveGennadiyHookTip(server, level, session);
		if (display == null) {
			return;
		}

		float yaw = chainYaw;
		float pitch = chainPitch;
		Vec3 redLinkAttachPosition = origin.add(direction.scale(Math.max(0.0D, tipAttachDistance - GENNADIY_HOOK_TIP_ATTACH_CORRECTION_BLOCKS)));
		Vec3 redLinkAttachOffset = rotateDisplayLocalOffset(GENNADIY_HOOK_TIP_RED_LINK_ATTACH_OFFSET, yaw, pitch);
		Vec3 position = redLinkAttachPosition.subtract(redLinkAttachOffset);
		display.setPos(position.x, position.y, position.z);
		clearGennadiyHookChainHitbox(display);

		display.setYRot(yaw);
		display.setXRot(pitch);
		display.yRotO = yaw;
		display.xRotO = pitch;
		display.hurtMarked = true;
	}

	private static Display.ItemDisplay resolveGennadiyHookTip(MinecraftServer server, ServerLevel level, GennadiyHookSession session) {
		Entity existing = findEntity(server, session.hookTipId);
		if (existing instanceof Display.ItemDisplay display && display.isAlive()) {
			configureGennadiyHookDisplay(display);
			clearGennadiyHookChainHitbox(display);
			return display;
		}
		Display.ItemDisplay display = new Display.ItemDisplay(EntityType.ITEM_DISPLAY, level);
		display.addTag(GENNADIY_HOOK_CHAIN_TAG);
		display.addTag(GENNADIY_DONKEY_OWNER_TAG_PREFIX + session.playerId);
		display.setNoGravity(true);
		display.noPhysics = true;
		display.setInvulnerable(true);
		display.setSilent(true);
		configureGennadiyHookDisplay(display);
		display.setItemStack(BattleDonkeyTurretItem.createHookClawDisplayStack());
		clearGennadiyHookChainHitbox(display);
		level.addFreshEntity(display);
		session.hookTipId = display.getUUID();
		return display;
	}

	private static void configureGennadiyHookDisplay(Display.ItemDisplay display) {
		if (display == null) {
			return;
		}
		display.setBillboardConstraints(Display.BillboardConstraints.FIXED);
		display.setItemTransform(ItemDisplayContext.FIXED);
		display.setViewRange(32.0F);
		display.setTransformationInterpolationDelay(0);
		display.setTransformationInterpolationDuration(0);
	}

	private static Vec3 rotateDisplayLocalOffset(Vec3 localOffset, float yawDegrees, float pitchDegrees) {
		if (localOffset == null || localOffset.lengthSqr() <= 1.0E-9D) {
			return Vec3.ZERO;
		}
		double yaw = Math.toRadians(yawDegrees);
		double pitch = Math.toRadians(pitchDegrees);
		double cosPitch = Math.cos(pitch);
		Vec3 forward = new Vec3(
				-Math.sin(yaw) * cosPitch,
				-Math.sin(pitch),
				Math.cos(yaw) * cosPitch
		);
		Vec3 right = new Vec3(Math.cos(yaw), 0.0D, Math.sin(yaw));
		Vec3 up = forward.cross(right);
		return right.scale(localOffset.x)
				.add(up.scale(localOffset.y))
				.add(forward.scale(localOffset.z));
	}

	private static void clearGennadiyHookChainHitbox(Display.ItemDisplay display) {
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

	private static void trimGennadiyHookSegments(MinecraftServer server, GennadiyHookSession session, int desiredCount) {
		if (session == null) {
			return;
		}
		for (int i = session.segmentIds.size() - 1; i >= desiredCount; i--) {
			UUID id = session.segmentIds.remove(i);
			Entity entity = findEntity(server, id);
			if (entity != null) {
				entity.discard();
			}
		}
	}

	private static void cleanupGennadiyHookSession(MinecraftServer server, GennadiyHookSession session) {
		if (session == null) {
			return;
		}
		stopGennadiyHookChainSound(server, session);
		trimGennadiyHookSegments(server, session, 0);
		removeGennadiyHookTip(server, session);
	}

	private static void removeGennadiyHookTip(MinecraftServer server, GennadiyHookSession session) {
		if (session == null || session.hookTipId == null) {
			return;
		}
		Entity entity = findEntity(server, session.hookTipId);
		if (entity != null) {
			entity.discard();
		}
		session.hookTipId = null;
	}

	private static void cleanupAllGennadiyHookChains(MinecraftServer server) {
		if (server == null) {
			return;
		}
		for (GennadiyHookSession session : new ArrayList<>(GENNADIY_HOOK_SESSIONS.values())) {
			cleanupGennadiyHookSession(server, session);
		}
		GENNADIY_HOOK_SESSIONS.clear();
		for (ServerLevel level : server.getAllLevels()) {
			List<Entity> entities = new ArrayList<>();
			for (Entity entity : level.getAllEntities()) {
				entities.add(entity);
			}
			for (Entity entity : entities) {
				if (entity != null && entity.getTags().contains(GENNADIY_HOOK_CHAIN_TAG)) {
					entity.discard();
				}
			}
		}
	}

	private static float yawFromDirection(Vec3 direction) {
		return (float) (Math.atan2(-direction.x, direction.z) * (180.0D / Math.PI));
	}

	private static float pitchFromDirection(Vec3 direction) {
		double horizontal = Math.sqrt(direction.x * direction.x + direction.z * direction.z);
		return (float) (-(Math.atan2(direction.y, horizontal) * (180.0D / Math.PI)));
	}

	private static void tickGennadiyDefense(MinecraftServer server) {
		if (server == null) {
			return;
		}
		long nowTick = server.overworld().getGameTime();
		tickGennadiyDefenseSessions(server, nowTick);
		tickGennadiyDefenseWaveVisuals(server, nowTick);
	}

	private static void tickGennadiyDefenseSessions(MinecraftServer server, long nowTick) {
		if (GENNADIY_DEFENSE_SESSIONS.isEmpty()) {
			return;
		}

		Iterator<Map.Entry<UUID, GennadiyDefenseSession>> iterator = GENNADIY_DEFENSE_SESSIONS.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<UUID, GennadiyDefenseSession> entry = iterator.next();
			GennadiyDefenseSession session = entry.getValue();
			ServerPlayer player = session == null ? null : server.getPlayerList().getPlayer(session.playerId);
			if (player == null
					|| !player.isAlive()
					|| player.isSpectator()
					|| !(player.level() instanceof ServerLevel level)
					|| !level.dimension().equals(session.dimension)) {
				iterator.remove();
				cleanupGennadiyDefenseSeat(server, session, player);
				if (player != null) {
					cleanupGennadiyDefenseEffects(player);
				}
				continue;
			}

			if (nowTick >= session.endTick) {
				iterator.remove();
				cleanupGennadiyDefenseSeat(server, session, player);
				cleanupGennadiyDefenseEffects(player);
				releaseGennadiyDefenseWave(level, player, session, nowTick);
				continue;
			}

			applyGennadiyDefenseStasis(player, session.endTick - nowTick);
			emitGennadiyDefenseSmokeAura(level, player, nowTick);
			maybePlayGennadiyDefenseChargeSound(player, session, nowTick);
		}
	}

	private static void applyGennadiyDefenseStasis(ServerPlayer player, long remainingTicks) {
		if (player == null) {
			return;
		}
		int effectDuration = (int) Math.min(Integer.MAX_VALUE, Math.max(20L, remainingTicks + 20L));
		GENNADIY_DEFENSE_APPLYING_EFFECT.set(Boolean.TRUE);
		try {
			player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, effectDuration, 0, false, false, false));
			player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, effectDuration, 2, false, false, false));
		} finally {
			GENNADIY_DEFENSE_APPLYING_EFFECT.set(Boolean.FALSE);
		}
		player.setSprinting(false);
		ensureGennadiyDefenseFsitSeat(player, GENNADIY_DEFENSE_SESSIONS.get(player.getUUID()));
		player.setDeltaMovement(Vec3.ZERO);
		player.setShiftKeyDown(false);
		player.setPose(Pose.STANDING);
		syncGennadiyDefenseHeadLock(player);
	}

	private static boolean isFsitSeatEntity(Entity entity) {
		return entity != null && FSIT_SEAT_ENTITY_CLASS_NAME.equals(entity.getClass().getName());
	}

	private static void ensureGennadiyDefenseFsitSeat(ServerPlayer player, GennadiyDefenseSession session) {
		if (player == null || session == null || !(player.level() instanceof ServerLevel level)) {
			return;
		}

		Entity vehicle = player.getVehicle();
		if (isFsitSeatEntity(vehicle)) {
			session.fsitSeatId = vehicle.getUUID();
			GENNADIY_DEFENSE_FSIT_SEATS.put(player.getUUID(), vehicle.getUUID());
			return;
		}

		if (session.fsitSeatId != null) {
			Entity existing = findEntity(level.getServer(), session.fsitSeatId);
			if (existing != null && isFsitSeatEntity(existing)) {
				existing.discard();
			}
			session.fsitSeatId = null;
		}

		if (player.getVehicle() != null) {
			return;
		}

		if (!createGennadiyDefenseFsitSeat(player)) {
			return;
		}

		Entity seat = player.getVehicle();
		if (isFsitSeatEntity(seat)) {
			session.fsitSeatId = seat.getUUID();
			GENNADIY_DEFENSE_FSIT_SEATS.put(player.getUUID(), seat.getUUID());
		}
	}

	private static boolean createGennadiyDefenseFsitSeat(ServerPlayer player) {
		if (player == null || Boolean.FALSE.equals(FSIT_SEAT_AVAILABLE)) {
			return false;
		}
		try {
			java.lang.reflect.Method createMethod = resolveFsitSeatCreateMethod();
			Object companion = FSIT_SEAT_COMPANION;
			if (createMethod == null || companion == null) {
				return false;
			}
			createMethod.invoke(companion, player, player.position());
			return true;
		} catch (ReflectiveOperationException | LinkageError exception) {
			FSIT_SEAT_AVAILABLE = Boolean.FALSE;
			Lg2.LOGGER.warn("Failed to create FSit seat for Gennadiy defense", exception);
			return false;
		}
	}

	private static java.lang.reflect.Method resolveFsitSeatCreateMethod() throws ReflectiveOperationException {
		if (FSIT_SEAT_CREATE_METHOD != null && FSIT_SEAT_COMPANION != null) {
			return FSIT_SEAT_CREATE_METHOD;
		}
		Class<?> seatClass = Class.forName(FSIT_SEAT_ENTITY_CLASS_NAME);
		Class<?> companionClass = Class.forName(FSIT_SEAT_COMPANION_CLASS_NAME);
		Object companion = seatClass.getField("Companion").get(null);
		java.lang.reflect.Method createMethod = companionClass.getMethod("create", ServerPlayer.class, Vec3.class);
		FSIT_SEAT_COMPANION = companion;
		FSIT_SEAT_CREATE_METHOD = createMethod;
		FSIT_SEAT_AVAILABLE = Boolean.TRUE;
		return createMethod;
	}

	private static void syncGennadiyDefenseHeadLock(ServerPlayer player) {
		if (player == null || player.connection == null || !isGennadiyDefenseActive(player)) {
			return;
		}
		GennadiyDefenseSession session = GENNADIY_DEFENSE_SESSIONS.get(player.getUUID());
		if (session == null) {
			return;
		}

		player.setSprinting(false);
		ensureGennadiyDefenseFsitSeat(player, session);
		player.setDeltaMovement(Vec3.ZERO);
		player.setShiftKeyDown(false);
		player.setPose(Pose.STANDING);
		float lockedYaw = session.lockedYaw;
		player.setYRot(lockedYaw);
		player.setXRot(GENNADIY_DEFENSE_FORCED_PITCH);
		player.yRotO = lockedYaw;
		player.xRotO = GENNADIY_DEFENSE_FORCED_PITCH;
		player.setYHeadRot(lockedYaw);
		player.setYBodyRot(lockedYaw);
		player.yHeadRotO = lockedYaw;
		player.yBodyRotO = lockedYaw;
		player.hurtMarked = true;
		sendGennadiyDefensePoseMetadata(player, true);

		player.connection.send(new ClientboundPlayerRotationPacket(lockedYaw, false, GENNADIY_DEFENSE_FORCED_PITCH, false));
		sendGennadiyDefenseRotationToViewers(player);
	}

	private static void sendGennadiyDefensePoseMetadata(ServerPlayer player, boolean forced) {
		if (player == null || !(player.level() instanceof ServerLevel level)) {
			return;
		}

		EntityDataAccessor<Byte> sharedFlagsAccessor = EntityTrackedDataAccessor.lg2$getDataSharedFlagsId();
		EntityDataAccessor<Pose> poseAccessor = EntityTrackedDataAccessor.lg2$getDataPose();
		Byte currentFlags = player.getEntityData().get(sharedFlagsAccessor);
		byte flags = currentFlags == null ? 0 : currentFlags;
		Pose pose = player.getPose();
		if (forced) {
			flags &= (byte) ~ENTITY_FLAG_SHIFTING;
			flags &= (byte) ~ENTITY_FLAG_SPRINTING;
			pose = Pose.STANDING;
		} else {
			flags &= (byte) ~ENTITY_FLAG_SHIFTING;
			pose = Pose.STANDING;
		}

		ClientboundSetEntityDataPacket packet = new ClientboundSetEntityDataPacket(
				player.getId(),
				List.of(
						SynchedEntityData.DataValue.create(sharedFlagsAccessor, flags),
						SynchedEntityData.DataValue.create(poseAccessor, pose)
				)
		);
		for (ServerPlayer viewer : level.players()) {
			if (viewer != null && viewer.connection != null) {
				viewer.connection.send(packet);
			}
		}
	}

	private static void sendGennadiyDefenseRotationToViewers(ServerPlayer player) {
		if (player == null || !(player.level() instanceof ServerLevel level)) {
			return;
		}
		PositionMoveRotation rotation = new PositionMoveRotation(
				player.position(),
				player.getDeltaMovement(),
				player.getYRot(),
				GENNADIY_DEFENSE_FORCED_PITCH
		);
		ClientboundTeleportEntityPacket packet = ClientboundTeleportEntityPacket.teleport(player.getId(), rotation, Collections.emptySet(), player.onGround());
		byte yaw = toGennadiyDefenseRotationByte(player.getYRot());
		byte pitch = toGennadiyDefenseRotationByte(GENNADIY_DEFENSE_FORCED_PITCH);
		ClientboundMoveEntityPacket.Rot rotationPacket = new ClientboundMoveEntityPacket.Rot(player.getId(), yaw, pitch, player.onGround());
		ClientboundRotateHeadPacket headPacket = new ClientboundRotateHeadPacket(player, yaw);
		for (ServerPlayer viewer : level.players()) {
			if (viewer != null && viewer != player && viewer.connection != null) {
				viewer.connection.send(packet);
				viewer.connection.send(rotationPacket);
				viewer.connection.send(headPacket);
			}
		}
	}

	private static byte toGennadiyDefenseRotationByte(float degrees) {
		return (byte) net.minecraft.util.Mth.floor((degrees * 256.0F) / 360.0F);
	}

	private static void cleanupGennadiyDefenseSeat(MinecraftServer server, GennadiyDefenseSession session, ServerPlayer player) {
		UUID seatId = null;
		if (session != null) {
			seatId = session.fsitSeatId;
			UUID mappedSeatId = GENNADIY_DEFENSE_FSIT_SEATS.remove(session.playerId);
			if (seatId == null) {
				seatId = mappedSeatId;
			}
		}
		if (player != null) {
			UUID mappedSeatId = GENNADIY_DEFENSE_FSIT_SEATS.remove(player.getUUID());
			if (seatId == null) {
				seatId = mappedSeatId;
			}
		}
		if (seatId == null) {
			return;
		}

		Entity vehicle = player == null ? null : player.getVehicle();
		if (vehicle != null && seatId.equals(vehicle.getUUID())) {
			player.stopRiding();
			vehicle.discard();
			return;
		}

		Entity seat = findEntity(server, seatId);
		if (seat != null && isFsitSeatEntity(seat)) {
			seat.discard();
		}
	}

	private static void cleanupGennadiyDefenseEffects(ServerPlayer player) {
		if (player == null) {
			return;
		}
		removeGennadiyDefenseManagedEffect(player, MobEffects.BLINDNESS, 0);
		removeGennadiyDefenseManagedEffect(player, MobEffects.SLOWNESS, 2);
		player.setShiftKeyDown(false);
		player.setPose(Pose.STANDING);
		sendGennadiyDefensePoseMetadata(player, false);
	}

	private static void removeGennadiyDefenseManagedEffect(ServerPlayer player, Holder<MobEffect> effect, int amplifier) {
		if (player == null || effect == null) {
			return;
		}
		MobEffectInstance current = player.getEffect(effect);
		if (current != null && current.getAmplifier() == amplifier && current.getDuration() <= 60) {
			player.removeEffect(effect);
		}
	}

	private static void releaseGennadiyDefenseWave(ServerLevel level, ServerPlayer player, GennadiyDefenseSession session, long nowTick) {
		if (level == null || player == null || session == null) {
			return;
		}
		Vec3 origin = player.position().add(0.0D, 0.18D, 0.0D);
		double maxRange = Math.max(0.0D, session.waveRange);
		double maxKnockbackBlocks = Math.max(0.0D, session.maxKnockbackBlocks);
		double knockbackBlocks = Math.min(maxKnockbackBlocks, Math.max(0.0D, session.accumulatedDamage) * Math.max(0.0D, session.knockbackBlocksPerDamage));
		double chargeRatio = maxKnockbackBlocks <= 1.0E-6D ? 0.0D : Math.max(0.0D, Math.min(1.0D, knockbackBlocks / maxKnockbackBlocks));
		double range = maxRange * chargeRatio;
		if (range <= 0.05D || knockbackBlocks <= 0.05D) {
			return;
		}

		double rangeSqr = range * range;
		for (LivingEntity target : level.getEntitiesOfClass(
				LivingEntity.class,
				player.getBoundingBox().inflate(range),
				target -> canGennadiyDefenseWaveHit(player, target)
		)) {
			double distanceSqr = target.distanceToSqr(player);
			if (distanceSqr > rangeSqr) {
				continue;
			}

			Vec3 horizontal = new Vec3(target.getX() - player.getX(), 0.0D, target.getZ() - player.getZ());
			if (horizontal.lengthSqr() <= 1.0E-6D) {
				horizontal = Vec3.directionFromRotation(0.0F, target.getYRot());
			}
			if (horizontal.lengthSqr() <= 1.0E-6D) {
				horizontal = new Vec3(1.0D, 0.0D, 0.0D);
			}

			double distance = Math.sqrt(distanceSqr);
			double falloff = 1.0D - Math.min(1.0D, distance / range) * 0.65D;
			double effectiveKnockbackBlocks = knockbackBlocks * Math.max(0.25D, falloff);
			if (effectiveKnockbackBlocks > 0.05D) {
				Vec3 direction = horizontal.normalize();
				double velocity = effectiveKnockbackBlocks * 0.115D;
				double vertical = Math.min(0.68D, 0.16D + velocity * 0.11D);
				target.push(direction.x * velocity, vertical, direction.z * velocity);
				target.hurtMarked = true;
				if (target instanceof ServerPlayer targetPlayer) {
					targetPlayer.connection.send(new ClientboundSetEntityMotionPacket(targetPlayer));
					float damage = (float) randomBetween(level, session.minDamage, session.maxDamage);
					if (damage > 0.0F) {
						targetPlayer.hurtServer(level, level.damageSources().magic(), damage);
					}
				}
			}
		}

		GENNADIY_DEFENSE_WAVE_VISUALS.add(new GennadiyDefenseWaveVisual(level.dimension(), origin, range, nowTick, GENNADIY_DEFENSE_WAVE_VISUAL_DURATION_TICKS));
		emitGennadiyDefenseBurst(level, origin);
		playGennadiyDefenseExplosionSound(level, origin);
	}

	private static boolean canGennadiyDefenseWaveHit(ServerPlayer player, LivingEntity target) {
		if (player == null || target == null || target == player || !target.isAlive() || target.isSpectator()) {
			return false;
		}
		return !(target instanceof ArmorStand);
	}

	private static double randomBetween(ServerLevel level, double min, double max) {
		if (level == null || max <= min) {
			return min;
		}
		return min + level.getRandom().nextDouble() * (max - min);
	}

	private static void tickGennadiyDefenseWaveVisuals(MinecraftServer server, long nowTick) {
		if (server == null || GENNADIY_DEFENSE_WAVE_VISUALS.isEmpty()) {
			return;
		}
		Iterator<GennadiyDefenseWaveVisual> iterator = GENNADIY_DEFENSE_WAVE_VISUALS.iterator();
		while (iterator.hasNext()) {
			GennadiyDefenseWaveVisual wave = iterator.next();
			ServerLevel level = wave.dimension == null ? null : server.getLevel(wave.dimension);
			long age = nowTick - wave.startTick;
			if (level == null || age > wave.durationTicks) {
				iterator.remove();
				continue;
			}
			double progress = Math.max(0.0D, Math.min(1.0D, age / (double) wave.durationTicks));
			emitGennadiyDefenseWaveRing(level, wave.origin, wave.range, progress);
		}
	}

	private static void emitGennadiyDefenseSmokeAura(ServerLevel level, ServerPlayer player, long nowTick) {
		if (level == null || player == null || Math.floorMod(nowTick, 2L) != 0L) {
			return;
		}
		double radius = Math.max(0.42D, player.getBbWidth() * 0.85D);
		double y = player.getY() + 0.25D + (Math.floorMod(nowTick, 8L) * 0.045D);
		for (int i = 0; i < 3; i++) {
			double angle = nowTick * 0.18D + i * (Math.PI * 2.0D / 3.0D);
			double x = player.getX() + Math.cos(angle) * radius;
			double z = player.getZ() + Math.sin(angle) * radius;
			level.sendParticles(ParticleTypes.SMOKE, x, y, z, 0, 0.0D, 0.018D, 0.0D, 1.0D);
		}
	}

	private static void emitGennadiyDefenseBurst(ServerLevel level, Vec3 origin) {
		if (level == null || origin == null) {
			return;
		}
		level.sendParticles(ParticleTypes.EXPLOSION, origin.x, origin.y + 0.55D, origin.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
		level.sendParticles(ParticleTypes.SMOKE, origin.x, origin.y + 0.45D, origin.z, 24, 0.45D, 0.35D, 0.45D, 0.045D);
		level.sendParticles(ParticleTypes.CLOUD, origin.x, origin.y + 0.2D, origin.z, 18, 0.35D, 0.08D, 0.35D, 0.08D);
	}

	private static void emitGennadiyDefenseWaveRing(ServerLevel level, Vec3 origin, double range, double progress) {
		if (level == null || origin == null || range <= 0.0D) {
			return;
		}
		double eased = 1.0D - Math.pow(1.0D - progress, 2.0D);
		double radius = Math.max(0.25D, range * eased);
		int points = Math.max(18, Math.min(120, (int) Math.round(radius * 4.0D)));
		double y = origin.y + 0.12D + progress * 0.22D;
		for (int i = 0; i < points; i++) {
			double angle = (Math.PI * 2.0D * i) / points;
			double x = origin.x + Math.cos(angle) * radius;
			double z = origin.z + Math.sin(angle) * radius;
			level.sendParticles(ParticleTypes.CLOUD, x, y, z, 0, Math.cos(angle) * 0.025D, 0.012D, Math.sin(angle) * 0.025D, 1.0D);
			if (i % 3 == 0) {
				level.sendParticles(ParticleTypes.SMOKE, x, y + 0.08D, z, 0, Math.cos(angle) * 0.018D, 0.01D, Math.sin(angle) * 0.018D, 1.0D);
			}
		}
	}

	private static void tickGennadiyDonkeyMovement(ServerLevel level, ServerPlayer owner, Donkey donkey, GennadiyDonkeyState state, long nowTick) {
		if (level == null || owner == null || donkey == null || state == null || donkey.hasPassenger(owner)) {
			return;
		}
		LivingEntity target = state.ammo < 2 ? null : resolveGennadiyDonkeyTarget(level.getServer(), owner, donkey, state);
		if (state.ammo < 2) {
			clearGennadiyDonkeyTarget(state);
		}
		Vec3 ownerPos = owner.position();
		Vec3 donkeyPos = donkey.position();
		double distanceSqr = horizontalDistanceToSqr(donkeyPos, ownerPos);
		double maxDistance = Math.max(GENNADIY_DONKEY_OWNER_MIN_DISTANCE_BLOCKS + 0.5D, state.followMaxDistance);
		double maxDistanceSqr = maxDistance * maxDistance;
		Vec3 movementTarget;
		double speed = GENNADIY_DONKEY_WALK_SPEED;
		if (distanceSqr > maxDistanceSqr) {
			if (state.outsideSinceTick == null) {
				state.outsideSinceTick = nowTick;
			}
			movementTarget = projectGennadiyDonkeyToOwnerRing(level, ownerPos, donkeyPos, Math.max(GENNADIY_DONKEY_OWNER_MIN_DISTANCE_BLOCKS, maxDistance - 0.5D));
			speed = GENNADIY_DONKEY_RETURN_SPEED;
			if (nowTick - state.outsideSinceTick >= GENNADIY_DONKEY_MAX_OUTSIDE_TICKS) {
				Vec3 returnPos = findGennadiyDonkeySpawnPos(level, owner);
				donkey.teleportTo(returnPos.x, returnPos.y, returnPos.z);
				donkey.getNavigation().stop();
				state.outsideSinceTick = null;
				state.wanderTarget = returnPos;
				state.nextWanderRetargetTick = nowTick + 20L;
			}
		} else if (target != null) {
			state.outsideSinceTick = null;
			state.wanderTarget = projectGennadiyDonkeyCombatPosition(level, owner, donkey, target, maxDistance);
			state.nextWanderRetargetTick = nowTick + 10L;
			movementTarget = state.wanderTarget == null ? findGennadiyDonkeySpawnPos(level, owner) : state.wanderTarget;
			speed = GENNADIY_DONKEY_RETURN_SPEED;
		} else {
			state.outsideSinceTick = null;
			if (nowTick >= state.nextMovementLogicTick) {
				state.nextMovementLogicTick = nowTick + GENNADIY_DONKEY_MOVEMENT_LOGIC_INTERVAL_TICKS;
				if (state.wanderTarget == null
						|| nowTick >= state.nextWanderRetargetTick
						|| donkey.position().distanceToSqr(state.wanderTarget) <= 1.0D
						|| horizontalDistanceToSqr(owner.position(), state.wanderTarget) > maxDistanceSqr) {
					state.wanderTarget = sampleGennadiyDonkeyWanderTarget(level, owner, donkey.position(), maxDistance);
					state.nextWanderRetargetTick = nowTick + 30L;
				}
			}
			movementTarget = state.wanderTarget == null ? findGennadiyDonkeySpawnPos(level, owner) : state.wanderTarget;
		}
		moveGennadiyDonkeyTowardTarget(donkey, movementTarget, speed, target);
	}

	private static Vec3 projectGennadiyDonkeyCombatPosition(ServerLevel level, ServerPlayer owner, Donkey donkey, LivingEntity target, double outerRadius) {
		if (level == null || owner == null || target == null) {
			return donkey == null ? null : donkey.position();
		}
		Vec3 ownerPos = owner.position();
		Vec3 awayFromTarget = new Vec3(ownerPos.x - target.getX(), 0.0D, ownerPos.z - target.getZ());
		if (awayFromTarget.lengthSqr() < 1.0E-4D && donkey != null) {
			awayFromTarget = new Vec3(donkey.getX() - target.getX(), 0.0D, donkey.getZ() - target.getZ());
		}
		if (awayFromTarget.lengthSqr() < 1.0E-4D) {
			return donkey == null ? ownerPos : donkey.position();
		}
		double radius = Math.max(GENNADIY_DONKEY_OWNER_MIN_DISTANCE_BLOCKS, outerRadius - 0.35D);
		Vec3 desired = ownerPos.add(awayFromTarget.normalize().scale(radius));
		Vec3 resolved = resolveGennadiyDonkeySpawnPosition(level, desired);
		return resolved != null ? resolved : desired;
	}

	private static Vec3 sampleGennadiyDonkeyWanderTarget(ServerLevel level, ServerPlayer owner, Vec3 donkeyPosition, double outerRadius) {
		double minDistance = GENNADIY_DONKEY_OWNER_MIN_DISTANCE_BLOCKS;
		double maxDistance = Math.max(minDistance + 0.5D, outerRadius - 0.35D);
		for (int attempt = 0; attempt < 10; attempt++) {
			double angle = owner.getRandom().nextDouble() * Math.PI * 2.0D;
			double distance = minDistance + owner.getRandom().nextDouble() * Math.max(0.001D, maxDistance - minDistance);
			Vec3 desired = new Vec3(
					owner.getX() + Math.cos(angle) * distance,
					owner.getY(),
					owner.getZ() + Math.sin(angle) * distance
			);
			Vec3 resolved = resolveGennadiyDonkeySpawnPosition(level, desired);
			if (resolved != null && (donkeyPosition == null || !segmentIntersectsInnerRadius(owner.position(), donkeyPosition, resolved, minDistance - 0.2D))) {
				return resolved;
			}
		}
		return findGennadiyDonkeySpawnPos(level, owner);
	}

	private static Vec3 projectGennadiyDonkeyToOwnerRing(ServerLevel level, Vec3 center, Vec3 currentPosition, double targetDistance) {
		Vec3 horizontal = new Vec3(currentPosition.x - center.x, 0.0D, currentPosition.z - center.z);
		if (horizontal.lengthSqr() < 1.0E-4D) {
			horizontal = new Vec3(1.0D, 0.0D, 0.0D);
		}
		Vec3 desired = center.add(horizontal.normalize().scale(Math.max(0.35D, targetDistance)));
		Vec3 resolved = resolveGennadiyDonkeySpawnPosition(level, desired);
		return resolved != null ? resolved : desired;
	}

	private static void moveGennadiyDonkeyTowardTarget(Donkey donkey, Vec3 target, double speed, LivingEntity lookTarget) {
		if (donkey == null || target == null) {
			return;
		}
		donkey.getNavigation().stop();
		Vec3 position = donkey.position();
		Vec3 horizontal = new Vec3(target.x - position.x, 0.0D, target.z - position.z);
		double distance = horizontal.length();
		if (distance <= 0.08D) {
			Vec3 delta = donkey.getDeltaMovement();
			donkey.setDeltaMovement(delta.x * 0.5D, delta.y, delta.z * 0.5D);
			updateGennadiyDonkeyLook(donkey, lookTarget, null);
			return;
		}
		Vec3 desiredMovement = horizontal.scale(Math.min(speed, distance) / distance);
		Vec3 delta = donkey.getDeltaMovement();
		Vec3 currentHorizontal = new Vec3(delta.x, 0.0D, delta.z);
		Vec3 movement = currentHorizontal.scale(1.0D - GENNADIY_DONKEY_STEERING_SMOOTHING).add(desiredMovement.scale(GENNADIY_DONKEY_STEERING_SMOOTHING));
		if (distance <= 0.55D) {
			movement = movement.scale(0.65D);
		}
		donkey.setDeltaMovement(movement.x, delta.y, movement.z);
		if (donkey.horizontalCollision && donkey.onGround()) {
			donkey.jumpFromGround();
		}
		updateGennadiyDonkeyLook(donkey, lookTarget, movement);
		donkey.hurtMarked = true;
	}

	private static void updateGennadiyDonkeyLook(Donkey donkey, LivingEntity lookTarget, Vec3 movement) {
		if (donkey == null) {
			return;
		}
		float yaw;
		if (lookTarget != null) {
			Vec3 toTarget = lookTarget.position().subtract(donkey.position());
			yaw = (float) (Math.toDegrees(Math.atan2(toTarget.z, toTarget.x)) - 90.0D);
		} else if (movement != null && movement.horizontalDistanceSqr() > 1.0E-6D) {
			yaw = (float) (Math.toDegrees(Math.atan2(movement.z, movement.x)) - 90.0D);
		} else {
			yaw = donkey.getYRot();
		}
		donkey.setYRot(yaw);
		donkey.yRotO = yaw;
		donkey.setYBodyRot(yaw);
		donkey.yBodyRotO = yaw;
		donkey.setYHeadRot(yaw);
		donkey.yHeadRotO = yaw;
	}

	private static InteractionResult handleGennadiyDonkeyEntityUse(ServerPlayer player, InteractionHand hand, Entity entity, Vec3 location) {
		if (player == null || entity == null) {
			return InteractionResult.PASS;
		}
		if (isGennadiyDonkeyManualTrigger(player, entity)) {
			return tryUseGennadiyDonkeyManualShoot(player, hand);
		}
		if (isGennadiyDonkeyVisual(player, entity)) {
			GennadiyDonkeyState state = GENNADIY_DONKEY_STATES.get(player.getUUID());
			Donkey donkey = getActiveGennadiyDonkey(player.level().getServer(), state);
			if (donkey != null) {
				forceGennadiyDonkeyRider(player, donkey, state);
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.SUCCESS;
		}
		UUID ownerId = GENNADIY_DONKEY_OWNER_BY_ENTITY.get(entity.getUUID());
		if (ownerId == null) {
			return InteractionResult.PASS;
		}
		if (!ownerId.equals(player.getUUID())) {
			return InteractionResult.SUCCESS;
		}
		if (entity instanceof Donkey donkey) {
			GennadiyDonkeyState state = GENNADIY_DONKEY_STATES.get(ownerId);
			forceGennadiyDonkeyRider(player, donkey, state);
			return InteractionResult.SUCCESS;
		}
		if (canUseGennadiyDonkeyManualShoot(player, hand)) {
			InteractionResult result = location != null ? entity.interactAt(player, location, hand) : InteractionResult.PASS;
			if (result == InteractionResult.PASS) {
				result = player.interactOn(entity, hand);
			}
			if (result != InteractionResult.PASS) {
				return result;
			}
			return tryUseGennadiyDonkeyManualShoot(player, hand);
		}
		return InteractionResult.PASS;
	}

	private static Donkey getActiveGennadiyDonkey(MinecraftServer server, GennadiyDonkeyState state) {
		if (server == null || state == null || state.dimension == null || state.donkeyId == null) {
			return null;
		}
		ServerLevel level = server.getLevel(state.dimension);
		Entity entity = level == null ? null : level.getEntity(state.donkeyId);
		return entity instanceof Donkey donkey && donkey.isAlive() ? donkey : null;
	}

	private static boolean isGennadiyDonkeyVisual(ServerPlayer player, Entity entity) {
		if (player == null || entity == null) {
			return false;
		}
		GennadiyDonkeyState state = GENNADIY_DONKEY_STATES.get(player.getUUID());
		return state != null && (
				(state.visualId != null && state.visualId.equals(entity.getUUID()))
						|| (state.helmetVisualId != null && state.helmetVisualId.equals(entity.getUUID()))
		);
	}

	private static void forceGennadiyDonkeyRider(ServerPlayer player, Donkey donkey, GennadiyDonkeyState state) {
		if (player == null || donkey == null || state == null || !state.isActive()) {
			return;
		}
		ServerLevel level = donkey.level() instanceof ServerLevel serverLevel ? serverLevel : null;
		ArmorStand visual = level == null ? null : findGennadiyDonkeyVisual(level, state);
		ArmorStand helmetVisual = level == null ? null : findGennadiyDonkeyHelmetVisual(level, state);
		if (visual != null && visual.getVehicle() == donkey) {
			visual.stopRiding();
		}
		if (helmetVisual != null && helmetVisual.getVehicle() == donkey) {
			helmetVisual.stopRiding();
		}
		if (!(player.getVehicle() == donkey && donkey.hasPassenger(player))) {
			forceEntityPassenger(donkey, player);
		}
		if (visual != null) {
			attachGennadiyDonkeyVisual(donkey, visual);
		}
		if (helmetVisual != null) {
			attachGennadiyDonkeyVisual(donkey, helmetVisual);
		}
	}

	private static InteractionResult tryUseGennadiyDonkeyManualShoot(ServerPlayer player, InteractionHand hand) {
		if (!canUseGennadiyDonkeyManualShoot(player, hand)) {
			return InteractionResult.PASS;
		}
		GennadiyDonkeyState state = GENNADIY_DONKEY_STATES.get(player.getUUID());
		ServerLevel level = (ServerLevel) player.level();
		Entity entity = level.getEntity(state.donkeyId);
		if (!(entity instanceof Donkey donkey)) {
			return InteractionResult.PASS;
		}
		long nowTick = level.getGameTime();
		state.lastManualInputTick = nowTick;
		if (nowTick >= state.nextShotTick) {
			fireGennadiyDonkeyTwinShot(level, player, donkey, state, player.getLookAngle().normalize());
		}
		return InteractionResult.SUCCESS;
	}

	public static boolean handleGennadiyDonkeyUseInteraction(ServerPlayer player, InteractionHand hand) {
		return tryUseGennadiyDonkeyManualShoot(player, hand) != InteractionResult.PASS;
	}

	private static boolean canUseGennadiyDonkeyManualShoot(ServerPlayer player, InteractionHand hand) {
		if (player == null
				|| hand != InteractionHand.MAIN_HAND
				|| !player.getMainHandItem().isEmpty()
				|| !(player.level() instanceof ServerLevel level)) {
			return false;
		}
		GennadiyDonkeyState state = GENNADIY_DONKEY_STATES.get(player.getUUID());
		if (state == null || !state.isActive() || state.ammo <= 0 || state.donkeyId == null) {
			return false;
		}
		Entity entity = level.getEntity(state.donkeyId);
		return entity instanceof Donkey donkey && donkey.hasPassenger(player);
	}

	private static void syncGennadiyDonkeyManualTrigger(ServerPlayer player) {
		if (player == null || !(player.level() instanceof ServerLevel level)) {
			return;
		}
		GennadiyDonkeyState state = GENNADIY_DONKEY_STATES.get(player.getUUID());
		if (!shouldMaintainGennadiyDonkeyManualTrigger(player, state)) {
			removeGennadiyDonkeyManualTrigger(player.level().getServer(), state);
			return;
		}
		Entity vehicle = player.getVehicle();
		if (!(vehicle instanceof Donkey donkey) || state == null) {
			removeGennadiyDonkeyManualTrigger(player.level().getServer(), state);
			return;
		}
		if (hasGennadiyDonkeyManualTriggerObstruction(player, state)) {
			removeGennadiyDonkeyManualTrigger(player.level().getServer(), state);
			return;
		}
		Interaction trigger = null;
		if (state.manualTriggerId != null) {
			Entity existing = level.getEntity(state.manualTriggerId);
			if (existing instanceof Interaction existingTrigger && existingTrigger.isAlive()) {
				trigger = existingTrigger;
			}
		}
		if (trigger == null) {
			trigger = new Interaction(EntityType.INTERACTION, level);
			trigger.addTag(GENNADIY_DONKEY_TRIGGER_TAG);
			trigger.addTag(GENNADIY_DONKEY_OWNER_TAG_PREFIX + player.getUUID());
			trigger.setNoGravity(true);
			trigger.setSilent(true);
			trigger.setInvisible(true);
			trigger.setResponse(false);
			trigger.setWidth(GENNADIY_DONKEY_AIR_TRIGGER_WIDTH);
			trigger.setHeight(GENNADIY_DONKEY_AIR_TRIGGER_HEIGHT);
			level.addFreshEntity(trigger);
			state.manualTriggerId = trigger.getUUID();
		}
		Vec3 pos = player.getEyePosition()
				.add(player.getLookAngle().normalize().scale(GENNADIY_DONKEY_AIR_TRIGGER_HEAD_FORWARD_OFFSET))
				.subtract(0.0D, GENNADIY_DONKEY_AIR_TRIGGER_HEIGHT * 0.5D, 0.0D);
		trigger.setInvisible(true);
		trigger.setPos(pos.x, pos.y, pos.z);
		trigger.setDeltaMovement(Vec3.ZERO);
		trigger.setYRot(player.getYRot());
		trigger.setXRot(player.getXRot());
		player.connection.send(ClientboundEntityPositionSyncPacket.of(trigger));
	}

	private static boolean shouldMaintainGennadiyDonkeyManualTrigger(ServerPlayer player, GennadiyDonkeyState state) {
		if (player == null || state == null || !state.isActive() || !player.isAlive() || player.isSpectator()) {
			return false;
		}
		if (!player.getMainHandItem().isEmpty()) {
			return false;
		}
		Entity vehicle = player.getVehicle();
		return vehicle instanceof Donkey donkey && state.donkeyId != null && state.donkeyId.equals(donkey.getUUID()) && state.ammo > 0;
	}

	private static boolean hasGennadiyDonkeyManualTriggerObstruction(ServerPlayer player, GennadiyDonkeyState state) {
		if (player == null || state == null) {
			return true;
		}
		double reach = Math.max(player.blockInteractionRange(), player.entityInteractionRange());
		HitResult hit = player.pick(reach, 1.0F, false);
		if (hit instanceof EntityHitResult entityHit) {
			Entity hitEntity = entityHit.getEntity();
			if ((state.manualTriggerId != null && state.manualTriggerId.equals(hitEntity.getUUID()))
					|| (state.visualId != null && state.visualId.equals(hitEntity.getUUID()))
					|| (state.helmetVisualId != null && state.helmetVisualId.equals(hitEntity.getUUID()))) {
				return false;
			}
		}
		return hit.getType() != HitResult.Type.MISS;
	}

	private static boolean isGennadiyDonkeyManualTrigger(ServerPlayer player, Entity entity) {
		if (player == null || entity == null) {
			return false;
		}
		GennadiyDonkeyState state = GENNADIY_DONKEY_STATES.get(player.getUUID());
		return state != null && state.manualTriggerId != null && state.manualTriggerId.equals(entity.getUUID());
	}

	private static void removeGennadiyDonkeyManualTrigger(MinecraftServer server, GennadiyDonkeyState state) {
		if (server == null || state == null || state.manualTriggerId == null) {
			return;
		}
		Entity trigger = findEntity(server, state.manualTriggerId);
		if (trigger != null) {
			trigger.discard();
		}
		state.manualTriggerId = null;
	}

	private static void ensureGennadiyDonkeyVisual(ServerLevel level, Donkey donkey, GennadiyDonkeyState state) {
		if (level == null || donkey == null || state == null) {
			return;
		}
		if (!state.legacyVisualsCleaned) {
			cleanupLegacyGennadiyDonkeyVisuals(level, donkey, state);
			state.legacyVisualsCleaned = true;
		}
		ArmorStand stand = findGennadiyDonkeyVisual(level, state);
		if (stand != null) {
			configureGennadiyDonkeyVisual(stand);
			attachGennadiyDonkeyVisual(donkey, stand);
			syncGennadiyDonkeyVisualEquipment(stand);
		} else {
			stand = EntityType.ARMOR_STAND.create(level, EntitySpawnReason.TRIGGERED);
			if (stand != null) {
				stand.addTag(GENNADIY_DONKEY_VISUAL_TAG);
				stand.addTag(GENNADIY_DONKEY_OWNER_TAG_PREFIX + state.ownerId);
				configureGennadiyDonkeyVisual(stand);
				stand.setPos(donkey.getX(), donkey.getY(), donkey.getZ());
				applyGennadiyDonkeyVisualYaw(stand, donkey.yBodyRot);
				level.addFreshEntity(stand);
				state.visualId = stand.getUUID();
				attachGennadiyDonkeyVisual(donkey, stand);
				syncGennadiyDonkeyVisualEquipment(stand);
			}
		}

		ArmorStand helmetStand = findGennadiyDonkeyHelmetVisual(level, state);
		if (helmetStand != null) {
			configureGennadiyDonkeyHelmetVisual(helmetStand);
			attachGennadiyDonkeyVisual(donkey, helmetStand);
			syncGennadiyDonkeyVisualEquipment(helmetStand);
		} else {
			helmetStand = EntityType.ARMOR_STAND.create(level, EntitySpawnReason.TRIGGERED);
			if (helmetStand != null) {
				helmetStand.addTag(GENNADIY_DONKEY_HELMET_VISUAL_TAG);
				helmetStand.addTag(GENNADIY_DONKEY_OWNER_TAG_PREFIX + state.ownerId);
				configureGennadiyDonkeyHelmetVisual(helmetStand);
				helmetStand.setPos(donkey.getX(), donkey.getY(), donkey.getZ());
				applyGennadiyDonkeyVisualYaw(helmetStand, donkey.getYHeadRot());
				level.addFreshEntity(helmetStand);
				state.helmetVisualId = helmetStand.getUUID();
				attachGennadiyDonkeyVisual(donkey, helmetStand);
				syncGennadiyDonkeyVisualEquipment(helmetStand);
			}
		}
		updateGennadiyDonkeyVisual(level, null, donkey, state);
	}

	private static ArmorStand findGennadiyDonkeyVisual(ServerLevel level, GennadiyDonkeyState state) {
		if (level == null || state == null) {
			return null;
		}
		if (state.visualId != null) {
			Entity entity = level.getEntity(state.visualId);
			if (entity instanceof ArmorStand stand) {
				return stand;
			}
			if (entity != null) {
				entity.discard();
			}
		}
		Entity donkey = state.donkeyId == null ? null : level.getEntity(state.donkeyId);
		if (donkey == null) {
			state.visualId = null;
			return null;
		}
		String ownerTag = GENNADIY_DONKEY_OWNER_TAG_PREFIX + state.ownerId;
		List<ArmorStand> stands = level.getEntitiesOfClass(
				ArmorStand.class,
				donkey.getBoundingBox().inflate(32.0D),
				candidate -> candidate.getTags().contains(GENNADIY_DONKEY_VISUAL_TAG) && candidate.getTags().contains(ownerTag)
		);
		if (!stands.isEmpty()) {
			ArmorStand keep = stands.get(0);
			for (int i = 1; i < stands.size(); i++) {
				stands.get(i).discard();
			}
			state.visualId = keep.getUUID();
			return keep;
		}
		state.visualId = null;
		return null;
	}

	private static ArmorStand findGennadiyDonkeyHelmetVisual(ServerLevel level, GennadiyDonkeyState state) {
		if (level == null || state == null) {
			return null;
		}
		if (state.helmetVisualId != null) {
			Entity entity = level.getEntity(state.helmetVisualId);
			if (entity instanceof ArmorStand stand) {
				return stand;
			}
			if (entity != null) {
				entity.discard();
			}
		}
		Entity donkey = state.donkeyId == null ? null : level.getEntity(state.donkeyId);
		if (donkey == null) {
			state.helmetVisualId = null;
			return null;
		}
		String ownerTag = GENNADIY_DONKEY_OWNER_TAG_PREFIX + state.ownerId;
		List<ArmorStand> stands = level.getEntitiesOfClass(
				ArmorStand.class,
				donkey.getBoundingBox().inflate(32.0D),
				candidate -> candidate.getTags().contains(GENNADIY_DONKEY_HELMET_VISUAL_TAG) && candidate.getTags().contains(ownerTag)
		);
		if (!stands.isEmpty()) {
			ArmorStand keep = stands.get(0);
			for (int i = 1; i < stands.size(); i++) {
				stands.get(i).discard();
			}
			state.helmetVisualId = keep.getUUID();
			return keep;
		}
		state.helmetVisualId = null;
		return null;
	}

	private static void cleanupLegacyGennadiyDonkeyVisuals(ServerLevel level, Donkey donkey, GennadiyDonkeyState state) {
		if (level == null || donkey == null || state == null) {
			return;
		}
		String ownerTag = GENNADIY_DONKEY_OWNER_TAG_PREFIX + state.ownerId;
		List<Display.ItemDisplay> displays = level.getEntitiesOfClass(
				Display.ItemDisplay.class,
				donkey.getBoundingBox().inflate(32.0D),
				candidate -> (candidate.getTags().contains(GENNADIY_DONKEY_VISUAL_TAG) || candidate.getTags().contains(GENNADIY_DONKEY_HELMET_VISUAL_TAG))
						&& candidate.getTags().contains(ownerTag)
		);
		for (Display.ItemDisplay display : displays) {
			display.discard();
		}
	}

	private static void updateGennadiyDonkeyVisual(ServerLevel level, ServerPlayer owner, Donkey donkey, GennadiyDonkeyState state) {
		updateGennadiyDonkeyVisual(level, owner, donkey, state, false, 0.0F, null);
	}

	private static void updateGennadiyDonkeyVisual(ServerLevel level, ServerPlayer owner, Donkey donkey, GennadiyDonkeyState state, boolean useYawOverride, float yawOverride, ServerPlayer priorityViewer) {
		if (level == null || donkey == null || state == null) {
			return;
		}
		ArmorStand stand = findGennadiyDonkeyVisual(level, state);
		ArmorStand helmetStand = findGennadiyDonkeyHelmetVisual(level, state);
		if (stand == null && helmetStand == null) {
			return;
		}
		if (stand != null) {
			attachGennadiyDonkeyVisual(donkey, stand);
		}
		if (helmetStand != null) {
			attachGennadiyDonkeyVisual(donkey, helmetStand);
		}
		boolean manual = owner != null && donkey.hasPassenger(owner);
		if (manual && useYawOverride) {
			applyGennadiyDonkeyManualVehicleYaw(donkey, yawOverride);
		}
		float turretYaw = donkey.yBodyRot;
		float helmetYaw = donkey.getYHeadRot();
		if (stand != null) {
			donkey.positionRider(stand);
			applyGennadiyDonkeyVisualYaw(stand, turretYaw);
			stand.setOldPosAndRot(stand.position(), turretYaw, stand.getXRot());
		}
		if (helmetStand != null) {
			donkey.positionRider(helmetStand);
			applyGennadiyDonkeyVisualYaw(helmetStand, helmetYaw);
			helmetStand.setOldPosAndRot(helmetStand.position(), helmetYaw, helmetStand.getXRot());
		}
		if (stand != null) {
			syncGennadiyDonkeyVisualEquipment(stand);
		}
		if (helmetStand != null) {
			syncGennadiyDonkeyVisualEquipment(helmetStand);
		}
	}

	private static void applyGennadiyDonkeyManualVehicleYaw(Donkey donkey, float yaw) {
		if (donkey == null) {
			return;
		}
		donkey.setYRot(yaw);
		donkey.yRotO = yaw;
		donkey.setYBodyRot(yaw);
		donkey.yBodyRotO = yaw;
		donkey.setYHeadRot(yaw);
		donkey.yHeadRotO = yaw;
	}

	private static void applyGennadiyDonkeyVisualYaw(ArmorStand stand, float yaw) {
		if (stand == null) {
			return;
		}
		stand.absSnapRotationTo(yaw, 0.0F);
		stand.setYHeadRot(yaw);
		stand.yHeadRotO = yaw;
		stand.setYBodyRot(yaw);
		stand.yBodyRotO = yaw;
	}

	private static void attachGennadiyDonkeyVisual(Donkey donkey, ArmorStand stand) {
		if (stand == null || donkey == null) {
			return;
		}
		if (stand.getVehicle() == donkey && donkey.hasPassenger(stand)) {
			return;
		}
		forceEntityPassenger(donkey, stand);
	}

	private static void configureGennadiyDonkeyVisual(ArmorStand stand) {
		if (stand == null) {
			return;
		}
		stand.setInvisible(true);
		stand.setInvulnerable(true);
		stand.setSilent(true);
		stand.setNoGravity(true);
		stand.setCustomName(null);
		stand.setCustomNameVisible(false);
		stand.setItemSlot(EquipmentSlot.HEAD, BattleDonkeyTurretItem.createDisplayStack());
		((ArmorStandAccessor) stand).lg2$setSmall(false);
		((ArmorStandAccessor) stand).lg2$setMarker(true);
	}

	private static void configureGennadiyDonkeyHelmetVisual(ArmorStand stand) {
		if (stand == null) {
			return;
		}
		stand.setInvisible(true);
		stand.setInvulnerable(true);
		stand.setSilent(true);
		stand.setNoGravity(true);
		stand.setCustomName(null);
		stand.setCustomNameVisible(false);
		stand.setItemSlot(EquipmentSlot.HEAD, BattleDonkeyTurretItem.createHelmetDisplayStack());
		((ArmorStandAccessor) stand).lg2$setSmall(false);
		((ArmorStandAccessor) stand).lg2$setMarker(true);
	}

	private static void syncGennadiyDonkeyVisualEquipment(ArmorStand stand) {
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

	private static void removeGennadiyDonkeyVisual(MinecraftServer server, GennadiyDonkeyState state) {
		if (server == null || state == null) {
			return;
		}
		if (state.visualId != null) {
			Entity visual = findEntity(server, state.visualId);
			if (visual != null) {
				visual.discard();
			}
		}
		if (state.helmetVisualId != null) {
			Entity visual = findEntity(server, state.helmetVisualId);
			if (visual != null) {
				visual.discard();
			}
		}
		state.visualId = null;
		state.helmetVisualId = null;
	}

	public static boolean handleGennadiyDefenseDamage(ServerLevel level, LivingEntity victim, DamageSource damageSource, float damage) {
		if (level == null || !(victim instanceof ServerPlayer player) || damageSource == null || damage <= 0.0F) {
			return false;
		}
		GennadiyDefenseSession session = GENNADIY_DEFENSE_SESSIONS.get(player.getUUID());
		if (session == null || !level.dimension().equals(session.dimension) || level.getGameTime() >= session.endTick) {
			return false;
		}

		session.accumulatedDamage += Math.max(0.0F, damage);
		LivingEntity attacker = resolveDamageAttacker(damageSource);
		if (attacker != null && attacker != player && attacker.isAlive()) {
			setGennadiyDonkeyTarget(player.getUUID(), attacker, GENNADIY_DONKEY_TARGET_PRIORITY_OWNER_COMBAT);
		}
		applyGennadiyDefenseStasis(player, session.endTick - level.getGameTime());
		return true;
	}

	public static boolean shouldBlockGennadiyDefenseEffect(LivingEntity entity, MobEffectInstance effect) {
		if (!(entity instanceof ServerPlayer player) || effect == null || GENNADIY_DEFENSE_APPLYING_EFFECT.get()) {
			return false;
		}
		return isGennadiyDefenseActive(player);
	}

	public static boolean shouldBlockGennadiyDefensePush(Entity entity) {
		return entity instanceof ServerPlayer player && isGennadiyDefenseActive(player);
	}

	public static boolean shouldBlockGennadiyDefenseDismount(ServerPlayer player) {
		return player != null
				&& player.isAlive()
				&& isGennadiyDefenseActive(player)
				&& isFsitSeatEntity(player.getVehicle());
	}

	private static boolean isGennadiyDefenseActive(ServerPlayer player) {
		if (player == null || !(player.level() instanceof ServerLevel level)) {
			return false;
		}
		GennadiyDefenseSession session = GENNADIY_DEFENSE_SESSIONS.get(player.getUUID());
		return session != null && level.dimension().equals(session.dimension) && level.getGameTime() < session.endTick;
	}

	private static void clearGennadiyDefense(ServerPlayer player) {
		if (player == null) {
			return;
		}
		GennadiyDefenseSession session = GENNADIY_DEFENSE_SESSIONS.remove(player.getUUID());
		cleanupGennadiyDefenseSeat(player.level().getServer(), session, player);
		cleanupGennadiyDefenseEffects(player);
	}

	public static void handleGennadiyCombatDamage(ServerLevel level, LivingEntity victim, DamageSource damageSource, float damage, boolean applied) {
		if (!applied || level == null || victim == null || damageSource == null || damage <= 0.0F) {
			return;
		}
		if (victim instanceof ServerPlayer damagedPlayer) {
			resetGennadiyReportsForDamagedHolder(damagedPlayer, level.getGameTime());
		}
		handleGennadiyReportHit(level, victim, damageSource);
		LivingEntity attacker = resolveDamageAttacker(damageSource);
		if (attacker == null || !attacker.isAlive() || attacker == victim) {
			return;
		}
		UUID donkeyOwnerId = GENNADIY_DONKEY_OWNER_BY_ENTITY.get(victim.getUUID());
		if (donkeyOwnerId != null) {
			setGennadiyDonkeyTarget(donkeyOwnerId, attacker, GENNADIY_DONKEY_TARGET_PRIORITY_DIRECT_HIT);
			return;
		}
		if (victim instanceof ServerPlayer victimPlayer) {
			GennadiyDonkeyState victimState = GENNADIY_DONKEY_STATES.get(victimPlayer.getUUID());
			if (victimState != null && victimState.isActive()) {
				setGennadiyDonkeyTarget(victimPlayer.getUUID(), attacker, GENNADIY_DONKEY_TARGET_PRIORITY_OWNER_COMBAT);
			}
		}
		if (attacker instanceof ServerPlayer attackerPlayer) {
			GennadiyDonkeyState attackerState = GENNADIY_DONKEY_STATES.get(attackerPlayer.getUUID());
			if (attackerState != null && attackerState.isActive()) {
				setGennadiyDonkeyTarget(attackerPlayer.getUUID(), victim, GENNADIY_DONKEY_TARGET_PRIORITY_OWNER_COMBAT);
			}
		}
	}

	private static void resetGennadiyReportsForDamagedHolder(ServerPlayer player, long nowTick) {
		if (player == null) {
			return;
		}
		boolean changed = false;
		Inventory inventory = player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (isGennadiyReportItem(stack)) {
				resetGennadiyReportCooldown(stack, player, nowTick);
				changed = true;
			}
		}
		if (player.containerMenu != null && isGennadiyReportItem(player.containerMenu.getCarried())) {
			resetGennadiyReportCooldown(player.containerMenu.getCarried(), player, nowTick);
			changed = true;
		}
		if (changed) {
			displayRemainingCooldown(player, getGennadiyReportCooldownTicks());
			player.inventoryMenu.broadcastChanges();
		}
	}

	private static void handleGennadiyReportHit(ServerLevel level, LivingEntity victim, DamageSource damageSource) {
		if (!(victim instanceof ServerPlayer target)
				|| !(damageSource.getEntity() instanceof ServerPlayer attacker)
				|| damageSource.getDirectEntity() != attacker
				|| target == attacker
				|| target.connection == null) {
			return;
		}
		ItemStack weapon = attacker.getMainHandItem();
		GennadiyReportData data = readGennadiyReportData(weapon);
		if (data == null) {
			return;
		}
		long nowTick = level.getGameTime();
		if (!attacker.getUUID().equals(data.holderId())) {
			resetGennadiyReportCooldown(weapon, attacker, nowTick);
			return;
		}
		enforceGennadiyReportComponents(weapon);
		if (data.cooldownEndTick() > nowTick) {
			long remainingTicks = data.cooldownEndTick() - nowTick;
			displayRemainingCooldown(attacker, remainingTicks);
			return;
		}
		target.connection.disconnect(Component.literal("Вас зарепортил " + attacker.getGameProfile().name() + "."));
	}

	public static void handleGennadiyRageMeleeDamage(ServerLevel level, LivingEntity victim, DamageSource damageSource, float damage, boolean applied) {
		if (!applied
				|| level == null
				|| victim == null
				|| damageSource == null
				|| damage <= 0.0F
				|| GENNADIY_RAGE_APPLYING_DAMAGE.get()) {
			return;
		}
		if (!(damageSource.getEntity() instanceof ServerPlayer attacker) || damageSource.getDirectEntity() != attacker) {
			return;
		}

		RaceAbilityConfig stock = getGennadiyStockAbility(attacker);
		if (!isGennadiyRageActive(attacker, stock)) {
			return;
		}
		double bonusRatio = getGennadiyRageMeleeDamageBonusRatio(stock);
		if (bonusRatio <= 0.0D) {
			return;
		}

		float bonusDamage = (float) (damage * bonusRatio);
		if (bonusDamage <= 0.0F) {
			return;
		}

		int previousInvulnerableTime = victim.invulnerableTime;
		victim.invulnerableTime = 0;
		GENNADIY_RAGE_APPLYING_DAMAGE.set(Boolean.TRUE);
		try {
			victim.hurtServer(level, damageSource, bonusDamage);
		} finally {
			GENNADIY_RAGE_APPLYING_DAMAGE.set(Boolean.FALSE);
			victim.invulnerableTime = Math.max(victim.invulnerableTime, previousInvulnerableTime);
		}
	}

	private static void setGennadiyDonkeyTarget(UUID ownerId, LivingEntity target, int priority) {
		if (ownerId == null || target == null || !target.isAlive() || target.isSpectator()) {
			return;
		}
		GennadiyDonkeyState state = GENNADIY_DONKEY_STATES.get(ownerId);
		if (state == null || !state.isActive()) {
			return;
		}
		if (state.ammo < 2) {
			clearGennadiyDonkeyTarget(state);
			return;
		}
		if (ownerId.equals(target.getUUID())) {
			return;
		}
		int normalizedPriority = Math.max(GENNADIY_DONKEY_TARGET_PRIORITY_AGGRO, priority);
		if (state.targetId != null && state.targetId.equals(target.getUUID())) {
			state.targetPriority = Math.max(state.targetPriority, normalizedPriority);
			return;
		}
		if (state.targetPriority > normalizedPriority && isGennadiyDonkeyTargetStillAlive(target.level().getServer(), state)) {
			return;
		}
		state.targetId = target.getUUID();
		state.targetPriority = normalizedPriority;
		state.wanderTarget = null;
		state.nextWanderRetargetTick = 0L;
	}

	private static boolean isGennadiyDonkeyTargetStillAlive(MinecraftServer server, GennadiyDonkeyState state) {
		if (server == null || state == null || state.targetId == null) {
			return false;
		}
		Entity entity = findEntity(server, state.targetId);
		return entity instanceof LivingEntity target && target.isAlive() && !target.isSpectator();
	}

	private static void clearGennadiyDonkeyTarget(GennadiyDonkeyState state) {
		if (state == null) {
			return;
		}
		state.targetId = null;
		state.targetPriority = 0;
		state.wanderTarget = null;
		state.nextWanderRetargetTick = 0L;
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
		for (CartelManualPage manualPage : cartelManualPages(player)) {
			pages.add(Filterable.passThrough(buildCartelShnyagaPage(manualPage)));
		}

		return new WrittenBookContent(
				Filterable.passThrough(localizeCartelShnyagaBookTitle(player)),
				localizeCartelShnyagaBookAuthor(player),
				0,
				pages,
				true
		);
	}

	private static Component buildCartelShnyagaPage(CartelManualPage manualPage) {
		MutableComponent page = Component.empty();
		page = page.append(Component.literal(manualPage.title())
				.withStyle(style -> style.withBold(true).withItalic(false).withColor(0x5B3118)));
		page = page.append(Component.literal("\n"));
		if (!manualPage.beforeImage().isBlank()) {
			page = page.append(cartelManualBodyComponent(manualPage.beforeImage()));
			if (manualPage.imageIndex() >= 0 || !manualPage.afterImage().isBlank()) {
				page = page.append(Component.literal("\n"));
			}
		}
		if (manualPage.imageIndex() >= 0) {
			page = page.append(Component.literal("\n".repeat(CARTEL_MANUAL_IMAGE_LEADING_LINES)));
			page = page.append(Component.literal(cartelManualImageGlyphRow(manualPage.imageIndex()))
					.withStyle(style -> style.withColor(0xFFFFFF).withItalic(false).withFont(CARTEL_MANUAL_PAGE_FONT)));
			if (!manualPage.afterImage().isBlank()) {
				page = page.append(Component.literal("\n".repeat(CARTEL_MANUAL_IMAGE_TRAILING_LINES + 1)));
			}
		}
		if (!manualPage.afterImage().isBlank()) {
			page = page.append(cartelManualBodyComponent(manualPage.afterImage()));
		}
		return page;
	}

	private static MutableComponent cartelManualBodyComponent(String text) {
		return Component.literal(text)
				.withStyle(style -> style.withItalic(false).withColor(0x2E2016));
	}

	private static String cartelManualImageGlyphRow(int imageIndex) {
		if (imageIndex < 0 || imageIndex >= CARTEL_MANUAL_IMAGE_COUNT) {
			return "";
		}
		StringBuilder builder = new StringBuilder(CARTEL_MANUAL_IMAGE_COLUMNS * 2);
		for (int column = 0; column < CARTEL_MANUAL_IMAGE_COLUMNS; column++) {
			if (column > 0) {
				builder.append(CARTEL_MANUAL_IMAGE_COLUMN_JOINER);
			}
			int glyph = CARTEL_MANUAL_IMAGE_FIRST_GLYPH + imageIndex * CARTEL_MANUAL_IMAGE_COLUMNS + column;
			builder.appendCodePoint(glyph);
		}
		return builder.toString();
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

	private static final CartelManualPage[] CARTEL_MANUAL_PAGES_RU = {
			new CartelManualPage("1. Личная книга", "Это твоя ЛИЧНАЯ книга. Никому не показывай рецепты. Если другие узнают, как ты делаешь товар, они смогут конкурировать с тобой. Крафты доступны всем, но знаешь их ТОЛЬКО ТЫ.", -1, ""),
			new CartelManualPage("2. Травка", "", 0, "Посади обычный папоротник. Когда он вырастет, есть шанс получить «Травку». Костная мука ускоряет рост."),
			new CartelManualPage("3. Сушка", "Обычная травка не годится для рецептов.", 1, "Для создания товара высуши её в печи."),
			new CartelManualPage("4. Косячок", "Сверху и снизу по 3 бумаги. В центре 3 сушённой травки. На выходе получишь «Косячок».", 2, ""),
			new CartelManualPage("5. Прикуривание", "Возьми в одну руку зажигалку, во вторую — косячок. Нажми ПКМ для прикуривания. Чтобы затянуться, зажми ПКМ: чем дольше затяг, тем больше дыма.", -1, ""),
			new CartelManualPage("6. Кокаин", "В котёл с водой добавь сушённую травку и костную муку в пропорции 1:1.", 3, "Как итог — получишь «Кокаин»."),
			new CartelManualPage("7. Партии", "Каждая операция тратит немного воды из котла. Выгоднее загружать сразу много сырья, например: 16 костной муки + 16 сушённой травки = 16 кокаина. Воды в котле хватает на 48 кокаина, но при малых партиях она расходуется быстрее.", -1, ""),
			new CartelManualPage("8. Заурядное зелье", "В варочной стойке: пузырёк с водой + любой ингредиент из списка: паучий глаз, сверкающий ломтик арбуза, сахар, огненный порошок, магмовый крем, слеза гаста, кроличья лапка, стержень вихря, блок слизи, камень или паутина.", -1, ""),
			new CartelManualPage("9. Метадон", "Варится в зельеварке. В заурядное зелье добавь кокаин. На выходе получишь «Метадон».", 4, ""),
			new CartelManualPage("10. Зависимость", "Тебе метадон даёт положительные эффекты. Другим — зависимость. Первые уколы для других дают негативные эффекты, последующие — положительные. Если зависимый долго не колет метадон, у него начинается ломка.", -1, "")
	};

	private static final CartelManualPage[] CARTEL_MANUAL_PAGES_EN = {
			new CartelManualPage("1. Personal Book", "This is your PERSONAL book. Do not show the recipes. If others learn how you make the goods, they can compete with you. The crafts work for everyone, but ONLY YOU know them.", -1, ""),
			new CartelManualPage("2. Travka", "", 0, "Plant a normal fern. When it grows, it has a chance to drop Travka. Bone meal speeds up the growth."),
			new CartelManualPage("3. Drying", "Raw Travka is not good for recipes.", 1, "Dry it in a furnace to make goods."),
			new CartelManualPage("4. Joint", "Put 3 papers on the top and bottom rows. Put 3 Dried Travka in the center row. Output: Joint.", 2, ""),
			new CartelManualPage("5. Lighting Up", "Hold flint and steel in one hand and the Joint in the other. Right-click to light it. Hold right-click to take a drag: the longer the drag, the more smoke.", -1, ""),
			new CartelManualPage("6. Cocaine", "Add Dried Travka and bone meal to a water cauldron in a 1:1 ratio.", 3, "Result: Cocaine."),
			new CartelManualPage("7. Batches", "Each operation spends some cauldron water. It is better to load many ingredients at once, for example: 16 bone meal + 16 Dried Travka = 16 Cocaine. A full cauldron is enough for 48 Cocaine, but small batches waste water faster.", -1, ""),
			new CartelManualPage("8. Mundane Potion", "In a brewing stand: water bottle + any ingredient from the list: spider eye, glistering melon slice, sugar, blaze powder, magma cream, ghast tear, rabbit foot, breeze rod, slime block, stone, or cobweb.", -1, ""),
			new CartelManualPage("9. Methadone", "Brew it in a brewing stand. Add Cocaine to a Mundane Potion. Output: Methadone.", 4, ""),
			new CartelManualPage("10. Addiction", "Methadone gives you positive effects. For others, it gives addiction. Their first injections cause negative effects; later ones become positive. If an addicted player goes too long without injecting Methadone, withdrawal begins.", -1, "")
	};
	private static CartelManualPage[] cartelManualPages(ServerPlayer player) {
		return cartelBookLanguage(player) == CartelBookLanguage.EN
				? CARTEL_MANUAL_PAGES_EN
				: CARTEL_MANUAL_PAGES_RU;
	}

	private record CartelManualPage(String title, String beforeImage, int imageIndex, String afterImage) {
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
			if (player == null || player == caster || !player.isAlive() || player.isSpectator()) {
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
		if (!target.isAlive() || target.isSpectator()) {
			caster.displayClientMessage(
					Component.literal(localizeCartelDisguiseText(caster, "target_unavailable"))
							.withStyle(style -> style.withColor(ChatFormatting.RED).withItalic(false)),
					true
			);
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
		sendPersonalSound(caster, SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, caster.position(), CARTEL_DISGUISE_PAGE_SOUND_VOLUME, CARTEL_DISGUISE_PAGE_SOUND_PITCH, nowTick ^ caster.getUUID().getLeastSignificantBits());
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

	private static ItemStack buildWomanShnyagaArrow(ServerPlayer viewer, boolean next) {
		if (viewer != null && PolymerResourcePackUtils.hasMainPack(viewer)) {
			return ItemStack.EMPTY;
		}

		ItemStack stack = new ItemStack(Items.ARROW);
		stack.set(
				DataComponents.CUSTOM_NAME,
				Component.literal(localizeWomanShnyagaMenuText(viewer, next ? "next" : "previous"))
						.withStyle(style -> style.withItalic(false))
		);
		return stack;
	}

	private static ItemStack buildWomanShnyagaHead(ServerPlayer viewer, ServerPlayer target) {
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
				Component.literal(localizeWomanShnyagaMenuText(viewer, "select"))
						.withStyle(style -> style.withColor(0xFF6F9A).withItalic(false).withBold(true))
		);
		return stack;
	}

	private static ItemStack buildWomanShnyagaEmptyState(ServerPlayer viewer) {
		ItemStack stack = new ItemStack(Items.BARRIER);
		stack.set(DataComponents.CUSTOM_NAME, Component.literal(localizeWomanShnyagaMenuText(viewer, "empty")));
		return stack;
	}

	private static Component buildWomanShnyagaMenuTitle(ServerPlayer viewer, ServerPlayer target) {
		if (viewer != null && PolymerResourcePackUtils.hasMainPack(viewer)) {
			return buildWomanShnyagaPackTitle(viewer, target);
		}

		if (target == null) {
			return Component.literal(" ");
		}
		return Component.literal(localizeWomanShnyagaMenuText(viewer, "recipient") + target.getGameProfile().name());
	}

	private static Component buildWomanShnyagaPackTitle(ServerPlayer viewer, ServerPlayer target) {
		Component title = Component.literal(buildHorizontalAdvance(WOMAN_SHNYAGA_LETTER_OVERLAY_X_OFFSET) + buildOverlayGlyph(WOMAN_SHNYAGA_LETTER_OVERLAY_GLYPH, WOMAN_SHNYAGA_LETTER_OVERLAY_WIDTH))
				.withStyle(style -> style.withColor(0xFFFFFF).withItalic(false).withFont(WOMAN_SHNYAGA_LETTER_OVERLAY_FONT));
		if (viewer == null || target == null) {
			return title;
		}

		String senderText = viewer.getGameProfile().name();
		String recipientText = target.getGameProfile().name();
		int senderWidth = measureWomanShnyagaNameWidth(senderText);
		int senderStartX = centerWomanShnyagaName(WOMAN_SHNYAGA_SENDER_LINE_CENTER_X, senderText);
		int recipientStartX = centerWomanShnyagaRecipientName(WOMAN_SHNYAGA_RECIPIENT_LINE_CENTER_X, recipientText);
		return title.copy()
				.append(Component.literal(buildWomanShnyagaTitleAdvance(senderStartX))
						.withStyle(style -> style.withColor(0xFFFFFF).withItalic(false)))
				.append(Component.literal(senderText)
						.withStyle(style -> style.withColor(0xC88D62).withItalic(false).withFont(WOMAN_SHNYAGA_NAME_FONT)))
				.append(Component.literal(buildHorizontalAdvance(recipientStartX - (senderStartX + senderWidth)))
						.withStyle(style -> style.withColor(0xFFFFFF).withItalic(false)))
				.append(Component.literal(recipientText)
						.withStyle(style -> style.withColor(0xC88D62).withItalic(false).withFont(WOMAN_SHNYAGA_NAME_FONT)));
	}

	private static String buildWomanShnyagaTitleAdvance(int targetX) {
		return buildHorizontalAdvance(targetX - WOMAN_SHNYAGA_LETTER_OVERLAY_X_OFFSET);
	}

	private static int centerWomanShnyagaName(double centerX, String text) {
		return (int) Math.round(centerX + WOMAN_SHNYAGA_SENDER_NAME_CORRECTION_X - measureWomanShnyagaNameWidth(text) / 2.0D);
	}

	private static int centerWomanShnyagaRecipientName(double centerX, String text) {
		return (int) Math.round(centerX + WOMAN_SHNYAGA_RECIPIENT_NAME_CORRECTION_X - measureWomanShnyagaNameWidth(text) / 2.0D);
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

	private static int measureWomanShnyagaNameWidth(String text) {
		if (text == null || text.isEmpty()) {
			return 0;
		}
		Map<Character, Integer> advances = getWomanShnyagaNameAdvanceMap();
		int width = 0;
		for (int i = 0; i < text.length(); i++) {
			char character = text.charAt(i);
			width += character == ' '
					? WOMAN_SHNYAGA_NAME_SPACE_ADVANCE
					: advances.getOrDefault(character, WOMAN_SHNYAGA_NAME_CHAR_ADVANCE);
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

	private static Map<Character, Integer> getWomanShnyagaNameAdvanceMap() {
		Map<Character, Integer> cached = WOMAN_SHNYAGA_NAME_ADVANCE_CACHE;
		if (cached != null) {
			return cached;
		}
		synchronized (ServerRaceSystem.class) {
			if (WOMAN_SHNYAGA_NAME_ADVANCE_CACHE == null) {
				WOMAN_SHNYAGA_NAME_ADVANCE_CACHE = loadBitmapFontAdvanceMap(
						WOMAN_SHNYAGA_NAME_TEXTURE_RESOURCE,
						WOMAN_SHNYAGA_NAME_FONT_ROWS,
						WOMAN_SHNYAGA_NAME_BITMAP_COLUMNS,
						WOMAN_SHNYAGA_NAME_BITMAP_ROWS,
						WOMAN_SHNYAGA_NAME_RENDER_HEIGHT,
						WOMAN_SHNYAGA_NAME_CHAR_ADVANCE,
						"woman_shnyaga_name"
				);
			}
			return WOMAN_SHNYAGA_NAME_ADVANCE_CACHE;
		}
	}

	private static Map<Character, Integer> loadCartelPassportNameAdvanceMap() {
		return loadBitmapFontAdvanceMap(
				CARTEL_PASSPORT_NAME_TEXTURE_RESOURCE,
				CARTEL_PASSPORT_NAME_FONT_ROWS,
				CARTEL_PASSPORT_NAME_BITMAP_COLUMNS,
				CARTEL_PASSPORT_NAME_BITMAP_ROWS,
				CARTEL_PASSPORT_NAME_RENDER_HEIGHT,
				CARTEL_PASSPORT_NAME_CHAR_ADVANCE,
				"passport_name"
		);
	}

	private static Map<Character, Integer> loadBitmapFontAdvanceMap(
			String textureResource,
			String[] rows,
			int columns,
			int rowCount,
			int renderHeight,
			int fallbackAdvance,
			String debugName
	) {
		Map<Character, Integer> advances = new LinkedHashMap<>();
		try (InputStream stream = ServerRaceSystem.class.getResourceAsStream(textureResource)) {
			if (stream == null) {
				return advances;
			}
			BufferedImage image = ImageIO.read(stream);
			if (image == null) {
				return advances;
			}

			int cellWidth = image.getWidth() / columns;
			int cellHeight = image.getHeight() / rowCount;
			float renderScale = (float) renderHeight / (float) cellHeight;

			for (int row = 0; row < rows.length; row++) {
				String rowChars = rows[row];
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
						advance = fallbackAdvance;
					} else {
						int glyphWidth = right - left + 1;
						advance = Math.max(1, Math.round(glyphWidth * renderScale)) + 1;
					}
					advances.put(character, advance);
				}
			}
		} catch (IOException exception) {
			Lg2.LOGGER.warn("Failed to measure {} font glyph widths, using fallback centering", debugName, exception);
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

	private static int getWomanShnyagaPreviousSlot(ServerPlayer viewer) {
		return WOMAN_SHNYAGA_PREVIOUS_SLOT;
	}

	private static int getWomanShnyagaHeadSlot(ServerPlayer viewer) {
		return WOMAN_SHNYAGA_HEAD_SLOT;
	}

	private static int getWomanShnyagaNextSlot(ServerPlayer viewer) {
		return WOMAN_SHNYAGA_NEXT_SLOT;
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

	private static String localizeWomanShnyagaMenuText(ServerPlayer player, String key) {
		String locale = normalizeCartelDisguiseLocale(player);
		if (locale.startsWith("rpr")) {
			return switch (key) {
				case "recipient" -> "Получатель: ";
				case "select" -> "Выбрать";
				case "previous" -> "Предыдущiй";
				case "next" -> "Слѣдующiй";
				case "empty" -> "Нѣтъ игроковъ";
				default -> "";
			};
		}
		if (locale.startsWith("uk")) {
			return switch (key) {
				case "recipient" -> "Одержувач: ";
				case "select" -> "Вибрати";
				case "previous" -> "Попередній";
				case "next" -> "Наступний";
				case "empty" -> "Немає гравців";
				default -> "";
			};
		}
		if (locale.startsWith("ja")) {
			return switch (key) {
				case "recipient" -> "宛先: ";
				case "select" -> "選択";
				case "previous" -> "前へ";
				case "next" -> "次へ";
				case "empty" -> "プレイヤーがいません";
				default -> "";
			};
		}
		if (locale.startsWith("ru")) {
			return switch (key) {
				case "recipient" -> "Получатель: ";
				case "select" -> "Выбрать";
				case "previous" -> "Предыдущий";
				case "next" -> "Следующий";
				case "empty" -> "Нет игроков";
				default -> "";
			};
		}
		return switch (key) {
			case "recipient" -> "Recipient: ";
			case "select" -> "Select";
			case "previous" -> "Previous";
			case "next" -> "Next";
			case "empty" -> "No players";
			default -> "";
		};
	}

	private static String localizeWomanShnyagaText(ServerPlayer player, String key) {
		String locale = normalizeCartelDisguiseLocale(player);
		if (locale.startsWith("rpr")) {
			return switch (key) {
				case "no_players_online" -> "На серверѣ нѣтъ подходящихъ игроковъ";
				case "already_linked" -> "Связь уже имѣется";
				case "target_already_linked" -> "Сей игрокъ уже связанъ";
				case "target_busy" -> "Сей игрокъ уже получилъ иное предложеніе";
				case "write_prompt" -> "Напишите посланіе для избраннаго парня въ чатъ.";
				case "target_unavailable" -> "Избранный игрокъ болѣе недоступенъ";
				case "proposal_sent" -> "Предложеніе отправлено.";
				case "no_pending_proposal" -> "Нѣтъ ожидающаго предложенія.";
				case "proposal_expired" -> "Предложеніе уже недѣйственно.";
				case "proposal_on_cooldown" -> "Нынѣ отвѣтъ невозможенъ: умѣніе въ охлажденіи.";
				case "accepted_woman" -> "%s согласился. Связь установлена.";
				case "accepted_target" -> "Вы стали парнемъ %s. Связь установлена.";
				case "rejected_woman" -> "Васъ отшилъ %s.";
				case "rejected_target" -> "Вы отшили %s.";
				case "warning_line" -> "ВНИМАНІЕ!";
				case "warning_suffix" -> "избрала ТЕБЯ.";
				case "accept_button" -> "[❤ Согласиться]";
				case "reject_button" -> "[💔 Отшить]";
				case "link_broken_kill_woman" -> "Васъ убилъ %s. Отношенія разрушены.";
				case "link_broken_kill_target" -> "Вы бросили %s, предательски убив её!";
				case "link_broken_whitelist_woman" -> "%s исключили съ сервера. Отношенія разрушены.";
				case "link_broken_whitelist_target" -> "Вы бросили %s: вы удалены из whitelist.";
				case "link_broken_whitelist_target_woman_removed" -> "%s исключили съ сервера. Отношенія разрушены.";
				default -> "";
			};
		}
		if (locale.startsWith("uk")) {
			return switch (key) {
				case "no_players_online" -> "На сервері немає відповідних гравців";
				case "already_linked" -> "У вас уже є зв'язок";
				case "target_already_linked" -> "Цей гравець уже пов'язаний";
				case "target_busy" -> "Цей гравець уже отримав іншу пропозицію";
				case "write_prompt" -> "Напишіть повідомлення для вибраного хлопця в чат.";
				case "target_unavailable" -> "Вибраний гравець більше недоступний";
				case "proposal_sent" -> "Пропозицію відправлено.";
				case "no_pending_proposal" -> "Немає активної пропозиції.";
				case "proposal_expired" -> "Пропозиція вже недійсна.";
				case "proposal_on_cooldown" -> "Відповідь зараз неможлива: здібність на відновленні.";
				case "accepted_woman" -> "%s погодився. Зв'язок встановлено.";
				case "accepted_target" -> "Ви стали хлопцем %s. Зв'язок встановлено.";
				case "rejected_woman" -> "Вас відшив %s.";
				case "rejected_target" -> "Ви відшили %s.";
				case "warning_line" -> "УВАГА!";
				case "warning_suffix" -> "обрала ТЕБЕ.";
				case "accept_button" -> "[❤ Погодитися]";
				case "reject_button" -> "[💔 Відшити]";
				case "link_broken_kill_woman" -> "Вас убив %s. Стосунки зруйновано.";
				case "link_broken_kill_target" -> "Ви кинули %s, підступно вбивши її!";
				case "link_broken_whitelist_woman" -> "%s виключили з сервера. Стосунки зруйновано.";
				case "link_broken_whitelist_target" -> "Ви кинули %s: вас видалено з whitelist.";
				case "link_broken_whitelist_target_woman_removed" -> "%s виключили з сервера. Стосунки зруйновано.";
				default -> "";
			};
		}
		if (locale.startsWith("ja")) {
			return switch (key) {
				case "no_players_online" -> "対象にできるプレイヤーがいません";
				case "already_linked" -> "すでに絆があります";
				case "target_already_linked" -> "そのプレイヤーはすでに結ばれています";
				case "target_busy" -> "そのプレイヤーには別の申し込みがあります";
				case "write_prompt" -> "選んだ相手へのメッセージをチャットに入力してください。";
				case "target_unavailable" -> "選んだプレイヤーはもう利用できません";
				case "proposal_sent" -> "申し込みを送りました。";
				case "no_pending_proposal" -> "返答する申し込みがありません。";
				case "proposal_expired" -> "その申し込みはもう無効です。";
				case "proposal_on_cooldown" -> "今は返答できません。この能力はクールダウン中です。";
				case "accepted_woman" -> "%s が承諾しました。絆が結ばれました。";
				case "accepted_target" -> "あなたは %s の彼氏になりました。絆が結ばれました。";
				case "rejected_woman" -> "%s に振られました。";
				case "rejected_target" -> "あなたは %s を振りました。";
				case "warning_line" -> "警告!";
				case "warning_suffix" -> "があなたを選びました。";
				case "accept_button" -> "[❤ 承諾する]";
				case "reject_button" -> "[💔 振る]";
				case "link_broken_kill_woman" -> "%s に殺されました。関係は壊れました。";
				case "link_broken_kill_target" -> "%s を裏切って殺し、振りました。";
				case "link_broken_whitelist_woman" -> "%s がサーバーから除外されました。関係は壊れました。";
				case "link_broken_whitelist_target" -> "%s を振りました: あなたが whitelist から削除されました。";
				case "link_broken_whitelist_target_woman_removed" -> "%s がサーバーから除外されました。関係は壊れました。";
				default -> "";
			};
		}
		if (locale.startsWith("ru")) {
			return switch (key) {
				case "no_players_online" -> "На сервере нет подходящих игроков";
				case "already_linked" -> "У вас уже есть связь";
				case "target_already_linked" -> "Этот игрок уже связан";
				case "target_busy" -> "Этот игрок уже получил другое предложение";
				case "write_prompt" -> "Напишите сообщение для выбранного парня в чат.";
				case "target_unavailable" -> "Выбранный игрок больше недоступен";
				case "proposal_sent" -> "Предложение отправлено.";
				case "no_pending_proposal" -> "Нет активного предложения.";
				case "proposal_expired" -> "Предложение уже недействительно.";
				case "proposal_on_cooldown" -> "Сейчас ответить нельзя: способность женщины в КД.";
				case "accepted_woman" -> "%s согласился. Связь установлена.";
				case "accepted_target" -> "Вы стали парнем %s. Связь установлена.";
				case "rejected_woman" -> "Вас отшил %s.";
				case "rejected_target" -> "Вы отшили %s.";
				case "warning_line" -> "ВНИМАНИЕ!";
				case "warning_suffix" -> "выбрала ТЕБЯ.";
				case "accept_button" -> "[❤ Согласиться]";
				case "reject_button" -> "[💔 Отшить]";
				case "link_broken_kill_woman" -> "Вас убил %s. Отношения разрушены.";
				case "link_broken_kill_target" -> "Вы бросили %s, предательски убив её!";
				case "link_broken_whitelist_woman" -> "%s исключили с сервера. Отношения разрушены.";
				case "link_broken_whitelist_target" -> "Вы бросили %s: вас удалили из whitelist.";
				case "link_broken_whitelist_target_woman_removed" -> "%s исключили с сервера. Отношения разрушены.";
				default -> "";
			};
		}
		return switch (key) {
			case "no_players_online" -> "There are no suitable players on the server";
			case "already_linked" -> "You already have a bond";
			case "target_already_linked" -> "That player is already bonded";
			case "target_busy" -> "That player already has another proposal";
			case "write_prompt" -> "Write a message for the selected guy in chat.";
			case "target_unavailable" -> "The selected player is no longer available";
			case "proposal_sent" -> "Proposal sent.";
			case "no_pending_proposal" -> "There is no active proposal.";
			case "proposal_expired" -> "That proposal is no longer valid.";
			case "proposal_on_cooldown" -> "You can't answer right now: her ability is on cooldown.";
			case "accepted_woman" -> "%s accepted. The bond is established.";
			case "accepted_target" -> "You became %s's boyfriend. The bond is established.";
			case "rejected_woman" -> "You were dumped by %s.";
			case "rejected_target" -> "You dumped %s.";
			case "warning_line" -> "WARNING!";
			case "warning_suffix" -> "chose YOU.";
			case "accept_button" -> "[❤ Accept]";
			case "reject_button" -> "[💔 Reject]";
			case "link_broken_kill_woman" -> "%s killed you. The relationship is ruined.";
			case "link_broken_kill_target" -> "You dumped %s by treacherously killing her!";
			case "link_broken_whitelist_woman" -> "%s was removed from the server. The relationship is ruined.";
			case "link_broken_whitelist_target" -> "You broke up with %s: you were removed from the whitelist.";
			case "link_broken_whitelist_target_woman_removed" -> "%s was removed from the server. The relationship is ruined.";
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

	private static MutableComponent localizedRaceMessage(ServerPlayer player, String key, String... args) {
		if (player != null && PolymerResourcePackUtils.hasMainPack(player)) {
			Object[] translatedArgs = new Object[args == null ? 0 : args.length];
			for (int index = 0; index < translatedArgs.length; index++) {
				translatedArgs[index] = Component.literal(args[index]);
			}
			return Component.translatable("message.lg2.race." + key, translatedArgs);
		}

		String text = localizeRaceMessageText(player, key);
		if (args != null && args.length > 0) {
			text = String.format(Locale.ROOT, text, (Object[]) args);
		}
		return Component.literal(text);
	}

	private static String localizeRaceMessageText(ServerPlayer player, String key) {
		String locale = normalizeCartelDisguiseLocale(player);
		if (locale.startsWith("rpr")) {
			return switch (key) {
				case "player_only" -> "Сію команду можетъ использовать токмо игрокъ";
				case "no_race" -> "У тебя пока нѣтъ назначенной расы";
				case "no_menu" -> "Не удалось открыть меню расы. Исполни /race reload";
				case "ability_disabled" -> "Умѣніе %s нынѣ недоступно";
				default -> "";
			};
		}
		if (locale.startsWith("uk")) {
			return switch (key) {
				case "player_only" -> "Цю команду може використовувати лише гравець";
				case "no_race" -> "У тебе поки немає призначеної раси";
				case "no_menu" -> "Не вдалося відкрити меню раси. Виконай /race reload";
				case "ability_disabled" -> "Здібність %s зараз недоступна";
				default -> "";
			};
		}
		if (locale.startsWith("ja")) {
			return switch (key) {
				case "player_only" -> "このコマンドはプレイヤーのみ使用できます";
				case "no_race" -> "まだ種族が割り当てられていません";
				case "no_menu" -> "種族メニューを開けませんでした。/race reload を実行してください";
				case "ability_disabled" -> "能力 %s は現在使用できません";
				default -> "";
			};
		}
		if (locale.startsWith("ru")) {
			return switch (key) {
				case "player_only" -> "Эту команду может использовать только игрок";
				case "no_race" -> "У тебя пока нет назначенной расы";
				case "no_menu" -> "Не удалось открыть меню расы. Выполни /race reload";
				case "ability_disabled" -> "Способность %s сейчас недоступна";
				default -> "";
			};
		}
		return switch (key) {
			case "player_only" -> "Only a player can use this command";
			case "no_race" -> "You do not have a race assigned yet";
			case "no_menu" -> "Failed to open race menu. Run /race reload";
			case "ability_disabled" -> "Ability %s is currently unavailable";
			default -> "";
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
		playCartelLawyerReactionSound(level, session, SoundEvents.VILLAGER_NO);

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

	private static void playCartelAttackActivationSound(ServerLevel level, LivingEntity target) {
		if (level == null || target == null) {
			return;
		}
		Vec3 origin = target.position().add(0.0D, Math.max(0.4D, target.getBbHeight() * 0.5D), 0.0D);
		playCartelResourcePackAwarePositionedSound(
				level,
				origin,
				CARTEL_ATTACK_FINGER_SOUND,
				SoundEvents.PILLAGER_AMBIENT,
				CARTEL_ATTACK_ACTIVATION_SOUND_VOLUME,
				CARTEL_ATTACK_ACTIVATION_SOUND_PITCH
		);
	}

	private static void playCartelSummonOutcomeSound(ServerLevel level, Vec3 origin, SoundEvent sound) {
		playCartelVanillaPositionedSound(level, origin, sound, CARTEL_SUMMON_OUTCOME_SOUND_VOLUME, CARTEL_SUMMON_OUTCOME_SOUND_PITCH);
	}

	private static void playCartelLawyerReactionSound(ServerLevel level, CartelDefenseSession session, SoundEvent sound) {
		if (level == null || session == null || sound == null) {
			return;
		}
		Entity lawyerEntity = level.getEntity(session.lawyerEntityId);
		if (lawyerEntity == null) {
			return;
		}
		playCartelVanillaPositionedSound(level, lawyerEntity.position(), sound, CARTEL_LAWYER_REACTION_SOUND_VOLUME, CARTEL_LAWYER_REACTION_SOUND_PITCH);
	}

	private static void maybePlayGennadiyDefenseChargeSound(ServerPlayer player, GennadiyDefenseSession session, long nowTick) {
		if (player == null || session == null || session.nextChargeSoundTick < 0L || nowTick < session.nextChargeSoundTick) {
			return;
		}
		playGennadiyDefenseChargeSound(player, nowTick);
		session.nextChargeSoundTick = nowTick + WOMAN_DEFENSE_PANIC_SOUND_INTERVAL_TICKS;
	}

	private static void playGennadiyDefenseChargeSound(ServerPlayer player, long nowTick) {
		if (player == null || player.connection == null) {
			return;
		}
		sendPersonalSound(
				player,
				SoundEvents.WARDEN_HEARTBEAT,
				SoundSource.PLAYERS,
				player.position(),
				GENNADIY_DEFENSE_CHARGE_SOUND_VOLUME,
				GENNADIY_DEFENSE_CHARGE_SOUND_PITCH,
				nowTick ^ player.getUUID().getMostSignificantBits()
		);
	}

	private static void playGennadiyDefenseExplosionSound(ServerLevel level, Vec3 origin) {
		if (level == null || origin == null) {
			return;
		}
		double rangeSqr = GENNADIY_DEFENSE_EXPLOSION_SOUND_RANGE_BLOCKS * GENNADIY_DEFENSE_EXPLOSION_SOUND_RANGE_BLOCKS;
		long seed = level.getRandom().nextLong();
		Holder<SoundEvent> sound = SoundEvents.GENERIC_EXPLODE;
		for (ServerPlayer viewer : level.players()) {
			if (viewer == null || viewer.connection == null || viewer.distanceToSqr(origin) > rangeSqr) {
				continue;
			}
			sendPositionedSound(
					viewer,
					sound,
					SoundSource.PLAYERS,
					origin,
					GENNADIY_DEFENSE_EXPLOSION_SOUND_VOLUME,
					GENNADIY_DEFENSE_EXPLOSION_SOUND_PITCH,
					seed
			);
		}
	}

	private static void playGennadiyDonkeyShootSound(ServerLevel level, Vec3 origin) {
		if (level == null || origin == null) {
			return;
		}
		double rangeSqr = GENNADIY_DONKEY_SHOOT_SOUND_RANGE_BLOCKS * GENNADIY_DONKEY_SHOOT_SOUND_RANGE_BLOCKS;
		long seed = level.getRandom().nextLong();
		Holder<SoundEvent> fallbackSound = BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.CROSSBOW_SHOOT);
		for (ServerPlayer viewer : level.players()) {
			if (viewer == null || viewer.connection == null || viewer.distanceToSqr(origin) > rangeSqr) {
				continue;
			}
			boolean hasPack = PolymerResourcePackUtils.hasMainPack(viewer);
			sendPositionedSound(
					viewer,
					hasPack ? GENNADIY_DONKEY_SHOOT_SOUND : fallbackSound,
					SoundSource.PLAYERS,
					origin,
					hasPack ? GENNADIY_DONKEY_SHOOT_SOUND_VOLUME : GENNADIY_DONKEY_SHOOT_FALLBACK_SOUND_VOLUME,
					hasPack ? GENNADIY_DONKEY_SHOOT_SOUND_PITCH : GENNADIY_DONKEY_SHOOT_FALLBACK_SOUND_PITCH,
					seed
			);
		}
	}

	private static void playGennadiyHookChainSound(ServerLevel level, Vec3 origin) {
		playGennadiyHookResourcePackAwareSound(
				level,
				origin,
				GENNADIY_HOOK_CHAIN_SOUND,
				SoundEvents.CHAIN_STEP,
				GENNADIY_HOOK_CHAIN_SOUND_VOLUME,
				GENNADIY_HOOK_CHAIN_SOUND_PITCH
		);
	}

	private static void playGennadiyHookImpactSound(ServerLevel level, Vec3 origin) {
		playGennadiyHookResourcePackAwareSound(
				level,
				origin,
				GENNADIY_HOOK_IMPACT_SOUND,
				SoundEvents.CHAIN_BREAK,
				GENNADIY_HOOK_IMPACT_SOUND_VOLUME,
				GENNADIY_HOOK_IMPACT_SOUND_PITCH
		);
	}

	private static void playGennadiyHookEndSound(ServerLevel level, Vec3 origin) {
		playGennadiyHookResourcePackAwareSound(
				level,
				origin,
				GENNADIY_HOOK_END_SOUND,
				SoundEvents.CHAIN_BREAK,
				GENNADIY_HOOK_END_SOUND_VOLUME,
				GENNADIY_HOOK_END_SOUND_PITCH
		);
	}

	private static void stopGennadiyHookChainSound(MinecraftServer server, GennadiyHookSession session) {
		if (server == null || session == null) {
			return;
		}
		ServerLevel level = server.getLevel(session.dimension);
		if (level == null) {
			return;
		}
		Entity caster = findEntity(server, session.playerId);
		if (caster != null && caster.level() == level) {
			stopGennadiyHookChainSound(level, caster.position());
		}
		Entity hookTip = session.hookTipId == null ? null : findEntity(server, session.hookTipId);
		if (hookTip != null && hookTip.level() == level) {
			stopGennadiyHookChainSound(level, hookTip.position());
		}
	}

	private static void stopGennadiyHookChainSound(ServerLevel level, Vec3 origin) {
		if (level == null || origin == null) {
			return;
		}
		double rangeSqr = GENNADIY_HOOK_SOUND_RANGE_BLOCKS * GENNADIY_HOOK_SOUND_RANGE_BLOCKS;
		for (ServerPlayer viewer : level.players()) {
			if (viewer == null || viewer.connection == null || viewer.distanceToSqr(origin) > rangeSqr) {
				continue;
			}
			viewer.connection.send(new ClientboundStopSoundPacket(GENNADIY_HOOK_CHAIN_SOUND_ID, SoundSource.PLAYERS));
			viewer.connection.send(new ClientboundStopSoundPacket(GENNADIY_HOOK_CHAIN_FALLBACK_SOUND_ID, SoundSource.PLAYERS));
		}
	}

	private static void playGennadiyHookResourcePackAwareSound(
			ServerLevel level,
			Vec3 origin,
			Holder<SoundEvent> packSound,
			SoundEvent fallbackSound,
			float volume,
			float pitch
	) {
		if (level == null || origin == null || packSound == null || fallbackSound == null) {
			return;
		}
		double rangeSqr = GENNADIY_HOOK_SOUND_RANGE_BLOCKS * GENNADIY_HOOK_SOUND_RANGE_BLOCKS;
		long seed = level.getRandom().nextLong();
		for (ServerPlayer viewer : level.players()) {
			if (viewer == null || viewer.connection == null || viewer.distanceToSqr(origin) > rangeSqr) {
				continue;
			}
			Holder<SoundEvent> sound = PolymerResourcePackUtils.hasMainPack(viewer)
					? packSound
					: BuiltInRegistries.SOUND_EVENT.wrapAsHolder(fallbackSound);
			sendPositionedSound(viewer, sound, SoundSource.PLAYERS, origin, volume, pitch, seed);
		}
	}

	private static void playCartelResourcePackAwarePositionedSound(
			ServerLevel level,
			Vec3 origin,
			Holder<SoundEvent> packSound,
			SoundEvent fallbackSound,
			float volume,
			float pitch
	) {
		if (level == null || origin == null || packSound == null || fallbackSound == null) {
			return;
		}
		double rangeSqr = CARTEL_SOUND_RANGE_BLOCKS * CARTEL_SOUND_RANGE_BLOCKS;
		long seed = level.getRandom().nextLong();
		for (ServerPlayer viewer : level.players()) {
			if (viewer == null || viewer.connection == null || viewer.distanceToSqr(origin) > rangeSqr) {
				continue;
			}
			Holder<SoundEvent> sound = PolymerResourcePackUtils.hasMainPack(viewer)
					? packSound
					: BuiltInRegistries.SOUND_EVENT.wrapAsHolder(fallbackSound);
			sendPositionedSound(viewer, sound, SoundSource.PLAYERS, origin, volume, pitch, seed);
		}
	}

	private static void playCartelVanillaPositionedSound(ServerLevel level, Vec3 origin, SoundEvent sound, float volume, float pitch) {
		if (level == null || origin == null || sound == null) {
			return;
		}
		double rangeSqr = CARTEL_SOUND_RANGE_BLOCKS * CARTEL_SOUND_RANGE_BLOCKS;
		long seed = level.getRandom().nextLong();
		Holder<SoundEvent> soundHolder = BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound);
		for (ServerPlayer viewer : level.players()) {
			if (viewer == null || viewer.connection == null || viewer.distanceToSqr(origin) > rangeSqr) {
				continue;
			}
			sendPositionedSound(viewer, soundHolder, SoundSource.PLAYERS, origin, volume, pitch, seed);
		}
	}

	private static void sendPositionedSound(ServerPlayer viewer, Holder<SoundEvent> sound, SoundSource source, Vec3 origin, float volume, float pitch, long seed) {
		if (viewer == null || viewer.connection == null || sound == null || source == null || origin == null) {
			return;
		}
		viewer.connection.send(new ClientboundSoundPacket(
				sound,
				source,
				origin.x,
				origin.y,
				origin.z,
				volume,
				pitch,
				seed
		));
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
		double armorDivider = positiveOrDefault(ability.cartelRaiderArmorDivider, CARTEL_DEFAULT_RAIDER_ARMOR_DIVIDER);
		CartelSummonSession session = new CartelSummonSession(level.dimension(), caster.getUUID(), target.getUUID(), nowTick + Math.max(1L, lifetimeTicks), Math.max(1L, afterKillTicks), armorDivider, target.position());

		BlockPos center = target.blockPosition();
		for (int i = 0; i < directions.size(); i++) {
			Direction direction = directions.get(i);
			BlockPos anchor = center.relative(direction, CARTEL_SPAWN_OFFSET_BLOCKS);
			Raider raider = spawnCartelRaider(level, raiderTypes.get(i), anchor, target, caster, armorDivider);
			if (raider != null) {
				session.raiderIds.add(raider.getUUID());
			}
		}

		if (session.raiderIds.isEmpty()) {
			return 0;
		}

		CARTEL_SUMMON_SESSIONS.put(UUID.randomUUID(), session);
		startOnlineCooldown(CARTEL_ATTACK_COOLDOWNS, caster.getUUID(), cooldownTicks);
		playCartelAttackActivationSound(level, target);

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
				session.lastTargetPosition = livingEntity.position();
			} else if (session.afterKillExpireTick == null) {
				session.afterKillExpireTick = nowTick + session.afterKillTicks;
				if (!session.outcomeSoundPlayed) {
					playCartelSummonOutcomeSound(level, session.lastTargetPosition, SoundEvents.PILLAGER_CELEBRATE);
					session.outcomeSoundPlayed = true;
				}
			}
			final LivingEntity chaseTarget = target;

			boolean timedOut = nowTick >= session.normalExpireTick
					|| (session.afterKillExpireTick != null && nowTick >= session.afterKillExpireTick);
			if (timedOut && chaseTarget != null && !session.outcomeSoundPlayed) {
				playCartelSummonOutcomeSound(level, chaseTarget.position(), SoundEvents.PILLAGER_HURT);
				session.outcomeSoundPlayed = true;
			}

			ServerPlayer owner = server.getPlayerList().getPlayer(session.ownerPlayerId);
			session.raiderIds.removeIf(raiderId -> {
				Entity entity = level.getEntity(raiderId);
				if (!(entity instanceof Raider raider) || !raider.isAlive()) {
					CARTEL_SUMMON_OWNER_BY_ENTITY.remove(raiderId);
					return true;
				}
				updateCartelRaiderArmor(owner, raider, session.armorDivider);

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

	private static Raider spawnCartelRaider(ServerLevel level, EntityType<? extends Raider> type, BlockPos anchor, LivingEntity target, ServerPlayer owner, double armorDivider) {
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
		clearCartelRaiderArmorEquipment(raider);
		updateCartelRaiderArmor(owner, raider, armorDivider);
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			raider.setDropChance(slot, 0.0F);
		}
		((MobXpRewardAccessor) (Object) raider).lg2$setXpReward(0);
		raider.addTag(CARTEL_SUMMON_TAG);
		raider.setTarget(target);

		if (!level.addFreshEntity(raider)) {
			return null;
		}

		CARTEL_SUMMON_OWNER_BY_ENTITY.put(raider.getUUID(), owner.getUUID());
		emitSmoke(level, raider.position());
		return raider;
	}

	private static void clearCartelRaiderArmorEquipment(Raider raider) {
		if (raider == null) {
			return;
		}
		raider.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
		raider.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
		raider.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
		raider.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
	}

	private static void updateCartelRaiderArmor(ServerPlayer owner, Raider raider, double armorDivider) {
		if (owner == null || raider == null) {
			return;
		}
		AttributeInstance armor = raider.getAttribute(Attributes.ARMOR);
		if (armor == null) {
			return;
		}
		double value = Math.max(0.0D, owner.getArmorValue() / Math.max(1.0D, armorDivider));
		if (Math.abs(armor.getBaseValue() - value) > 1.0E-4D) {
			armor.setBaseValue(value);
		}
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

	private static void emitWomanOmenParticles(ServerLevel level, Vec3 pos) {
		level.sendParticles(ParticleTypes.RAID_OMEN, pos.x, pos.y + 0.7D, pos.z, 16, 0.35D, 0.5D, 0.35D, 0.01D);
	}

	private static void playWomanUniqueActivationSound(ServerPlayer caster, ServerPlayer target) {
		if (caster == null || target == null) {
			return;
		}
		long seed = caster.level().getGameTime() ^ caster.getUUID().getLeastSignificantBits() ^ target.getUUID().getMostSignificantBits();
		sendPersonalSound(caster, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, caster.position(), 0.2F, 1.35F, seed);
		sendPersonalSound(target, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, target.position(), 0.2F, 1.35F, seed);
	}

	private static void playWomanPositionedSound(ServerLevel level, Vec3 origin, SoundEvent sound, float volume, float pitch) {
		if (level == null || origin == null || sound == null) {
			return;
		}
		double rangeSqr = WOMAN_SOUND_RANGE_BLOCKS * WOMAN_SOUND_RANGE_BLOCKS;
		long seed = level.getRandom().nextLong();
		Holder<SoundEvent> soundHolder = BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound);
		for (ServerPlayer viewer : level.players()) {
			if (viewer == null || viewer.connection == null || viewer.distanceToSqr(origin) > rangeSqr) {
				continue;
			}
			sendPositionedSound(viewer, soundHolder, SoundSource.PLAYERS, origin, volume, pitch, seed);
		}
	}

	private static void emitWomanDefenseOmenAura(ServerLevel level, LivingEntity woman, long gameTime) {
		if (level == null || woman == null) {
			return;
		}

		double radius = Math.max(0.34D, woman.getBbWidth() * 0.58D);
		double baseY = woman.getY() + 0.2D;
		double height = Math.max(0.7D, woman.getBbHeight() - 0.35D);
		double rotation = gameTime * 0.12D;
		int primaryIndex = (int) Math.floorMod(gameTime, 4L);
		emitWomanDefenseOmenAuraParticle(level, woman, baseY, height, radius, rotation, primaryIndex);
		if (Math.floorMod(gameTime, 3L) == 0L) {
			emitWomanDefenseOmenAuraParticle(level, woman, baseY, height, radius, rotation, (primaryIndex + 2) % 4);
		}
	}

	private static void emitWomanDefenseOmenAuraParticle(ServerLevel level, LivingEntity woman, double baseY, double height, double radius, double rotation, int index) {
		double angle = rotation + (Math.PI * 0.5D * index);
		double x = woman.getX() + Math.cos(angle) * radius;
		double z = woman.getZ() + Math.sin(angle) * radius;
		double y = baseY + height * (index % 2 == 0 ? 0.25D : 0.75D);
		level.sendParticles(ParticleTypes.RAID_OMEN, x, y, z, 0, 0.0D, 0.018D, 0.0D, 1.0D);
	}

	private static void sendPersonalSound(ServerPlayer player, SoundEvent sound, SoundSource source, Vec3 pos, float volume, float pitch, long seed) {
		if (player == null || player.connection == null || sound == null || source == null || pos == null) {
			return;
		}
		player.connection.send(new ClientboundSoundPacket(
				BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound),
				source,
				pos.x,
				pos.y,
				pos.z,
				volume,
				pitch,
				seed
		));
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
		COPPER_MAN_JETPACK_FLIGHT_MOMENTUM.remove(playerId);
		stopCopperManJetpackFlightSound(player);
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

	private static final class WomanShnyagaMenu extends ChestMenu {
		private final SimpleContainer container;
		private final ServerPlayer viewer;
		private final RaceAbilityConfig ability;
		private int selectedIndex;

		private WomanShnyagaMenu(int syncId, Inventory inventory, ServerPlayer viewer, RaceAbilityConfig ability, int selectedIndex) {
			this(syncId, inventory, new SimpleContainer(CARTEL_DISGUISE_MENU_ROWS * 9), viewer, ability, selectedIndex);
		}

		private WomanShnyagaMenu(
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

			List<ServerPlayer> candidates = collectWomanShnyagaCandidates(this.viewer);
			if (candidates.isEmpty()) {
				return;
			}

			this.selectedIndex = Math.floorMod(this.selectedIndex, candidates.size());
			int previousSlot = getWomanShnyagaPreviousSlot(this.viewer);
			int headSlot = getWomanShnyagaHeadSlot(this.viewer);
			int nextSlot = getWomanShnyagaNextSlot(this.viewer);
			if (slotId == previousSlot) {
				this.selectedIndex = Math.floorMod(this.selectedIndex - 1, candidates.size());
				openWomanShnyagaMenu(this.viewer, candidates, this.selectedIndex, this.ability);
				return;
			}
			if (slotId == nextSlot) {
				this.selectedIndex = Math.floorMod(this.selectedIndex + 1, candidates.size());
				openWomanShnyagaMenu(this.viewer, candidates, this.selectedIndex, this.ability);
				return;
			}
			if (slotId == headSlot) {
				beginWomanShnyagaPrompt(this.viewer, candidates.get(this.selectedIndex), this.ability);
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

			List<ServerPlayer> candidates = collectWomanShnyagaCandidates(this.viewer);
			int previousSlot = getWomanShnyagaPreviousSlot(this.viewer);
			int headSlot = getWomanShnyagaHeadSlot(this.viewer);
			int nextSlot = getWomanShnyagaNextSlot(this.viewer);
			if (candidates.isEmpty()) {
				this.container.setItem(headSlot, buildWomanShnyagaEmptyState(this.viewer));
				return;
			}

			this.selectedIndex = Math.floorMod(this.selectedIndex, candidates.size());
			this.container.setItem(previousSlot, buildWomanShnyagaArrow(this.viewer, false));
			this.container.setItem(headSlot, buildWomanShnyagaHead(this.viewer, candidates.get(this.selectedIndex)));
			this.container.setItem(nextSlot, buildWomanShnyagaArrow(this.viewer, true));
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
