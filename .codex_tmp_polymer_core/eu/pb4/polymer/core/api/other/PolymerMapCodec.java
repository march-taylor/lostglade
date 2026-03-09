package eu.pb4.polymer.core.api.other;

import com.mojang.serialization.*;
import eu.pb4.polymer.core.api.block.BlockMapper;
import eu.pb4.polymer.core.api.utils.PolymerObject;
import eu.pb4.polymer.rsm.api.RegistrySyncUtils;
import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;
import xyz.nucleoid.packettweaker.PacketContext;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import net.minecraft.class_11419;
import net.minecraft.class_11432;
import net.minecraft.class_11438;
import net.minecraft.class_2378;
import net.minecraft.class_3902;
import net.minecraft.class_9704;
import net.minecraft.class_9712;
import net.minecraft.class_9721;
import net.minecraft.class_9722;
import net.minecraft.class_9723;

public class PolymerMapCodec<T> extends MapCodec<T> implements PolymerObject {
    private static final Map<MapCodec<?>, PolymerMapCodec<?>> OVERLAYS = new IdentityHashMap<>();
    private final MapCodec<T> selfCodec;
    private final Transform<T, Object> fallbackValue;

    @Deprecated(forRemoval = true)
    public <K> PolymerMapCodec(MapCodec<T> selfCodec, MapCodec<K> fallbackCodec, K fallbackValue) {
        this(selfCodec,  (x, ctx) -> fallbackValue);
    }

    private PolymerMapCodec(MapCodec<T> selfCodec, Transform<T, Object> fallbackValue) {
        this.selfCodec = selfCodec;
        this.fallbackValue = fallbackValue;
    }

    public static <T extends K, K> MapCodec<T> ofStatic(MapCodec<T> selfCodec, K fallbackValue) {
        return new PolymerMapCodec<T>(selfCodec, (x, ctx) -> fallbackValue);
    }

    public static <T extends K, K> MapCodec<T> ofDynamic(MapCodec<T> codec, Transform<T, K> transform) {
        //noinspection unchecked
        return new PolymerMapCodec<T>(codec, (Transform<T, Object>) transform);
    }

    public static <T extends class_11419> MapCodec<T> ofDialog(MapCodec<T> codec, Transform<T, class_11419> transform) {
        return ofDynamic(codec,  transform);
    }

    public static <T extends class_11432> MapCodec<T> ofDialogBody(MapCodec<T> codec, Transform<T, class_11432> transform) {
        return ofDynamic(codec, transform);
    }

    public static <T extends class_11438> MapCodec<T> ofDialogInputControl(MapCodec<T> codec, Transform<T, class_11438> transform) {
        return ofDynamic(codec,  transform);
    }

    public static <T extends class_9723> MapCodec<T> ofEnchantmentValueEffect(MapCodec<T> codec) {
        return ofStatic(codec, new class_9712.class_9715(List.of()));
    }

    public static <T extends class_9722> MapCodec<T> ofEnchantmentLocationBasedEffect(MapCodec<T> codec) {
        return ofStatic(codec, new class_9712.class_9714(List.of()));
    }

    public static <T extends class_9721> MapCodec<T> ofEnchantmentEntityEffect(MapCodec<T> codec) {
        return ofStatic(codec, new class_9712.class_9713(List.of()));
    }

    public static <T extends class_9704> MapCodec<T> ofEnchantmentLevelBasedValue(MapCodec<T> codec) {
        return ofStatic(codec, new class_9704.class_9706(0));
    }

    @SuppressWarnings("unchecked")
    @Nullable
    public static <T> PolymerMapCodec<T> getOverlay(MapEncoder<T> codec) {
        return codec instanceof PolymerMapCodec<T> ? (PolymerMapCodec<T>) codec : (PolymerMapCodec<T>) OVERLAYS.get(codec);
    }

    public static <T extends A, A> void setOverlay(class_2378<MapCodec<A>> registry, MapCodec<T> sourceCodec, PolymerMapCodec<T> codec) {
        //noinspection unchecked
        RegistrySyncUtils.setServerEntry(registry, (MapCodec<A>) sourceCodec);
        OVERLAYS.put(sourceCodec, codec);
    }

    @ApiStatus.Internal
    public Object getPolymerReplacement(T data, PacketContext context) {
        return this.fallbackValue.transform(data, context);
    }

    @ApiStatus.Internal
    @Deprecated(forRemoval = true)
    public Object fallbackValue() {
        return fallbackValue;
    }

    @ApiStatus.Internal
    @Deprecated(forRemoval = true)
    public MapCodec<Object> fallbackCodec() {
        return MapCodec.unit(class_3902.field_17274);
    }

    @Override
    public <T1> Stream<T1> keys(DynamicOps<T1> ops) {
        return this.selfCodec.keys(ops);
    }

    @Override
    public <T1> DataResult<T> decode(DynamicOps<T1> ops, MapLike<T1> input) {
        return this.selfCodec.decode(ops, input);
    }

    @Override
    public <T1> RecordBuilder<T1> encode(T input, DynamicOps<T1> ops, RecordBuilder<T1> prefix) {
        return this.selfCodec.encode(input, ops, prefix);
    }

    public interface Transform<T extends K, K> {
        K transform(T data, PacketContext context);
    }
}
