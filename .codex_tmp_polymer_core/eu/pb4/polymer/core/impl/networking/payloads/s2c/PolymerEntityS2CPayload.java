package eu.pb4.polymer.core.impl.networking.payloads.s2c;

import eu.pb4.polymer.core.impl.networking.S2CPackets;
import eu.pb4.polymer.networking.api.ContextByteBuf;
import eu.pb4.polymer.networking.impl.packets.DisableS2CPayload;
import net.minecraft.class_2540;
import net.minecraft.class_2960;
import net.minecraft.class_8710;
import net.minecraft.class_9139;
import xyz.nucleoid.packettweaker.PacketContext;

public record PolymerEntityS2CPayload(int entityId, class_2960 typeId) implements class_8710 {
    public static final class_8710.class_9154<PolymerEntityS2CPayload> ID = new class_8710.class_9154<>(S2CPackets.WORLD_ENTITY);
    public static final class_9139<ContextByteBuf, PolymerEntityS2CPayload> CODEC = class_9139.method_56438(PolymerEntityS2CPayload::write, PolymerEntityS2CPayload::read);

    public void write(class_2540 buf) {
        buf.method_10804(this.entityId);
        buf.method_10812(this.typeId);
    }


    public static PolymerEntityS2CPayload read(class_2540 buf) {
        return new PolymerEntityS2CPayload(buf.method_10816(), buf.method_10810());
    }

    @Override
    public class_9154<? extends class_8710> method_56479() {
        return ID;
    }
}
