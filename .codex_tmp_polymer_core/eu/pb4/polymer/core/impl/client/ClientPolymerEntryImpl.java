package eu.pb4.polymer.core.impl.client;

import eu.pb4.polymer.core.api.client.ClientPolymerEntry;
import net.minecraft.class_2960;
import org.jetbrains.annotations.Nullable;

public record ClientPolymerEntryImpl<T>(class_2960 identifier, @Nullable T registryEntry) implements ClientPolymerEntry<T> {
}
