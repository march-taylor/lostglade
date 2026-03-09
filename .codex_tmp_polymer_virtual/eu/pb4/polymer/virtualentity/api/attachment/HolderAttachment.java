package eu.pb4.polymer.virtualentity.api.attachment;

import eu.pb4.polymer.common.impl.CompatStatus;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.VirtualEntityUtils;
import eu.pb4.polymer.virtualentity.impl.SimpleUpdateType;
import eu.pb4.polymer.virtualentity.impl.VoidUpdateType;
import java.util.Collection;
import java.util.function.Consumer;
import net.minecraft.class_243;
import net.minecraft.class_2596;
import net.minecraft.class_2602;
import net.minecraft.class_3218;
import net.minecraft.class_3222;
import net.minecraft.class_3244;

public interface HolderAttachment {
    ElementHolder holder();
    void destroy();
    class_243 getPos();
    class_3218 getWorld();
    void updateCurrentlyTracking(Collection<class_3244> currentlyTracking);
    void updateTracking(class_3244 tracking);

    default boolean isRemoved() {
        return false;
    }

    default void startWatching(class_3222 handler) {
        if (this.holder().getAttachment() == this) {
            if (CompatStatus.IMMERSIVE_PORTALS) {
                VirtualEntityUtils.wrapCallWithContext(this.getWorld(), () -> this.holder().startWatching(handler));
            } else {
                this.holder().startWatching(handler);
            }
        }
    }

    default void startWatching(class_3244 handler) {
        if (this.holder().getAttachment() == this) {
            if (CompatStatus.IMMERSIVE_PORTALS) {
                VirtualEntityUtils.wrapCallWithContext(this.getWorld(), () -> this.holder().startWatching(handler));
            } else {
                this.holder().startWatching(handler);
            }
        }
    }

    default void startWatchingExtraPackets(class_3244 handler, Consumer<class_2596<class_2602>> packetConsumer) {};

    default void stopWatching(class_3222 handler) {
        if (this.holder().getAttachment() == this) {
            if (CompatStatus.IMMERSIVE_PORTALS) {
                VirtualEntityUtils.wrapCallWithContext(this.getWorld(), () -> this.holder().stopWatching(handler));
            } else {
                this.holder().stopWatching(handler);
            }
        }
    }

    default void stopWatching(class_3244 handler) {
        if (this.holder().getAttachment() == this) {
            if (CompatStatus.IMMERSIVE_PORTALS) {
                VirtualEntityUtils.wrapCallWithContext(this.getWorld(), () -> this.holder().stopWatching(handler));
            } else {
                this.holder().stopWatching(handler);
            }
        }
    }

    default void tick() {
        if (this.holder().getAttachment() == this) {
            if (CompatStatus.IMMERSIVE_PORTALS) {
                VirtualEntityUtils.wrapCallWithContext(this.getWorld(), () -> this.holder().tick());
            } else {
                this.holder().tick();
            }
        }
    }

    /**
     * This shouldn't change value once added to target!
     */
    default boolean shouldTick() {
        return true;
    }

    default boolean canUpdatePosition() {
        return true;
    }

    interface UpdateType {
        UpdateType POSITION = UpdateType.of("BlockState");

        static UpdateType of() {
            return new VoidUpdateType();
        }

        static UpdateType of(String type) {
            return new SimpleUpdateType(type);
        }
    }
}
