package eu.pb4.polymer.core.mixin.block.packet;

import com.llamalad7.mixinextras.injector.ModifyReceiver;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import eu.pb4.polymer.core.api.block.PolymerBlockUtils;
import eu.pb4.polymer.core.impl.ClientMetadataKeys;
import eu.pb4.polymer.core.impl.PolymerImpl;
import eu.pb4.polymer.core.impl.interfaces.PolymerIdMapper;
import eu.pb4.polymer.networking.api.server.PolymerServerNetworking;
import net.minecraft.class_2248;
import net.minecraft.class_2497;
import net.minecraft.class_2680;
import net.minecraft.class_2816;
import net.minecraft.class_2837;
import net.minecraft.class_2841;
import net.minecraft.class_3508;
import net.minecraft.class_6490;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import xyz.nucleoid.packettweaker.PacketContext;

@Mixin(class_2841.class_6561.class)
public abstract class PalettedContainerDataMixin<T> {
    @Shadow public abstract class_2837<T> palette();

    @Shadow public abstract class_6490 storage();

    @ModifyReturnValue(method = "getSerializedSize", at = @At("RETURN"))
    private int changeCalculatedSize(int value) {
        var palette = this.palette();
        if (palette instanceof class_2816<T> && palette.method_12288(0) instanceof class_2680) {
            var player = PacketContext.get();
            if (player.getClientConnection() == null) {
                return value;
            }

            var storage = this.storage();
            value -= storage.method_15212().length * 8;
            int bits;

            var playerBitCount = PolymerServerNetworking.getMetadata(player.getClientConnection(), ClientMetadataKeys.BLOCKSTATE_BITS, class_2497.field_21037);
            if (playerBitCount == null) {
                bits = PolymerImpl.SYNC_MODDED_ENTRIES_POLYMC
                        ? ((PolymerIdMapper<?>) class_2248.field_10651).polymer$getVanillaBitCount()
                        : ((PolymerIdMapper<?>) class_2248.field_10651).polymer$getNonPolymerBitCount();
            } else {
                bits = playerBitCount.method_10701();
            }

            var elementsPerLong = (char)(64 / bits);
            value += (storage.method_15215() + elementsPerLong - 1) / elementsPerLong * 8;
        }
        return value;
    }

    @ModifyReceiver(method = "write", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/BitStorage;getRaw()[J"), require = 0)
    private class_6490 polymer$replaceData(class_6490 storage) {
        var palette = this.palette();
        if (palette instanceof class_2816<T> && palette.method_12288(0) instanceof class_2680) {
            var player = PacketContext.get();
            if (player.getClientConnection() == null) {
                return storage;
            }
            int bits;

            var playerBitCount = PolymerServerNetworking.getMetadata(player.getClientConnection(), ClientMetadataKeys.BLOCKSTATE_BITS, class_2497.field_21037);
            if (playerBitCount == null) {
                bits = PolymerImpl.SYNC_MODDED_ENTRIES_POLYMC
                        ? ((PolymerIdMapper<?>) class_2248.field_10651).polymer$getVanillaBitCount()
                        : ((PolymerIdMapper<?>) class_2248.field_10651).polymer$getNonPolymerBitCount();
            } else {
                bits = playerBitCount.method_10701();
            }
            final int size = storage.method_15215();
            var data = new class_3508(bits, size);

            var stateMap = class_2248.field_10651;

            for (int i = 0; i < size; i++) {
                data.method_15210(i, stateMap.method_10206(PolymerBlockUtils.getPolymerBlockState(stateMap.method_10200(storage.method_15211(i)), player)));
            }

            return data;
        }

        return storage;
    }
}
