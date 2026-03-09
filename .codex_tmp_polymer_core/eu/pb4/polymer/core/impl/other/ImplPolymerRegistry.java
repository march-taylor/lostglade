package eu.pb4.polymer.core.impl.other;

import eu.pb4.polymer.core.api.utils.PolymerRegistry;
import eu.pb4.polymer.core.impl.PolymerImpl;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.class_2960;

@ApiStatus.Internal
public class ImplPolymerRegistry<T> implements PolymerRegistry<T> {
    private final Map<class_2960, T> entryMap = new Object2ObjectOpenHashMap<>();
    private final Int2ObjectMap<T> rawIdMap = new Int2ObjectOpenHashMap<>();
    private final Object2IntMap<T> entryIdMap = new Object2IntOpenHashMap<>();
    private final Map<T, class_2960> identifierMap = new Object2ObjectOpenHashMap<>();
    private final Map<class_2960, Set<T>> tags = new Object2ObjectOpenHashMap<>();
    private final List<T> entries = new ArrayList<>();
    private final T defaultValue;
    private final class_2960 defaultIdentifier;
    private final String name;
    private final String shortName;

    private int currentId = 0;
    private final Map<T, Set<class_2960>> entryTags = new Object2ObjectOpenHashMap<>();
    private final WeakHashMap<class_2960, Key> keys = new WeakHashMap<>();

    public ImplPolymerRegistry(String name, String shortName) {
        this(name, shortName, null, null);
    }

    public ImplPolymerRegistry(String name, String shortName, @Nullable class_2960 defaultIdentifier, @Nullable T defaultValue) {
        this.name = name;
        this.shortName = shortName;
        this.defaultValue = defaultValue;
        this.defaultIdentifier = defaultIdentifier;

        this.rawIdMap.defaultReturnValue(this.defaultValue);
        this.entryIdMap.defaultReturnValue(-1);
    }

    public String getName() {
        return name;
    }

    public String getShortName() {
        return shortName;
    }

    @Override
    public T get(class_2960 identifier) {
        return this.entryMap.getOrDefault(identifier, this.defaultValue);
    }

    @Override
    public T method_10200(int id) {
        try {
            return this.rawIdMap.get(id);
        } catch (Throwable e) {
            if (PolymerImpl.LOG_INVALID_SERVER_IDS_CLIENT) {
                e.printStackTrace();
            }
            return this.defaultValue;
        }
    }


    @Nullable
    @Override
    public T getDirect(class_2960 identifier) {
        return this.entryMap.get(identifier);
    }

    @Override
    public class_2960 getEntryId(T entry) {
        return this.identifierMap.getOrDefault(entry, this.defaultIdentifier);
    }

    @Override
    public int method_10206(T entry) {
        return this.entryIdMap.getInt(entry);
    }

    @Override
    public int method_10204() {
        return this.entryMap.size();
    }

    @Override
    public boolean contains(class_2960 id) {
        return this.entryMap.containsKey(id);
    }

    @Override
    public boolean containsEntry(T entry) {
        return this.entryIdMap.containsKey(entry);
    }

    @Override
    public Stream<T> stream() {
        return this.entries.stream();
    }

    public void set(class_2960 identifier, int rawId, T entry) {
        this.entryMap.put(identifier, entry);
        this.identifierMap.put(entry, identifier);
        this.rawIdMap.put(rawId, entry);
        this.entryIdMap.put(entry, rawId);
        this.entries.add(entry);
        this.currentId = Math.max(this.currentId, rawId) + 1;
    }

    public void set(class_2960 identifier, T entry) {
        this.set(identifier, this.currentId, entry);
    }

    public void clear() {
        this.rawIdMap.clear();
        this.identifierMap.clear();
        this.entryMap.clear();
        this.entryIdMap.clear();
        this.entries.clear();
        this.currentId = 0;
    }

    @NotNull
    @Override
    public Iterator<T> iterator() {
        return this.entries.iterator();
    }

    public Iterable<class_2960> ids() {
        return () -> ImplPolymerRegistry.this.identifierMap.values().iterator();
    }

    @Override
    public Iterable<Map.Entry<class_2960, T>> entries() {
        return () -> this.entryMap.entrySet().iterator();
    }

    @Override
    public Set<T> getTag(class_2960 tag) {
        var x = this.tags.get(tag);
        return x != null ? x : Collections.emptySet();
    }

    @Override
    public Collection<class_2960> getTags() {
        return this.tags.keySet();
    }

    @Override
    public Collection<class_2960> getTagsOf(T entry) {
        var list = this.entryTags.get(entry);
        return list == null ? Collections.emptySet() : list;
    }

    public void createTag(class_2960 tag, IntList ids) {
        var set = new HashSet<T>();
        ids.forEach((id) -> {
            var obj = this.rawIdMap.get(id);
            if (obj != null) {
                set.add(obj);
                this.entryTags.computeIfAbsent(obj, (x) -> new HashSet<>()).add(tag);
            }
        });

        this.tags.put(tag, set);
    }

    public void remove(T entry) {
        if (this.identifierMap.containsKey(entry)) {
            var id = this.identifierMap.get(entry);
            var rawId = this.entryIdMap.getInt(entry);
            this.rawIdMap.remove(rawId);
            this.entryMap.remove(id);
            this.entryIdMap.removeInt(entry);
            this.identifierMap.remove(entry);
            this.entryTags.remove(entry);
            this.entries.remove(entry);
        }
    }

    public void removeIf(Predicate<T> removePredicate) {
        for (var x : new ArrayList<>(this.rawIdMap.values())) {
            if (removePredicate.test(x)) {
                remove(x);
            }
        }
    }

    public Key getKey(class_2960 id) {
        var key = this.getEntryId(this.get(id));

        if (key == null) {
            return Key.EMPTY;
        }

        return this.keys.computeIfAbsent(id, Key::new);
    }

    public record Key(class_2960 identifier) {
        public static final Key EMPTY = new Key(class_2960.method_60654("polymer:empty"));
    }
}
