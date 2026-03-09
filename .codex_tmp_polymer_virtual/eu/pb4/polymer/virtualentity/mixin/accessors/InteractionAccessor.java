package eu.pb4.polymer.virtualentity.mixin.accessors;

import net.minecraft.class_2940;
import net.minecraft.class_8150;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(class_8150.class)
public interface InteractionAccessor {
    @Accessor
    static class_2940<Float> getDATA_WIDTH_ID() {
        throw new UnsupportedOperationException();
    }

    @Accessor
    static class_2940<Float> getDATA_HEIGHT_ID() {
        throw new UnsupportedOperationException();
    }

    @Accessor
    static class_2940<Boolean> getDATA_RESPONSE_ID() {
        throw new UnsupportedOperationException();
    }
}
