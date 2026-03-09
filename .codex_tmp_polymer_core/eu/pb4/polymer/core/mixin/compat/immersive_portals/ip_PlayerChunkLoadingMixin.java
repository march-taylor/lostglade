package eu.pb4.polymer.core.mixin.compat.immersive_portals;

import eu.pb4.polymer.common.impl.CommonImplUtils;
import net.minecraft.class_2818;
import net.minecraft.class_3218;
import net.minecraft.class_3244;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.chunk_loading.PlayerChunkLoading;


@Pseudo
@Mixin(value = PlayerChunkLoading.class)
public class ip_PlayerChunkLoadingMixin {
    @Inject(method = "sendChunkPacket", at = @At("HEAD"), require = 0)
    private static void polymer_setPlayerNow(class_3244 serverGamePacketListenerImpl, class_3218 serverLevel, class_2818 levelChunk, CallbackInfo ci) {
    }

    @Inject(method = "sendChunkPacket", at = @At("TAIL"), require = 0)
    private static void polymer_resetPlayer(class_3244 serverGamePacketListenerImpl, class_3218 serverLevel, class_2818 levelChunk, CallbackInfo ci) {
    }
}
