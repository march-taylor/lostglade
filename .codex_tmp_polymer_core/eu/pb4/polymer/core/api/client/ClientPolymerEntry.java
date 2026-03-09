package eu.pb4.polymer.core.api.client;

import eu.pb4.polymer.core.impl.client.ClientPolymerEntryImpl;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.class_2378;
import net.minecraft.class_2960;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public interface ClientPolymerEntry<T> {
    class_2960 identifier();
    @Nullable T registryEntry();

    static <T> ClientPolymerEntry<T> of(class_2960 identifier, @Nullable T registryEntry) {
        return new ClientPolymerEntryImpl<>(identifier, registryEntry);
    }

    static <T> ClientPolymerEntry<T> of(class_2960 identifier, class_2378<T> registry) {
        return new ClientPolymerEntryImpl<>(identifier, registry.method_10250(identifier) ? registry.method_63535(identifier) : null);
    }

    static <T> ClientPolymerEntry<T> of(class_2960 identifier) {
        return new ClientPolymerEntryImpl<>(identifier, null);
    }
}
