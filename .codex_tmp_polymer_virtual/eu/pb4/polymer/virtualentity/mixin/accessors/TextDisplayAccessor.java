package eu.pb4.polymer.virtualentity.mixin.accessors;

import net.minecraft.class_2561;
import net.minecraft.class_2940;
import net.minecraft.class_8113;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(class_8113.class_8123.class)
public interface TextDisplayAccessor {
    @Accessor
    static byte getFLAG_SHADOW() {
        throw new UnsupportedOperationException();
    }

    @Accessor
    static byte getFLAG_SEE_THROUGH() {
        throw new UnsupportedOperationException();
    }

    @Accessor
    static byte getFLAG_USE_DEFAULT_BACKGROUND() {
        throw new UnsupportedOperationException();
    }

    @Accessor
    static byte getFLAG_ALIGN_LEFT() {
        throw new UnsupportedOperationException();
    }

    @Accessor
    static byte getFLAG_ALIGN_RIGHT() {
        throw new UnsupportedOperationException();
    }

    @Accessor
    static class_2940<class_2561> getDATA_TEXT_ID() {
        throw new UnsupportedOperationException();
    }

    @Accessor
    static class_2940<Integer> getDATA_LINE_WIDTH_ID() {
        throw new UnsupportedOperationException();
    }

    @Accessor
    static class_2940<Integer> getDATA_BACKGROUND_COLOR_ID() {
        throw new UnsupportedOperationException();
    }

    @Accessor
    static class_2940<Byte> getDATA_TEXT_OPACITY_ID() {
        throw new UnsupportedOperationException();
    }

    @Accessor
    static class_2940<Byte> getDATA_STYLE_FLAGS_ID() {
        throw new UnsupportedOperationException();
    }
}
