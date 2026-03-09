package eu.pb4.polymer.core.mixin.block;

import eu.pb4.polymer.core.api.block.PolymerBlock;
import eu.pb4.polymer.core.api.block.PolymerBlockUtils;
import eu.pb4.polymer.core.api.utils.PolymerSyncedObject;
import net.minecraft.class_1922;
import net.minecraft.class_1937;
import net.minecraft.class_2248;
import net.minecraft.class_2338;
import net.minecraft.class_265;
import net.minecraft.class_2680;
import net.minecraft.class_3222;
import net.minecraft.class_3726;
import net.minecraft.class_3727;
import net.minecraft.class_4970;
import net.minecraft.class_7923;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.nucleoid.packettweaker.PacketContext;

@Mixin(class_4970.class)
public class BlockBehaviourMixin {
    @Inject(method = "getShape", at = @At("HEAD"), cancellable = true)
    private void polymer$replaceOutlineShape(class_2680 state, class_1922 world, class_2338 pos, class_3726 context, CallbackInfoReturnable<class_265> cir) {
        //noinspection ConstantValue
        if (((Object) this) instanceof class_2248 block1 && PolymerSyncedObject.getSyncedObject(class_7923.field_41175, block1) instanceof PolymerBlock block) {
            var clientState = PolymerBlockUtils.getBlockStateSafely(block, state,
                    world instanceof class_1937 realWorld ? PacketContext.create(realWorld.method_30349()) : PacketContext.create());
            if (!(PolymerSyncedObject.getSyncedObject(class_7923.field_41175, clientState.method_26204()) instanceof PolymerBlock)) {
                cir.setReturnValue(clientState.method_26172(world, pos, context));
            }
        }
    }

    @Inject(method = "getCollisionShape", at = @At("HEAD"), cancellable = true)
    private void polymer$replaceCollision(class_2680 state, class_1922 world, class_2338 pos, class_3726 context, CallbackInfoReturnable<class_265> cir) {
        //noinspection ConstantValue
        if (((Object) this) instanceof class_2248 block1 && PolymerSyncedObject.getSyncedObject(class_7923.field_41175, block1) instanceof PolymerBlock block) {
            var clientState = context instanceof class_3727 entityShapeContext
                    && entityShapeContext.method_32480() instanceof class_3222 player && player.field_13987 != null
                    ? PolymerBlockUtils.getBlockStateSafely(block, state, PacketContext.create(player))
                    : PolymerBlockUtils.getBlockStateSafely(block, state, world instanceof class_1937 realWorld ? PacketContext.create(realWorld.method_30349()) : PacketContext.create());
            if (!(PolymerSyncedObject.getSyncedObject(class_7923.field_41175, clientState.method_26204()) instanceof PolymerBlock)) {
                cir.setReturnValue(clientState.method_26194(world, pos, context));
            }
        }
    }
}
