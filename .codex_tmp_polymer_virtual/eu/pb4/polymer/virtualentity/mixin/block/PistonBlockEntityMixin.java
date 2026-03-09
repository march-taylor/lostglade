package eu.pb4.polymer.virtualentity.mixin.block;

import com.llamalad7.mixinextras.sugar.Local;
import eu.pb4.polymer.virtualentity.api.attachment.BlockBoundAttachment;
import eu.pb4.polymer.virtualentity.impl.PistonExt;
import eu.pb4.polymer.virtualentity.impl.attachment.PistonAttachment;
import net.minecraft.class_1937;
import net.minecraft.class_2338;
import net.minecraft.class_2586;
import net.minecraft.class_2591;
import net.minecraft.class_2669;
import net.minecraft.class_2680;
import net.minecraft.class_3218;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(class_2669.class)
public abstract class PistonBlockEntityMixin extends class_2586 implements PistonExt {
    public PistonBlockEntityMixin(class_2591<?> type, class_2338 pos, class_2680 state) {
        super(type, pos, state);
    }

    @Shadow public abstract class_2680 getMovedState();

    @Nullable
    @Unique
    private PistonAttachment attachment;

    @Override
    public void polymer$setAttachement(PistonAttachment attachment) {
        this.attachment = attachment;
    }

    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/piston/PistonMovingBlockEntity;moveCollidedEntities(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;FLnet/minecraft/world/level/block/piston/PistonMovingBlockEntity;)V"))
    private static void updatePos(class_1937 world, class_2338 pos, class_2680 state, class_2669 blockEntity, CallbackInfo ci, @Local float progress) {
        var att = ((PistonBlockEntityMixin) (Object) blockEntity).attachment;
        if (att != null) {
            att.update(progress);
        }
    }

    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/Block;updateFromNeighbourShapes(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private static void updatePos(class_1937 world, class_2338 pos, class_2680 state, class_2669 blockEntity, CallbackInfo ci) {
        var att = ((PistonBlockEntityMixin) (Object) blockEntity).attachment;

        if (att != null) {
            att.update(1);
            BlockBoundAttachment.fromMoving(att.holder(), (class_3218) world, pos, blockEntity.method_11495());
            ((PistonBlockEntityMixin) (Object) blockEntity).attachment = null;
        }
    }

    @Inject(method = "finalTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/Block;updateFromNeighbourShapes(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private void updatePos(CallbackInfo ci) {
        var att = this.attachment;

        if (att != null) {
            att.update(1);
            BlockBoundAttachment.fromMoving(att.holder(), (class_3218) this.field_11863, field_11867, this.getMovedState());
            this.attachment = null;
        }
    }
}
