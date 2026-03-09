package eu.pb4.polymer.core.mixin.block.packet;

import eu.pb4.polymer.core.impl.interfaces.ChunkDataS2CPacketInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.BitSet;
import net.minecraft.class_2672;
import net.minecraft.class_2818;
import net.minecraft.class_3568;

@Mixin(class_2672.class)
public class ClientboundLevelChunkWithLightPacketMixin implements ChunkDataS2CPacketInterface {
    @Unique
    private class_2818 polymer$worldChunk;

    @Inject(method = "<init>(Lnet/minecraft/world/level/chunk/LevelChunk;Lnet/minecraft/world/level/lighting/LevelLightEngine;Ljava/util/BitSet;Ljava/util/BitSet;)V", at = @At("TAIL"))
    private void polymer$storeWorldChunk(class_2818 chunk, class_3568 lightingProvider, BitSet bitSet, BitSet bitSet2, CallbackInfo ci) {
        this.polymer$worldChunk = chunk;
    }

    public class_2818 polymer$getWorldChunk() {
        return this.polymer$worldChunk;
    }
}
