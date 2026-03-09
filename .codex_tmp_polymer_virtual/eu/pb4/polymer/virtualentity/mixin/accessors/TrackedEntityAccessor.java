package eu.pb4.polymer.virtualentity.mixin.accessors;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Set;
import net.minecraft.class_3231;
import net.minecraft.class_3898;
import net.minecraft.class_5629;

@Mixin(class_3898.class_3208.class)
public interface TrackedEntityAccessor {
    @Accessor
    Set<class_5629> getSeenBy();

    @Accessor
    class_3231 getServerEntity();
}
