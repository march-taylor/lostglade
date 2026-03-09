package eu.pb4.polymer.core.mixin.item.packet;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import eu.pb4.polymer.common.api.PolymerCommonUtils;
import eu.pb4.polymer.core.api.item.PolymerItemUtils;
import eu.pb4.polymer.core.mixin.item.DataComponentPatchAccessor;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import xyz.nucleoid.packettweaker.PacketContext;

import java.util.Optional;
import net.minecraft.class_1799;
import net.minecraft.class_9129;
import net.minecraft.class_9331;

@Mixin(targets = "net/minecraft/world/item/ItemStack$1", priority = 500)
public abstract class ItemStackPacketCodecMixin {

    @ModifyVariable(method = "encode(Lnet/minecraft/network/RegistryFriendlyByteBuf;Lnet/minecraft/world/item/ItemStack;)V", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private class_1799 polymer$replaceWithVanillaItem(class_1799 itemStack, @Local(argsOnly = true) class_9129 buf) {
        var player = PacketContext.get();
        if (player.getRegistryWrapperLookup() == null) {
            player = PacketContext.create(buf.method_56349());
        }

        return PolymerItemUtils.getPolymerItemStack(itemStack, player);
    }

    @ModifyArg(method = "encode(Lnet/minecraft/network/RegistryFriendlyByteBuf;Lnet/minecraft/world/item/ItemStack;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/network/codec/StreamCodec;encode(Ljava/lang/Object;Ljava/lang/Object;)V", ordinal = 1), index = 1)
    private Object polymer$addSyncedDefaults(Object object, @Local(argsOnly = true) class_1799 stack) {
        var changedDefaults = PolymerItemUtils.getSyncedDefaultComponents(stack.method_7909());
        if (changedDefaults.isEmpty()) {
            return object;
        }
        var original = ((DataComponentPatchAccessor) object).getMap();
        var changes = new Reference2ObjectOpenHashMap<class_9331<?>, Optional<?>>(changedDefaults.size() + original.size());
        changes.putAll(original);
        for (var type : changedDefaults) {
            if (!changes.containsKey(type)) {
                changes.put(type, Optional.ofNullable(stack.method_7909().method_57347().method_58694(type)));
            }
        }

        return DataComponentPatchAccessor.createComponentChanges(changes);
    }
    @ModifyReturnValue(method = "decode(Lnet/minecraft/network/RegistryFriendlyByteBuf;)Lnet/minecraft/world/item/ItemStack;", at = @At(value = "RETURN", ordinal = 1))
    private class_1799 polymerCore$decodeItemStackServer(class_1799 stack, @Local(argsOnly = true) class_9129 buf) {
        return PolymerCommonUtils.isServerNetworkingThread() ? PolymerItemUtils.getRealItemStack(stack, buf.method_56349()) : stack;
    }
}