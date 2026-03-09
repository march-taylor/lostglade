package eu.pb4.polymer.core.mixin.other;

import net.minecraft.class_1703;
import net.minecraft.class_5916;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(class_1703.class)
public interface AbstractContainerMenuAccessor {
    @Accessor
    class_5916 getSynchronizer();
}
