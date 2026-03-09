package eu.pb4.polymer.virtualentity.mixin;

import eu.pb4.polymer.virtualentity.impl.HolderAttachmentHolder;
import eu.pb4.polymer.virtualentity.impl.HolderHolder;
import org.apache.commons.lang3.mutable.MutableObject;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import net.minecraft.class_1923;
import net.minecraft.class_2818;
import net.minecraft.class_3218;
import net.minecraft.class_3222;
import net.minecraft.class_3244;
import net.minecraft.class_8608;

@Mixin(class_8608.class)
public class ChunkDataSenderMixin {
    @Inject(method = "sendChunk", at = @At("TAIL"), require = 0)
    private static void polymerVE$addToHolograms(class_3244 handler, class_3218 world, class_2818 chunk, CallbackInfo ci) {
        for (var hologram : ((HolderAttachmentHolder) chunk).polymerVE$getHolders()) {
            hologram.startWatching(handler);
        }
    }

    @Inject(method = "dropChunk", at = @At("HEAD"), require = 0)
    private void polymerVE$chunkUnload(class_3222 player, class_1923 pos, CallbackInfo ci) {
        for (var holder : new ArrayList<>(((HolderHolder) player.field_13987).polymer$getHolders())) {
            var att = holder.getAttachment();
            if (att != null && holder.getChunkPos().equals(pos)) {
                att.updateTracking(player.field_13987);
            }
        }
    }
}
