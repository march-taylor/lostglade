package eu.pb4.polymer.core.mixin.other;

import net.minecraft.class_1761;
import net.minecraft.class_7706;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(class_7706.class)
public interface CreativeModeTabsAccessor {
    @Invoker
    static void callBuildAllTabContents(class_1761.class_8128 x) {
        throw new UnsupportedOperationException();
    }

    @Accessor
    static class_1761.class_8128 getCACHED_PARAMETERS() {
        throw new UnsupportedOperationException();
    }
}
