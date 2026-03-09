package eu.pb4.polymer.core.mixin.other;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.serialization.Codec;
import eu.pb4.polymer.common.api.PolymerCommonUtils;
import eu.pb4.polymer.core.api.utils.PolymerSyncedObject;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import xyz.nucleoid.packettweaker.PacketContext;

import java.util.Optional;
import java.util.function.Function;
import net.minecraft.class_2378;
import net.minecraft.class_6880;

@Mixin(class_2378.class)
public interface RegistryMixin {
    @Shadow class_6880<Object> wrapAsHolder(Object value);

    @Shadow Optional<class_6880.class_6883<Object>> get(int rawId);

    @ModifyReturnValue(method = "referenceHolderWithLifecycle", at = @At(value = "RETURN"))
    private Codec<class_6880.class_6883<Object>> patchCodec(Codec<class_6880.class_6883<Object>> codec) {
        return codec.xmap(Function.identity(), content -> { // Encode
            if (PolymerCommonUtils.isServerNetworkingThread() && content.method_40227()
                    && content.comp_349() instanceof PolymerSyncedObject<?> obj) {
                var ctx = PacketContext.get();
                if (obj.canSyncRawToClient(ctx)) {
                    return content;
                }
                //noinspection unchecked
                var val = ((PolymerSyncedObject<Object>) obj).getPolymerReplacement(content.comp_349(), ctx);
                return val != null && this.wrapAsHolder(val) instanceof class_6880.class_6883<Object> ref ? ref : this.get(0).orElseThrow();
            }
            return content;
        });
    }
}
