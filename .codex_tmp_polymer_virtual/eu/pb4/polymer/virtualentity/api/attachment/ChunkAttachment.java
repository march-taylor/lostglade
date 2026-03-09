package eu.pb4.polymer.virtualentity.api.attachment;

import eu.pb4.polymer.common.impl.CommonImpl;
import eu.pb4.polymer.common.impl.CompatStatus;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.VirtualEntityUtils;
import eu.pb4.polymer.virtualentity.impl.HolderAttachmentHolder;
import eu.pb4.polymer.virtualentity.impl.compat.ImmersivePortalsUtils;
import eu.pb4.polymer.virtualentity.mixin.block.WorldChunkAccessor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.class_2338;
import net.minecraft.class_243;
import net.minecraft.class_2818;
import net.minecraft.class_3215;
import net.minecraft.class_3218;
import net.minecraft.class_3222;
import net.minecraft.class_3244;

@SuppressWarnings("ClassCanBeRecord")
public class ChunkAttachment implements HolderAttachment {
    private final ElementHolder holder;
    private final class_2818 chunk;
    protected class_243 pos;
    private final boolean autoTick;
    private volatile boolean removed = false;

    public ChunkAttachment(ElementHolder holder, class_2818 chunk, class_243 position, boolean autoTick) {
        this.chunk = chunk;
        this.pos = position;
        this.holder = holder;
        this.autoTick = autoTick;
        this.attach();
    }

    protected void attach() {
        ((HolderAttachmentHolder) chunk).polymerVE$addHolder(this);
        this.holder.setAttachment(this);
    }

    public static HolderAttachment of(ElementHolder holder, class_3218 world, class_2338 pos) {
        return of(holder, world, class_243.method_24953(pos));
    }

    public static HolderAttachment ofTicking(ElementHolder holder, class_3218 world, class_2338 pos) {
        return ofTicking(holder, world, class_243.method_24953(pos));
    }

    public static HolderAttachment of(ElementHolder holder, class_3218 world, class_243 pos) {
        var chunk = world.method_22350(class_2338.method_49638(pos));

        if (chunk instanceof class_2818 chunk1) {
            return new ChunkAttachment(holder, chunk1, pos, false);
        } else {
            CommonImpl.LOGGER.warn("Some mod tried to attach to chunk at " + class_2338.method_49638(pos).method_23854() + ", but it isn't loaded!", new NullPointerException());
            return new ManualAttachment(holder, world, () -> pos);
        }
    }

    public static HolderAttachment ofTicking(ElementHolder holder, class_3218 world, class_243 pos) {
        var chunk = world.method_22350(class_2338.method_49638(pos));

        if (chunk instanceof class_2818 chunk1) {
            return new ChunkAttachment(holder, chunk1, pos, true);
        } else {
            CommonImpl.LOGGER.warn("Some mod tried to attach to chunk at " + class_2338.method_49638(pos).method_23854() + ", but it isn't loaded!", new NullPointerException());
            return new ManualAttachment(holder, world, () -> pos);
        }
    }

    @Override
    public ElementHolder holder() {
        return this.holder;
    }

    @Override
    public void destroy() {
        if (this.removed) return;
        this.removed = true;

        ((HolderAttachmentHolder) chunk).polymerVE$removeHolder(this);
        if (this.holder.getAttachment() == this) {
            this.holder.setAttachment(null);
        }
    }

    @Override
    public void tick() {
        if (this.removed) return;
        if (this.autoTick) {
            this.holder().tick();
        }
    }

    @Override
    public boolean shouldTick() {
        return this.autoTick;
    }

    @Override
    public void updateCurrentlyTracking(Collection<class_3244> currentlyTracking) {
        if (this.removed || !((WorldChunkAccessor) chunk).isLoaded()) return;

        List<class_3244> watching = new ArrayList<>();

        for (class_3222 x : getPlayersWatchingChunk(chunk)) {
            class_3244 networkHandler = x.field_13987;
            watching.add(networkHandler);
        }

        for (var player : currentlyTracking) {
            if (!watching.contains(player)) {
                this.holder.stopWatching(player);
            }
        }

        for (var x : watching) {
            this.holder.startWatching(x.method_32311().field_13987);
        }
    }

    private static List<class_3222> getPlayersWatchingChunk(class_2818 chunk) {
        if (CompatStatus.IMMERSIVE_PORTALS) {
            return ImmersivePortalsUtils.getPlayerTracking(chunk);
        } else {
            return ((class_3215) chunk.method_12200().method_8398()).field_17254.method_17210(chunk.method_12004(), false);
        }
    }

    @Override
    public void updateTracking(class_3244 tracking) {
        if (this.removed) return;
        if (tracking.field_14140.method_29504() || !VirtualEntityUtils.isPlayerTracking(tracking.method_32311(), this.chunk)) {
            this.stopWatching(tracking);
        }
    }

    @Override
    public class_243 getPos() {
        return this.pos;
    }

    @Override
    public class_3218 getWorld() {
        return (class_3218) this.chunk.method_12200();
    }

    public class_2818 getChunk() {
        return this.chunk;
    }

    @Override
    public boolean isRemoved() {
        return this.removed;
    }
}
