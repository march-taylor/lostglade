package eu.pb4.polymer.core.mixin.client;

import net.minecraft.class_1761;
import net.minecraft.class_481;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(class_481.class)
public interface CreativeModeInventoryScreenAccessor {
    @Accessor
    static void setSelectedTab(class_1761 selectedTab) {
        throw new UnsupportedOperationException();
    }
}
