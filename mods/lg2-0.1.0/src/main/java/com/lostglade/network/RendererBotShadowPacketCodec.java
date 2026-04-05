package com.lostglade.network;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.BundlePacket;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundMoveMinecartPacket;
import net.minecraft.network.protocol.game.ClientboundProjectilePowerPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityLinkPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket;
import net.minecraft.network.protocol.game.GamePacketTypes;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class RendererBotShadowPacketCodec {
	private static final Map<Class<?>, StreamCodec<RegistryFriendlyByteBuf, ? extends Packet<?>>> CLASS_TO_CODEC = new HashMap<>();
	private static final Map<String, StreamCodec<RegistryFriendlyByteBuf, ? extends Packet<?>>> TYPE_ID_TO_CODEC = new HashMap<>();

	static {
		register(GamePacketTypes.CLIENTBOUND_ADD_ENTITY, ClientboundAddEntityPacket.class);
		register(GamePacketTypes.CLIENTBOUND_SET_ENTITY_DATA, ClientboundSetEntityDataPacket.class);
		register(GamePacketTypes.CLIENTBOUND_UPDATE_ATTRIBUTES, ClientboundUpdateAttributesPacket.class);
		register(GamePacketTypes.CLIENTBOUND_SET_EQUIPMENT, ClientboundSetEquipmentPacket.class);
		register(GamePacketTypes.CLIENTBOUND_SET_PASSENGERS, ClientboundSetPassengersPacket.class);
		register(GamePacketTypes.CLIENTBOUND_SET_ENTITY_LINK, ClientboundSetEntityLinkPacket.class);
		register(GamePacketTypes.CLIENTBOUND_ROTATE_HEAD, ClientboundRotateHeadPacket.class);
		register(GamePacketTypes.CLIENTBOUND_SET_ENTITY_MOTION, ClientboundSetEntityMotionPacket.class);
		register(GamePacketTypes.CLIENTBOUND_ENTITY_POSITION_SYNC, ClientboundEntityPositionSyncPacket.class);
		register(GamePacketTypes.CLIENTBOUND_TELEPORT_ENTITY, ClientboundTeleportEntityPacket.class);
		register(GamePacketTypes.CLIENTBOUND_MOVE_ENTITY_POS, ClientboundMoveEntityPacket.Pos.class);
		register(GamePacketTypes.CLIENTBOUND_MOVE_ENTITY_POS_ROT, ClientboundMoveEntityPacket.PosRot.class);
		register(GamePacketTypes.CLIENTBOUND_MOVE_ENTITY_ROT, ClientboundMoveEntityPacket.Rot.class);
		register(GamePacketTypes.CLIENTBOUND_REMOVE_ENTITIES, ClientboundRemoveEntitiesPacket.class);
		register(GamePacketTypes.CLIENTBOUND_ENTITY_EVENT, ClientboundEntityEventPacket.class);
		register(GamePacketTypes.CLIENTBOUND_PROJECTILE_POWER, ClientboundProjectilePowerPacket.class);
		register(GamePacketTypes.CLIENTBOUND_MOVE_MINECART_ALONG_TRACK, ClientboundMoveMinecartPacket.class);
		register(GamePacketTypes.CLIENTBOUND_LEVEL_CHUNK_WITH_LIGHT, ClientboundLevelChunkWithLightPacket.class);
		register(GamePacketTypes.CLIENTBOUND_FORGET_LEVEL_CHUNK, ClientboundForgetLevelChunkPacket.class);
	}

	private RendererBotShadowPacketCodec() {
	}

	public static RendererBotPayloads.ShadowPacketData encodePacket(RegistryAccess registryAccess, Packet<? extends ClientGamePacketListener> packet) {
		if (packet == null) {
			return null;
		}
		StreamCodec<RegistryFriendlyByteBuf, Packet<? extends ClientGamePacketListener>> codec = codecForPacket(packet);
		RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), registryAccess);
		try {
			codec.encode(buffer, packet);
			byte[] payload = new byte[buffer.readableBytes()];
			buffer.getBytes(buffer.readerIndex(), payload);
			return new RendererBotPayloads.ShadowPacketData(packet.type().id().toString(), payload);
		} finally {
			buffer.release();
		}
	}

	public static List<RendererBotPayloads.ShadowPacketData> encodePacketList(
			RegistryAccess registryAccess,
			Iterable<? extends Packet<? extends ClientGamePacketListener>> packets
	) {
		List<RendererBotPayloads.ShadowPacketData> encoded = new ArrayList<>();
		if (packets == null) {
			return encoded;
		}
		for (Packet<? extends ClientGamePacketListener> packet : packets) {
			flattenAndEncode(registryAccess, packet, encoded);
		}
		return encoded;
	}

	public static Packet<ClientGamePacketListener> decodePacket(
			RegistryAccess registryAccess,
			RendererBotPayloads.ShadowPacketData packetData
	) {
		if (packetData == null) {
			return null;
		}
		StreamCodec<RegistryFriendlyByteBuf, Packet<ClientGamePacketListener>> codec = codecForType(packetData.packetTypeId());
		RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(packetData.packetBytes()), registryAccess);
		try {
			return codec.decode(buffer);
		} finally {
			buffer.release();
		}
	}

	public static byte[] encodeChunkPacket(RegistryAccess registryAccess, ClientboundLevelChunkWithLightPacket packet) {
		RendererBotPayloads.ShadowPacketData encoded = encodePacket(registryAccess, packet);
		return encoded == null ? new byte[0] : encoded.packetBytes();
	}

	public static ClientboundLevelChunkWithLightPacket decodeChunkPacket(RegistryAccess registryAccess, byte[] packetBytes) {
		RendererBotPayloads.ShadowPacketData encoded = new RendererBotPayloads.ShadowPacketData(
				GamePacketTypes.CLIENTBOUND_LEVEL_CHUNK_WITH_LIGHT.id().toString(),
				packetBytes == null ? new byte[0] : packetBytes
		);
		Packet<ClientGamePacketListener> packet = decodePacket(registryAccess, encoded);
		if (!(packet instanceof ClientboundLevelChunkWithLightPacket chunkPacket)) {
			throw new IllegalStateException("Decoded shadow chunk packet has unexpected type: " + (packet == null ? "null" : packet.getClass().getName()));
		}
		return chunkPacket;
	}

	@SuppressWarnings("unchecked")
	private static StreamCodec<RegistryFriendlyByteBuf, Packet<? extends ClientGamePacketListener>> codecForPacket(Packet<? extends ClientGamePacketListener> packet) {
		StreamCodec<RegistryFriendlyByteBuf, ? extends Packet<?>> codec = CLASS_TO_CODEC.get(packet.getClass());
		if (codec == null) {
			throw new IllegalArgumentException("Unsupported renderer bot shadow packet class: " + packet.getClass().getName());
		}
		return (StreamCodec<RegistryFriendlyByteBuf, Packet<? extends ClientGamePacketListener>>) codec;
	}

	@SuppressWarnings("unchecked")
	private static StreamCodec<RegistryFriendlyByteBuf, Packet<ClientGamePacketListener>> codecForType(String packetTypeId) {
		StreamCodec<RegistryFriendlyByteBuf, ? extends Packet<?>> codec = TYPE_ID_TO_CODEC.get(packetTypeId);
		if (codec == null) {
			throw new IllegalArgumentException("Unsupported renderer bot shadow packet type: " + packetTypeId);
		}
		return (StreamCodec<RegistryFriendlyByteBuf, Packet<ClientGamePacketListener>>) codec;
	}

	private static void flattenAndEncode(
			RegistryAccess registryAccess,
			Packet<? extends ClientGamePacketListener> packet,
			List<RendererBotPayloads.ShadowPacketData> encoded
	) {
		if (packet == null || encoded == null) {
			return;
		}
		if (packet instanceof BundlePacket<?> bundlePacket) {
			for (Packet<?> child : bundlePacket.subPackets()) {
				@SuppressWarnings("unchecked")
				Packet<? extends ClientGamePacketListener> clientPacket = (Packet<? extends ClientGamePacketListener>) child;
				flattenAndEncode(registryAccess, clientPacket, encoded);
			}
			return;
		}
		RendererBotPayloads.ShadowPacketData encodedPacket = encodePacket(registryAccess, packet);
		if (encodedPacket != null) {
			encoded.add(encodedPacket);
		}
	}

	@SuppressWarnings("unchecked")
	private static void register(PacketType<?> packetType, Class<? extends Packet<?>> packetClass) {
		try {
			Field streamCodecField = packetClass.getDeclaredField("STREAM_CODEC");
			streamCodecField.setAccessible(true);
			StreamCodec<?, ?> streamCodec = (StreamCodec<?, ?>) streamCodecField.get(null);
			StreamCodec<RegistryFriendlyByteBuf, ? extends Packet<?>> typedCodec =
					(StreamCodec<RegistryFriendlyByteBuf, ? extends Packet<?>>) streamCodec;
			CLASS_TO_CODEC.put(packetClass, typedCodec);
			TYPE_ID_TO_CODEC.put(packetType.id().toString(), typedCodec);
		} catch (ReflectiveOperationException reflectiveOperationException) {
			throw new ExceptionInInitializerError(reflectiveOperationException);
		}
	}
}
