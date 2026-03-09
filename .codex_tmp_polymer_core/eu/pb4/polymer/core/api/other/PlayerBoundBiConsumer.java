package eu.pb4.polymer.core.api.other;

import eu.pb4.polymer.core.api.entity.PolymerEntity;
import eu.pb4.polymer.core.impl.entity.DirectEntityPlayerBoundBiConsumer;
import eu.pb4.polymer.core.impl.entity.DirectEntityPlayerBoundConsumer;
import eu.pb4.polymer.core.impl.entity.PolymericEntityPlayerBoundBiConsumer;
import eu.pb4.polymer.core.impl.entity.PolymericEntityPlayerBoundConsumer;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.class_1297;
import net.minecraft.class_2596;
import net.minecraft.class_5629;

public interface PlayerBoundBiConsumer<T, Y> extends BiConsumer<T, Y> {
    static PlayerBoundBiConsumer<class_2596<?>, List<UUID>> createPacketFor(Set<class_5629> listeners, class_1297 entity, BiConsumer<class_2596<?>, List<UUID>> receiver) {
        var polymerEntity = PolymerEntity.get(entity);
        return polymerEntity != null
                ? PolymericEntityPlayerBoundBiConsumer.create(listeners, polymerEntity, entity, receiver)
                : new DirectEntityPlayerBoundBiConsumer<>(listeners, entity, receiver);
    }

    Set<class_5629> receivers();
}
