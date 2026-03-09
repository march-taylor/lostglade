package eu.pb4.polymer.core.impl.networking.entry;

import eu.pb4.polymer.networking.api.ContextByteBuf;
import org.jetbrains.annotations.ApiStatus;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.class_2248;
import net.minecraft.class_2540;
import net.minecraft.class_2680;
import net.minecraft.class_2769;
import net.minecraft.class_2960;
import net.minecraft.class_3244;
import net.minecraft.class_7923;
import net.minecraft.class_9139;

@ApiStatus.Internal
public record DebugBlockStateEntry(Map<String, String> states, int numId, class_2960 blockId) {
    public static final class_9139<ContextByteBuf, DebugBlockStateEntry> CODEC = class_9139.method_56438(DebugBlockStateEntry::write, DebugBlockStateEntry::read);


    public void write(class_2540 buf) {
        buf.method_10804(numId);
        buf.method_10812(blockId);
        buf.method_34063(states, class_2540::method_10814, class_2540::method_10814);
    }

    public static DebugBlockStateEntry of(class_2680 state, class_3244 player, int version) {
        var list = new HashMap<String, String>();

        for (var entry : state.method_11656().entrySet()) {
            list.put(entry.getKey().method_11899(), ((class_2769) entry.getKey()).method_11901(entry.getValue()));
        }

        return new DebugBlockStateEntry(list,
                class_2248.field_10651.method_10206(state),
                class_7923.field_41175.method_10221(state.method_26204())
        );
    }

    public static DebugBlockStateEntry read(class_2540 buf) {
        var numId = buf.method_10816();
        var blockId = buf.method_10810();
        var states = buf.method_34067(class_2540::method_19772, class_2540::method_19772);
        return new DebugBlockStateEntry(states, numId, blockId);
    }

    public String asString() {
        var builder = new StringBuilder();

        builder.append(this.blockId);

        if (!this.states.isEmpty()) {
            builder.append("[");
            var iterator = this.states().entrySet().stream().sorted().iterator();

            while (iterator.hasNext()) {
                var entry = iterator.next();
                builder.append(entry.getKey());
                builder.append("=");
                builder.append(entry.getValue());

                if (iterator.hasNext()) {
                    builder.append(",");
                }
            }
            builder.append("]");
        }

        return builder.toString();
    }
}
