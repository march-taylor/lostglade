package eu.pb4.polymer.core.mixin.block;

import eu.pb4.polymer.core.api.block.PolymerBlock;
import eu.pb4.polymer.core.api.block.PolymerBlockUtils;
import eu.pb4.polymer.core.api.utils.PolymerSyncedObject;
import net.minecraft.class_1922;
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
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.nucleoid.packettweaker.PacketContext;

@Mixin(class_4970.class_4971.class)
public abstract class BlockStateBaseMixin {
    @Shadow
    public abstract class_2248 getBlock();

    @SuppressWarnings("DataFlowIssue")
    @Inject(method = "getCollisionShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;", at = @At("HEAD"), cancellable = true)
    private void polymer$replaceCollision(class_1922 level, class_2338 pos, class_3726 context, CallbackInfoReturnable<class_265> cir) {
        //noinspection ConstantValue
        if (PolymerSyncedObject.getSyncedObject(class_7923.field_41175, this.getBlock()) instanceof PolymerBlock block
                && context instanceof class_3727 entityShapeContext
                && entityShapeContext.method_32480() instanceof class_3222 player && block.overridePlayerCollisionsWithPolymer(level, pos, (class_2680) (Object) this, player)) {
            var clientState =  PolymerBlockUtils.getBlockStateSafely(block, (class_2680) (Object) this, PacketContext.create(player));
            if (!(PolymerSyncedObject.getSyncedObject(class_7923.field_41175, clientState.method_26204()) instanceof PolymerBlock)) {
                cir.setReturnValue(clientState.method_26194(level, pos, context));
            }
        }
    }
}
