package eu.pb4.polymer.core.impl.networking.payloads.s2c;

import eu.pb4.polymer.core.impl.networking.S2CPackets;
import eu.pb4.polymer.networking.api.ContextByteBuf;
import net.minecraft.class_2540;
import net.minecraft.class_2960;
import net.minecraft.class_8710;
import net.minecraft.class_9139;
import xyz.nucleoid.packettweaker.PacketContext;

public record PolymerItemGroupContentClearS2CPayload(class_2960 groupId) implements class_8710 {
    public static final class_8710.class_9154<PolymerItemGroupContentClearS2CPayload> ID = new class_8710.class_9154<>(S2CPackets.SYNC_ITEM_GROUP_CONTENTS_CLEAR);

    public static final class_9139<ContextByteBuf, PolymerItemGroupContentClearS2CPayload> CODEC = class_9139.method_56438(PolymerItemGroupContentClearS2CPayload::write, PolymerItemGroupContentClearS2CPayload::read);
    public void write(class_2540 buf) {
        buf.method_10812(this.groupId);
    }

    public static PolymerItemGroupContentClearS2CPayload read(class_2540 buf) {
        return new PolymerItemGroupContentClearS2CPayload(buf.method_10810());
    }

    @Override
    public class_9154<? extends class_8710> method_56479() {
        return ID;
    }
}
