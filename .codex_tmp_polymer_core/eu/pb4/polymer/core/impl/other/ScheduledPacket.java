package eu.pb4.polymer.core.impl.other;

import net.minecraft.class_2596;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public record ScheduledPacket(class_2596<?> packet, int time) {
}
