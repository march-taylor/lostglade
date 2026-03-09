package eu.pb4.polymer.core.api.other;

import eu.pb4.polymer.core.api.utils.PolymerObject;
import eu.pb4.polymer.rsm.api.RegistrySyncUtils;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.class_2378;
import net.minecraft.class_2561;
import net.minecraft.class_2960;
import net.minecraft.class_3446;
import net.minecraft.class_3468;
import net.minecraft.class_7923;

public final class PolymerStat {
    private static final Map<class_2960, class_2561> NAMES = new HashMap<>();

    /**
     * Register a custom server-compatible statistic.
     * Registering a {@link net.minecraft.class_3445} in the vanilla way will cause clients to disconnect when opening the statistics screen.
     *
     * @param id        the Identifier for the stat
     * @param formatter the formatter for the stat to use
     * @return the PolymerStat ({@link class_2960}) for the custom stat
     */
    public static class_2960 registerStat(String id, class_3446 formatter) {
        return registerStat(id, class_2561.method_43471("stat." + id.replace(':', '.')), formatter);
    }

    /**
     * Register a custom server-compatible statistic.
     * Registering a {@link net.minecraft.class_3445} in the vanilla way will cause clients to disconnect when opening the statistics screen.
     *
     * @param id        the Identifier for the stat
     * @param name      the name used in /polymer stats
     * @param formatter the formatter for the stat to use
     * @return the PolymerStat ({@link class_2960}) for the custom stat
     */
    public static class_2960 registerStat(String id, class_2561 name, class_3446 formatter) {
        var idx = class_2960.method_60654(id);
        class_2378.method_10230(class_7923.field_41183, idx, idx);
        class_3468.field_15419.method_14955(idx, formatter);
        //noinspection unchecked
        RegistrySyncUtils.setServerEntry((class_2378<Object>) (Object) class_7923.field_41183, (Object) idx);
        NAMES.put(idx, name);
        return idx;
    }

    /**
     * Register a custom server-compatible statistic.
     * Registering a {@link net.minecraft.class_3445} in the vanilla way will cause clients to disconnect when opening the statistics screen.
     *
     * @param id        the Identifier for the stat
     * @param formatter the formatter for the stat to use
     * @return the PolymerStat ({@link class_2960}) for the custom stat
     */
    public static class_2960 registerStat(class_2960 id, class_3446 formatter) {
        return registerStat(id.toString(), formatter);
    }

    /**
     * Register a custom server-compatible statistic.
     * Registering a {@link net.minecraft.class_3445} in the vanilla way will cause clients to disconnect when opening the statistics screen.
     *
     * @param id        the Identifier for the stat
     * @param name      the name used in /polymer stats
     * @param formatter the formatter for the stat to use
     * @return the PolymerStat ({@link class_2960}) for the custom stat
     */
    public static class_2960 registerStat(class_2960 id, class_2561 name, class_3446 formatter) {
        return registerStat(id.toString(), name, formatter);
    }


    public static class_2561 getName(class_2960 identifier) {
        return NAMES.getOrDefault(identifier, class_2561.method_43473());
    }
}
