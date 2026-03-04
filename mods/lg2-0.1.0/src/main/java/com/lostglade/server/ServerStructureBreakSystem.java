package com.lostglade.server;

import com.lostglade.block.ModBlocks;
import com.lostglade.item.ModItems;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class ServerStructureBreakSystem {
	public static final int STRUCTURE_HALF_WIDTH = 2;
	public static final int STRUCTURE_HALF_DEPTH = 1;
	public static final int STRUCTURE_HEIGHT = 3;

	private static final int BREAK_ANIMATION_TICKS = 20;
	private static final BlockParticleOption BREAK_PARTICLE =
			new BlockParticleOption(ParticleTypes.BLOCK, Blocks.IRON_BLOCK.defaultBlockState());

	private static final String DISPLAY_ROOT_TAG = "lg2_server_display";
	private static final String DISPLAY_ANCHOR_PREFIX = "lg2_anchor:";
	private static final String DISPLAY_AXIS_PREFIX = "lg2_axis:";

	private static final Map<StructureKey, ActiveBreakSession> ACTIVE_BREAKS = new HashMap<>();
	private static int nextCrackIdBase = 420_000;

	private ServerStructureBreakSystem() {
	}

	public static void register() {
		AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
			if (world.isClientSide()) {
				return InteractionResult.PASS;
			}
			if (!(player instanceof ServerPlayer serverPlayer)) {
				return InteractionResult.PASS;
			}
			if (serverPlayer.isSpectator() || hand != InteractionHand.MAIN_HAND) {
				return InteractionResult.PASS;
			}
			if (!serverPlayer.getMainHandItem().is(ModItems.SPECIAL_PICKAXE)) {
				return InteractionResult.PASS;
			}
			if (!(world instanceof ServerLevel level) || !level.getBlockState(pos).is(ModBlocks.SERVER)) {
				return InteractionResult.PASS;
			}

			Optional<ResolvedStructure> resolved = resolveStructure(level, pos);
			if (resolved.isEmpty()) {
				return InteractionResult.SUCCESS;
			}

			tryStartBreak(level, resolved.get());
			return InteractionResult.SUCCESS;
		});

		ServerTickEvents.END_SERVER_TICK.register(ServerStructureBreakSystem::tickBreakSessions);
	}

	public static List<BlockPos> getStructurePositions(BlockPos anchor, Direction.Axis axis) {
		List<BlockPos> positions = new ArrayList<>(45);

		for (int dy = 0; dy < STRUCTURE_HEIGHT; dy++) {
			for (int depth = -STRUCTURE_HALF_DEPTH; depth <= STRUCTURE_HALF_DEPTH; depth++) {
				for (int width = -STRUCTURE_HALF_WIDTH; width <= STRUCTURE_HALF_WIDTH; width++) {
					BlockPos pos = axis == Direction.Axis.X
							? anchor.offset(depth, dy, width)
							: anchor.offset(width, dy, depth);
					positions.add(pos);
				}
			}
		}

		return positions;
	}

	public static void applyStructureDisplayTags(Display.ItemDisplay display, BlockPos anchor, Direction.Axis axis) {
		display.addTag(DISPLAY_ROOT_TAG);
		display.addTag(DISPLAY_ANCHOR_PREFIX + anchor.getX() + "," + anchor.getY() + "," + anchor.getZ());
		display.addTag(DISPLAY_AXIS_PREFIX + (axis == Direction.Axis.X ? "x" : "z"));
	}

	private static void tryStartBreak(ServerLevel level, ResolvedStructure structure) {
		StructureKey key = new StructureKey(level.dimension(), structure.anchor());
		if (ACTIVE_BREAKS.containsKey(key)) {
			return;
		}

		int crackIdBase = allocateCrackIdBase(structure.positions().size());
		ACTIVE_BREAKS.put(key, new ActiveBreakSession(key, structure.positions(), crackIdBase, structure.axis()));
	}

	private static void tickBreakSessions(MinecraftServer server) {
		Iterator<Map.Entry<StructureKey, ActiveBreakSession>> iterator = ACTIVE_BREAKS.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<StructureKey, ActiveBreakSession> entry = iterator.next();
			ActiveBreakSession session = entry.getValue();
			ServerLevel level = server.getLevel(session.key().dimension());
			if (level == null) {
				iterator.remove();
				continue;
			}

			session.incrementAge();
			int stage = Math.min(9, (session.age() * 10) / BREAK_ANIMATION_TICKS);
			sendCrackStage(level, session.positions(), session.crackIdBase(), stage);
			sendFallbackBreakParticles(level, session.positions(), stage);

			if (session.age() < BREAK_ANIMATION_TICKS) {
				continue;
			}

			clearCracks(level, session.positions(), session.crackIdBase());
			removeStructureDisplays(level, session.key().anchor(), session.axis());
			destroyStructureAndDropCenter(level, session.key().anchor(), session.positions());
			iterator.remove();
		}
	}

	private static void sendCrackStage(ServerLevel level, List<BlockPos> positions, int crackIdBase, int stage) {
		for (int i = 0; i < positions.size(); i++) {
			level.destroyBlockProgress(crackIdBase + i, positions.get(i), stage);
		}
	}

	private static void clearCracks(ServerLevel level, List<BlockPos> positions, int crackIdBase) {
		for (int i = 0; i < positions.size(); i++) {
			level.destroyBlockProgress(crackIdBase + i, positions.get(i), -1);
		}
	}

	private static void sendFallbackBreakParticles(ServerLevel level, List<BlockPos> positions, int stage) {
		if ((stage & 1) != 0) {
			return;
		}

		for (BlockPos pos : positions) {
			level.sendParticles(
					BREAK_PARTICLE,
					pos.getX() + 0.5D,
					pos.getY() + 0.5D,
					pos.getZ() + 0.5D,
					2,
					0.25D,
					0.25D,
					0.25D,
					0.01D
			);
		}
	}

	private static void destroyStructureAndDropCenter(ServerLevel level, BlockPos anchor, List<BlockPos> positions) {
		for (BlockPos pos : positions) {
			if (level.getBlockState(pos).is(ModBlocks.SERVER)) {
				level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
			}
		}

		level.playSound(
				null,
				anchor,
				SoundEvents.ENDER_DRAGON_DEATH,
				SoundSource.AMBIENT,
				1.2F,
				1.0F
		);

		ItemEntity drop = new ItemEntity(
				level,
				anchor.getX() + 0.5D,
				anchor.getY() + 0.5D,
				anchor.getZ() + 0.5D,
				new ItemStack(ModBlocks.SERVER_ITEM)
		);
		drop.setDefaultPickUpDelay();
		level.addFreshEntity(drop);
	}

	private static Optional<ResolvedStructure> resolveStructure(ServerLevel level, BlockPos hitPos) {
		Optional<ResolvedStructure> byDisplay = resolveByDisplayTags(level, hitPos);
		if (byDisplay.isPresent()) {
			return byDisplay;
		}

		return resolveByGeometryFallback(level, hitPos);
	}

	private static Optional<ResolvedStructure> resolveByDisplayTags(ServerLevel level, BlockPos hitPos) {
		AABB searchBox = new AABB(hitPos).inflate(8.0D, 6.0D, 8.0D);
		List<Display.ItemDisplay> displays = level.getEntities(
				EntityType.ITEM_DISPLAY,
				searchBox,
				display -> display.getTags().contains(DISPLAY_ROOT_TAG)
		);

		for (Display.ItemDisplay display : displays) {
			Optional<BlockPos> anchor = parseAnchorTag(display);
			Optional<Direction.Axis> axis = parseAxisTag(display);
			if (anchor.isEmpty() || axis.isEmpty()) {
				continue;
			}

			List<BlockPos> positions = getStructurePositions(anchor.get(), axis.get());
			if (!positions.contains(hitPos) || !isWholeStructurePresent(level, positions)) {
				continue;
			}

			return Optional.of(new ResolvedStructure(anchor.get(), axis.get(), positions));
		}

		return Optional.empty();
	}

	private static Optional<ResolvedStructure> resolveByGeometryFallback(ServerLevel level, BlockPos hitPos) {
		Set<CandidateAnchor> checkedAnchors = new HashSet<>();
		for (Direction.Axis axis : List.of(Direction.Axis.Z, Direction.Axis.X)) {
			for (int dy = 0; dy < STRUCTURE_HEIGHT; dy++) {
				for (int depth = -STRUCTURE_HALF_DEPTH; depth <= STRUCTURE_HALF_DEPTH; depth++) {
					for (int width = -STRUCTURE_HALF_WIDTH; width <= STRUCTURE_HALF_WIDTH; width++) {
						BlockPos anchor = axis == Direction.Axis.X
								? hitPos.offset(-depth, -dy, -width)
								: hitPos.offset(-width, -dy, -depth);

						if (!checkedAnchors.add(new CandidateAnchor(anchor, axis))) {
							continue;
						}

						List<BlockPos> positions = getStructurePositions(anchor, axis);
						if (positions.contains(hitPos) && isWholeStructurePresent(level, positions)) {
							return Optional.of(new ResolvedStructure(anchor, axis, positions));
						}
					}
				}
			}
		}

		return Optional.empty();
	}

	private static boolean isWholeStructurePresent(ServerLevel level, List<BlockPos> positions) {
		for (BlockPos pos : positions) {
			if (!level.getBlockState(pos).is(ModBlocks.SERVER)) {
				return false;
			}
		}
		return true;
	}

	private static void removeStructureDisplays(ServerLevel level, BlockPos anchor, Direction.Axis axis) {
		AABB searchBox = new AABB(anchor).inflate(8.0D, 6.0D, 8.0D);
		List<Display.ItemDisplay> displays = level.getEntities(
				EntityType.ITEM_DISPLAY,
				searchBox,
				display -> display.getTags().contains(DISPLAY_ROOT_TAG)
		);

		for (Display.ItemDisplay itemDisplay : displays) {
			Optional<BlockPos> taggedAnchor = parseAnchorTag(itemDisplay);
			Optional<Direction.Axis> taggedAxis = parseAxisTag(itemDisplay);
			if (taggedAnchor.isPresent() && taggedAxis.isPresent()
					&& taggedAnchor.get().equals(anchor)
					&& taggedAxis.get() == axis) {
				itemDisplay.discard();
			}
		}
	}

	private static Optional<BlockPos> parseAnchorTag(Entity display) {
		for (String tag : display.getTags()) {
			if (!tag.startsWith(DISPLAY_ANCHOR_PREFIX)) {
				continue;
			}

			String payload = tag.substring(DISPLAY_ANCHOR_PREFIX.length());
			String[] parts = payload.split(",");
			if (parts.length != 3) {
				return Optional.empty();
			}

			try {
				int x = Integer.parseInt(parts[0]);
				int y = Integer.parseInt(parts[1]);
				int z = Integer.parseInt(parts[2]);
				return Optional.of(new BlockPos(x, y, z));
			} catch (NumberFormatException ignored) {
				return Optional.empty();
			}
		}

		return Optional.empty();
	}

	private static Optional<Direction.Axis> parseAxisTag(Entity display) {
		for (String tag : display.getTags()) {
			if (!tag.startsWith(DISPLAY_AXIS_PREFIX)) {
				continue;
			}

			String payload = tag.substring(DISPLAY_AXIS_PREFIX.length());
			if ("x".equals(payload)) {
				return Optional.of(Direction.Axis.X);
			}
			if ("z".equals(payload)) {
				return Optional.of(Direction.Axis.Z);
			}
			return Optional.empty();
		}

		return Optional.empty();
	}

	private static int allocateCrackIdBase(int expectedPositions) {
		int base = nextCrackIdBase;
		nextCrackIdBase += Math.max(1, expectedPositions + 1);
		if (nextCrackIdBase > Integer.MAX_VALUE - 10_000) {
			nextCrackIdBase = 420_000;
		}
		return base;
	}

	private record StructureKey(ResourceKey<Level> dimension, BlockPos anchor) {
	}

	private record CandidateAnchor(BlockPos anchor, Direction.Axis axis) {
	}

	private record ResolvedStructure(BlockPos anchor, Direction.Axis axis, List<BlockPos> positions) {
	}

	private static final class ActiveBreakSession {
		private final StructureKey key;
		private final List<BlockPos> positions;
		private final int crackIdBase;
		private final Direction.Axis axis;
		private int ageTicks;

		private ActiveBreakSession(StructureKey key, List<BlockPos> positions, int crackIdBase, Direction.Axis axis) {
			this.key = key;
			this.positions = positions;
			this.crackIdBase = crackIdBase;
			this.axis = axis;
			this.ageTicks = 0;
		}

		private void incrementAge() {
			this.ageTicks++;
		}

		private int age() {
			return this.ageTicks;
		}

		private StructureKey key() {
			return this.key;
		}

		private List<BlockPos> positions() {
			return this.positions;
		}

		private int crackIdBase() {
			return this.crackIdBase;
		}

		private Direction.Axis axis() {
			return this.axis;
		}
	}
}
