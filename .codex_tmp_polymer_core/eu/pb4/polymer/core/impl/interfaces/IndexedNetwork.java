package eu.pb4.polymer.core.impl.interfaces;

import java.util.function.IntFunction;
import net.minecraft.class_2359;

public interface IndexedNetwork<T> extends class_2359<T> {

    void polymer$setDecoder(IntFunction<T> decoder);

    static <T> void set(class_2359<T> i, IntFunction<T> decoder) {
        ((IndexedNetwork<T>) i).polymer$setDecoder(decoder);
    }
}
