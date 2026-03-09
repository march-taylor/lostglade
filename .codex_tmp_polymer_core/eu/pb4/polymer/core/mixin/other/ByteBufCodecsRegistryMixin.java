package eu.pb4.polymer.core.mixin.other;


import eu.pb4.polymer.core.api.utils.PolymerSyncedObject;
import net.minecraft.class_5321;
import net.minecraft.class_6880;
import net.minecraft.class_9129;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import xyz.nucleoid.packettweaker.PacketContext;

@Mixin(targets = "net/minecraft/network/codec/ByteBufCodecs$29", priority = 500)
public abstract class ByteBufCodecsRegistryMixin {

    @Shadow @Final private class_5321 val$registryKey;

    @SuppressWarnings({"rawtypes", "ShadowModifiers"})

    @ModifyVariable(method = "encode(Lnet/minecraft/network/RegistryFriendlyByteBuf;Ljava/lang/Object;)V", at = @At("HEAD"), argsOnly = true)
    private Object polymer$changeData(Object val, class_9129 buf) {
        var player = PacketContext.get();
        //noinspection unchecked
        var reg = buf.method_56349().method_30530(this.val$registryKey);


        if (val instanceof class_6880<?> registryEntry) {
            var value = registryEntry.comp_349();
            var obj = PolymerSyncedObject.getSyncedObject(reg, value);

            if (obj != null) {
                var replacement = obj.getPolymerReplacement(value, player);

                if (replacement != null) {
                    //noinspection unchecked
                    return reg.method_47983(replacement);
                }

                return reg.method_40265(0);
            }
        } else {
            var obj = PolymerSyncedObject.getSyncedObject(reg, val);
            if (obj != null) {
                var replacement = obj.getPolymerReplacement(val, player);

                if (replacement != null) {
                    return replacement;
                }
                return reg.method_10200(0);
            }
        }

        return val;
    }
}