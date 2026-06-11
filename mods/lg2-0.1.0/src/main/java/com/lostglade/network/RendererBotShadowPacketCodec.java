package com.lostglade.network;

import com.lostglade.Lg2;
import eu.pb4.polymer.core.impl.networking.PacketPatcher;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.BundlePacket;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket;
import net.minecraft.network.protocol.game.ClientboundBlockEventPacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundMoveMinecartPacket;
import net.minecraft.network.protocol.game.ClientboundProjectilePowerPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import net.minecraft.network.protocol.game.ClientboundHurtAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityLinkPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket;
import net.minecraft.network.protocol.game.GamePacketTypes;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import xyz.nucleoid.packettweaker.PacketContext;

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
		register(GamePacketTypes.CLIENTBOUND_ANIMATE, ClientboundAnimatePacket.class);
		register(GamePacketTypes.CLIENTBOUND_BLOCK_DESTRUCTION, ClientboundBlockDestructionPacket.class);
		register(GamePacketTypes.CLIENTBOUND_BLOCK_EVENT, ClientboundBlockEventPacket.class);
		register(GamePacketTypes.CLIENTBOUND_DAMAGE_EVENT, ClientboundDamageEventPacket.class);
		register(GamePacketTypes.CLIENTBOUND_SET_ENTITY_DATA, ClientboundSetEntityDataPacket.class);
		register(GamePacketTypes.CLIENTBOUND_UPDATE_ATTRIBUTES, ClientboundUpdateAttributesPacket.class);
		register(GamePacketTypes.CLIENTBOUND_SET_EQUIPMENT, ClientboundSetEquipmentPacket.class);
		register(GamePacketTypes.CLIENTBOUND_SET_PASSENGERS, ClientboundSetPassengersPacket.class);
		register(GamePacketTypes.CLIENTBOUND_SET_ENTITY_LINK, ClientboundSetEntityLinkPacket.class);
		register(GamePacketTypes.CLIENTBOUND_ROTATE_HEAD, ClientboundRotateHeadPacket.class);
		register(GamePacketTypes.CLIENTBOUND_SET_ENTITY_MOTION, ClientboundSetEntityMotionPacket.class);
		register(GamePacketTypes.CLIENTBOUND_ENTITY_POSITION_SYNC, ClientboundEntityPositionSyncPacket.class);
		register(GamePacketTypes.CLIENTBOUND_TELEPORT_ENTITY, ClientboundTeleportEntityPacket.class);
		register(GamePacketTypes.CLIENTBOUND_HURT_ANIMATION, ClientboundHurtAnimationPacket.class);
		register(GamePacketTypes.CLIENTBOUND_LEVEL_EVENT, ClientboundLevelEventPacket.class);
		register(GamePacketTypes.CLIENTBOUND_LEVEL_PARTICLES, ClientboundLevelParticlesPacket.class);
		register(GamePacketTypes.CLIENTBOUND_MOVE_ENTITY_POS, ClientboundMoveEntityPacket.Pos.class);
		register(GamePacketTypes.CLIENTBOUND_MOVE_ENTITY_POS_ROT, ClientboundMoveEntityPacket.PosRot.class);
		register(GamePacketTypes.CLIENTBOUND_MOVE_ENTITY_ROT, ClientboundMoveEntityPacket.Rot.class);
		register(GamePacketTypes.CLIENTBOUND_REMOVE_ENTITIES, ClientboundRemoveEntitiesPacket.class);
		register(GamePacketTypes.CLIENTBOUND_ENTITY_EVENT, ClientboundEntityEventPacket.class);
		register(GamePacketTypes.CLIENTBOUND_PROJECTILE_POWER, ClientboundProjectilePowerPacket.class);
		register(GamePacketTypes.CLIENTBOUND_MOVE_MINECART_ALONG_TRACK, ClientboundMoveMinecartPacket.class);
		register(GamePacketTypes.CLIENTBOUND_SET_PLAYER_TEAM, ClientboundSetPlayerTeamPacket.class);
		register(GamePacketTypes.CLIENTBOUND_PLAYER_INFO_UPDATE, ClientboundPlayerInfoUpdatePacket.class);
		register(GamePacketTypes.CLIENTBOUND_PLAYER_INFO_REMOVE, ClientboundPlayerInfoRemovePacket.class);
		register(GamePacketTypes.CLIENTBOUND_SOUND, ClientboundSoundPacket.class);
		register(GamePacketTypes.CLIENTBOUND_SOUND_ENTITY, ClientboundSoundEntityPacket.class);
		register(GamePacketTypes.CLIENTBOUND_STOP_SOUND, ClientboundStopSoundPacket.class);
		register(GamePacketTypes.CLIENTBOUND_LEVEL_CHUNK_WITH_LIGHT, ClientboundLevelChunkWithLightPacket.class);
		register(GamePacketTypes.CLIENTBOUND_FORGET_LEVEL_CHUNK, ClientboundForgetLevelChunkPacket.class);
	}

	private RendererBotShadowPacketCodec() {
	}

	public static RendererBotPayloads.ShadowPacketData encodePacket(
			RegistryAccess registryAccess,
			ServerCommonPacketListenerImpl packetListener,
			Packet<? extends ClientGamePacketListener> packet
	) {
		if (packet == null) {
			return null;
		}
		Packet<? extends ClientGamePacketListener> patchedPacket = patchPacket(packetListener, packet);
		if (patchedPacket == null) {
			return null;
		}
		return encodePatchedPacket(registryAccess, packetListener, patchedPacket);
	}

	public static List<RendererBotPayloads.ShadowPacketData> encodePacketList(
			RegistryAccess registryAccess,
			ServerCommonPacketListenerImpl packetListener,
			Iterable<? extends Packet<? extends ClientGamePacketListener>> packets
	) {
		List<RendererBotPayloads.ShadowPacketData> encoded = new ArrayList<>();
		if (packets == null) {
			return encoded;
		}
		for (Packet<? extends ClientGamePacketListener> packet : packets) {
			flattenAndEncode(registryAccess, packetListener, packet, encoded);
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

	public static byte[] encodeChunkPacket(
			RegistryAccess registryAccess,
			ServerCommonPacketListenerImpl packetListener,
			ClientboundLevelChunkWithLightPacket packet
	) {
		RendererBotPayloads.ShadowPacketData encoded = encodePacket(registryAccess, packetListener, packet);
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
			ServerCommonPacketListenerImpl packetListener,
			Packet<? extends ClientGamePacketListener> packet,
			List<RendererBotPayloads.ShadowPacketData> encoded
	) {
		if (packet == null || encoded == null) {
			return;
		}
		Packet<? extends ClientGamePacketListener> patchedPacket = patchPacket(packetListener, packet);
		if (patchedPacket == null) {
			return;
		}
		if (patchedPacket instanceof BundlePacket<?> bundlePacket) {
			for (Packet<?> child : bundlePacket.subPackets()) {
				@SuppressWarnings("unchecked")
				Packet<? extends ClientGamePacketListener> clientPacket = (Packet<? extends ClientGamePacketListener>) child;
				flattenAndEncode(registryAccess, packetListener, clientPacket, encoded);
			}
			return;
		}
		RendererBotPayloads.ShadowPacketData encodedPacket = encodePatchedPacket(registryAccess, packetListener, patchedPacket);
		if (encodedPacket != null) {
			encoded.add(encodedPacket);
		}
	}

	private static RendererBotPayloads.ShadowPacketData encodePatchedPacket(
			RegistryAccess registryAccess,
			ServerCommonPacketListenerImpl packetListener,
			Packet<? extends ClientGamePacketListener> packet
	) {
		return PacketContext.supplyWithContext(packetListener, packet, () -> {
			StreamCodec<RegistryFriendlyByteBuf, Packet<? extends ClientGamePacketListener>> codec;
			try {
				codec = codecForPacket(packet);
			} catch (IllegalArgumentException illegalArgumentException) {
				Lg2.LOGGER.warn("Skipping unsupported renderer bot shadow packet {}", packet.getClass().getName());
				return null;
			}
			RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), registryAccess);
			try {
				codec.encode(buffer, packet);
				byte[] payload = new byte[buffer.readableBytes()];
				buffer.getBytes(buffer.readerIndex(), payload);
				return new RendererBotPayloads.ShadowPacketData(packet.type().id().toString(), payload);
			} finally {
				buffer.release();
			}
		});
	}

	private static Packet<? extends ClientGamePacketListener> patchPacket(
			ServerCommonPacketListenerImpl packetListener,
			Packet<? extends ClientGamePacketListener> packet
	) {
		if (packet == null) {
			return null;
		}
		if (packetListener == null) {
			return packet;
		}
		return PacketContext.supplyWithContext(packetListener, packet, () -> {
			@SuppressWarnings("unchecked")
			Packet<? extends ClientGamePacketListener> patchedPacket =
					(Packet<? extends ClientGamePacketListener>) PacketPatcher.replace(packetListener, packet);
			if (patchedPacket == null || PacketPatcher.prevent(packetListener, patchedPacket)) {
				return null;
			}
			return patchedPacket;
		});
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
