package eu.pb4.polymer.core.mixin.client.compat;

import dev.emi.emi.api.stack.ItemEmiStack;
import eu.pb4.polymer.core.impl.client.compat.CompatUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.class_1799;
import net.minecraft.class_2960;
import net.minecraft.class_9331;
import net.minecraft.class_9334;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@SuppressWarnings("UnstableApiUsage")
@Environment(EnvType.CLIENT)
@Pseudo
@Mixin(ItemEmiStack.class)
public abstract class emi_ItemEmiStack2Mixin {

    @Shadow
    public abstract class_1799 getItemStack();

    @Shadow public abstract <T> @Nullable T get(class_9331<? extends T> type);

    @Inject(method = "getKey", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void polymer$getKey(CallbackInfoReturnable<Object> cir) {
        var nbt = this.get(class_9334.field_49628);
        if (CompatUtils.isServerSide(nbt)) {
            cir.setReturnValue(CompatUtils.getKey(nbt));
        }
    }

    @Inject(method = "getId", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void polymer$getId(CallbackInfoReturnable<class_2960> cir) {
        var id = CompatUtils.getId(this.get(class_9334.field_49628));
        if (id != null) {
            cir.setReturnValue(id);
        }
    }
}
