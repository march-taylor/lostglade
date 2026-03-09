package eu.pb4.polymer.core.impl.entity;

import eu.pb4.polymer.core.api.other.PlayerBoundBiConsumer;
import eu.pb4.polymer.core.api.other.PlayerBoundConsumer;
import eu.pb4.polymer.core.impl.interfaces.EntityAttachedPacket;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.class_1297;
import net.minecraft.class_5629;

public record DirectEntityPlayerBoundBiConsumer<T, Y>(Set<class_5629> receivers, class_1297 entity, BiConsumer<T, Y> consumer) implements PlayerBoundBiConsumer<T, Y> {
    @Override
    public void accept(T t, Y y) {
        consumer.accept(EntityAttachedPacket.setIfEmpty(t, entity), y);
    }
}
