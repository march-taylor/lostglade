package eu.pb4.polymer.core.mixin.client.rendering;

import eu.pb4.polymer.core.api.block.PolymerBlock;
import eu.pb4.polymer.core.api.utils.PolymerKeepModel;
import eu.pb4.polymer.core.api.utils.PolymerSyncedObject;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.class_2246;
import net.minecraft.class_2680;
import net.minecraft.class_773;
import net.minecraft.class_7923;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Environment(EnvType.CLIENT)
@Mixin(class_773.class)
public class BlockModelsMixin {
    /*@Inject(method = "getModelId(Lnet/minecraft/block/BlockState;)Lnet/minecraft/client/util/ModelIdentifier;", at = @At("HEAD"), cancellable = true, require = 0)
    private static void polymer$skipModels(BlockState state, CallbackInfoReturnable<ModelIdentifier> cir) {
        if (PolymerKeepModel.useServerModel(state.getBlock())) {
            cir.setReturnValue(new ModelIdentifier(Identifier.of("minecraft", "air"), ""));
        }
    }*/

    @ModifyVariable(method = "getBlockModel", at = @At("HEAD"), require = 0, argsOnly = true)
    private class_2680 polymer$replaceBlockState(class_2680 state) {
        return PolymerSyncedObject.getSyncedObject(class_7923.field_41175, state.method_26204()) instanceof PolymerBlock block && !PolymerKeepModel.is(block) ? class_2246.field_10124.method_9564() : state;
    }
}
