package eu.pb4.polymer.core.mixin.entity;

import net.minecraft.class_1533;
import net.minecraft.class_1799;
import net.minecraft.class_2940;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(class_1533.class)
public interface ItemFrameAccessor {
    @Accessor
    static class_2940<class_1799> getDATA_ITEM() {
        throw new UnsupportedOperationException();
    }
}
