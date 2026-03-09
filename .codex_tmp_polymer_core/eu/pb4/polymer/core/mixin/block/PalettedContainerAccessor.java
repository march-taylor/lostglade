package eu.pb4.polymer.core.mixin.block;

import net.minecraft.class_2841;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(class_2841.class)
public interface PalettedContainerAccessor<T> {
    @Accessor
    class_2841.class_6561<T> getData();
}
