package eu.pb4.polymer.virtualentity.api.tracker;

import eu.pb4.polymer.common.impl.entity.InternalEntityHelpers;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import org.apache.commons.lang3.ObjectUtils;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.class_1299;
import net.minecraft.class_2940;
import net.minecraft.class_2945;

public class SimpleDataTracker implements DataTrackerLike {
    private final Entry<?>[] entries;
    private boolean dirty;

    @SuppressWarnings("rawtypes")
    public SimpleDataTracker(class_1299<?> baseEntity) {
        var entries = InternalEntityHelpers.getExampleTrackedDataOfEntityType(baseEntity);
        this.entries = new Entry[entries.length];

        for (int i = 0; i < entries.length; i++) {
            var x = entries[i];
            //noinspection unchecked
            this.entries[i] = new Entry(x.method_12797(), x.method_12794());
        }
    }

    @Override
    public <T> T get(class_2940<T> data) {
        var entry = this.getEntry(data);
        return entry != null ? entry.get() : null;
    }

    @Nullable
    public <T> Entry<T> getEntry(class_2940<T> data) {
        if (data.comp_2327() > this.entries.length) {
            return null;
        }

        var x = this.entries[data.comp_2327()];

        if (x.data != data) {
            return null;
        }

        //noinspection unchecked
        return (Entry<T>) x;
    }

    @Override
    public boolean isDirty(class_2940<?> key) {
        var x = getEntry(key);
        return x != null && x.isDirty();
    }

    @Override
    public <T> void set(class_2940<T> key, T value, boolean forceDirty) {
        var entry = getEntry(key);
        if (entry != null && (forceDirty || ObjectUtils.notEqual(value, entry.get()))) {
            entry.set(value);
            entry.setDirty(true);
            this.dirty = true;
        }
    }

    @Override
    public <T> void setDirty(class_2940<T> key, boolean isDirty) {
        var entry = getEntry(key);
        if (entry != null) {
            entry.setDirty(isDirty);
            this.dirty |= isDirty;
        }
    }

    @Override
    public boolean isDirty() {
        return this.dirty;
    }

    @Override
    @Nullable
    public List<class_2945.class_7834<?>> getDirtyEntries() {
        List<class_2945.class_7834<?>> list = null;
        if (this.dirty) {
            for (int i = 0; i < this.entries.length; i++) {
                var entry = this.entries[i];
                if (entry.isDirty()) {
                    entry.setDirty(false);
                    if (list == null) {
                        list = new ArrayList<>();
                    }

                    list.add(entry.toSerialized());
                }
            }
        }

        this.dirty = false;
        return list;
    }

    @Override
    @Nullable
    public List<class_2945.class_7834<?>> getChangedEntries() {
        List<class_2945.class_7834<?>> list = null;
        for (int i = 0; i < this.entries.length; i++) {
            var entry = this.entries[i];
            if (!entry.isUnchanged()) {
                if (list == null) {
                    list = new ArrayList<>();
                }

                list.add(entry.toSerialized());
            }
        }

        return list;
    }

    public static class Entry<T> {
        final class_2940<T> data;
        private final T initialValue;
        T value;
        private boolean dirty;

        public Entry(class_2940<T> data, T value) {
            this.data = data;
            this.initialValue = value;
            this.value = value;
        }

        public class_2940<T> getData() {
            return this.data;
        }

        public void set(T value) {
            this.value = value;
        }

        public T get() {
            return this.value;
        }

        public boolean isDirty() {
            return this.dirty;
        }

        public void setDirty(boolean dirty) {
            this.dirty = dirty;
        }

        public boolean isUnchanged() {
            return this.initialValue.equals(this.value);
        }

        public class_2945.class_7834<T> toSerialized() {
            return class_2945.class_7834.method_46360(this.data, this.value);
        }
    }
}