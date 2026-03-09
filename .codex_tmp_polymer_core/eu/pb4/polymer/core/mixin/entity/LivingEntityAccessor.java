package eu.pb4.polymer.core.mixin.entity;

import net.minecraft.class_1309;
import net.minecraft.class_2940;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(class_1309.class)
public interface LivingEntityAccessor {

    @Accessor
    static class_2940<Byte> getDATA_LIVING_ENTITY_FLAGS() {
        throw new UnsupportedOperationException();
    }
}
