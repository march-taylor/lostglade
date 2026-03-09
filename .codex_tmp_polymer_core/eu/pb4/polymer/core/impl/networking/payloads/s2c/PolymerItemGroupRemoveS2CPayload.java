package eu.pb4.polymer.core.impl.networking.payloads.s2c;

import eu.pb4.polymer.core.impl.networking.S2CPackets;
import eu.pb4.polymer.networking.api.ContextByteBuf;
import net.minecraft.class_2960;
import net.minecraft.class_8710;
import net.minecraft.class_9139;
import xyz.nucleoid.packettweaker.PacketContext;

public record PolymerItemGroupRemoveS2CPayload(class_2960 groupId) implements class_8710 {
    public static final class_8710.class_9154<PolymerItemGroupRemoveS2CPayload> ID = new class_8710.class_9154<>(S2CPackets.SYNC_ITEM_GROUP_REMOVE);
    public static final class_9139<ContextByteBuf, PolymerItemGroupRemoveS2CPayload> CODEC = class_2960.field_48267.method_56432(PolymerItemGroupRemoveS2CPayload::new, PolymerItemGroupRemoveS2CPayload::groupId).method_56430();

    @Override
    public class_9154<? extends class_8710> method_56479() {
        return ID;
    }
}
