package eu.pb4.polymer.virtualentity.api.attachment;

import eu.pb4.polymer.virtualentity.api.ElementHolder;
import java.util.Collection;
import java.util.Objects;
import java.util.function.Supplier;
import net.minecraft.class_243;
import net.minecraft.class_3218;
import net.minecraft.class_3244;

public final class ManualAttachment implements HolderAttachment {
    private final ElementHolder holder;
    private final class_3218 world;
    private final Supplier<class_243> posSupplier;

    public ManualAttachment(ElementHolder holder, class_3218 world, Supplier<class_243> posSupplier) {
        this.holder = holder;
        this.world = world;
        this.posSupplier = posSupplier;
        holder.setAttachment(this);
    }

    @Override
    public void destroy() {
        if (this.holder.getAttachment() == this) {
            this.holder.setAttachment(null);
        }
    }

    @Override
    public class_243 getPos() {
        return this.posSupplier.get();
    }

    @Override
    public class_3218 getWorld() {
        return this.world;
    }

    @Override
    public void updateCurrentlyTracking(Collection<class_3244> currentlyTracking) {
    }

    @Override
    public void updateTracking(class_3244 tracking) {
    }

    @Override
    public ElementHolder holder() {
        return holder;
    }

    public class_3218 world() {
        return world;
    }

    public Supplier<class_243> posSupplier() {
        return posSupplier;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (ManualAttachment) obj;
        return Objects.equals(this.holder, that.holder) &&
                Objects.equals(this.world, that.world) &&
                Objects.equals(this.posSupplier, that.posSupplier);
    }

    @Override
    public int hashCode() {
        return Objects.hash(holder, world, posSupplier);
    }

    @Override
    public String toString() {
        return "ManualAttachment[" +
                "holder=" + holder + ", " +
                "world=" + world + ", " +
                "posSupplier=" + posSupplier + ']';
    }

}
