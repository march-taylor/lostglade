package eu.pb4.polymer.virtualentity.api.attachment;

import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.impl.HolderAttachmentHolder;
import eu.pb4.polymer.virtualentity.mixin.accessors.TrackedEntityAccessor;
import eu.pb4.polymer.virtualentity.mixin.accessors.ChunkMapAccessor;
import java.util.Collection;
import net.minecraft.class_1297;
import net.minecraft.class_243;
import net.minecraft.class_3218;
import net.minecraft.class_3244;
import net.minecraft.class_3898;

@SuppressWarnings("ClassCanBeRecord")
public class EntityAttachment implements HolderAttachment {
    protected final class_1297 entity;
    private final ElementHolder holder;
    private final boolean autoTick;

    private boolean removed = false;

    public EntityAttachment(ElementHolder holder, class_1297 entity, boolean autoTick) {
        this.entity = entity;
        this.holder = holder;
        this.autoTick = autoTick;
        if (this.getClass() == EntityAttachment.class) {
            this.attach();
        }
    }

    protected void attach() {
        this.holder.setAttachment(this);
        ((HolderAttachmentHolder) entity).polymerVE$addHolder(this);
    }

    public static EntityAttachment of(ElementHolder holder, class_1297 entity) {
        return new EntityAttachment(holder, entity, false);
    }

    public static EntityAttachment ofTicking(ElementHolder holder, class_1297 entity) {
        return new EntityAttachment(holder, entity, true);
    }

    @Override
    public ElementHolder holder() {
        return this.holder;
    }

    @Override
    public void destroy() {
        if (this.removed) return;
        this.removed = true;

        ((HolderAttachmentHolder) entity).polymerVE$removeHolder(this);
        if (this.holder.getAttachment() == this) {
            this.holder.setAttachment(null);
        }
    }

    @Override
    public void tick() {
        if (this.removed) return;

        if (this.autoTick) {
            this.holder.tick();
        }
    }

    @Override
    public void updateCurrentlyTracking(Collection<class_3244> currentlyTracking) {
        if (this.removed) return;

        if (this.holder.getAttachment() != this) {
            return;
        }

        var entry = getTrackerEntry();

        if (entry == null) {
            for (var x : currentlyTracking) {
                this.holder.stopWatching(x);
            }
            return;
        }

        var watching = ((TrackedEntityAccessor) entry).getSeenBy();

        for (var player : currentlyTracking) {
            if (!watching.contains(player)) {
                this.holder.stopWatching(player);
            }
        }

        for (var x : watching) {
            this.holder.startWatching(x.method_32311().field_13987);
        }
    }

    @Override
    public boolean canUpdatePosition() {
        return !this.removed && !this.entity.method_31481() && this.entity.method_73183().method_8469(this.entity.method_5628()) == this.entity;
    }

    @Override
    public void updateTracking(class_3244 tracking) {
        // left that to impl logic
    }

    private class_3898.class_3208 getTrackerEntry() {
        return ((ChunkMapAccessor) ((class_3218) this.entity.method_73183()).method_14178().field_17254).getEntityTrackers().get(this.entity.method_5628());
    }

    @Override
    public class_243 getPos() {
        return this.entity.method_73189();
    }

    @Override
    public class_3218 getWorld() {
        return (class_3218) this.entity.method_73183();
    }


    @Override
    public boolean shouldTick() {
        return this.autoTick;
    }

    @Override
    public boolean isRemoved() {
        return this.removed;
    }
}
