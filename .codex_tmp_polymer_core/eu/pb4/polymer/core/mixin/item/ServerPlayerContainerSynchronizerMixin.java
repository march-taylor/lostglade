package eu.pb4.polymer.core.mixin.item;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import eu.pb4.polymer.core.impl.interfaces.GenericPlayerContext;
import net.minecraft.class_10927;
import net.minecraft.class_3222;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net/minecraft/server/level/ServerPlayer$1")
public class ServerPlayerContainerSynchronizerMixin {
    @Shadow @Final private class_3222 field_58075;

    @ModifyReturnValue(method = "createSlot", at = @At("TAIL"))
    private class_10927 setContextForSlot(class_10927 slot) {
        if (slot instanceof GenericPlayerContext context) {
            context.polymer$setPlayer(this.field_58075);
        }
        return slot;
    }
}
