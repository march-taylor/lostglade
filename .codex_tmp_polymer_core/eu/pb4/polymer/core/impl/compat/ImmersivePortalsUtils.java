package eu.pb4.polymer.core.impl.compat;

import eu.pb4.polymer.core.impl.networking.BlockPacketUtil;
import qouteall.imm_ptl.core.api.PortalAPI;
import qouteall.imm_ptl.core.chunk_loading.ImmPtlChunkTracking;
import qouteall.imm_ptl.core.network.PacketRedirection;

import java.util.List;
import java.util.Objects;
import net.minecraft.class_1923;
import net.minecraft.class_1937;
import net.minecraft.class_2596;
import net.minecraft.class_2658;
import net.minecraft.class_2818;
import net.minecraft.class_3222;
import net.minecraft.class_3244;
import net.minecraft.class_5321;

public class ImmersivePortalsUtils {
    public static void sendBlockPackets(class_3244 handler, class_2596<?> packet) {
        if (packet instanceof class_2658 payloadS2CPacket &&  payloadS2CPacket.comp_1646() instanceof PacketRedirection.Payload payload) {
            PacketRedirection.withForceRedirect(Objects.requireNonNull(
                    handler.field_14140.method_51469().method_8503().method_3847(PortalAPI.serverIntToDimKey(handler.method_32311().method_51469().method_8503(), payload.dimensionIntId()))), () -> {
                BlockPacketUtil.sendFromPacket(payload.packet(), handler);
            });
        } else {
            BlockPacketUtil.sendFromPacket(packet, handler);
        }
    }

    public static List<class_3222> getPlayerTracking(class_2818 chunk) {
        return ImmPtlChunkTracking.getPlayersViewingChunk(chunk.method_12200().method_27983(), chunk.method_12004().field_9181, chunk.method_12004().field_9180, false);
    }

    public static List<class_3222> getPlayerTracking(class_5321<class_1937> worldRegistryKey, class_1923 pos) {
        return ImmPtlChunkTracking.getPlayersViewingChunk(worldRegistryKey, pos.field_9181, pos.field_9180, false);
    }
}
