package eu.pb4.polymer.core.mixin.block.packet;

import eu.pb4.polymer.core.api.block.PolymerBlockUtils;
import eu.pb4.polymer.core.impl.client.InternalClientRegistry;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.class_2359;
import net.minecraft.class_2680;
import net.minecraft.class_2814;
import net.minecraft.class_2834;
import net.minecraft.class_6564;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import xyz.nucleoid.packettweaker.PacketContext;

@Mixin(value = {class_2834.class, class_6564.class, class_2814.class}, priority = 500)
public abstract class BlockPaletteMixin {

    @ModifyArg(method = {"write", "getSerializedSize"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/core/IdMap;getId(Ljava/lang/Object;)I"))
    public Object polymer_getIdRedirect(Object object) {
        if (object instanceof class_2680 blockState) {
            return PolymerBlockUtils.getPolymerBlockState(blockState, PacketContext.get());
        }
        return object;
    }

    @Environment(EnvType.CLIENT)
    @Redirect(method = "read", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/IdMap;byIdOrThrow(I)Ljava/lang/Object;"))
    private Object polymer_replaceState(class_2359<?> instance, int i) {
        return InternalClientRegistry.decodeRegistry(instance, i);
    }
}