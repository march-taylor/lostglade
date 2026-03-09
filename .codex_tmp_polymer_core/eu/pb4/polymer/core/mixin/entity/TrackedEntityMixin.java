package eu.pb4.polymer.core.mixin.entity;

import com.llamalad7.mixinextras.sugar.Local;
import eu.pb4.polymer.core.api.entity.PolymerTrackerPacketSender;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.Set;
import net.minecraft.class_1297;
import net.minecraft.class_3231;
import net.minecraft.class_3898;
import net.minecraft.class_5629;

@Mixin(class_3898.class_3208.class)
public abstract class TrackedEntityMixin {

    @Shadow
    @Final
    private Set<class_5629> seenBy;

    @ModifyArg(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerEntity;<init>(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/Entity;IZLnet/minecraft/server/level/ServerEntity$Synchronizer;)V"))
    private class_3231.class_12004 replaceReceiver(class_3231.class_12004 sender, @Local(argsOnly = true) class_1297 entity) {
        return PolymerTrackerPacketSender.of(sender, () -> this.seenBy, entity);
    }
}
