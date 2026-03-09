package eu.pb4.polymer.core.impl.networking.payloads.s2c;

import eu.pb4.polymer.core.impl.networking.S2CPackets;
import eu.pb4.polymer.networking.api.ContextByteBuf;
import net.minecraft.class_1799;
import net.minecraft.class_2540;
import net.minecraft.class_2561;
import net.minecraft.class_2960;
import net.minecraft.class_8710;
import net.minecraft.class_8824;
import net.minecraft.class_9129;
import net.minecraft.class_9139;
import xyz.nucleoid.packettweaker.PacketContext;

public record PolymerItemGroupDefineS2CPayload(class_2960 groupId, class_2561 name, class_1799 icon) implements class_8710 {
    public static final class_8710.class_9154<PolymerItemGroupDefineS2CPayload> ID = new class_8710.class_9154<>(S2CPackets.SYNC_ITEM_GROUP_DEFINE);
    public static final class_9139<ContextByteBuf, PolymerItemGroupDefineS2CPayload> CODEC = class_9139.method_56438(PolymerItemGroupDefineS2CPayload::write, PolymerItemGroupDefineS2CPayload::read);

    public void write(class_2540 buf) {
        buf.method_10812(this.groupId);

        class_8824.field_49668.encode(buf, name);
        class_1799.field_48349.encode((class_9129) buf, icon);
    }

    public static PolymerItemGroupDefineS2CPayload read(class_2540 buf) {
        return new PolymerItemGroupDefineS2CPayload(buf.method_10810(), class_8824.field_49668.decode(buf), class_1799.field_48349.decode((class_9129) buf));
    }

    @Override
    public class_9154<? extends class_8710> method_56479() {
        return ID;
    }
}
