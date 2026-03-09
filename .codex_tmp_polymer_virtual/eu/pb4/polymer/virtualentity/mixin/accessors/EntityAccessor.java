package eu.pb4.polymer.virtualentity.mixin.accessors;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.class_1297;
import net.minecraft.class_2561;
import net.minecraft.class_2940;
import net.minecraft.class_4050;

@Mixin(class_1297.class)
public interface EntityAccessor {
    @Accessor
    static class_2940<Integer> getDATA_TICKS_FROZEN() {
        throw new UnsupportedOperationException();
    }

    @Accessor
    static class_2940<Boolean> getDATA_NO_GRAVITY() {
        throw new UnsupportedOperationException();
    }

    @Accessor
    static class_2940<class_4050> getDATA_POSE() {
        throw new UnsupportedOperationException();
    }

    @Accessor
    static class_2940<Byte> getDATA_SHARED_FLAGS_ID() {
        throw new UnsupportedOperationException();
    }

    @Accessor
    static int getFLAG_ONFIRE() {
        throw new UnsupportedOperationException();
    }

    @Accessor
    static int getFLAG_SHIFT_KEY_DOWN() {
        throw new UnsupportedOperationException();
    }

    @Accessor
    static int getFLAG_SPRINTING() {
        throw new UnsupportedOperationException();
    }

    @Accessor
    static int getFLAG_SWIMMING() {
        throw new UnsupportedOperationException();
    }

    @Accessor
    static int getFLAG_INVISIBLE() {
        throw new UnsupportedOperationException();
    }

    @Accessor
    static int getFLAG_GLOWING() {
        throw new UnsupportedOperationException();
    }

    @Accessor
    static int getFLAG_FALL_FLYING() {
        throw new UnsupportedOperationException();
    }

    @Accessor
    static class_2940<Integer> getDATA_AIR_SUPPLY_ID() {
        throw new UnsupportedOperationException();
    }

    @Accessor
    static class_2940<Optional<class_2561>> getDATA_CUSTOM_NAME() {
        throw new UnsupportedOperationException();
    }

    @Accessor
    static class_2940<Boolean> getDATA_CUSTOM_NAME_VISIBLE() {
        throw new UnsupportedOperationException();
    }

    @Accessor
    static class_2940<Boolean> getDATA_SILENT() {
        throw new UnsupportedOperationException();
    }

    @Accessor
    static AtomicInteger getENTITY_COUNTER() {
        throw new UnsupportedOperationException();
    }
}
