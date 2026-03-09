package eu.pb4.polymer.virtualentity.mixin.block;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import eu.pb4.polymer.virtualentity.api.BlockWithElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.BlockBoundAttachment;
import eu.pb4.polymer.virtualentity.impl.HolderAttachmentHolder;
import eu.pb4.polymer.virtualentity.impl.PistonExt;
import eu.pb4.polymer.virtualentity.impl.attachment.PistonAttachment;
import net.minecraft.class_1937;
import net.minecraft.class_2338;
import net.minecraft.class_2350;
import net.minecraft.class_2586;
import net.minecraft.class_2665;
import net.minecraft.class_3218;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(class_2665.class)
public class PistonBlockMixin {
    @Inject(method = "moveBlocks", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/core/BlockPos;relative(Lnet/minecraft/core/Direction;)Lnet/minecraft/core/BlockPos;", ordinal = 1, shift = At.Shift.BEFORE))
    private void collectAttachmentHolder(class_1937 world, class_2338 pos, class_2350 dir, boolean retract, CallbackInfoReturnable<Boolean> cir,
                                         @Local(ordinal = 2) class_2338 blockPos, @Share("attachment") LocalRef<PistonAttachment> attachment) {
        if (world instanceof class_3218 serverWorld) {
            var x = BlockBoundAttachment.get(world, blockPos);

            if (x != null ) {
                var holder = BlockWithElementHolder.get(x.getBlockState());
                if (holder != null) {
                    var transformed = holder.createMovingElementHolder(serverWorld, blockPos, x.getBlockState(), x.holder());

                    if (transformed != null) {
                        if (transformed == x.holder()) {
                            x.destroy();
                        }
                        attachment.set(new PistonAttachment(transformed, world.method_8500(blockPos), x.getBlockState(), blockPos, retract ? dir : dir.method_10153()));
                    }
                }
            }
        }
    }

    @ModifyArg(method = "moveBlocks", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;setBlockEntity(Lnet/minecraft/world/level/block/entity/BlockEntity;)V", ordinal = 0))
    private class_2586 collectAttachmentHolder(class_2586 blockEntity, @Share("attachment") LocalRef<PistonAttachment> attachment) {
        var val = attachment.get();
        if (val != null && blockEntity != null) {
            ((PistonExt) blockEntity).polymer$setAttachement(val);
            attachment.set(null);
        }
        return blockEntity;
    }
}
