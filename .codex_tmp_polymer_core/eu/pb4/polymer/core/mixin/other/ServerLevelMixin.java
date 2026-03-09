package eu.pb4.polymer.core.mixin.other;

import eu.pb4.polymer.core.impl.PolymerImplUtils;
import net.minecraft.class_1297;
import net.minecraft.class_3218;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(class_3218.class)
public class ServerLevelMixin {
    @ModifyVariable(method = "playSeededSound(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/Holder;Lnet/minecraft/sounds/SoundSource;FFJ)V", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private class_1297 ignoreEntityException(class_1297 entity) {
        return PolymerImplUtils.IGNORE_PLAY_SOUND_EXCLUSION.get() != null ? null : entity;
    }

    @ModifyVariable(method = "playSeededSound(Lnet/minecraft/world/entity/Entity;DDDLnet/minecraft/core/Holder;Lnet/minecraft/sounds/SoundSource;FFJ)V", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private class_1297 ignoreEntityException2(class_1297 entity) {
        return PolymerImplUtils.IGNORE_PLAY_SOUND_EXCLUSION.get() != null ? null : entity;
    }
}
