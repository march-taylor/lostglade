package eu.pb4.polymer.virtualentity.mixin.compat;

import eu.pb4.polymer.virtualentity.impl.HolderAttachmentHolder;
import net.minecraft.class_2818;
import net.minecraft.class_3218;
import net.minecraft.class_3244;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.chunk_loading.PlayerChunkLoading;
import qouteall.imm_ptl.core.network.PacketRedirection;

@Pseudo
@Mixin(value = PlayerChunkLoading.class)
public class ip_PlayerChunkLoadingMixin {

    @Inject(method = "sendChunkPacket", at = @At("TAIL"), require = 0)
    private static void polymerVE$addToPlayerPlayer(class_3244 serverGamePacketListenerImpl, class_3218 serverLevel, class_2818 levelChunk, CallbackInfo ci) {
        if (!serverGamePacketListenerImpl.field_14140.method_29504()) {
            PacketRedirection.withForceRedirect(serverLevel, () -> {
                for (var hologram : ((HolderAttachmentHolder) levelChunk).polymerVE$getHolders()) {
                    hologram.startWatching(serverGamePacketListenerImpl);
                }
            });
        }
    }
}
