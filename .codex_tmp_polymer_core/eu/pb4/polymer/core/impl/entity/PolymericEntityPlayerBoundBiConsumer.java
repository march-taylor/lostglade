package eu.pb4.polymer.core.impl.entity;

import eu.pb4.polymer.core.api.entity.PolymerEntity;
import eu.pb4.polymer.core.api.other.PlayerBoundBiConsumer;
import eu.pb4.polymer.core.api.other.PlayerBoundConsumer;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.class_1297;
import net.minecraft.class_2596;
import net.minecraft.class_5629;

public record PolymericEntityPlayerBoundBiConsumer(Set<class_5629> receivers, PolymerEntity polymerEntity, BiConsumer<class_2596<?>, List<UUID>> consumer)
        implements PlayerBoundBiConsumer<class_2596<?>, List<UUID>> {
    public static PolymericEntityPlayerBoundBiConsumer create(Set<class_5629> listeners, PolymerEntity polymerEntity, class_1297 entity, BiConsumer<class_2596<?>, List<UUID>> receiver) {
        return new PolymericEntityPlayerBoundBiConsumer(listeners, polymerEntity, new DirectEntityPlayerBoundBiConsumer<>(listeners, entity, receiver));
    }
    @Override
    public void accept(class_2596<?> t, List<UUID> y) {
        polymerEntity.onEntityPacketSent(x -> consumer.accept(x, y), t);
    }
}
