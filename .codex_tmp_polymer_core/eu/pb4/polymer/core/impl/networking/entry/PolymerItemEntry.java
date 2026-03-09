package eu.pb4.polymer.core.impl.networking.entry;

import eu.pb4.polymer.networking.api.ContextByteBuf;
import net.minecraft.class_1792;
import net.minecraft.class_1799;
import net.minecraft.class_2960;
import net.minecraft.class_3244;
import net.minecraft.class_7923;
import net.minecraft.class_9139;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public record PolymerItemEntry(int numId, class_2960 identifier, class_1799 representation) {
    public static final class_9139<ContextByteBuf, PolymerItemEntry> CODEC = class_9139.method_56438(PolymerItemEntry::write, PolymerItemEntry::read);

    public static PolymerItemEntry of(class_1792 item, class_3244 handler, int version) {
        return new PolymerItemEntry(class_1792.method_7880(item), class_7923.field_41178.method_10221(item), item.method_7854());
    }

    public static PolymerItemEntry read(ContextByteBuf buf) {
        return new PolymerItemEntry(buf.method_10816(), buf.method_10810(), class_1799.field_49268.decode(buf));
    }

    public void write(ContextByteBuf buf) {
        buf.method_10804(this.numId);

        buf.method_10812(this.identifier);
        class_1799.field_49268.encode(buf, this.representation);
    }
}
