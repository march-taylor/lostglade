package eu.pb4.polymer.core.impl.networking;

import eu.pb4.polymer.core.api.block.PolymerBlock;
import eu.pb4.polymer.core.api.utils.PolymerSyncedObject;
import eu.pb4.polymer.core.impl.PolymerImplUtils;
import eu.pb4.polymer.core.impl.interfaces.ChunkDataS2CPacketInterface;
import eu.pb4.polymer.core.impl.interfaces.PolymerBlockPosStorage;
import eu.pb4.polymer.core.impl.interfaces.PolymerGamePacketListenerExtension;
import eu.pb4.polymer.core.mixin.block.packet.ClientboundBlockUpdatePacketAccessor;
import eu.pb4.polymer.core.mixin.block.packet.ClientboundSectionBlocksUpdatePacketAccessor;
import net.minecraft.class_2338;
import net.minecraft.class_2596;
import net.minecraft.class_2622;
import net.minecraft.class_2626;
import net.minecraft.class_2637;
import net.minecraft.class_2672;
import net.minecraft.class_2680;
import net.minecraft.class_2818;
import net.minecraft.class_3222;
import net.minecraft.class_3244;
import net.minecraft.class_4076;
import net.minecraft.class_7923;
import xyz.nucleoid.packettweaker.PacketContext;

public class BlockPacketUtil {
    public static void sendFromPacket(class_2596<?> packet, class_3244 handler) {
        if (packet instanceof class_2626 blockUpdatePacket) {
            class_2680 blockState = ((ClientboundBlockUpdatePacketAccessor) blockUpdatePacket).polymer$getState();
            if (PolymerImplUtils.POLYMER_STATES.contains(blockState)) {
                PolymerGamePacketListenerExtension.of(handler).polymer$delayAfterSequence(new SendSingleBlockInfo(handler, blockUpdatePacket.method_11309(), blockState));
            }
        } else if (packet instanceof class_2672) {
            class_2818 wc = ((ChunkDataS2CPacketInterface) packet).polymer$getWorldChunk();
            PolymerBlockPosStorage wci = (PolymerBlockPosStorage) wc;
            if (wc != null && wci.polymer$hasAny()) {
                PolymerServerProtocol.sendSectionUpdate(handler, wc);
                var ctx = PacketContext.create(handler);
                var iterator = wci.polymer$iterator();
                while (iterator.hasNext()) {
                    var pos = iterator.next();
                    var blockState = wc.method_8320(pos);
                    if (PolymerSyncedObject.getSyncedObject(class_7923.field_41175, blockState.method_26204()) instanceof PolymerBlock polymerBlock) {
                        polymerBlock.onPolymerBlockSend(blockState, pos, ctx);
                    }
                }
            }
        } else if (packet instanceof class_2637) {
            var chunk = (ClientboundSectionBlocksUpdatePacketAccessor) packet;

            PolymerGamePacketListenerExtension.of(handler).polymer$delayAfterSequence(new SendSequanceBlockInfo(handler,
                    chunk.polymer_getSectionPos(), chunk.polymer_getBlockStates(), chunk.polymer_getPositions()));
        }
    }

    public static void splitChunkDelta(class_3244 handler, class_2637 cPacket) {
        cPacket.method_30621((blockPos, blockState) -> handler.method_14364(new class_2626(blockPos.method_10062(), blockState)));
    }

    public static void sendUpdate(class_3222 player, class_2338 pos) {
        var state = player.method_51469().method_8320(pos);
        player.field_13987.method_14364(new class_2626(pos, state));

        if (state.method_31709()) {
            var be = player.method_51469().method_8321(pos);
            if (be != null) {
                player.field_13987.method_14364(class_2622.method_38585(be));
            }
        }
    }

    private record SendSingleBlockInfo(class_3244 handler, class_2338 pos, class_2680 blockState) implements Runnable {
        @Override
        public void run() {
            PolymerServerProtocol.sendBlockUpdate(handler, pos, blockState);
            if (PolymerSyncedObject.getSyncedObject(class_7923.field_41175, blockState.method_26204()) instanceof PolymerBlock polymerBlock) {
                polymerBlock.onPolymerBlockSend(blockState, pos.method_25503(), PacketContext.create(handler));
            }
        }
    }

    private record SendSequanceBlockInfo(class_3244 handler, class_4076 chunkPos,
                                         class_2680[] blockStates, short[] localPos) implements Runnable {
        @Override
        public void run() {
            PolymerServerProtocol.sendMultiBlockUpdate(handler, chunkPos, localPos, blockStates);

            var blockPos = new class_2338.class_2339();
            var ctx = PacketContext.create(handler);

            for (int i = 0; i < localPos.length; i++) {
                class_2680 blockState = blockStates[i];
                blockPos.method_10103(chunkPos.method_30554(localPos[i]), chunkPos.method_30555(localPos[i]), chunkPos.method_30556(localPos[i]));


                if (PolymerSyncedObject.getSyncedObject(class_7923.field_41175, blockState.method_26204()) instanceof PolymerBlock polymerBlock) {
                    polymerBlock.onPolymerBlockSend(blockState, blockPos, ctx);
                }
            }
        }
    }
}
