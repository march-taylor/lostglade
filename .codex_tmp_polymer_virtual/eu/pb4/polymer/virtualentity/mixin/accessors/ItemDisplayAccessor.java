package eu.pb4.polymer.virtualentity.mixin.accessors;

import net.minecraft.class_1799;
import net.minecraft.class_2940;
import net.minecraft.class_8113;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(class_8113.class_8122.class)
public interface ItemDisplayAccessor {
    @Accessor
    static class_2940<class_1799> getDATA_ITEM_STACK_ID() {
        throw new UnsupportedOperationException();
    }

    @Accessor
    static class_2940<Byte> getDATA_ITEM_DISPLAY_ID() {
        throw new UnsupportedOperationException();
    }
}
