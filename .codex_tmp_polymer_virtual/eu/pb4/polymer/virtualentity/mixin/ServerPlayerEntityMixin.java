package eu.pb4.polymer.virtualentity.mixin;

import eu.pb4.polymer.virtualentity.impl.HolderHolder;
import net.minecraft.class_1282;
import net.minecraft.class_1297;
import net.minecraft.class_2709;
import net.minecraft.class_3218;
import net.minecraft.class_3222;
import net.minecraft.class_3244;
import net.minecraft.class_5454;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Set;

@Mixin(class_3222.class)
public class ServerPlayerEntityMixin {
    @Shadow
    @Final
    public MinecraftServer server;
    @Shadow
    public class_3244 connection;

    @Inject(method = "disconnect", at = @At("HEAD"))
    private void polymerVE$removeFromHologramsOnDisconnect(CallbackInfo ci) {
        for (var holder : new ArrayList<>(((HolderHolder) this.connection).polymer$getHolders())) {
            holder.stopWatching(this.connection);
        }
    }

    @Inject(method = "die", at = @At("TAIL"))
    private void polymerVE$removeOnDeath(class_1282 source, CallbackInfo ci) {
        for (var holder : new ArrayList<>(((HolderHolder) this.connection).polymer$getHolders())) {
            var att = holder.getAttachment();
            if (att != null) {
                att.updateTracking(this.connection);
            }
        }
    }

    @Inject(method = "teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDLjava/util/Set;FFZ)Z", at = @At(value = "RETURN"))
    private void polymerVE$removeOnWorldChange(class_3218 serverWorld, double destX, double destY, double destZ, Set<class_2709> flags, float yaw, float pitch, boolean bl, CallbackInfoReturnable<Boolean> cir) {
        for (var holder : new ArrayList<>(((HolderHolder) this.connection).polymer$getHolders())) {
            var att = holder.getAttachment();
            if (att != null) {
                att.updateTracking(this.connection);
            }
        }
    }

    @Inject(method = "teleport(Lnet/minecraft/world/level/portal/TeleportTransition;)Lnet/minecraft/server/level/ServerPlayer;", at = @At(value = "RETURN"))
    private void polymerVE$removeOnWorldChange3(class_5454 teleportTarget, CallbackInfoReturnable<class_1297> cir) {
        for (var holder : new ArrayList<>(((HolderHolder) this.connection).polymer$getHolders())) {
            var att = holder.getAttachment();
            if (att != null) {
                att.updateTracking(this.connection);
            }
        }
    }

    @Inject(method = "snapTo", at = @At(value = "RETURN"))
    private void polymerVE$removeOnWorldChange2(double x, double y, double z, CallbackInfo ci) {
        for (var holder : new ArrayList<>(((HolderHolder) this.connection).polymer$getHolders())) {
            var att = holder.getAttachment();
            if (att != null) {
                att.updateTracking(this.connection);
            }
        }
    }


}
