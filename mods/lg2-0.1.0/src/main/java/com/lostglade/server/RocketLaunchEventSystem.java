package com.lostglade.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.lostglade.Lg2;
import com.mojang.brigadier.Command;
import com.mojang.math.Transformation;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * A one-shot, redstone-driven launch event which turns a player-built rocket
 * into BlockDisplay entities and unlocks Yandex Maps GPS after the rocket has
 * left the visible world.
 */
public final class RocketLaunchEventSystem {
	private static final Gson STATE_GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final String STATE_FILE_NAME = "lg2-rocket-launch.json";
	private static final String DISPLAY_TAG = "lg2_rocket_launch_display";
	// Kept only to remove smoke ItemDisplays left by an older version after an update.
	private static final String EXHAUST_DISPLAY_TAG = "lg2_rocket_launch_exhaust";
	private static final Identifier ROCKET_SOUND_ID = Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "rocket_launch");
	private static final Holder<SoundEvent> ROCKET_SOUND = Holder.direct(SoundEvent.createVariableRangeEvent(ROCKET_SOUND_ID));

	private static final int MAX_SELECTION_VOLUME = 250_000;
	private static final int MAX_AUTO_SELECTION_FOOTPRINT = 4_096;
	private static final int MAX_ROCKET_BLOCKS = 1_200;
	private static final long PREHEAT_TICKS = 20L * 12L;
	private static final long ESCAPE_TICKS = 20L * 60L;
	private static final long TOTAL_LAUNCH_TICKS = PREHEAT_TICKS + ESCAPE_TICKS;
	/* Keeping the real entities inside the normal entity tracking range avoids
	 * clients dropping them. Beyond this point the rocket keeps shrinking, which
	 * matches the apparent angular size of a much more distant object. */
	private static final double MAX_PHYSICAL_ASCENT = 98.0D;
	private static final double MIN_VISIBLE_SCALE = 0.0125D;
	private static final float ROCKET_DISPLAY_VIEW_RANGE = 1_000_000.0F;
	private static final int DISPLAY_INTERPOLATION_TICKS = 2;
	private static final DustColorTransitionOptions SPACE_RACE_FLAME_WIDE = new DustColorTransitionOptions(0xFF8800, 0x701F1F, 4.0F);
	private static final DustColorTransitionOptions SPACE_RACE_FLAME_CORE = new DustColorTransitionOptions(0xFF8800, 0x701F1F, 3.0F);
	private static final double AUDIO_EMITTER_RADIUS = 44.0D;
	private static final double AUDIO_AUDIENCE_RADIUS = 190.0D;
	private static final float ROCKET_LAUNCH_SOUND_VOLUME = 20.0F;
	private static final Vec3[] AUDIO_EMITTER_DIRECTIONS = {
			new Vec3(1.0D, 0.0D, 0.0D),
			new Vec3(0.70710678118D, 0.0D, 0.70710678118D),
			new Vec3(0.0D, 0.0D, 1.0D),
			new Vec3(-0.70710678118D, 0.0D, 0.70710678118D),
			new Vec3(-1.0D, 0.0D, 0.0D),
			new Vec3(-0.70710678118D, 0.0D, -0.70710678118D),
			new Vec3(0.0D, 0.0D, -1.0D),
			new Vec3(0.70710678118D, 0.0D, -0.70710678118D)
	};

	private static final Map<UUID, AdminSelection> ADMIN_SELECTIONS = new HashMap<>();
	private static RocketState rocket;
	private static boolean stateLoaded;
	private static boolean stateDirty;

	private RocketLaunchEventSystem() {
	}

	public static void register() {
		ADMIN_SELECTIONS.clear();
		rocket = null;
		stateLoaded = false;
		stateDirty = false;

		ServerLifecycleEvents.SERVER_STARTED.register(RocketLaunchEventSystem::loadState);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			saveState(server);
			ADMIN_SELECTIONS.clear();
		});
		ServerTickEvents.END_SERVER_TICK.register(RocketLaunchEventSystem::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			ADMIN_SELECTIONS.remove(handler.player.getUUID());
			ServerSelectionHighlightSystem.clear(handler.player);
		});
		UseBlockCallback.EVENT.register(RocketLaunchEventSystem::onUseBlock);

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
				Commands.literal("rocket")
						.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
						.then(Commands.literal("select")
								.executes(context -> beginPointSelection(context.getSource()))
								.then(Commands.literal("done").executes(context -> finishPointSelection(context.getSource())))
								.then(Commands.literal("clear").executes(context -> clearPointSelection(context.getSource()))))
						.then(Commands.literal("preview").executes(context -> previewSelection(context.getSource())))
						.then(Commands.literal("arm").executes(context -> armRocket(context.getSource())))
						.then(Commands.literal("abort").executes(context -> abortRocket(context.getSource())))
						.then(Commands.literal("reset").executes(context -> resetEvent(context.getSource())))
						.then(Commands.literal("status").executes(context -> showStatus(context.getSource())))
		));
	}

	private static int beginPointSelection(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		AdminSelection selection = new AdminSelection();
		selection.collectingPoints = true;
		ADMIN_SELECTIONS.put(player.getUUID(), selection);
		ServerSelectionHighlightSystem.clear(player);
		source.sendSuccess(() -> Component.literal("Режим выбора ракеты включён. Правым кликом отметь 2+ нижних крайних блока ракеты с разных сторон; "
				+ "верх система найдёт сама."), false);
		return Command.SINGLE_SUCCESS;
	}

	private static int finishPointSelection(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		AdminSelection selection = ADMIN_SELECTIONS.get(player.getUUID());
		if (selection == null || selection.bottomPoints.size() < 2) {
			source.sendFailure(Component.literal("Нужно отметить хотя бы два нижних крайних блока ракеты."));
			return 0;
		}
		selection.collectingPoints = false;
		return previewSelection(player, selection) ? Command.SINGLE_SUCCESS : 0;
	}

	private static int clearPointSelection(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		ADMIN_SELECTIONS.remove(player.getUUID());
		ServerSelectionHighlightSystem.clear(player);
		source.sendSuccess(() -> Component.literal("Выбор ракеты очищен."), false);
		return Command.SINGLE_SUCCESS;
	}

	private static int previewSelection(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		AdminSelection selection = ADMIN_SELECTIONS.get(player.getUUID());
		if (selection == null || !selection.isComplete()) {
			source.sendFailure(Component.literal("Введи /rocket select и правым кликом отметь 2+ нижних крайних блока ракеты."));
			return 0;
		}
		return previewSelection(player, selection) ? Command.SINGLE_SUCCESS : 0;
	}

	private static InteractionResult onUseBlock(net.minecraft.world.entity.player.Player player, Level world,
				net.minecraft.world.InteractionHand hand, BlockHitResult hitResult) {
		if (world.isClientSide() || hand != net.minecraft.world.InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.PASS;
		}
		AdminSelection selection = ADMIN_SELECTIONS.get(serverPlayer.getUUID());
		if (selection == null || !selection.collectingPoints || hitResult == null) {
			return InteractionResult.PASS;
		}
		BlockPos point = hitResult.getBlockPos().immutable();
		if (world.getBlockState(point).isAir()) {
			serverPlayer.sendSystemMessage(Component.literal("Отмечай именно нижний блок ракеты, а не воздух."));
			return InteractionResult.FAIL;
		}
		if (selection.addBottomPoint(point)) {
			serverPlayer.sendSystemMessage(Component.literal("Нижняя точка " + selection.bottomPoints.size() + ": " + formatPos(point)));
		}
		if (selection.bottomPoints.size() >= 2) {
			previewSelection(serverPlayer, selection);
		} else {
			serverPlayer.sendSystemMessage(Component.literal("Отметь ещё хотя бы одну нижнюю точку с другой стороны ракеты."));
		}
		return InteractionResult.SUCCESS;
	}

	private static boolean previewSelection(ServerPlayer player, AdminSelection selection) {
		if (player == null || selection == null || !selection.isComplete() || !(player.level() instanceof ServerLevel level)) {
			return false;
		}
		SelectionBounds bounds = resolveAutomaticBounds(level, selection);
		if (bounds == null) {
			player.sendSystemMessage(Component.literal("Не удалось определить объём ракеты. Отметь нижние крайние блоки плотнее."));
			return false;
		}
		if (bounds.volume() > MAX_SELECTION_VOLUME) {
			player.sendSystemMessage(Component.literal("Объём выделения слишком большой (лимит " + MAX_SELECTION_VOLUME + " блоков)."));
			return false;
		}
		List<ServerSelectionHighlightSystem.DisplayBlueprint> blueprints = new ArrayList<>();
		for (BlockPos pos : BlockPos.betweenClosed(bounds.minX, bounds.minY, bounds.minZ, bounds.maxX, bounds.maxY, bounds.maxZ)) {
			BlockState blockState = level.getBlockState(pos);
			if (blockState.isAir()) {
				continue;
			}
			if (blueprints.size() >= MAX_ROCKET_BLOCKS) {
				player.sendSystemMessage(Component.literal("В ракете больше " + MAX_ROCKET_BLOCKS + " блоков; сузь выделение."));
				return false;
			}
			// This is the same invisible item-display carrier used by the Bluetooth
			// adapter.  It adds only the glow outline and never renders a second,
			// black copy of the selected block.
			blueprints.add(new ServerSelectionHighlightSystem.ItemDisplayBlueprint(
					level,
					Vec3.atCenterOf(pos),
					0.0F,
					0.0F,
					ServerSelectionHighlightSystem.createHighlightCarrierStack(),
					ItemDisplayContext.FIXED,
					ServerSelectionHighlightSystem.defaultHighlightCarrierTransformation()
			));
		}
		ServerSelectionHighlightSystem.show(player, blueprints);
		player.sendSystemMessage(Component.literal("Подсвечено блоков ракеты: " + blueprints.size()
				+ ". Ещё нижние точки можно добавить правым кликом; затем /rocket arm."));
		return true;
	}

	/**
	 * The administrator only marks the footprint at the bottom.  We take its
	 * horizontal extremes and scan upward to the highest non-air block inside
	 * this footprint, so no top coordinates have to be entered manually.
	 */
	private static SelectionBounds resolveAutomaticBounds(ServerLevel level, AdminSelection selection) {
		if (level == null || selection == null || selection.bottomPoints.size() < 2) {
			return null;
		}
		int minX = Integer.MAX_VALUE;
		int minY = Integer.MAX_VALUE;
		int minZ = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int maxZ = Integer.MIN_VALUE;
		for (BlockPos point : selection.bottomPoints) {
			minX = Math.min(minX, point.getX());
			minY = Math.min(minY, point.getY());
			minZ = Math.min(minZ, point.getZ());
			maxX = Math.max(maxX, point.getX());
			maxZ = Math.max(maxZ, point.getZ());
		}
		long footprint = (long) (maxX - minX + 1) * (maxZ - minZ + 1);
		if (footprint <= 0L || footprint > MAX_AUTO_SELECTION_FOOTPRINT) {
			return null;
		}
		long scanHeight = Math.max(1L, MAX_SELECTION_VOLUME / footprint);
		int scanTop = (int) Math.min((long) level.getMaxY() - 1L, (long) minY + scanHeight - 1L);
		int maxY = Integer.MIN_VALUE;
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		for (int y = minY; y <= scanTop; y++) {
			for (int z = minZ; z <= maxZ; z++) {
				for (int x = minX; x <= maxX; x++) {
					cursor.set(x, y, z);
					if (!level.getBlockState(cursor).isAir()) {
						maxY = y;
					}
				}
			}
		}
		if (maxY < minY) {
			return null;
		}
		SelectionBounds bounds = new SelectionBounds(minX, minY, minZ, maxX, maxY, maxZ);
		selection.automaticBounds = bounds;
		return bounds;
	}

	private static int armRocket(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		if (rocket != null && !LaunchStage.COMPLETED.name().equals(rocket.stage)) {
			source.sendFailure(Component.literal("Ракета уже взведена или находится в полёте. Используй /rocket status."));
			return 0;
		}
		ServerPlayer player = source.getPlayerOrException();
		if (!(player.level() instanceof ServerLevel level)) {
			return 0;
		}
		AdminSelection selection = ADMIN_SELECTIONS.get(player.getUUID());
		if (selection == null || !selection.isComplete()) {
			source.sendFailure(Component.literal("Сначала введи /rocket select и отметь 2+ нижних крайних блока ракеты."));
			return 0;
		}
		SelectionBounds bounds = resolveAutomaticBounds(level, selection);
		if (bounds == null) {
			source.sendFailure(Component.literal("Не удалось определить объём ракеты по отмеченным нижним точкам."));
			return 0;
		}
		if (bounds.volume() > MAX_SELECTION_VOLUME) {
			source.sendFailure(Component.literal("Объём выделения превышает лимит " + MAX_SELECTION_VOLUME + " блоков."));
			return 0;
		}

		List<PersistedRocketBlock> blocks = captureRocketBlocks(level, bounds, source);
		if (blocks == null) {
			return 0;
		}
		if (blocks.isEmpty()) {
			source.sendFailure(Component.literal("В выделении нет блоков для ракеты."));
			return 0;
		}

		RocketState next = new RocketState();
		next.stage = LaunchStage.ARMED.name();
		next.dimension = level.dimension().identifier().toString();
		next.minX = bounds.minX;
		next.minY = bounds.minY;
		next.minZ = bounds.minZ;
		next.maxX = bounds.maxX;
		next.maxY = bounds.maxY;
		next.maxZ = bounds.maxZ;
		next.lastTriggerPowered = isRocketPowered(level, blocks);
		next.blocks = blocks;

		for (PersistedRocketBlock block : blocks) {
			BlockState state = deserializeBlockState(block.state);
			Display.BlockDisplay display = createRocketDisplay(level, block, state);
			level.addFreshEntity(display);
			block.displayUuid = display.getUUID().toString();
		}
		for (PersistedRocketBlock block : blocks) {
			level.setBlock(block.pos(), Blocks.AIR.defaultBlockState(), 3);
		}

		rocket = next;
		stateLoaded = true;
		stateDirty = true;
		MonitorYandexMapsRuntime.setGpsEnabled(source.getServer(), false);
		saveState(source.getServer());
		selection.collectingPoints = false;
		ServerSelectionHighlightSystem.clear(player);
		source.sendSuccess(
				() -> Component.literal("Ракета взведена: " + blocks.size() + " блоков. Подай редстоун к любому её дисплею "
						+ "после снятия сигнала."),
				true
		);
		return Command.SINGLE_SUCCESS;
	}

	private static List<PersistedRocketBlock> captureRocketBlocks(ServerLevel level, SelectionBounds bounds, CommandSourceStack source) {
		List<PersistedRocketBlock> blocks = new ArrayList<>();
		for (BlockPos pos : BlockPos.betweenClosed(bounds.minX, bounds.minY, bounds.minZ, bounds.maxX, bounds.maxY, bounds.maxZ)) {
			BlockState state = level.getBlockState(pos);
			if (state.isAir()) {
				continue;
			}
			if (level.getBlockEntity(pos) != null) {
				source.sendFailure(Component.literal("Ракета не может содержать block entity: " + formatPos(pos)
						+ ". Убери сундук, табличку, баннер и т.п."));
				return null;
			}
			if (state.getBlock() instanceof FallingBlock) {
				source.sendFailure(Component.literal("Ракета не может содержать падающий блок: " + formatPos(pos) + "."));
				return null;
			}
			if (blocks.size() >= MAX_ROCKET_BLOCKS) {
				source.sendFailure(Component.literal("Лимит ракеты — " + MAX_ROCKET_BLOCKS + " блоков."));
				return null;
			}
			blocks.add(new PersistedRocketBlock(pos.getX(), pos.getY(), pos.getZ(), serializeBlockState(state)));
		}
		return blocks;
	}

	private static int abortRocket(CommandSourceStack source) {
		if (rocket == null || !LaunchStage.ARMED.name().equals(rocket.stage)) {
			source.sendFailure(Component.literal("Отменить можно только взведённую, ещё не запущенную ракету."));
			return 0;
		}
		ServerLevel level = rocketLevel(source.getServer());
		if (level == null) {
			source.sendFailure(Component.literal("Мир ракеты недоступен."));
			return 0;
		}
		for (PersistedRocketBlock block : rocket.blocks) {
			discardRocketDisplay(level, block);
			BlockPos pos = block.pos();
			if (level.getBlockState(pos).isAir()) {
				level.setBlock(pos, deserializeBlockState(block.state), 3);
			}
		}
		discardLegacyRocketExhaustDisplays(level);
		rocket = null;
		stateDirty = true;
		MonitorYandexMapsRuntime.setGpsEnabled(source.getServer(), false);
		saveState(source.getServer());
		source.sendSuccess(() -> Component.literal("Взведение отменено: блоки ракеты возвращены."), true);
		return Command.SINGLE_SUCCESS;
	}

	private static int resetEvent(CommandSourceStack source) {
		if (rocket != null && LaunchStage.ARMED.name().equals(rocket.stage)) {
			return abortRocket(source);
		}
		if (rocket != null) {
			ServerLevel level = rocketLevel(source.getServer());
			if (level != null) {
				for (PersistedRocketBlock block : rocket.blocks) {
					discardRocketDisplay(level, block);
				}
				discardLegacyRocketExhaustDisplays(level);
			}
		}
		rocket = null;
		stateDirty = true;
		MonitorYandexMapsRuntime.setGpsEnabled(source.getServer(), false);
		saveState(source.getServer());
		source.sendSuccess(() -> Component.literal("Ивент ракеты сброшен. GPS Яндекс-карт выключен."), true);
		return Command.SINGLE_SUCCESS;
	}

	private static int showStatus(CommandSourceStack source) {
		if (rocket == null) {
			source.sendSuccess(() -> Component.literal("Ракета не настроена. GPS Яндекс-карт выключен."), false);
			return Command.SINGLE_SUCCESS;
		}
		String stage = switch (LaunchStage.from(rocket.stage)) {
			case ARMED -> "взведена; ждёт редстоун у любого блока ракеты";
			case LAUNCHING -> "в полёте (" + Math.min(100, Math.round(rocket.launchElapsedTicks * 100.0D / TOTAL_LAUNCH_TICKS)) + "% )";
			case COMPLETED -> "успешно улетела; GPS включён";
		};
		source.sendSuccess(() -> Component.literal("Ракета: " + stage + ". Блоков: " + rocket.blocks.size()), false);
		return Command.SINGLE_SUCCESS;
	}

	private static void tick(MinecraftServer server) {
		if (!stateLoaded || rocket == null) {
			return;
		}
		ServerLevel level = rocketLevel(server);
		if (level == null) {
			return;
		}
		discardLegacyRocketExhaustDisplays(level);
		LaunchStage stage = LaunchStage.from(rocket.stage);
		if (stage == LaunchStage.ARMED) {
			boolean powered = isRocketPowered(level, rocket.blocks);
			if (powered && !rocket.lastTriggerPowered) {
				startLaunch(server, level);
				return;
			}
			rocket.lastTriggerPowered = powered;
			return;
		}
		if (stage != LaunchStage.LAUNCHING) {
			return;
		}

		rocket.launchElapsedTicks++;
		long elapsed = rocket.launchElapsedTicks;
		if (elapsed < PREHEAT_TICKS) {
			updateRocketDisplays(level, 0.0D, 1.0D, preheatShake(elapsed));
			emitRocketExhaustParticles(level, 0.0D, 1.0D, elapsed, true);
		} else {
			long flightTicks = elapsed - PREHEAT_TICKS;
			double virtualAscent = virtualRocketAscent(flightTicks);
			double physicalAscent = Math.min(MAX_PHYSICAL_ASCENT, virtualAscent);
			double scale = virtualAscent <= MAX_PHYSICAL_ASCENT
					? 1.0D
					: Math.max(MIN_VISIBLE_SCALE, MAX_PHYSICAL_ASCENT / virtualAscent);
			updateRocketDisplays(level, physicalAscent, scale, Vec3.ZERO);
			emitRocketExhaustParticles(level, physicalAscent, scale, elapsed, false);
		}

		if (elapsed % 20L == 0L) {
			stateDirty = true;
			saveState(server);
		}
		if (elapsed >= TOTAL_LAUNCH_TICKS) {
			completeLaunch(server, level);
		}
	}

	private static void startLaunch(MinecraftServer server, ServerLevel level) {
		if (!ensureRocketDisplays(level)) {
			Lg2.LOGGER.warn("Rocket launch at {} could not resolve every display entity; launch cancelled.", rocket.origin());
			return;
		}
		rocket.stage = LaunchStage.LAUNCHING.name();
		rocket.launchElapsedTicks = 0L;
		rocket.lastTriggerPowered = true;
		discardLegacyRocketExhaustDisplays(level);
		stateDirty = true;
		saveState(server);
		MonitorYandexMapsRuntime.setGpsEnabled(server, false);
		playSynchronizedLaunchSound(level, rocket.origin(), level.getRandom().nextLong());
		level.playSound(null, BlockPos.containing(rocket.origin()), SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 3.0F, 0.55F);
	}

	private static void completeLaunch(MinecraftServer server, ServerLevel level) {
		for (PersistedRocketBlock block : rocket.blocks) {
			discardRocketDisplay(level, block);
		}
		discardLegacyRocketExhaustDisplays(level);
		for (ServerPlayer player : level.players()) {
			if (player.connection != null) {
				player.connection.send(new ClientboundStopSoundPacket(ROCKET_SOUND_ID, SoundSource.BLOCKS));
			}
		}
		rocket.stage = LaunchStage.COMPLETED.name();
		rocket.launchElapsedTicks = TOTAL_LAUNCH_TICKS;
		stateDirty = true;
		MonitorYandexMapsRuntime.setGpsEnabled(server, true);
		saveState(server);
	}

	private static double virtualRocketAscent(long flightTicks) {
		double progress = Mth.clamp(flightTicks / (double) ESCAPE_TICKS, 0.0D, 1.0D);
		// Long, initially gentle ascent: the model reaches the tracking-safe
		// height only after roughly 23 seconds, then accelerates into space.
		return 2.0D * progress + 6_800.0D * progress * progress * progress * progress;
	}

	private static Vec3 preheatShake(long elapsed) {
		double ramp = Mth.clamp(elapsed / (double) PREHEAT_TICKS, 0.0D, 1.0D);
		double amplitude = 0.012D + ramp * 0.050D;
		// Never move sideways or down: those directions can push the bottom
		// BlockDisplays into a pad and make their shaded faces look black.
		double upwardBounce = Math.pow((Math.sin(elapsed * 1.91D) + 1.0D) * 0.5D, 2.0D) * amplitude;
		return new Vec3(0.0D, upwardBounce, 0.0D);
	}

	private static void updateRocketDisplays(ServerLevel level, double ascent, double scale, Vec3 shake) {
		Vec3 origin = rocket.origin();
		for (PersistedRocketBlock block : rocket.blocks) {
			Display.BlockDisplay display = resolveRocketDisplay(level, block);
			if (display == null) {
				continue;
			}
			double x = origin.x + (block.x - origin.x) * scale + shake.x;
			double y = origin.y + ascent + (block.y - origin.y) * scale + shake.y;
			double z = origin.z + (block.z - origin.z) * scale + shake.z;
			display.setPosRotInterpolationDuration(DISPLAY_INTERPOLATION_TICKS);
			display.setTransformationInterpolationDelay(0);
			display.setTransformationInterpolationDuration(DISPLAY_INTERPOLATION_TICKS);
			display.setTransformation(new Transformation(
					new Vector3f(),
					new Quaternionf(),
					new Vector3f((float) scale, (float) scale, (float) scale),
					new Quaternionf()
			));
			display.setPos(x, y, z);
		}
	}

	private static void emitRocketExhaustParticles(
			ServerLevel level,
			double ascent,
			double rocketScale,
			long elapsed,
			boolean preheating
	) {
		if (level == null || rocket == null || rocketScale < 0.05D) {
			return;
		}
		Vec3 origin = rocket.origin();
		Vec3 engine = origin.add(0.0D, ascent - 0.30D * rocketScale, 0.0D);
		// Same three large orange-to-red dust discs as Space Race.
		level.sendParticles(SPACE_RACE_FLAME_WIDE, engine.x, engine.y - 1.20D * rocketScale, engine.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
		level.sendParticles(SPACE_RACE_FLAME_WIDE, engine.x, engine.y - 1.70D * rocketScale, engine.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
		level.sendParticles(SPACE_RACE_FLAME_CORE, engine.x, engine.y - 0.20D * rocketScale, engine.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);

		if (elapsed % 2L != 0L) {
			return;
		}
		double warmup = preheating ? Mth.clamp(elapsed / (double) PREHEAT_TICKS, 0.0D, 1.0D) : 1.0D;
		boolean groundBurst = preheating || ascent < 3.0D;
		int engines = 8;
		int streamsPerEngine = 3 + (warmup > 0.55D ? 1 : 0);
		double footprintRadius = rocketFootprintRadius() * rocketScale;
		for (int engineIndex = 0; engineIndex < engines; engineIndex++) {
			double engineAngle = engineIndex * (Math.PI * 2.0D / engines) + Math.PI * 0.125D;
			for (int streamIndex = 0; streamIndex < streamsPerEngine; streamIndex++) {
				// Every engine emits a small fan, rather than a single point.  The fans
				// overlap into one continuous cloud without the gaps of the old ring.
				double fanOffset = (streamIndex - (streamsPerEngine - 1) * 0.5D) * 0.23D;
				double direction = engineAngle + fanOffset;
				double pulse = 0.86D + 0.14D * Math.sin(elapsed * 0.31D + engineIndex * 1.71D + streamIndex);
				if (groundBurst) {
					// While the pad is occupied, exhaust exits at the outside edge of
					// the base and travels horizontally across the ground.  That keeps
					// it out of both the rocket blocks and the floor.
					double outletRadius = footprintRadius + 0.18D + streamIndex * 0.16D;
					double horizontalSpeed = (0.020D + warmup * 0.030D) * pulse;
					level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
							origin.x + Math.cos(direction) * outletRadius,
							rocket.minY + 0.07D,
							origin.z + Math.sin(direction) * outletRadius,
							0, Math.cos(direction) * horizontalSpeed, 0.001D,
							Math.sin(direction) * horizontalSpeed, 1.0D);
				} else {
					// After lift-off the nozzles have free air below them.  Move the
					// source underneath the rocket and add a clear downward component.
					double nozzleRadius = Math.max(0.25D, footprintRadius * 0.48D);
					double horizontalSpeed = (0.028D + warmup * 0.035D) * pulse;
					level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
							origin.x + Math.cos(engineAngle) * nozzleRadius,
							engine.y - 0.56D * rocketScale,
							origin.z + Math.sin(engineAngle) * nozzleRadius,
							0, Math.cos(direction) * horizontalSpeed, -0.025D - warmup * 0.020D,
							Math.sin(direction) * horizontalSpeed, 1.0D);
				}
			}
		}
	}

	private static double rocketFootprintRadius() {
		if (rocket == null) {
			return 1.0D;
		}
		double halfX = (rocket.maxX - rocket.minX + 1) * 0.5D;
		double halfZ = (rocket.maxZ - rocket.minZ + 1) * 0.5D;
		return Math.sqrt(halfX * halfX + halfZ * halfZ);
	}

	private static void playSynchronizedLaunchSound(ServerLevel level, Vec3 origin, long seed) {
		if (level == null || origin == null) {
			return;
		}
		for (ServerPlayer player : level.players()) {
			if (player.connection == null || player.distanceToSqr(origin) > AUDIO_AUDIENCE_RADIUS * AUDIO_AUDIENCE_RADIUS) {
				continue;
			}
			Vec3 source = nearestVirtualEmitter(origin, player.position());
			Holder<SoundEvent> sound = PolymerResourcePackUtils.hasMainPack(player)
					? ROCKET_SOUND
					: BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.FIREWORK_ROCKET_LARGE_BLAST);
			player.connection.send(new ClientboundSoundPacket(
					sound,
					SoundSource.BLOCKS,
					source.x,
					source.y,
					source.z,
					ROCKET_LAUNCH_SOUND_VOLUME,
					1.0F,
					seed
			));
		}
	}

	private static Vec3 nearestVirtualEmitter(Vec3 origin, Vec3 listener) {
		Vec3 best = origin;
		double bestDistance = Double.MAX_VALUE;
		for (Vec3 direction : AUDIO_EMITTER_DIRECTIONS) {
			Vec3 candidate = origin.add(direction.x * AUDIO_EMITTER_RADIUS, 0.0D, direction.z * AUDIO_EMITTER_RADIUS);
			double distance = candidate.distanceToSqr(listener);
			if (distance < bestDistance) {
				bestDistance = distance;
				best = candidate;
			}
		}
		return best;
	}

	private static Display.BlockDisplay createRocketDisplay(ServerLevel level, PersistedRocketBlock block, BlockState state) {
		Display.BlockDisplay display = new Display.BlockDisplay(EntityType.BLOCK_DISPLAY, level);
		display.addTag(DISPLAY_TAG);
		display.setPos(block.x, block.y, block.z);
		display.setBlockState(state);
		display.setNoGravity(true);
		display.setInvulnerable(true);
		display.setSilent(true);
		display.setShadowRadius(0.0F);
		display.setShadowStrength(0.0F);
		display.setViewRange(ROCKET_DISPLAY_VIEW_RANGE);
		display.setPosRotInterpolationDuration(DISPLAY_INTERPOLATION_TICKS);
		display.setTransformationInterpolationDelay(0);
		display.setTransformationInterpolationDuration(DISPLAY_INTERPOLATION_TICKS);
		display.setTransformation(Transformation.identity());
		return display;
	}

	private static boolean ensureRocketDisplays(ServerLevel level) {
		boolean complete = true;
		for (PersistedRocketBlock block : rocket.blocks) {
			if (resolveRocketDisplay(level, block) != null) {
				continue;
			}
			// A restart before the launch can leave a selected chunk unloaded. Do not
			// create a duplicate in that case; the redstone source itself must be in a
			// loaded chunk, and the launch is retried on the next rising signal.
			if (!level.hasChunkAt(block.pos())) {
				complete = false;
				continue;
			}
			Display.BlockDisplay display = createRocketDisplay(level, block, deserializeBlockState(block.state));
			level.addFreshEntity(display);
			block.displayUuid = display.getUUID().toString();
			stateDirty = true;
		}
		return complete;
	}

	private static Display.BlockDisplay resolveRocketDisplay(ServerLevel level, PersistedRocketBlock block) {
		if (level == null || block == null || block.displayUuid == null || block.displayUuid.isBlank()) {
			return null;
		}
		try {
			Entity entity = level.getEntity(UUID.fromString(block.displayUuid));
			return entity instanceof Display.BlockDisplay display && display.getTags().contains(DISPLAY_TAG) ? display : null;
		} catch (IllegalArgumentException ignored) {
			return null;
		}
	}

	private static void discardRocketDisplay(ServerLevel level, PersistedRocketBlock block) {
		Display.BlockDisplay display = resolveRocketDisplay(level, block);
		if (display != null) {
			display.discard();
		}
	}

	private static void discardLegacyRocketExhaustDisplays(ServerLevel level) {
		if (level == null || rocket == null || rocket.exhaustDisplayUuids == null || rocket.exhaustDisplayUuids.isEmpty()) {
			return;
		}
		for (String rawUuid : rocket.exhaustDisplayUuids) {
			if (rawUuid == null || rawUuid.isBlank()) {
				continue;
			}
			try {
				Entity entity = level.getEntity(UUID.fromString(rawUuid));
				if (entity != null && entity.getTags().contains(EXHAUST_DISPLAY_TAG)) {
					entity.discard();
				}
			} catch (IllegalArgumentException ignored) {
				// Ignore malformed legacy state and clear it below.
			}
		}
		rocket.exhaustDisplayUuids.clear();
		stateDirty = true;
	}

	private static boolean isRocketPowered(ServerLevel level, List<PersistedRocketBlock> blocks) {
		if (level == null || blocks == null) {
			return false;
		}
		for (PersistedRocketBlock block : blocks) {
			if (block == null || !level.hasChunkAt(block.pos())) {
				continue;
			}
			BlockPos pos = block.pos();
			if (level.hasNeighborSignal(pos) || level.getBestNeighborSignal(pos) > 0 || level.getBlockState(pos).is(Blocks.REDSTONE_BLOCK)) {
				return true;
			}
		}
		return false;
	}

	private static ServerLevel rocketLevel(MinecraftServer server) {
		if (server == null || rocket == null || rocket.dimension == null) {
			return null;
		}
		Identifier dimensionId = Identifier.tryParse(rocket.dimension);
		return dimensionId == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, dimensionId));
	}

	private static void loadState(MinecraftServer server) {
		rocket = null;
		stateDirty = false;
		Path path = statePath(server);
		if (Files.exists(path)) {
			try (Reader reader = Files.newBufferedReader(path)) {
				RocketState loaded = STATE_GSON.fromJson(reader, RocketState.class);
				if (isValidState(loaded)) {
					rocket = loaded;
				} else {
					Lg2.LOGGER.warn("Ignoring invalid persisted rocket-launch state from {}", path);
				}
			} catch (Exception exception) {
				Lg2.LOGGER.warn("Failed to load rocket-launch state from {}", path, exception);
			}
		}
		stateLoaded = true;
		MonitorYandexMapsRuntime.setGpsEnabled(server, rocket != null && LaunchStage.COMPLETED.name().equals(rocket.stage));
	}

	private static void saveState(MinecraftServer server) {
		if (!stateLoaded || !stateDirty || server == null) {
			return;
		}
		Path path = statePath(server);
		try {
			Files.createDirectories(path.getParent());
			if (rocket == null) {
				Files.deleteIfExists(path);
			} else {
				try (Writer writer = Files.newBufferedWriter(path)) {
					STATE_GSON.toJson(rocket, writer);
				}
			}
			stateDirty = false;
		} catch (IOException exception) {
			Lg2.LOGGER.warn("Failed to save rocket-launch state to {}", path, exception);
		}
	}

	private static Path statePath(MinecraftServer server) {
		return server.getWorldPath(LevelResource.ROOT).resolve(STATE_FILE_NAME);
	}

	private static boolean isValidState(RocketState state) {
		if (state == null || LaunchStage.fromOrNull(state.stage) == null || state.dimension == null || Identifier.tryParse(state.dimension) == null) {
			return false;
		}
		if (state.blocks == null) {
			state.blocks = new ArrayList<>();
		}
		if (state.exhaustDisplayUuids == null) {
			state.exhaustDisplayUuids = new ArrayList<>();
		}
		return true;
	}

	private static String serializeBlockState(BlockState state) {
		if (state == null) {
			return "minecraft:air";
		}
		Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
		StringBuilder serialized = new StringBuilder(id == null ? "minecraft:air" : id.toString());
		if (state.getProperties().isEmpty()) {
			return serialized.toString();
		}
		serialized.append('[');
		boolean first = true;
		for (Property<?> property : state.getProperties()) {
			if (!first) {
				serialized.append(',');
			}
			first = false;
			serialized.append(property.getName()).append('=').append(propertyValueName(state, property));
		}
		return serialized.append(']').toString();
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private static String propertyValueName(BlockState state, Property<?> property) {
		Property rawProperty = property;
		return rawProperty.getName(state.getValue(rawProperty));
	}

	private static BlockState deserializeBlockState(String serialized) {
		if (serialized == null || serialized.isBlank()) {
			return Blocks.AIR.defaultBlockState();
		}
		int propertiesStart = serialized.indexOf('[');
		String rawId = propertiesStart < 0 ? serialized : serialized.substring(0, propertiesStart);
		Identifier id = Identifier.tryParse(rawId);
		if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) {
			return Blocks.AIR.defaultBlockState();
		}
		Block block = BuiltInRegistries.BLOCK.getValue(id);
		BlockState state = block.defaultBlockState();
		if (propertiesStart < 0 || !serialized.endsWith("]")) {
			return state;
		}
		String properties = serialized.substring(propertiesStart + 1, serialized.length() - 1);
		for (String propertyString : properties.split(",")) {
			int split = propertyString.indexOf('=');
			if (split <= 0 || split >= propertyString.length() - 1) {
				continue;
			}
			Property<?> property = block.getStateDefinition().getProperty(propertyString.substring(0, split));
			if (property != null) {
				state = applyProperty(state, property, propertyString.substring(split + 1));
			}
		}
		return state;
	}

	private static <T extends Comparable<T>> BlockState applyProperty(BlockState state, Property<T> property, String value) {
		return property.getValue(value).map(parsed -> state.setValue(property, parsed)).orElse(state);
	}

	private static String formatPos(BlockPos pos) {
		return pos == null ? "?" : pos.getX() + " " + pos.getY() + " " + pos.getZ();
	}

	private enum LaunchStage {
		ARMED,
		LAUNCHING,
		COMPLETED;

		private static LaunchStage from(String raw) {
			LaunchStage stage = fromOrNull(raw);
			return stage == null ? ARMED : stage;
		}

		private static LaunchStage fromOrNull(String raw) {
			if (raw == null) {
				return null;
			}
			try {
				return valueOf(raw.toUpperCase(Locale.ROOT));
			} catch (IllegalArgumentException ignored) {
				return null;
			}
		}
	}

	private static final class AdminSelection {
		private final List<BlockPos> bottomPoints = new ArrayList<>();
		private boolean collectingPoints;
		private SelectionBounds automaticBounds;

		private boolean isComplete() {
			return this.bottomPoints.size() >= 2;
		}

		private boolean addBottomPoint(BlockPos point) {
			if (point == null || this.bottomPoints.contains(point)) {
				return false;
			}
			this.bottomPoints.add(point.immutable());
			this.automaticBounds = null;
			return true;
		}
	}

	private record SelectionBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
		private long volume() {
			return (long) (this.maxX - this.minX + 1) * (this.maxY - this.minY + 1) * (this.maxZ - this.minZ + 1);
		}
	}

	private static final class RocketState {
		private String stage;
		private String dimension;
		private int minX;
		private int minY;
		private int minZ;
		private int maxX;
		private int maxY;
		private int maxZ;
		private boolean lastTriggerPowered;
		private long launchElapsedTicks;
		private List<PersistedRocketBlock> blocks = new ArrayList<>();
		private List<String> exhaustDisplayUuids = new ArrayList<>();

		private Vec3 origin() {
			return new Vec3((this.minX + this.maxX + 1) * 0.5D, this.minY, (this.minZ + this.maxZ + 1) * 0.5D);
		}
	}

	private static final class PersistedRocketBlock {
		private int x;
		private int y;
		private int z;
		private String state;
		private String displayUuid;

		private PersistedRocketBlock(int x, int y, int z, String state) {
			this.x = x;
			this.y = y;
			this.z = z;
			this.state = state;
		}

		private BlockPos pos() {
			return new BlockPos(this.x, this.y, this.z);
		}
	}
}
