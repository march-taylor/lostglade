package eu.pb4.polymer.virtualentity.mixin;

import eu.pb4.polymer.virtualentity.api.VirtualEntityUtils;
import eu.pb4.polymer.virtualentity.impl.EntityExt;
import eu.pb4.polymer.virtualentity.impl.HolderAttachmentHolder;
import eu.pb4.polymer.virtualentity.impl.HolderHolder;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.class_1297;
import net.minecraft.class_2752;
import net.minecraft.class_3222;
import net.minecraft.class_3231;

@Mixin(class_3231.class)
public class EntityTrackerEntryMixin {
    @Shadow @Final private class_1297 entity;

    @Shadow private List<class_1297> lastPassengers;


    @Shadow @Final private class_3231.class_12004 synchronizer;

    @Inject(method = "addPairing", at = @At("TAIL"))
    private void polymerVE$startTracking(class_3222 player, CallbackInfo ci) {
        boolean hasPassangers = false;
        var a = ((HolderAttachmentHolder) this.entity).polymerVE$getHolders();
        if (!a.isEmpty()) {
            for (var x : a) {
                x.startWatching(player);
                hasPassangers |= !x.holder().getAttachedPassengerEntityIds().isEmpty();
            }
        }

        if (hasPassangers || !((EntityExt) this.entity).polymerVE$getVirtualRidden().isEmpty()) {
            player.field_13987.method_14364(new class_2752(this.entity));
        }
    }

    @Inject(method = "sendChanges", at = @At("HEAD"))
    private void polymerVE$tick(CallbackInfo ci) {
        var a = ((HolderAttachmentHolder) this.entity).polymerVE$getHolders();
        if (!a.isEmpty()) {
            var arr = a.toArray(HolderHolder.HOLDER_ATTACHMENTS);
            for (int i = 0; i < arr.length; i++) {
                arr[i].tick();
            }
        }

        if (((EntityExt) this.entity).polymerVE$getAndClearVirtualRiddenDirty() && this.entity.method_5685().equals(this.lastPassengers)) {
            this.synchronizer.method_18734(new class_2752(this.entity));
        }
    }

    @Inject(method = "removePairing", at = @At("TAIL"))
    private void polymerVE$stopTracking(class_3222 player, CallbackInfo ci) {
        for (var x : ((HolderAttachmentHolder) this.entity).polymerVE$getHolders()) {
            x.stopWatching(player);
        }
    }
}
