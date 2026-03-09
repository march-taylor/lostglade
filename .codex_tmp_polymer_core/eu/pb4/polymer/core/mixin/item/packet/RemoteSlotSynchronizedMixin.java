package eu.pb4.polymer.core.mixin.item.packet;

import eu.pb4.polymer.common.api.PolymerCommonUtils;
import eu.pb4.polymer.core.api.item.PolymerItemUtils;
import eu.pb4.polymer.core.impl.interfaces.GenericPlayerContext;
import io.netty.buffer.Unpooled;
import net.minecraft.class_1799;
import net.minecraft.class_3222;
import net.minecraft.class_9129;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import xyz.nucleoid.packettweaker.PacketContext;

@Mixin(targets = "net/minecraft/world/inventory/RemoteSlot$Synchronized")
public class RemoteSlotSynchronizedMixin implements GenericPlayerContext {
    @Unique
    private PacketContext context = PacketContext.create();

    @Override
    public void polymer$setPlayer(class_3222 player) {
        this.context = PacketContext.create(player);
    }

    @ModifyArg(method = "matches", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/HashedStack;matches(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/network/HashedPatchMap$HashGenerator;)Z"))
    private class_1799 polymerifyCheckedStack(class_1799 stack) {
        if (PolymerItemUtils.isServerItem(stack, this.context) && this.context.getPlayer() != null) {
            var buf = new class_9129(Unpooled.buffer(), this.context.getPlayer().method_56673());
            PolymerCommonUtils.executeWithNetworkingLogic(context.getBackingPacketListener(), () -> class_1799.field_48349.encode(buf, stack));
            return class_1799.field_48349.decode(buf);
        }
        return stack;
    }
}
