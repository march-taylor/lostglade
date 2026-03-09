package eu.pb4.polymer.core.mixin;

import it.unimi.dsi.fastutil.ints.IntList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Map;
import net.minecraft.class_2960;
import net.minecraft.class_6864;

@Mixin(class_6864.class_5748.class)
public interface NetworkPayloadAccessor {
    @Accessor
    Map<class_2960, IntList> getTags();

    @Invoker("<init>")
    static class_6864.class_5748 createSerialized(Map<class_2960, IntList> contents) {
        throw new UnsupportedOperationException();
    }
}
