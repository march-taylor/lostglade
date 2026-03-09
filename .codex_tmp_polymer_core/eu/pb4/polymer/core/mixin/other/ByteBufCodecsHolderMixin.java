package eu.pb4.polymer.core.mixin.other;


import eu.pb4.polymer.core.api.other.PolymerSoundEvent;
import eu.pb4.polymer.core.api.utils.PolymerSyncedObject;
import eu.pb4.polymer.rsm.api.RegistrySyncUtils;
import net.minecraft.class_3414;
import net.minecraft.class_6880;
import net.minecraft.class_7923;
import net.minecraft.class_9129;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import xyz.nucleoid.packettweaker.PacketContext;

@Mixin(targets = "net/minecraft/network/codec/ByteBufCodecs$30", priority = 500)
public abstract class ByteBufCodecsHolderMixin {
    @ModifyVariable(method = "encode(Lnet/minecraft/network/RegistryFriendlyByteBuf;Lnet/minecraft/core/Holder;)V", at = @At("HEAD"), argsOnly = true)
    private class_6880<?> polymer$changeData(class_6880<?> val, class_9129 buf) {
        var player = PacketContext.get();

        if (val.comp_349() instanceof class_3414 soundEvent) {
            if (PolymerSyncedObject.getSyncedObject(class_7923.field_41172, soundEvent) instanceof PolymerSoundEvent syncedObject) {
                var replacement = syncedObject.getPolymerReplacement(soundEvent, player);

                if (PolymerSyncedObject.getSyncedObject(class_7923.field_41172, replacement) instanceof PolymerSoundEvent) {
                    return class_6880.method_40223(replacement);
                }

                return class_7923.field_41172.method_47983(replacement);
            } else if (RegistrySyncUtils.isServerEntry(class_7923.field_41172, soundEvent)) {
                return class_6880.method_40223(soundEvent);
            }
        }

        return val;
    }

}