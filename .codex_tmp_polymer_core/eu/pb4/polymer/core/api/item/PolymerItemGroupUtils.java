package eu.pb4.polymer.core.api.item;

import eu.pb4.polymer.common.api.events.SimpleEvent;
import eu.pb4.polymer.common.impl.CommonImplUtils;
import eu.pb4.polymer.core.api.utils.PolymerRegistry;
import eu.pb4.polymer.core.impl.InternalServerRegistry;
import eu.pb4.polymer.core.impl.PolymerImpl;
import eu.pb4.polymer.core.impl.interfaces.CreativeModeTabExtra;
import java.util.*;
import net.minecraft.class_1761;
import net.minecraft.class_1799;
import net.minecraft.class_2960;
import net.minecraft.class_3222;
import net.minecraft.class_5321;
import net.minecraft.class_7225;
import net.minecraft.class_7699;
import net.minecraft.class_7706;
import net.minecraft.class_7923;
import net.minecraft.class_7924;


/**
 * A server side item group that can be synchronized with polymer clients
 * It also has its own server side functionality
 */
public final class PolymerItemGroupUtils {
    public static final PolymerRegistry<class_1761> REGISTRY = InternalServerRegistry.ITEM_GROUPS;
    /**
     * Even called on synchronization of ItemGroups
     */
    public static final SimpleEvent<ItemGroupEventListener> LIST_EVENT = new SimpleEvent<>();
    private static final Map<ItemGroupKey, Contents> CONTENT_CACHE = new HashMap<>();

    private PolymerItemGroupUtils() {
    }

    public static Contents getContentsFor(class_3222 player, class_1761 group) {
        return getContentsFor(group, player.method_51469().method_8503().method_30611(), player.method_51469().method_45162(), CommonImplUtils.permissionCheck(player, "op_items", 2));
    }

    public static Contents getContentsFor(class_1761 group, class_7225.class_7874 lookup, class_7699 featureSet, boolean operator) {
        var key = new ItemGroupKey(getId(group), operator);
        var value = CONTENT_CACHE.get(key);
        if (value == null) {
            try {
                 value = ((CreativeModeTabExtra) group).polymer$getContentsWith(getId(group), featureSet, operator, lookup);
            } catch (Throwable t) {
                // Some mods use client classes in their item groups because vanilla doesn't call them on the server anymore
                // Catch instead of letting the game crash, even though it's their fault...
                PolymerImpl.LOGGER.warn("Failed to load contents for an ItemGroup", t);
                value = new Contents(List.of(), List.of());
            }
            CONTENT_CACHE.put(key, value);
        }
        return value;
    }

    /**
     * Returns list of ItemGroups accessible by player
     */
    public static List<class_1761> getItemGroups(class_3222 player) {
        var list = new LinkedHashSet<class_1761>();

        for (var g : class_7706.method_47341()) {
            try {
                if (g.method_47312() == class_1761.class_7916.field_41052 && ((CreativeModeTabExtra) g).polymer$isSyncable()) {
                    list.add(g);
                }
            } catch (Throwable e) {
                PolymerImpl.LOGGER.warn("Something broke!", e);
            }
        }

        for (var g : InternalServerRegistry.ITEM_GROUPS) {
            try {
                if (g.method_47312() == class_1761.class_7916.field_41052 && ((CreativeModeTabExtra) g).polymer$isSyncable()) {
                    list.add(g);
                }
            } catch (Throwable e) {
                PolymerImpl.LOGGER.warn("Something broke!", e);
            }
        }

        var sync = new PolymerItemGroupUtils.ItemGroupListBuilder() {
            @Override
            public void add(class_1761 group) {
                list.add(group);
            }

            @Override
            public void remove(class_1761 group) {
                list.remove(group);
            }
        };

        PolymerItemGroupUtils.LIST_EVENT.invoke((x) -> x.onItemGroupGet(player, sync));

        return new ArrayList<>(list);
    }

    public static boolean isPolymerItemGroup(class_1761 group) {
        return InternalServerRegistry.ITEM_GROUPS.containsEntry(group);
    }

    public static class_1761.class_7913 builder() {
        return new class_1761.class_7913(class_1761.class_7915.field_41050, -1);
    }

    public static void registerPolymerItemGroup(class_2960 identifier, class_1761 group) {
        if (class_7923.field_44687.method_10250(identifier)) {
            PolymerImpl.LOGGER.warn("ItemGroup '{}' is already registered in vanilla registry!", identifier);
        } else if (contains(identifier)) {
            PolymerImpl.LOGGER.warn("ItemGroup '{}' is already registered under the same id!", identifier);
        } else if (isPolymerItemGroup(group)) {
            PolymerImpl.LOGGER.warn("ItemGroup '{}' is already registered as '{}'! ", identifier, REGISTRY.method_10206(group));
        } else {
            InternalServerRegistry.ITEM_GROUPS.set(identifier, group);
        }
    }

    public static Boolean contains(class_2960 identifier) { return InternalServerRegistry.ITEM_GROUPS.contains(identifier); }

    public static void registerPolymerItemGroup(class_5321<class_1761> identifier, class_1761 group) {
        registerPolymerItemGroup(identifier.method_29177(), group);
    }

    public static class_2960 getId(class_1761 group) {
        var x = REGISTRY.getEntryId(group);

        if (x == null) {
            return class_7923.field_44687.method_10221(group);
        }
        return x;
    }

    public static class_5321<class_1761> getKey(class_1761 group) {
        var x = REGISTRY.getEntryId(group);

        if (x == null) {
            return class_7923.field_44687.method_29113(group).orElseThrow();
        }
        return class_5321.method_29179(class_7924.field_44688, x);
    }

    public static void invalidateItemGroupCache() {
        CONTENT_CACHE.clear();
    }

    @FunctionalInterface
    public interface ItemGroupEventListener {
        void onItemGroupGet(class_3222 player, ItemGroupListBuilder builder);
    }

    public interface ItemGroupListBuilder {
        void add(class_1761 group);

        void remove(class_1761 group);
    }

    public record Contents(Collection<class_1799> main, Collection<class_1799> search) {
    }

    private record ItemGroupKey(class_2960 identifier, boolean operator) {}
}
