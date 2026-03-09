package eu.pb4.polymer.core.mixin.entity;

import eu.pb4.polymer.core.impl.interfaces.EntityAttachedPacket;
import net.minecraft.class_1297;
import net.minecraft.class_2616;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(class_2616.class)
public class ClientboundAnimatePacketMixin {
    @Inject(method = "<init>(Lnet/minecraft/world/entity/Entity;I)V", at = @At("TAIL"))
    private void attachEntity(class_1297 entity, int animationId, CallbackInfo ci) {
        EntityAttachedPacket.set(this, entity);
    }
}
