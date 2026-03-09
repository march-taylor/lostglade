package eu.pb4.polymer.virtualentity.mixin.accessors;

import net.minecraft.class_3231;
import net.minecraft.class_7422;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(class_3231.class)
public interface ServerEntityAccessor {
    @Accessor
    class_7422 getPositionCodec();
}
