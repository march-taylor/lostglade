package eu.pb4.polymer.virtualentity.api.tracker;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import net.minecraft.class_2940;
import net.minecraft.class_2945;

public class WrappingDataTracker implements DataTrackerLike {
    private final DataTrackerLike dataTracker;

    public WrappingDataTracker(DataTrackerLike tracker) {
        this.dataTracker = tracker;
    }
    
    @Override
    public <T> @Nullable T get(class_2940<T> data) {
        return dataTracker.get(data);
    }

    @Override
    public <T> void set(class_2940<T> key, T value, boolean forceDirty) {
        dataTracker.set(key, value, forceDirty);
    }

    @Override
    public <T> void setDirty(class_2940<T> key, boolean isDirty) {
        dataTracker.set(key, dataTracker.get(key), isDirty);
    }

    @Override
    public boolean isDirty() {
        return dataTracker.isDirty();
    }

    @Override
    public boolean isDirty(class_2940<?> key) {
        return dataTracker.isDirty(key);
    }

    @Override
    public @Nullable List<class_2945.class_7834<?>> getDirtyEntries() {
        return dataTracker.getDirtyEntries();
    }

    @Override
    public @Nullable List<class_2945.class_7834<?>> getChangedEntries() {
        return dataTracker.getChangedEntries();
    }
}
