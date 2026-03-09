package eu.pb4.polymer.virtualentity.api.elements;

import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.impl.SafeBundler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.function.Predicate;
import net.minecraft.class_243;
import net.minecraft.class_2596;
import net.minecraft.class_2602;
import net.minecraft.class_3222;

public abstract class AbstractElement implements VirtualElement {
    private static final Predicate<class_3222> DEFAULT_VISIBILITY = p -> true;
    private ElementHolder holder;
    private class_243 offset = class_243.field_1353;
    @Nullable
    private class_243 overridePos;
    @Nullable
    protected class_243 lastSyncedPos;
    private InteractionHandler handler = InteractionHandler.EMPTY;

    protected Predicate<class_3222> elementVisiblityPredicate = DEFAULT_VISIBILITY;

    @Override
    public class_243 getOffset() {
        return this.offset;
    }

    @Override
    public void setOffset(class_243 offset) {
        this.offset = offset;
    }

    @Nullable
    public class_243 getOverridePos() {
        return this.overridePos;
    }

    @Nullable
    public void setOverridePos(class_243 vec3d) {
        this.overridePos = vec3d;
    }

    @Override
    public class_243 getLastSyncedPos() {
        return this.lastSyncedPos;
    }
    public void updateLastSyncedPos() {
        this.lastSyncedPos = getCurrentPos();
    }

    @Override
    public @Nullable ElementHolder getHolder() {
        return this.holder;
    }

    @Override
    public void setHolder(ElementHolder holder) {
        this.holder = holder;
    }

    @Override
    public InteractionHandler getInteractionHandler(class_3222 player) {
        return this.handler;
    }

    public void setInteractionHandler(InteractionHandler handler) {
        this.handler = handler;
    }

    public final void setVisibilityPredicate(Predicate<class_3222> predicate) {
        if (this.elementVisiblityPredicate == predicate) {
            return;
        }
        var oldPredicate = this.elementVisiblityPredicate;
        if (this.holder != null) {
            for (var player : this.holder.getWatchingPlayers()) {
                if (oldPredicate.test(player.method_32311()) && !predicate.test(player.method_32311())) {
                    var x = new SafeBundler(player::method_14364);
                    this.stopWatching(player.method_32311(), x);
                    x.finish();
                }
            }
        }
        this.elementVisiblityPredicate = predicate;
        if (this.holder != null) {
            for (var player : this.holder.getWatchingPlayers()) {
                if (!oldPredicate.test(player.method_32311()) && predicate.test(player.method_32311())) {
                    var x = new SafeBundler(player::method_14364);
                    this.startWatching(player.method_32311(), x);
                    x.finish();
                }
            }
        }
    }

    public final Predicate<class_3222> getVisibilityPredicate() {
        return this.elementVisiblityPredicate;
    }

    public void sendPacket(class_2596<? extends class_2602> packet) {
        if (this.holder != null) {
            this.holder.sendPacket(packet, DEFAULT_VISIBILITY);
        }
    }

    public void sendPacket(class_2596<? extends class_2602> packet, Predicate<class_3222> predicate) {
        if (this.holder != null) {
            this.holder.sendPacket(packet, predicate.and(DEFAULT_VISIBILITY));
        }
    }
}
