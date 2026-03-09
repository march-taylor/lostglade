package eu.pb4.polymer.core.impl.networking.payloads.c2s;

import eu.pb4.polymer.core.impl.networking.C2SPackets;
import eu.pb4.polymer.networking.api.ContextByteBuf;
import eu.pb4.polymer.networking.api.PolymerNetworking;
import eu.pb4.polymer.networking.impl.packets.DisableS2CPayload;
import net.minecraft.class_2540;
import net.minecraft.class_8710;
import net.minecraft.class_9139;
import xyz.nucleoid.packettweaker.PacketContext;

public record PolymerChangeTooltipC2SPayload(boolean advanced) implements class_8710 {
    public static final class_8710.class_9154<DisableS2CPayload> ID = new class_8710.class_9154<>(C2SPackets.CHANGE_TOOLTIP);

    public static final class_9139<ContextByteBuf, PolymerChangeTooltipC2SPayload> CODEC =
            class_9139.method_56438(PolymerChangeTooltipC2SPayload::write, PolymerChangeTooltipC2SPayload::read);
    public void write(class_2540 buf) {
        buf.method_52964(advanced);
    }

    public static PolymerChangeTooltipC2SPayload read(class_2540 buf) {
        return new PolymerChangeTooltipC2SPayload(buf.readBoolean());
    }

    @Override
    public class_9154<? extends class_8710> method_56479() {
        return ID;
    }
}
