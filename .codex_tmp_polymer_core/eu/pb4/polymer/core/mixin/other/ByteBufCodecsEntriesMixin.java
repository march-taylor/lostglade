package eu.pb4.polymer.core.mixin.other;


import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import eu.pb4.polymer.core.api.block.PolymerBlockUtils;
import eu.pb4.polymer.core.api.utils.PolymerSyncedObject;
import eu.pb4.polymer.core.impl.interfaces.RegistryEntryRegistry;
import eu.pb4.polymer.core.impl.networking.TransformingPacketCodec;
import io.netty.buffer.ByteBuf;
import net.minecraft.class_2248;
import net.minecraft.class_2359;
import net.minecraft.class_2378;
import net.minecraft.class_2680;
import net.minecraft.class_6880;
import net.minecraft.class_9139;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import xyz.nucleoid.packettweaker.PacketContext;

@Mixin(targets = "net/minecraft/network/codec/ByteBufCodecs", priority = 500)
public interface ByteBufCodecsEntriesMixin {
    @ModifyReturnValue(method = "idMapper(Lnet/minecraft/core/IdMap;)Lnet/minecraft/network/codec/StreamCodec;", at = @At("TAIL"))
    private static <T> class_9139<ByteBuf, T> polymer$changeData(class_9139<ByteBuf, T> original, @Local(argsOnly = true) class_2359<T> iterable) {
        if (iterable instanceof class_2378<T> registry) {
            return TransformingPacketCodec.encodeOnly(original, (byteBuf, val) -> {
                var player = PacketContext.get();

                var polymerSyncedObject = PolymerSyncedObject.getSyncedObject(registry, val);
                if (polymerSyncedObject != null) {
                    var obj = polymerSyncedObject.getPolymerReplacement(val, player);

                    if (obj != null) {
                        return obj;
                    } else {
                        return registry.method_10200(0);
                    }
                }
                return val;
            });
        }
        if (iterable instanceof RegistryEntryRegistry<?> tmp) {
            //noinspection unchecked
            var registry = (class_2378<Object>) tmp.polymer$getRegistry();
            return TransformingPacketCodec.encodeOnly(original, (byteBuf, val) -> {
                var player = PacketContext.get();

                //noinspection unchecked
                var polymerSyncedObject = PolymerSyncedObject.getSyncedObject(registry, ((class_6880<Object>) val).comp_349());

                if (polymerSyncedObject != null) {
                    //noinspection unchecked
                    var obj = polymerSyncedObject.getPolymerReplacement(((class_6880<Object>) val).comp_349(), player);

                    if (obj != null) {
                        //noinspection unchecked
                        return (T) registry.method_47983(obj);
                    } else {
                        //noinspection unchecked
                        return (T) registry.method_40265(0);
                    }
                }
                return val;
            });
        } else if (iterable == class_2248.field_10651) {
            return TransformingPacketCodec.encodeOnly(original, (byteBuf, val) -> {
                var player = PacketContext.get();

                //noinspection unchecked
                return (T) PolymerBlockUtils.getPolymerBlockState((class_2680) val, player);
            });
        }

        return original;
    }
}