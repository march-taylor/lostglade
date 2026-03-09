package eu.pb4.polymer.virtualentity.mixin.block;

import eu.pb4.polymer.virtualentity.impl.HolderHolder;
import net.minecraft.class_3222;
import net.minecraft.class_3898;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(class_3898.class)
public abstract class ServerChunkLoadingManagerMixin {

    @Inject(method = "updatePlayerStatus", at = @At("TAIL"))
    private void polymerVE$clearHolograms(class_3222 player, boolean added, CallbackInfo ci) {
        if (!added) {
            var holders = ((HolderHolder) player.field_13987).polymer$getHolders();
            if (!holders.isEmpty()) {
                var arr = holders.toArray(HolderHolder.ELEMENT_HOLDERS);
                for (int i = 0; i < arr.length; i++) {
                    var holder = arr[i];
                    if (holder.getAttachment() != null) {
                        holder.getAttachment().updateTracking(player.field_13987);
                    }
                }
            }
        }
    }
}
