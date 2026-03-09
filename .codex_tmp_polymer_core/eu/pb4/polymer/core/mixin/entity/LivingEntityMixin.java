package eu.pb4.polymer.core.mixin.entity;

import eu.pb4.polymer.core.impl.interfaces.EntityAttachedPacket;
import net.minecraft.class_1297;
import net.minecraft.class_1299;
import net.minecraft.class_1309;
import net.minecraft.class_1937;
import net.minecraft.class_2596;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(class_1309.class)
public abstract class LivingEntityMixin extends class_1297 {

    @ModifyArg(method = "handleEquipmentChanges(Ljava/util/Map;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerChunkCache;sendToTrackingPlayers(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/network/protocol/Packet;)V"))
    private class_2596<?> polymer_addPlayerContext(class_2596<?> packet) {
        return EntityAttachedPacket.setIfEmpty(packet, this);
    }

    public LivingEntityMixin(class_1299<?> type, class_1937 world) {
        super(type, world);
    }
}
