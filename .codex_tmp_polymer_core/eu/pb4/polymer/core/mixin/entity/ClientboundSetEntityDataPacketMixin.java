package eu.pb4.polymer.core.mixin.entity;

import eu.pb4.polymer.common.impl.entity.InternalEntityHelpers;
import eu.pb4.polymer.core.api.entity.PolymerEntity;
import eu.pb4.polymer.core.api.entity.PolymerEntityUtils;
import eu.pb4.polymer.core.impl.interfaces.EntityAttachedPacket;
import eu.pb4.polymer.core.impl.interfaces.PossiblyInitialPacket;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import xyz.nucleoid.packettweaker.PacketContext;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.class_2739;
import net.minecraft.class_2945;
import net.minecraft.class_3850;
import net.minecraft.class_7923;

@SuppressWarnings({"rawtypes", "unchecked", "ConstantConditions"})
@Mixin(class_2739.class)
public class ClientboundSetEntityDataPacketMixin implements PossiblyInitialPacket {
    @Shadow
    @Final
    private int id;
    @Unique
    private boolean isInitial = false;

    @Unique
    @Nullable
    private List<class_2945.class_7834<?>> polymer$createEntries(List<class_2945.class_7834<?>> trackedValues) {
        var entity = EntityAttachedPacket.get(this, this.id);
        if (entity == null) {
            return trackedValues != null ? new ArrayList<>(trackedValues) : null;
        }

        var entries = new ArrayList<class_2945.class_7834<?>>();
        var player = PacketContext.get();

        var polymerEntity = PolymerEntity.get(entity);
        if (polymerEntity != null && InternalEntityHelpers.canPatchTrackedData(player.getPlayer(), entity)) {
            var mod = trackedValues != null ? new ArrayList<>(trackedValues) : new ArrayList<class_2945.class_7834<?>>();
            polymerEntity.modifyRawTrackedData(mod, player.getPlayer(), this.isInitial);

            var legalTrackedData = InternalEntityHelpers.getExampleTrackedDataOfEntityType((polymerEntity.getPolymerEntityType(player)));

            if (!mod.isEmpty() && legalTrackedData != null && legalTrackedData.length != 0) {
                for (var entry : mod) {
                    if (entry.comp_1115() < legalTrackedData.length) {
                        var x = legalTrackedData[entry.comp_1115()];
                        if (x != null && x.method_12797().comp_2328() == entry.comp_1116()) {
                            entries.add(entry);
                        }
                    }
                }
            } else {
                entries.addAll(mod);
            }
        } else if (trackedValues == null) {
            return null;
        } else {
            entries.addAll(trackedValues);
        }

        final var size = entries.size();
        for (int i = 0; i < size; i++) {
            var entry = entries.get(i);

            if (entry.comp_1117() instanceof class_3850 data) {
                var x = PolymerEntityUtils.getPolymerProfession(data.comp_3521().comp_349());
                if (x != null) {
                    entries.set(i, new class_2945.class_7834(entry.comp_1115(), entry.comp_1116(), data.method_16921(class_7923.field_41195.method_47983(x.getPolymerReplacement(data.comp_3521().comp_349(), player)))));
                }
            }
        }

        return entries;
    }

    @ModifyArg(method = "write(Lnet/minecraft/network/RegistryFriendlyByteBuf;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/game/ClientboundSetEntityDataPacket;pack(Ljava/util/List;Lnet/minecraft/network/RegistryFriendlyByteBuf;)V"))
    private List<class_2945.class_7834<?>> polymer$changeForPacket(List<class_2945.class_7834<?>> value) {
        return this.polymer$createEntries(value);
    }

    @Override
    public boolean polymer$getInitial() {
        return this.isInitial;
    }

    @Override
    public void polymer$setInitial() {
        this.isInitial = true;
    }
}
