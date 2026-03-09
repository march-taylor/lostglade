package eu.pb4.polymer.core.impl.interfaces;

import net.minecraft.class_2596;
import net.minecraft.class_8609;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
@SuppressWarnings({"unused"})
public interface PolymerCommonPacketListenerExtension {
    void polymer$schedulePacket(class_2596<?> packet, int duration);

    void polymer$delayAction(String identifier, int delay, Runnable action);
    static PolymerCommonPacketListenerExtension of(class_8609 handler) {
        return (PolymerCommonPacketListenerExtension) handler;
    }
}
