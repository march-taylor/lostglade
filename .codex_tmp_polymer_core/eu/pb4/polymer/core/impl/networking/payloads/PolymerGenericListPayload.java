package eu.pb4.polymer.core.impl.networking.payloads;

import eu.pb4.polymer.networking.api.ContextByteBuf;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.class_8710;
import net.minecraft.class_9135;
import net.minecraft.class_9139;

public record PolymerGenericListPayload<T>(class_9154<PolymerGenericListPayload<T>> id, List<T> entries) implements class_8710 {
    public static <T> class_9139<ContextByteBuf, PolymerGenericListPayload<T>> codec(class_9154<PolymerGenericListPayload<T>> id, class_9139<ContextByteBuf, T> codec) {
        return codec.method_56433(class_9135.method_56363()).method_56432(x -> new PolymerGenericListPayload<>(id, x), PolymerGenericListPayload::entries);
    }
    @Override
    public class_9154<? extends class_8710> method_56479() {
        return id;
    }
}
