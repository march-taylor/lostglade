package eu.pb4.polymer.core.impl.networking.entry;

import eu.pb4.polymer.networking.api.ContextByteBuf;
import java.util.function.BiFunction;
import net.minecraft.class_2540;
import net.minecraft.class_2960;
import net.minecraft.class_9139;

public record IdValueEntry(int rawId, class_2960 id)  {

    public static final class_9139<ContextByteBuf, IdValueEntry> CODEC = class_9139.method_56438(IdValueEntry::write, IdValueEntry::read);
    public void write(class_2540 buf) {
        buf.method_10804(rawId);
        buf.method_10812(id);
    }

    public static IdValueEntry read(class_2540 buf) {
        return new IdValueEntry(buf.method_10816(), buf.method_10810());
    }

    public static <T> T read(class_2540 buf, BiFunction<Integer, class_2960, T> function) {
        return function.apply(buf.method_10816(), buf.method_10810());
    }
}
