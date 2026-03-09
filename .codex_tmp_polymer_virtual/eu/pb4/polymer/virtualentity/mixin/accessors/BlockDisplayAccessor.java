package eu.pb4.polymer.virtualentity.mixin.accessors;

import net.minecraft.class_2680;
import net.minecraft.class_2940;
import net.minecraft.class_8113;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(class_8113.class_8115.class)
public interface BlockDisplayAccessor {
    @Accessor
    static class_2940<class_2680> getDATA_BLOCK_STATE_ID() {
        throw new UnsupportedOperationException();
    }
}
