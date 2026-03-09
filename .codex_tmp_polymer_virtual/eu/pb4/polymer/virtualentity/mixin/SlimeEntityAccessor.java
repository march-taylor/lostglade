package eu.pb4.polymer.virtualentity.mixin;

import net.minecraft.class_1621;
import net.minecraft.class_2940;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(class_1621.class)
public interface SlimeEntityAccessor {
    @Accessor
    static class_2940<Integer> getID_SIZE() {
        throw new UnsupportedOperationException();
    }
}
