package eu.pb4.polymer.core.impl.entity;

import eu.pb4.polymer.core.api.other.PlayerBoundConsumer;
import eu.pb4.polymer.core.impl.interfaces.EntityAttachedPacket;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.class_1297;
import net.minecraft.class_5629;

public record DirectEntityPlayerBoundConsumer<T>(Set<class_5629> receivers, class_1297 entity, Consumer<T> consumer) implements PlayerBoundConsumer<T> {
    @Override
    public void accept(T t) {
        consumer.accept(EntityAttachedPacket.setIfEmpty(t, entity));
    }
}
