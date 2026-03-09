package eu.pb4.polymer.core.impl.networking.entry;

import eu.pb4.polymer.core.impl.networking.payloads.s2c.PolymerEntityS2CPayload;
import eu.pb4.polymer.networking.api.ContextByteBuf;
import net.minecraft.class_1299;
import net.minecraft.class_2540;
import net.minecraft.class_2561;
import net.minecraft.class_2960;
import net.minecraft.class_7923;
import net.minecraft.class_8824;
import net.minecraft.class_9139;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public record PolymerEntityEntry(class_2960 identifier, int rawId, class_2561 name) {

    public static final class_9139<ContextByteBuf, PolymerEntityEntry> CODEC = class_9139.method_56438(PolymerEntityEntry::write, PolymerEntityEntry::read);

    public void write(class_2540 buf) {
        buf.method_10812(identifier);
        buf.method_10804(this.rawId);
        class_8824.field_49668.encode(buf, name);
    }

    public static PolymerEntityEntry of(class_1299<?> entityType) {
        return new PolymerEntityEntry(
                class_7923.field_41177.method_10221(entityType),
                class_7923.field_41177.method_10206(entityType),
                entityType.method_5897()
        );
    }

    public static PolymerEntityEntry read(class_2540 buf) {
        return new PolymerEntityEntry(buf.method_10810(), buf.method_10816(), class_8824.field_49668.decode(buf));
    }
}
