package eu.pb4.polymer.core.impl.networking.payloads.s2c;

import eu.pb4.polymer.core.impl.networking.S2CPackets;
import net.minecraft.class_8710;
import xyz.nucleoid.packettweaker.PacketContext;

public record PolymerItemGroupApplyUpdateS2CPayload() implements class_8710 {
    public static final class_8710.class_9154<PolymerItemGroupApplyUpdateS2CPayload> ID = new class_8710.class_9154<>(S2CPackets.SYNC_ITEM_GROUP_APPLY_UPDATE);


    @Override
    public class_9154<? extends class_8710> method_56479() {
        return ID;
    }
}
