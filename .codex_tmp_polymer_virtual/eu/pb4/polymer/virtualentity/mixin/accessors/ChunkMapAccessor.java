package eu.pb4.polymer.virtualentity.mixin.accessors;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.minecraft.class_3193;
import net.minecraft.class_3898;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(class_3898.class)
public interface ChunkMapAccessor {
    @Accessor("entityMap")
    Int2ObjectMap<class_3898.class_3208> getEntityTrackers();

    @Accessor("serverViewDistance")
    int getWatchDistance();

    @Accessor
    Long2ObjectLinkedOpenHashMap<class_3193> getVisibleChunkMap();
}
