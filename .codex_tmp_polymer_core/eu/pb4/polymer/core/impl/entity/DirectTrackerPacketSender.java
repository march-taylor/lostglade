package eu.pb4.polymer.core.impl.entity;

import eu.pb4.polymer.core.api.entity.PolymerTrackerPacketSender;
import eu.pb4.polymer.core.impl.interfaces.EntityAttachedPacket;

import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.class_1297;
import net.minecraft.class_2596;
import net.minecraft.class_2602;
import net.minecraft.class_3222;
import net.minecraft.class_3231;
import net.minecraft.class_5629;

public record DirectTrackerPacketSender(class_3231.class_12004 tracker, Supplier<Set<class_5629>> listenerSupplier, class_1297 entity) implements PolymerTrackerPacketSender {
    @Override
    public Set<class_5629> listeners() {
        return listenerSupplier.get();
    }

    @Override
    public void method_18730(class_2596<? super class_2602> packet) {
        this.tracker.method_18730(EntityAttachedPacket.setIfEmpty(packet, entity));
    }

    @Override
    public void method_18734(class_2596<? super class_2602> packet) {
        this.tracker.method_18734(EntityAttachedPacket.setIfEmpty(packet, entity));
    }

    @Override
    public void method_74531(class_2596<? super class_2602> packet, Predicate<class_3222> predicate) {
        this.tracker.method_74531(EntityAttachedPacket.setIfEmpty(packet, entity), predicate);
    }
}
