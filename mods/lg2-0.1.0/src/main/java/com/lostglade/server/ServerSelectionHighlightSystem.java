package com.lostglade.server;

import com.mojang.math.Transformation;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ServerSelectionHighlightSystem {
	private static final float HIGHLIGHT_VIEW_RANGE = 1_000_000.0F;
	private static final Map<UUID, int[]> ACTIVE_ENTITY_IDS = new HashMap<>();

	private ServerSelectionHighlightSystem() {
	}

	public static void register() {
		ACTIVE_ENTITY_IDS.clear();
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> ACTIVE_ENTITY_IDS.remove(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> ACTIVE_ENTITY_IDS.clear());
	}

	public static void clear(ServerPlayer player) {
		if (player == null) {
			return;
		}
		int[] activeIds = ACTIVE_ENTITY_IDS.remove(player.getUUID());
		if (activeIds != null && activeIds.length > 0) {
			player.connection.send(new ClientboundRemoveEntitiesPacket(activeIds));
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
		for (DisplayBlueprint blueprint : blueprints) {
			Entity entity = createEntity(blueprint);
			if (entity == null) {
				continue;
			}
			spawnedIds.add(entity.getId());
			sendSpawnPackets(player, entity);
		}
		if (!spawnedIds.isEmpty()) {
			int[] ids = new int[spawnedIds.size()];
			for (int i = 0; i < spawnedIds.size(); i++) {
				ids[i] = spawnedIds.get(i);
			}
			ACTIVE_ENTITY_IDS.put(player.getUUID(), ids);
		}
	}

	private static Entity createEntity(DisplayBlueprint blueprint) {
		if (blueprint == null || blueprint.level() == null || blueprint.position() == null) {
			return null;
		}
		return switch (blueprint) {
			case BlockDisplayBlueprint blockBlueprint -> createBlockDisplay(blockBlueprint);
			case ItemDisplayBlueprint itemBlueprint -> createItemDisplay(itemBlueprint);
		};
	}

	private static Display.BlockDisplay createBlockDisplay(BlockDisplayBlueprint blueprint) {
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
		display.setInvisible(true);
		display.setGlowingTag(true);
		display.setViewRange(HIGHLIGHT_VIEW_RANGE);
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

	public sealed interface DisplayBlueprint permits BlockDisplayBlueprint, ItemDisplayBlueprint {
		ServerLevel level();

		Vec3 position();

		float yRot();

		float xRot();

		Transformation transformation();
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
