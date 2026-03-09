package eu.pb4.polymer.core.impl.other;

import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.class_9323;
import net.minecraft.class_9326;
import net.minecraft.class_9331;

public record ComponentChangesMap(class_9326 changes) implements class_9323 {
    @Nullable
    @Override
    public <T> T method_58694(class_9331<? extends T> type) {
        var x = this.changes.method_57845(type);
        //noinspection OptionalAssignedToNull
        return x != null ? x.orElse(null) : null;
    }

    @Override
    public Set<class_9331<?>> method_57831() {
        var set = new HashSet<class_9331<?>>();
        for (var entry : this.changes.method_57846()) {
            if (entry.getValue().isPresent()) {
                set.add(entry.getKey());
            }
        }
        return set;
    }
}
