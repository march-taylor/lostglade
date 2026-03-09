package eu.pb4.polymer.core.impl;

import eu.pb4.polymer.common.impl.CommonImplUtils;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap;
import org.jetbrains.annotations.ApiStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.class_2378;

@ApiStatus.Internal
public class ImplPolymerRegistryEvent {
    private static final Map<class_2378<?>, List<Consumer<?>>> EVENTS = new Object2ObjectOpenCustomHashMap<>(CommonImplUtils.IDENTITY_HASH);
    public static void invokeRegistered(class_2378<?> ts, Object entry) {
        //noinspection unchecked
        var x = (List<Consumer<Object>>) (Object) EVENTS.get(ts);

        if (x != null) {
            for (var a : x) {
                a.accept(entry);
            }
        }
    }

    public static <T> void register(class_2378<T> registry, Consumer<T> tConsumer) {
        EVENTS.computeIfAbsent(registry, (a) -> new ArrayList<>()).add(tConsumer);
    }

    public static <T> void iterateAndRegister(class_2378<T> registry, Consumer<T> tConsumer) {
        for (var x : registry) {
            tConsumer.accept(x);
        }
        register(registry, tConsumer);
    }
}
