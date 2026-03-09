package eu.pb4.polymer.virtualentity.api.tracker;

import eu.pb4.polymer.common.mixin.SyncedEntityDataAccessor;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import net.minecraft.class_2940;
import net.minecraft.class_2945;

public interface DataTrackerLike {
    @Nullable
    <T> T get(class_2940<T> data);

    @Nullable

    default <T> void set(class_2940<T> key, T value) {
        set(key, value, false);
    }

    <T> void set(class_2940<T> key, T value, boolean forceDirty);

    <T> void setDirty(class_2940<T> key, boolean isDirty);

    boolean isDirty();

    boolean isDirty(class_2940<?> key);

    @Nullable
    List<class_2945.class_7834<?>> getDirtyEntries();

    @Nullable
    List<class_2945.class_7834<?>> getChangedEntries();

    static DataTrackerLike wrap(class_2945 dataTracker) {
        return new DataTrackerLike() {
            @Override
            public <T> @Nullable T get(class_2940<T> data) {
                return dataTracker.method_12789(data);
            }

            @Override
            public <T> void set(class_2940<T> key, T value, boolean forceDirty) {
                dataTracker.method_49743(key, value, forceDirty);
            }

            @Override
            public <T> void setDirty(class_2940<T> key, boolean isDirty) {
                dataTracker.method_49743(key, dataTracker.method_12789(key), isDirty);
            }

            @Override
            public boolean isDirty() {
                return dataTracker.method_12786();
            }

            @Override
            public boolean isDirty(class_2940<?> key) {
                return ((SyncedEntityDataAccessor) dataTracker).getItemsById()[key.comp_2327()].method_12796();
            }

            @Override
            public @Nullable List<class_2945.class_7834<?>> getDirtyEntries() {
                return dataTracker.method_12781();
            }

            @Override
            public @Nullable List<class_2945.class_7834<?>> getChangedEntries() {
                return dataTracker.method_46357();
            }
        };
    }
}