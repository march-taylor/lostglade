package eu.pb4.polymer.core.api.utils;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import net.minecraft.class_2359;
import net.minecraft.class_2960;

@ApiStatus.NonExtendable
public interface PolymerRegistry<T> extends class_2359<T> {
    @Nullable
    T get(class_2960 identifier);

    @Nullable
    T method_10200(int id);

    @Nullable
    T getDirect(class_2960 identifier);

    @Nullable
    class_2960 getEntryId(T entry);
    @Override
    int method_10206(T entry);
    Iterable<class_2960> ids();
    Iterable<Map.Entry<class_2960, T>> entries();

    Set<T> getTag(class_2960 tag);
    Collection<class_2960> getTags();
    Collection<class_2960> getTagsOf(T entry);
    int method_10204();

    boolean contains(class_2960 id);
    boolean containsEntry(T entry);

    Stream<T> stream();
}
