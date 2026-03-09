package eu.pb4.polymer.core.impl.networking;

import java.util.function.BiFunction;
import net.minecraft.class_2540;
import net.minecraft.class_9139;

public record TransformingPacketCodec<B, V>(class_9139<B, V> codec, BiFunction<B, V, V> encodeTransform, BiFunction<B, V, V> decodeTransform) implements class_9139<B, V> {
    private static final BiFunction<class_2540, Object, Object> PASSTHROUGH = (b, o) -> o;

    @Override
    public V decode(B buf) {
        return decodeTransform.apply(buf, this.codec.decode(buf));
    }

    @Override
    public void encode(B buf, V value) {
        this.codec.encode(buf, this.encodeTransform.apply(buf, value));
    }

    public static <B, V> class_9139<B, V> encodeOnly(class_9139<B, V> codec, BiFunction<B, V, V> encodeTransform) {
        //noinspection unchecked
        return new TransformingPacketCodec<>(codec, encodeTransform, (BiFunction<B, V, V>) PASSTHROUGH);
    }
}
