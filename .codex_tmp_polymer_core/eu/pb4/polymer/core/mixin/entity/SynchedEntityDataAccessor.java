package eu.pb4.polymer.core.mixin.entity;

import net.minecraft.class_2945;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(class_2945.class)
public interface SynchedEntityDataAccessor {
    @Accessor
    class_2945.class_2946<?>[] getItemsById();
}
