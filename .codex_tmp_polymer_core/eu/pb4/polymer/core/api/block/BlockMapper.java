package eu.pb4.polymer.core.api.block;

import eu.pb4.polymer.common.api.events.SimpleEvent;
import eu.pb4.polymer.core.impl.interfaces.PolymerGamePacketListenerExtension;
import eu.pb4.polymer.core.impl.other.BlockMapperImpl;
import org.apache.commons.lang3.mutable.MutableObject;
import org.jetbrains.annotations.Nullable;
import xyz.nucleoid.packettweaker.PacketContext;

import java.util.Map;
import java.util.function.BiFunction;
import net.minecraft.class_2680;
import net.minecraft.class_3222;
import net.minecraft.class_3244;

/**
 * Do not use, unless you really need it, and you are 100% sure about what you need!
 *
 * Allows changing how blocks display for certain players.
 * You can replace any block that way, including vanilla ones.
 *
 * To only change your own blocks see {@link PolymerBlock}
 */
public interface BlockMapper {
    SimpleEvent<BiFunction<PacketContext, BlockMapper, @Nullable BlockMapper>> DEFAULT_MAPPER_EVENT = new SimpleEvent<>();

    class_2680 toClientSideState(class_2680 state, PacketContext context);
    String getMapperName();

    static BlockMapper createDefault() {
        return BlockMapperImpl.DEFAULT;
    }

    static BlockMapper getDefault(PacketContext context) {
        var obj = new MutableObject<>(BlockMapperImpl.DEFAULT);
        DEFAULT_MAPPER_EVENT.invoke((c) -> {
             var mapper = c.apply(context, obj.getValue());

             if (mapper != null) {
                 obj.setValue(mapper);
             }
        });

        return obj.getValue();
    }

    static BlockMapper createMap(Map<class_2680, class_2680> stateMap) {
        return BlockMapperImpl.getMap(stateMap);
    }

    static BlockMapper createStack(BlockMapper overlay, BlockMapper base) {
        return BlockMapperImpl.createStack(overlay, base);
    }

    static BlockMapper getFrom(PacketContext context) {
        return getFrom(context.getPlayer());
    }
    static BlockMapper getFrom(@Nullable class_3222 player) {
        return player != null ? PolymerGamePacketListenerExtension.of(player).polymer$getBlockMapper() : BlockMapper.createDefault();
    }

    static void resetMapper(@Nullable class_3222 player) {
        if (player != null) {
            PolymerGamePacketListenerExtension.of(player).polymer$setBlockMapper(getDefault(PacketContext.create(player)));
        }
    }

    static void set(class_3244 handler, BlockMapper mapper) {
        PolymerGamePacketListenerExtension.of(handler).polymer$setBlockMapper(mapper);
    }

    static BlockMapper get(class_3244 handler) {
        return PolymerGamePacketListenerExtension.of(handler).polymer$getBlockMapper();
    }
}
