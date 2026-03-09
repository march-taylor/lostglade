package eu.pb4.polymer.core.impl.interfaces;

import it.unimi.dsi.fastutil.shorts.ShortSet;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import net.minecraft.class_2338;
import net.minecraft.class_4076;

@ApiStatus.Internal
public interface PolymerBlockPosStorage {
    @Nullable
    ShortSet polymer$getBackendSet();

    @Nullable
    Iterator<class_2338.class_2339> polymer$iterator(class_4076 sectionPos);

    @Nullable
    Iterator<class_2338.class_2339> polymer$iterator();

    void polymer$setSynced(int x, int y, int z, boolean lightSource);
    void polymer$removeSynced(int x, int y, int z);

    boolean polymer$isSynced(int x, int y, int z);

    boolean polymer$hasAny();

    static short pack(int x, int y, int z) {
        return (short) ((x & 15) << 8 | (z & 15) << 4 | (y & 15));
    }
    boolean polymer$hasLights();
    boolean polymer$requireLights();
    void polymer$setRequireLights(boolean value);
}
