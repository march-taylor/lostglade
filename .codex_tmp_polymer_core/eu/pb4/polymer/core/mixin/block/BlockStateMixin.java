package eu.pb4.polymer.core.mixin.block;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.serialization.Codec;
import eu.pb4.polymer.common.api.PolymerCommonUtils;
import eu.pb4.polymer.core.api.block.PolymerBlock;
import eu.pb4.polymer.core.api.block.PolymerBlockUtils;
import eu.pb4.polymer.core.api.utils.PolymerSyncedObject;
import eu.pb4.polymer.core.impl.interfaces.BlockStateExtra;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import xyz.nucleoid.packettweaker.PacketContext;

import java.util.function.Function;
import net.minecraft.class_2680;
import net.minecraft.class_7923;

@Mixin(class_2680.class)
public abstract class BlockStateMixin implements BlockStateExtra {
    @Shadow protected abstract class_2680 asState();

    @Unique
    private boolean polymer$calculatedIsLight;
    @Unique
    private boolean polymer$isLight;

    @Override
    public boolean polymer$isPolymerLightSource() {
        if (this.polymer$calculatedIsLight) {
            return this.polymer$isLight;
        }

        if (PolymerSyncedObject.getSyncedObject(class_7923.field_41175, this.asState().method_26204()) instanceof PolymerBlock polymerBlock) {
            this.polymer$isLight = this.asState().method_26213() != polymerBlock.getPolymerBlockState(this.asState(), PacketContext.create()).method_26213();
        }


        this.polymer$calculatedIsLight = true;
        return false;
    }

    @ModifyExpressionValue(method = "<clinit>", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;codec(Lcom/mojang/serialization/Codec;Ljava/util/function/Function;)Lcom/mojang/serialization/Codec;"))
    private static Codec<class_2680> patchCodec(Codec<class_2680> codec) {
        return codec.xmap(Function.identity(), content -> { // Encode
            if (PolymerCommonUtils.isServerNetworkingThread() && PolymerSyncedObject.getSyncedObject(class_7923.field_41175, content.method_26204()) != null) {
                return PolymerBlockUtils.getPolymerBlockState(content, PacketContext.get());
            }
            return content;
        });
    }
}
