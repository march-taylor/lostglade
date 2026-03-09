package eu.pb4.polymer.core.mixin.client.debug;

import eu.pb4.polymer.core.impl.client.ClientDebugFlags;
import net.minecraft.class_11719;
import net.minecraft.class_327;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(class_327.class)
public class FontMixin {
    @ModifyVariable(method = "getGlyphSource", at = @At("HEAD"), argsOnly = true)
    private class_11719 replaceFonts(class_11719 source) {
        if (ClientDebugFlags.customFonts) return source;
        return class_11719.field_61972;
    }
}
