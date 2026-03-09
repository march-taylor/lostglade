package eu.pb4.polymer.core.impl.interfaces;

import eu.pb4.polymer.core.api.utils.PolymerSyncedObject;
import java.util.List;
import java.util.Map;
import net.minecraft.class_2378;
import net.minecraft.class_6862;
import net.minecraft.class_6885;

public interface RegistryExtension<T> {
    static <T> List<T> getPolymerEntries(class_2378<T> registry) {
        return ((RegistryExtension<T>) registry).polymer$getEntries();
    }

    Map<class_6862<T>, class_6885.class_6888<T>> polymer$getTagsInternal();
    List<T> polymer$getEntries();

    void polymer$setOverlay(T value, PolymerSyncedObject<T> syncedObject);
    PolymerSyncedObject<T> polymer$getOverlay(T value);
}
