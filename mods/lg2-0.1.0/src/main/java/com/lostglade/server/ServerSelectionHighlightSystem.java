package com.lostglade.server;

import com.lostglade.Lg2;
import com.lostglade.mixin.EntityTrackedDataAccessor;
import com.mojang.math.Transformation;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ServerSelectionHighlightSystem {
	private static final float HIGHLIGHT_VIEW_RANGE = 1_000_000.0F;
	private static final byte GLOWING_FLAG_MASK = 0x40;
	private static final float DEFAULT_HIGHLIGHT_CARRIER_SCALE = 2.0F;
	private static final Identifier HIGHLIGHT_CARRIER_ITEM_MODEL_ID = Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "ore_highlight_carrier");
	private static final Map<UUID, int[]> ACTIVE_ENTITY_IDS = new HashMap<>();
	private static final Map<UUID, List<GlowingEntityState>> ACTIVE_GLOWING_ENTITIES = new HashMap<>();

	private ServerSelectionHighlightSystem() {
	}

	public static void register() {
		ACTIVE_ENTITY_IDS.clear();
		ACTIVE_GLOWING_ENTITIES.clear();
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			ACTIVE_ENTITY_IDS.remove(handler.player.getUUID());
			ACTIVE_GLOWING_ENTITIES.remove(handler.player.getUUID());
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			ACTIVE_ENTITY_IDS.clear();
			ACTIVE_GLOWING_ENTITIES.clear();
		});
	}

	public static void clear(ServerPlayer player) {
		if (player == null) {
			return;
		}
		int[] activeIds = ACTIVE_ENTITY_IDS.remove(player.getUUID());
		if (activeIds != null && activeIds.length > 0) {
			player.connection.send(new ClientboundRemoveEntitiesPacket(activeIds));
		}
		List<GlowingEntityState> activeGlows = ACTIVE_GLOWING_ENTITIES.remove(player.getUUID());
		if (activeGlows != null && !activeGlows.isEmpty()) {
			for (GlowingEntityState state : activeGlows) {
				restoreEntityGlow(player, state);
			}
		}
	}

	public static void show(ServerPlayer player, List<DisplayBlueprint> blueprints) {
		if (player == null) {
			return;
		}
		clear(player);
		if (blueprints == null || blueprints.isEmpty()) {
			return;
		}
		List<Integer> spawnedIds = new ArrayList<>();
		List<GlowingEntityState> glowingEntities = new ArrayList<>();
		for (DisplayBlueprint blueprint : blueprints) {
			if (blueprint instanceof EntityGlowBlueprint glowBlueprint) {
				GlowingEntityState state = applyEntityGlow(player, glowBlueprint.entity());
				if (state != null) {
					glowingEntities.add(state);
				}
				continue;
			}
			Entity entity = createEntity(blueprint);
			if (entity != null) {
				spawnedIds.add(entity.getId());
				sendSpawnPackets(player, entity);
			}
		}
		if (!spawnedIds.isEmpty()) {
			int[] ids = new int[spawnedIds.size()];
			for (int i = 0; i < spawnedIds.size(); i++) {
				ids[i] = spawnedIds.get(i);
			}
			ACTIVE_ENTITY_IDS.put(player.getUUID(), ids);
		}
		if (!glowingEntities.isEmpty()) {
			ACTIVE_GLOWING_ENTITIES.put(player.getUUID(), List.copyOf(glowingEntities));
		}
	}

	private static Entity createEntity(DisplayBlueprint blueprint) {
		if (blueprint == null) {
			return null;
		}
		return switch (blueprint) {
			case BlockDisplayBlueprint blockBlueprint -> createBlockDisplay(blockBlueprint);
			case ItemDisplayBlueprint itemBlueprint -> createItemDisplay(itemBlueprint);
			case EntityGlowBlueprint ignored -> null;
		};
	}

	private static Display.BlockDisplay createBlockDisplay(BlockDisplayBlueprint blueprint) {
		if (blueprint.level() == null || blueprint.position() == null) {
			return null;
		}
		BlockState blockState = blueprint.blockState();
		if (blockState == null) {
			return null;
		}
		Display.BlockDisplay display = new Display.BlockDisplay(EntityType.BLOCK_DISPLAY, blueprint.level());
		configureDisplayEntity(display, blueprint.position(), blueprint.yRot(), blueprint.xRot(), blueprint.transformation());
		display.setBlockState(blockState);
		return display;
	}

	private static Display.ItemDisplay createItemDisplay(ItemDisplayBlueprint blueprint) {
		if (blueprint.level() == null || blueprint.position() == null) {
			return null;
		}
		ItemStack stack = blueprint.itemStack();
		if (stack == null || stack.isEmpty()) {
			return null;
		}
		Display.ItemDisplay display = new Display.ItemDisplay(EntityType.ITEM_DISPLAY, blueprint.level());
		configureDisplayEntity(display, blueprint.position(), blueprint.yRot(), blueprint.xRot(), blueprint.transformation());
		display.setItemStack(stack.copy());
		display.setItemTransform(blueprint.displayContext());
		display.setBillboardConstraints(Display.BillboardConstraints.FIXED);
		return display;
	}

	private static void configureDisplayEntity(Display display, Vec3 position, float yRot, float xRot, Transformation transformation) {
		display.setPos(position.x, position.y, position.z);
		display.setYRot(yRot);
		display.setXRot(xRot);
		display.setYHeadRot(yRot);
		display.setYBodyRot(yRot);
		display.setTransformation(transformation == null ? Transformation.identity() : transformation);
		display.setNoGravity(true);
		display.setInvulnerable(true);
		display.setSilent(true);
		display.setShadowRadius(0.0F);
		display.setShadowStrength(0.0F);
		display.setInvisible(false);
		display.setGlowingTag(true);
		display.setViewRange(HIGHLIGHT_VIEW_RANGE);
	}

	private static GlowingEntityState applyEntityGlow(ServerPlayer viewer, Entity entity) {
		if (viewer == null || entity == null || !entity.isAlive()) {
			return null;
		}
		if (!(entity.level() instanceof ServerLevel level)) {
			return null;
		}
		EntityDataAccessor<Byte> accessor = EntityTrackedDataAccessor.lg2$getDataSharedFlagsId();
		byte currentFlags = entity.getEntityData().get(accessor);
		boolean wasGlowing = (currentFlags & GLOWING_FLAG_MASK) != 0;
		byte updatedFlags = (byte) (currentFlags | GLOWING_FLAG_MASK);
		viewer.connection.send(new ClientboundSetEntityDataPacket(
				entity.getId(),
				List.of(SynchedEntityData.DataValue.create(accessor, updatedFlags))
		));
		return new GlowingEntityState(level.dimension(), entity.getUUID(), wasGlowing);
	}

	private static void restoreEntityGlow(ServerPlayer viewer, GlowingEntityState state) {
		if (viewer == null || state == null || viewer.level() == null || viewer.level().getServer() == null) {
			return;
		}
		ServerLevel level = viewer.level().getServer().getLevel(state.dimension());
		if (level == null) {
			return;
		}
		Entity entity = level.getEntity(state.entityUuid());
		if (entity == null || !entity.isAlive()) {
			return;
		}
		EntityDataAccessor<Byte> accessor = EntityTrackedDataAccessor.lg2$getDataSharedFlagsId();
		byte currentFlags = entity.getEntityData().get(accessor);
		byte restoredFlags = state.wasGlowing()
				? (byte) (currentFlags | GLOWING_FLAG_MASK)
				: (byte) (currentFlags & ~GLOWING_FLAG_MASK);
		viewer.connection.send(new ClientboundSetEntityDataPacket(
				entity.getId(),
				List.of(SynchedEntityData.DataValue.create(accessor, restoredFlags))
		));
	}

	public static ItemStack createHighlightCarrierStack() {
		ItemStack stack = new ItemStack(Items.PAPER);
		stack.set(DataComponents.ITEM_MODEL, HIGHLIGHT_CARRIER_ITEM_MODEL_ID);
		return stack;
	}

	public static Transformation defaultHighlightCarrierTransformation() {
		return new Transformation(
				new Vector3f(0.0F, 0.0F, 0.0F),
				new Quaternionf(),
				new Vector3f(DEFAULT_HIGHLIGHT_CARRIER_SCALE, DEFAULT_HIGHLIGHT_CARRIER_SCALE, DEFAULT_HIGHLIGHT_CARRIER_SCALE),
				new Quaternionf()
		);
	}

	@SuppressWarnings("unchecked")
	private static void sendSpawnPackets(ServerPlayer player, Entity entity) {
		ServerEntity tracker = new ServerEntity(
				(ServerLevel) entity.level(),
				entity,
				1,
				false,
				NOOP_SYNCHRONIZER
		);
		tracker.sendPairingData(player, packet -> player.connection.send((Packet<? super ClientGamePacketListener>) packet));
		List<SynchedEntityData.DataValue<?>> values = entity.getEntityData().getNonDefaultValues();
		if (values != null && !values.isEmpty()) {
			player.connection.send(new ClientboundSetEntityDataPacket(entity.getId(), values));
		}
	}

	public sealed interface DisplayBlueprint permits BlockDisplayBlueprint, ItemDisplayBlueprint, EntityGlowBlueprint {
	}

	public record BlockDisplayBlueprint(
			ServerLevel level,
			Vec3 position,
			float yRot,
			float xRot,
			BlockState blockState,
			Transformation transformation
	) implements DisplayBlueprint {
	}

	public record ItemDisplayBlueprint(
			ServerLevel level,
			Vec3 position,
			float yRot,
			float xRot,
			ItemStack itemStack,
			ItemDisplayContext displayContext,
			Transformation transformation
	) implements DisplayBlueprint {
	}

	public record EntityGlowBlueprint(Entity entity) implements DisplayBlueprint {
	}

	private record GlowingEntityState(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension, UUID entityUuid, boolean wasGlowing) {
	}

	private static final ServerEntity.Synchronizer NOOP_SYNCHRONIZER = new ServerEntity.Synchronizer() {
		@Override
		public void sendToTrackingPlayers(Packet<? super ClientGamePacketListener> packet) {
		}

		@Override
		public void sendToTrackingPlayersAndSelf(Packet<? super ClientGamePacketListener> packet) {
		}

		@Override
		public void sendToTrackingPlayersFiltered(Packet<? super ClientGamePacketListener> packet, java.util.function.Predicate<ServerPlayer> predicate) {
		}
	};
}
