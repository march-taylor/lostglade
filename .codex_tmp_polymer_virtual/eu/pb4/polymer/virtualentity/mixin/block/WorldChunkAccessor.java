package eu.pb4.polymer.virtualentity.mixin.block;

import net.minecraft.class_2818;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(class_2818.class)
public interface WorldChunkAccessor {
    @Accessor
    boolean isLoaded();
}
