package eu.pb4.polymer.core.mixin.other;

import eu.pb4.polymer.rsm.api.RegistrySyncUtils;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.class_2378;
import net.minecraft.class_2617;
import net.minecraft.class_3445;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(class_2617.class)
public abstract class ClientboundAwardStatsPacketMixin {

    @Mutable
    @Shadow @Final private Object2IntMap<class_3445<?>> stats;

    @Shadow public abstract boolean equals(Object par1);

    @Inject(method = "<init>(Lit/unimi/dsi/fastutil/objects/Object2IntMap;)V", at = @At("TAIL"))
    public void polymer$onWrite(Object2IntMap<class_3445<?>> stats, CallbackInfo ci) {
        //noinspection RedundantSuppression
        this.stats = stats.object2IntEntrySet().stream().filter(statEntry -> {
            //noinspection unchecked,rawtypes
            return !RegistrySyncUtils.isServerEntry((class_2378) statEntry.getKey().method_14949().method_14959(), statEntry.getKey().method_14951());
        }).collect(Object2IntOpenHashMap::new, (map, statEntry) -> map.addTo(statEntry.getKey(), statEntry.getIntValue()), Object2IntOpenHashMap::putAll);
    }
}
