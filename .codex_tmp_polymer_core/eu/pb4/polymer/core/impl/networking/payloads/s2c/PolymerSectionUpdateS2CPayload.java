package eu.pb4.polymer.core.impl.networking.payloads.s2c;

import eu.pb4.polymer.core.impl.networking.S2CPackets;
import eu.pb4.polymer.networking.api.ContextByteBuf;
import net.minecraft.class_2540;
import net.minecraft.class_4076;
import net.minecraft.class_8710;
import net.minecraft.class_9139;
import xyz.nucleoid.packettweaker.PacketContext;

public record PolymerSectionUpdateS2CPayload(class_4076 chunkPos, short[] pos, int[] blocks)  implements class_8710 {
    public static final class_8710.class_9154<PolymerSectionUpdateS2CPayload> ID = new class_8710.class_9154<>(S2CPackets.WORLD_CHUNK_SECTION_UPDATE);
    public static final class_9139<ContextByteBuf, PolymerSectionUpdateS2CPayload> CODEC = class_9139.method_56438(PolymerSectionUpdateS2CPayload::write, PolymerSectionUpdateS2CPayload::read);

    public void write(class_2540 buf) {
        class_4076.field_62995.encode(buf, this.chunkPos);
        buf.method_10804(this.pos.length);
        for (int i = 0; i < this.pos.length; i++) {
            buf.method_10791((long) this.blocks[i] << 12 | (long)this.pos[i]);
        }
    }

    public static PolymerSectionUpdateS2CPayload read(class_2540 buf) {
        var chunkPos = class_4076.field_62995.decode(buf);
        int i = buf.method_10816();
        var pos = new short[i];
        var blocks = new int[i];

        for(int j = 0; j < i; ++j) {
            long l = buf.method_10792();
            pos[j] = (short)((int)(l & 4095L));
            blocks[j] = (int)(l >>> 12);
        }


        return new PolymerSectionUpdateS2CPayload(chunkPos, pos, blocks);
    }

    @Override
    public class_9154<? extends class_8710> method_56479() {
        return ID;
    }
}
