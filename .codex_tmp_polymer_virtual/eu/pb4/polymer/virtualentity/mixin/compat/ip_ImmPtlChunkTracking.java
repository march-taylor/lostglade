package eu.pb4.polymer.virtualentity.mixin.compat;

import com.llamalad7.mixinextras.sugar.Local;
import eu.pb4.polymer.virtualentity.api.attachment.ChunkAttachment;
import eu.pb4.polymer.virtualentity.impl.HolderHolder;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.imm_ptl.core.chunk_loading.ImmPtlChunkTracking;

import java.util.ArrayList;
import java.util.Map;
import net.minecraft.class_3222;

@Pseudo
@Mixin(ImmPtlChunkTracking.class)
public class ip_ImmPtlChunkTracking {
    @Inject(method = "lambda$purge$5", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;send(Lnet/minecraft/network/protocol/Packet;)V"))
    private static void polymerVE$chunkUnload(Map.Entry e, CallbackInfoReturnable<Boolean> cir, @Local class_3222 player, @Local ImmPtlChunkTracking.PlayerWatchRecord record) {
        for (var holder : new ArrayList<>(((HolderHolder) player.field_13987).polymer$getHolders())) {
            if (holder.getAttachment() instanceof ChunkAttachment chunkAttachment
                    && chunkAttachment.getWorld().method_27983().equals(record.dimension)
                    && holder.getChunkPos().method_8324() == record.chunkPos) {
                holder.getAttachment().stopWatching(player.field_13987);
            }
        }
    }

    @Inject(method = "forceRemovePlayer", at = @At("TAIL"), require = 0)
    private static void polymerVE$chunkUnload2(class_3222 oldPlayer, CallbackInfo ci) {
        for (var holder : new ArrayList<>(((HolderHolder) oldPlayer.field_13987).polymer$getHolders())) {
            if (holder.getAttachment() instanceof ChunkAttachment chunkAttachment) {
                holder.getAttachment().stopWatching(oldPlayer.field_13987);
            }
        }
    }
}
