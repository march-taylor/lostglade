package eu.pb4.polymer.core.mixin.other;

import eu.pb4.polymer.core.api.utils.PolymerSyncedObject;
import eu.pb4.polymer.core.api.utils.PolymerUtils;
import eu.pb4.polymer.core.mixin.NetworkPayloadAccessor;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import xyz.nucleoid.packettweaker.PacketContext;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.class_2378;
import net.minecraft.class_2790;
import net.minecraft.class_2960;
import net.minecraft.class_5321;
import net.minecraft.class_6864;
import net.minecraft.class_7923;

@Mixin(class_2790.class)
public class ClientboundUpdateTagsPacketMixin {
    @ModifyArg(method = "write", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/FriendlyByteBuf;writeMap(Ljava/util/Map;Lnet/minecraft/network/codec/StreamEncoder;Lnet/minecraft/network/codec/StreamEncoder;)V"))
    private Map<class_5321<? extends class_2378<?>>, class_6864.class_5748> polymer$skipEntries(Map<class_5321<? extends class_2378<?>>, class_6864.class_5748> groups) {
        var regMap = new HashMap<class_5321<? extends class_2378<?>>, class_6864.class_5748>();
        var player = PacketContext.get();
        for (var regEntry : groups.entrySet()) {
            if (PolymerUtils.isServerOnlyRegistry(regEntry.getKey())) {
                continue;
            }
            //noinspection rawtypes,unchecked
            var reg = class_7923.field_41167.method_29107((class_5321) regEntry.getKey());

            if (reg != null) {
                var map = new HashMap<class_2960, IntList>();

                for (var entry : ((NetworkPayloadAccessor) (Object) regEntry.getValue()).getTags().entrySet()) {
                    var list = new IntArrayList(entry.getValue().size());

                    for (int i : entry.getValue()) {
                        //noinspection unchecked
                        if (PolymerSyncedObject.canSyncRawToClient(reg, reg.method_10200(i), player)) {
                            list.add(i);
                        }
                    }
                    map.put(entry.getKey(), list);
                }

                regMap.put(regEntry.getKey(), NetworkPayloadAccessor.createSerialized(map));
            } else {
                // Dynamic registry, client *should* understand it
                regMap.put(regEntry.getKey(), regEntry.getValue());
            }
        }
        return regMap;
    }
}