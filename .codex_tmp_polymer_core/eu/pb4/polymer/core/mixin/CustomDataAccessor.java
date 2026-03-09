package eu.pb4.polymer.core.mixin;

import net.minecraft.class_2487;
import net.minecraft.class_9279;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(class_9279.class)
public interface CustomDataAccessor {
    @Accessor("tag")
    class_2487 polymer$getNbtUnsafe();
}
