package eu.pb4.polymer.virtualentity.mixin.block;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import eu.pb4.polymer.virtualentity.api.BlockWithElementHolder;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.BlockBoundAttachment;
import eu.pb4.polymer.virtualentity.impl.attachment.FallingBlockEntityAttachment;
import net.minecraft.class_1297;
import net.minecraft.class_1299;
import net.minecraft.class_1540;
import net.minecraft.class_1937;
import net.minecraft.class_2338;
import net.minecraft.class_2680;
import net.minecraft.class_3218;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(class_1540.class)
public abstract class FallingBlockEntityMixin extends class_1297 {
    public FallingBlockEntityMixin(class_1299<?> type, class_1937 world) {
        super(type, world);
    }

    @Shadow public abstract class_2680 getBlockState();

    @Nullable
    @Unique
    private FallingBlockEntityAttachment attachment;

    @Inject(method = "fall", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z", shift = At.Shift.BEFORE))
    private static void getCurrentAttachment(class_1937 world, class_2338 pos, class_2680 state, CallbackInfoReturnable<class_1540> cir, @Local class_1540 entity,
                                             @Share("holder") LocalRef<ElementHolder> ref) {
        var x = BlockBoundAttachment.get(world, pos);
        if (x != null) {
            var holder = BlockWithElementHolder.get(x.getBlockState());
            if (holder != null) {
                var transformed = holder.createMovingElementHolder((class_3218) world, pos, x.getBlockState(), x.holder());

                if (transformed != null) {
                    if (transformed == x.holder()) {
                        x.holder().setAttachment(null);
                        x.destroy();
                    }
                    ref.set(transformed);
                }
            }
        }
    }

    @Inject(method = "fall", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z", shift = At.Shift.AFTER))
    private static void attach(class_1937 world, class_2338 pos, class_2680 state, CallbackInfoReturnable<class_1540> cir, @Local class_1540 entity,
                                             @Share("holder") LocalRef<ElementHolder> ref) {
        var x = ref.get();
        if (x != null)  {
            ((FallingBlockEntityMixin) (Object) entity).attachment = new FallingBlockEntityAttachment(x, entity);
        }
    }


    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z", shift = At.Shift.BEFORE))
    private void updatePos(CallbackInfo ci, @Local(ordinal = 0) class_2338 blockPos) {
        var att = this.attachment;

        if (att != null) {
            BlockBoundAttachment.fromMoving(att.holder(), (class_3218) this.method_73183(), blockPos, this.getBlockState());
        }
    }
}
