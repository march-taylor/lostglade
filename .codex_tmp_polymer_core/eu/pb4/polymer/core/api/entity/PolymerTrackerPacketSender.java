package eu.pb4.polymer.core.api.entity;

import eu.pb4.polymer.core.impl.entity.DirectTrackerPacketSender;
import eu.pb4.polymer.core.impl.entity.PolymericTrackerPacketSender;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.class_1297;
import net.minecraft.class_3231;
import net.minecraft.class_5629;

public interface PolymerTrackerPacketSender extends class_3231.class_12004 {
    Set<class_5629> listeners();

    static PolymerTrackerPacketSender of(class_3231.class_12004 tracker, Supplier<Set<class_5629>> listeners, class_1297 entity) {
        if (PolymerEntity.get(entity) instanceof PolymerEntity polymerEntity) {
            return new PolymericTrackerPacketSender(tracker, listeners, entity, polymerEntity);
        } else {
            return new DirectTrackerPacketSender(tracker, listeners, entity);
        }
    }
}
