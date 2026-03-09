package eu.pb4.polymer.core.mixin.item;

import eu.pb4.polymer.core.api.item.PolymerItemUtils;
import eu.pb4.polymer.core.mixin.other.AbstractContainerMenuAccessor;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.nucleoid.packettweaker.PacketContext;

import java.util.ArrayList;
import net.minecraft.class_10291;
import net.minecraft.class_1661;
import net.minecraft.class_1703;
import net.minecraft.class_1799;
import net.minecraft.class_1856;
import net.minecraft.class_2788;
import net.minecraft.class_3222;
import net.minecraft.class_3914;
import net.minecraft.class_3917;
import net.minecraft.class_3971;
import net.minecraft.class_3975;

@Mixin(class_3971.class)
public abstract class StonecutterMenuMixin extends class_1703 {

    @Shadow private class_10291.class_10293<class_3975> recipesForInput;
    @Unique
    @Nullable
    private class_3222 player;

    protected StonecutterMenuMixin(@Nullable class_3917<?> type, int syncId) {
        super(type, syncId);
    }

    @Inject(method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/inventory/ContainerLevelAccess;)V", at = @At("TAIL"))
    private void catchPlayer(int syncId, class_1661 playerInventory, class_3914 context, CallbackInfo ci) {
        if (PolymerItemUtils.isStonecutterFixEnabled() && playerInventory.field_7546 instanceof class_3222 player) {
            this.player = player;
            player.field_13987.method_14364(new class_2788(player.method_51469().method_64577().method_64692(), this.recipesForInput));
        }
    }

    @Inject(method = "setupRecipeList", at = @At("TAIL"))
    private void sendClientUpdates(class_1799 stack, CallbackInfo ci) {
        if (!PolymerItemUtils.isStonecutterFixEnabled() || this.player == null) {
            return;
        }

        if (this.recipesForInput.method_64716()) {
            player.field_13987.method_14364(new class_2788(player.method_51469().method_64577().method_64692(), this.recipesForInput));
        } else {
            var list = new ArrayList<class_10291.class_10292<class_3975>>();

            var clientItem = class_1856.method_8101(PolymerItemUtils.getClientItemStack(stack, PacketContext.create(this.player)).method_7909());

            for (var x : this.recipesForInput.comp_3255()) {
                list.add(new class_10291.class_10292<>(clientItem, x.comp_3254()));
            }

            player.field_13987.method_14364(new class_2788(player.method_51469().method_64577().method_64692(),
                    new class_10291.class_10293<>(list)));

        }



        var handler =  ((AbstractContainerMenuAccessor) this).getSynchronizer();
        handler.method_34261(this, 0, class_1799.field_8037);
        handler.method_34261(this, 0, stack);
    }
}
