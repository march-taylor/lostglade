package eu.pb4.polymer.core.mixin.compat;

import eu.pb4.polymer.core.api.block.PolymerBlockUtils;
import eu.pb4.polymer.core.impl.client.InternalClientRegistry;
import net.caffeinemc.mods.lithium.common.world.chunk.LithiumHashPalette;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.class_2359;
import net.minecraft.class_2680;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import xyz.nucleoid.packettweaker.PacketContext;

@Pseudo
@Mixin(value = LithiumHashPalette.class, priority = 500)
public class lithium_BlockPaletteMixin {
    @ModifyArg(method = {"write", "getSerializedSize" }, at = @At(value = "INVOKE", target = "Lnet/minecraft/core/IdMap;getId(Ljava/lang/Object;)I"))
    public Object polymer$getIdRedirect(Object object) {
        if (object instanceof class_2680 blockState) {
            return PolymerBlockUtils.getPolymerBlockState(blockState, PacketContext.get());
        }
        return object;
    }

    @Environment(EnvType.CLIENT)
    @Redirect(method = "read", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/IdMap;byIdOrThrow(I)Ljava/lang/Object;"), require = 0)
    private Object polymer$replaceState(class_2359<?> instance, int index) {
        return InternalClientRegistry.decodeRegistry(instance, index);
    }
}
