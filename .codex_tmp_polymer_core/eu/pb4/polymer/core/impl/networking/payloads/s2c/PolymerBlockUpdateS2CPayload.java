package eu.pb4.polymer.core.impl.networking.payloads.s2c;

import eu.pb4.polymer.core.impl.networking.S2CPackets;
import eu.pb4.polymer.networking.api.ContextByteBuf;
import eu.pb4.polymer.networking.impl.packets.DisableS2CPayload;
import net.minecraft.class_2338;
import net.minecraft.class_8710;
import net.minecraft.class_9139;
import xyz.nucleoid.packettweaker.PacketContext;

public record PolymerBlockUpdateS2CPayload(class_2338 pos, int blockId) implements class_8710 {
    public static final class_8710.class_9154<PolymerBlockUpdateS2CPayload> ID = new class_8710.class_9154<>(S2CPackets.WORLD_SET_BLOCK_UPDATE);
    public static final class_9139<ContextByteBuf, PolymerBlockUpdateS2CPayload> CODEC = class_9139.method_56438(PolymerBlockUpdateS2CPayload::write, PolymerBlockUpdateS2CPayload::read);

    public void write(ContextByteBuf buf) {
        buf.method_10807(pos);
        buf.method_10804(blockId);
    }

    @Override
    public class_9154<? extends class_8710> method_56479() {
        return ID;
    }

    public static PolymerBlockUpdateS2CPayload read(ContextByteBuf buf) {
        return new PolymerBlockUpdateS2CPayload(buf.method_10811(), buf.method_10816());
    }
}
