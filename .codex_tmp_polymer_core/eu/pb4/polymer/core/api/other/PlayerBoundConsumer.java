package eu.pb4.polymer.core.api.other;

import eu.pb4.polymer.core.api.entity.PolymerEntity;
import eu.pb4.polymer.core.impl.entity.DirectEntityPlayerBoundConsumer;
import eu.pb4.polymer.core.impl.entity.PolymericEntityPlayerBoundConsumer;
import java.util.Collection;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.class_1297;
import net.minecraft.class_2596;
import net.minecraft.class_5629;

public interface PlayerBoundConsumer<T> extends Consumer<T> {
    static PlayerBoundConsumer<class_2596<?>> createPacketFor(Set<class_5629> listeners, class_1297 entity, Consumer<class_2596<?>> receiver) {
        var polymerEntity = PolymerEntity.get(entity);
        return polymerEntity != null
                ? PolymericEntityPlayerBoundConsumer.create(listeners, polymerEntity, entity, receiver)
                : new DirectEntityPlayerBoundConsumer<>(listeners, entity, receiver);
    }

    Set<class_5629> receivers();
}
