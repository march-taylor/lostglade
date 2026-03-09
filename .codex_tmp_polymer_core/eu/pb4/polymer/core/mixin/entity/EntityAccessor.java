package eu.pb4.polymer.core.mixin.entity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.class_1297;

@Mixin(class_1297.class)
public interface EntityAccessor {
    @Accessor
    static AtomicInteger getENTITY_COUNTER() {
        throw new UnsupportedOperationException();
    }
}
