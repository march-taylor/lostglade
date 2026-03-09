package eu.pb4.polymer.core.mixin.entity;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;
import eu.pb4.polymer.core.api.entity.PolymerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.class_1297;
import net.minecraft.class_2596;
import net.minecraft.class_2602;
import net.minecraft.class_3222;
import net.minecraft.class_3898;

@Mixin(class_3898.class)
public class ServerChunkLoadingManagerMixin {
    @WrapWithCondition(method = "sendToTrackingPlayersAndSelf", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ChunkMap$TrackedEntity;sendToTrackingPlayersAndSelf(Lnet/minecraft/network/protocol/Packet;)V"))
    private boolean wrapSendToNearbyForMoreControl(class_3898.class_3208 instance, class_2596<?> packet, @Local(argsOnly = true) class_1297 entity) {
        var polymerEntity = PolymerEntity.get(entity);
        if (polymerEntity != null) {
            //noinspection unchecked
            polymerEntity.onEntityPacketSent((Consumer<class_2596<?>>) (Object) ((Consumer<class_2596<class_2602>>) instance::method_18734), packet);
            return false;
        }
        return true;
    }

    @WrapWithCondition(method = "sendToTrackingPlayers", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ChunkMap$TrackedEntity;sendToTrackingPlayers(Lnet/minecraft/network/protocol/Packet;)V"))
    private boolean wrapSendToOtherNearbyForMoreControl(class_3898.class_3208 instance, class_2596<?> packet, @Local(argsOnly = true) class_1297 entity) {
        var polymerEntity = PolymerEntity.get(entity);
        if (polymerEntity != null) {
            //noinspection unchecked
            polymerEntity.onEntityPacketSent((Consumer<class_2596<?>>) (Object) ((Consumer<class_2596<class_2602>>) instance::method_18730), packet);
            return false;
        }
        return true;
    }

    @WrapWithCondition(method = "sendToTrackingPlayersFiltered", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ChunkMap$TrackedEntity;sendToTrackingPlayersFiltered(Lnet/minecraft/network/protocol/Packet;Ljava/util/function/Predicate;)V"))
    private boolean wrapSendToOtherNearbyForMoreControl(class_3898.class_3208 instance, class_2596<? super class_2602> packet, Predicate<class_3222> predicate, @Local(argsOnly = true) class_1297 entity) {
        var polymerEntity = PolymerEntity.get(entity);
        if (polymerEntity != null) {
            //noinspection unchecked
            polymerEntity.onEntityPacketSent(x -> instance.method_74531((class_2596<? super class_2602>) x, predicate), packet);
            return false;
        }
        return true;
    }
}
