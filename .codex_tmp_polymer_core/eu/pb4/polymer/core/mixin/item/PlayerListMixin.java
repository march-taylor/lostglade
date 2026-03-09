package eu.pb4.polymer.core.mixin.item;

import eu.pb4.polymer.core.api.item.PolymerItemGroupUtils;
import eu.pb4.polymer.core.api.utils.PolymerSyncUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import net.minecraft.class_3222;
import net.minecraft.class_3324;

@Mixin(class_3324.class)
public abstract class PlayerListMixin {
    @Shadow public abstract List<class_3222> getPlayers();

    @Inject(method = "reloadResources", at = @At("HEAD"))
    private void polymerCore$invalidateItemGroups(CallbackInfo ci) {
        PolymerItemGroupUtils.invalidateItemGroupCache();
        for (var player : this.getPlayers()) {
            PolymerSyncUtils.synchronizeCreativeTabs(player.field_13987);
        }
    }
}
