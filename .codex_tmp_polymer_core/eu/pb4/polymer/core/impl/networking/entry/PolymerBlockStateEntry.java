package eu.pb4.polymer.core.impl.networking.entry;

import eu.pb4.polymer.networking.api.ContextByteBuf;
import org.jetbrains.annotations.ApiStatus;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.class_2248;
import net.minecraft.class_2540;
import net.minecraft.class_2680;
import net.minecraft.class_2769;
import net.minecraft.class_7923;
import net.minecraft.class_9139;

@ApiStatus.Internal
public record PolymerBlockStateEntry(Map<String, String> properties, int numId, int blockId) {
    public static final IdentityHashMap<class_2680, PolymerBlockStateEntry> CACHE = new IdentityHashMap<>();

    public static final class_9139<ContextByteBuf, PolymerBlockStateEntry> CODEC = class_9139.method_56438(PolymerBlockStateEntry::write, PolymerBlockStateEntry::read);

    public void write(class_2540 buf) {
        buf.method_10804(numId);
        buf.method_10804(blockId);
        buf.method_34063(properties, class_2540::method_10814, class_2540::method_10814);
    }

    public static PolymerBlockStateEntry of(class_2680 state) {
        var value = CACHE.get(state);
        if (value == null) {
            var list = new HashMap<String, String>();

            for (var entry : state.method_11656().entrySet()) {
                list.put(entry.getKey().method_11899(), ((class_2769) (Object) entry.getKey()).method_11901(entry.getValue()));
            }
            value = new PolymerBlockStateEntry(list, class_2248.field_10651.method_10206(state), class_7923.field_41175.method_10206(state.method_26204()));
            CACHE.put(state, value);
        }

        return value;
    }

    public static PolymerBlockStateEntry read(class_2540 buf) {
        var numId = buf.method_10816();
        var blockId = buf.method_10816();
        var states = buf.method_34067(class_2540::method_19772, class_2540::method_19772);
        return new PolymerBlockStateEntry(states, numId, blockId);
    }
}
