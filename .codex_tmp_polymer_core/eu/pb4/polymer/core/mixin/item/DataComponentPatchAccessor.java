package eu.pb4.polymer.core.mixin.item;

import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Optional;
import net.minecraft.class_9326;
import net.minecraft.class_9331;

@Mixin(class_9326.class)
public interface DataComponentPatchAccessor {
    @Invoker("<init>")
    static class_9326 createComponentChanges(Reference2ObjectMap<class_9331<?>, Optional<?>> changedComponents) {
        throw new UnsupportedOperationException();
    }

    @Accessor
    Reference2ObjectMap<class_9331<?>, Optional<?>> getMap();
}
