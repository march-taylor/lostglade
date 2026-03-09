package eu.pb4.polymer.core.mixin.block.packet;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.class_3898;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(class_3898.class)
public interface ServerMapAccessor {
    @Accessor("entityMap")
    Int2ObjectMap<class_3898.class_3208> polymer$getEntityTrackers();
}
