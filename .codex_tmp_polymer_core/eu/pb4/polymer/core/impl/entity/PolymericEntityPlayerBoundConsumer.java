package eu.pb4.polymer.core.impl.entity;

import eu.pb4.polymer.core.api.entity.PolymerEntity;
import eu.pb4.polymer.core.api.other.PlayerBoundConsumer;
import java.util.Collection;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.class_1297;
import net.minecraft.class_2596;
import net.minecraft.class_5629;

public record PolymericEntityPlayerBoundConsumer(Set<class_5629> receivers, PolymerEntity polymerEntity, Consumer<class_2596<?>> consumer)
        implements PlayerBoundConsumer<class_2596<?>> {
    public static PolymericEntityPlayerBoundConsumer create(Set<class_5629> listeners, PolymerEntity polymerEntity, class_1297 entity, Consumer<class_2596<?>> receiver) {
        return new PolymericEntityPlayerBoundConsumer(listeners, polymerEntity, new DirectEntityPlayerBoundConsumer<>(listeners, entity, receiver));
    }
    @Override
    public void accept(class_2596<?> t) {
        polymerEntity.onEntityPacketSent(consumer, t);
    }
}
