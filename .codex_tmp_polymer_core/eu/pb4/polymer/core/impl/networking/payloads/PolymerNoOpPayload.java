package eu.pb4.polymer.core.impl.networking.payloads;

import eu.pb4.polymer.core.impl.networking.S2CPackets;
import net.minecraft.class_8710;

public record PolymerNoOpPayload() implements class_8710 {
    public static final class_9154<PolymerNoOpPayload> ID = new class_9154<>(S2CPackets.SYNC_STARTED);
    public static final PolymerNoOpPayload INSTANCE = new PolymerNoOpPayload();

    @Override
    public class_9154<? extends class_8710> method_56479() {
        return ID;
    }
}
