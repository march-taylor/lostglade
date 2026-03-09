package eu.pb4.polymer.core.mixin.block;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import eu.pb4.polymer.common.api.PolymerCommonUtils;
import eu.pb4.polymer.core.impl.PolymerImplUtils;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.BitSet;
import net.minecraft.class_2596;
import net.minecraft.class_2672;
import net.minecraft.class_2818;
import net.minecraft.class_3244;
import net.minecraft.class_3568;
import net.minecraft.class_8608;

@Mixin(value = class_8608.class, priority = 1001)
public class PlayerChunkSenderMixin {
    @WrapOperation(method = "sendChunk", at = @At(value = "NEW", target = "(Lnet/minecraft/world/level/chunk/LevelChunk;Lnet/minecraft/world/level/lighting/LevelLightEngine;Ljava/util/BitSet;Ljava/util/BitSet;)Lnet/minecraft/network/protocol/game/ClientboundLevelChunkWithLightPacket;"), require = 0)
    private static class_2672 addContext(class_2818 chunk, class_3568 lightProvider, @Nullable BitSet skyBits, @Nullable BitSet blockBits, Operation<class_2672> call,
                                                 @Local(argsOnly = true) class_3244 handler) {
        return PolymerCommonUtils.executeWithNetworkingLogic(handler, () -> call.call(chunk, lightProvider, skyBits, blockBits));
    }

    @WrapWithCondition(method = "dropChunk", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;send(Lnet/minecraft/network/protocol/Packet;)V"), require = 0)
    private boolean skipChunkClearing(class_3244 instance, class_2596 packet) {
        return PolymerImplUtils.IS_RELOADING_WORLD.get() == null;
    }
}
