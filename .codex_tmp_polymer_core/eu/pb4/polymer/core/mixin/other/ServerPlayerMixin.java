package eu.pb4.polymer.core.mixin.other;

import eu.pb4.polymer.core.impl.interfaces.GenericPlayerContext;
import net.minecraft.class_1703;
import net.minecraft.class_3222;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(class_3222.class)
public class ServerPlayerMixin {

    @Inject(method = "initMenu", at = @At("HEAD"))
    private void polymer$setPlayerContext(class_1703 screenHandler, CallbackInfo ci) {
        if (screenHandler instanceof GenericPlayerContext context) {
            context.polymer$setPlayer((class_3222) (Object) this);
        }
    }
}
