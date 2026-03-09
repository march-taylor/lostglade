package eu.pb4.polymer.core.mixin.entity;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.datafixers.util.Pair;
import eu.pb4.polymer.core.api.entity.PolymerEntity;
import eu.pb4.polymer.core.api.entity.PolymerTrackerPacketSender;
import eu.pb4.polymer.core.api.other.PlayerBoundConsumer;
import eu.pb4.polymer.core.impl.interfaces.PossiblyInitialPacket;
import eu.pb4.polymer.core.impl.networking.PolymerServerProtocol;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.class_1297;
import net.minecraft.class_1304;
import net.minecraft.class_1309;
import net.minecraft.class_1799;
import net.minecraft.class_2596;
import net.minecraft.class_2602;
import net.minecraft.class_2739;
import net.minecraft.class_2744;
import net.minecraft.class_2945;
import net.minecraft.class_3222;
import net.minecraft.class_3231;

@Mixin(class_3231.class)
public abstract class ServerEntityMixin {
    @Shadow @Final private class_1297 entity;

    @Shadow @Nullable private List<class_2945.class_7834<?>> trackedDataValues;

    @Shadow @Final private class_3231.class_12004 synchronizer;

    @ModifyVariable(method = "sendPairingData", at = @At("HEAD"), argsOnly = true)
    private Consumer<class_2596<?>> polymer$packetWrap(Consumer<class_2596<?>> packetConsumer, @Local(argsOnly = true) class_3222 player) {
        return PlayerBoundConsumer.createPacketFor(Set.of(player.field_13987), this.entity, packetConsumer);
    }

    @ModifyArg(method = "sendPairingData", at = @At(value = "INVOKE", target = "Ljava/util/function/Consumer;accept(Ljava/lang/Object;)V", ordinal = 1))
    private Object polymer$markAsInitial(Object obj) {
        ((PossiblyInitialPacket) obj).polymer$setInitial();
        return obj;
    }

    @ModifyArg(method = "sendPairingData", at = @At(value = "INVOKE", target = "Ljava/util/function/Consumer;accept(Ljava/lang/Object;)V", ordinal = 2))
    private Object polymer$markAsInitial2(Object obj) {
        ((PossiblyInitialPacket) obj).polymer$setInitial();
        return obj;
    }

    @Inject(method = "sendPairingData", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getAddEntityPacket(Lnet/minecraft/server/level/ServerEntity;)Lnet/minecraft/network/protocol/Packet;"))
    private void polymer$sendPacketsBeforeSpawning(class_3222 player, Consumer<class_2596<?>> sender, CallbackInfo ci) {
        var polymerEntity = PolymerEntity.get(this.entity);
        if (polymerEntity != null) {
            try {
                polymerEntity.onBeforeSpawnPacket(player, sender);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Inject(method = "addPairing", at = @At("TAIL"))
    private void polymer$sendEntityInfo(class_3222 player, CallbackInfo ci) {
        var polymerEntity = PolymerEntity.get(this.entity);
        if (polymerEntity != null && polymerEntity.canSynchronizeToPolymerClient(player)) {
            PolymerServerProtocol.sendEntityInfo(player.field_13987, this.entity);
        }
    }

    @Inject(method = "sendChanges", at = @At("HEAD"))
    private void polymer$tickHead(CallbackInfo ci) {
        var polymerEntity = PolymerEntity.get(this.entity);
        if (polymerEntity != null && this.synchronizer instanceof PolymerTrackerPacketSender accessor) {
            polymerEntity.beforeEntityTrackerTick(Collections.unmodifiableSet(accessor.listeners()));
        }
    }

    @Inject(method = "sendChanges", at = @At("TAIL"))
    private void polymer$tick(CallbackInfo ci) {
        var polymerEntity = PolymerEntity.get(this.entity);
        if (polymerEntity != null && this.synchronizer instanceof PolymerTrackerPacketSender accessor) {
            polymerEntity.onEntityTrackerTick(Collections.unmodifiableSet(accessor.listeners()));
        }
    }

    @Inject(method = "sendPairingData", at = @At("TAIL"))
    private void polymer$modifyCreationData(class_3222 player, Consumer<class_2596<class_2602>> sender, CallbackInfo ci) {
        var polymerEntity = PolymerEntity.get(this.entity);
        if (polymerEntity != null) {
            if (polymerEntity.sendEmptyTrackerUpdates(player) && this.trackedDataValues == null) {
                var x = new class_2739(this.entity.method_5628(), List.of());
                ((PossiblyInitialPacket) (Object) x).polymer$setInitial();
                sender.accept(x);
            }

            try {
                if (this.entity instanceof class_1309 livingEntity) {
                    var list = new ArrayList<Pair<class_1304, class_1799>>();

                    for (class_1304 slot : class_1304.values()) {
                        class_1799 stack = livingEntity.method_6118(slot);
                        if (!stack.method_7960()) {
                            list.add(new Pair<>(slot, stack));
                        }
                    }

                    sender.accept(new class_2744(this.entity.method_5628(), list));
                } else {
                    sender.accept(new class_2744(this.entity.method_5628(), new ArrayList<>()));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
