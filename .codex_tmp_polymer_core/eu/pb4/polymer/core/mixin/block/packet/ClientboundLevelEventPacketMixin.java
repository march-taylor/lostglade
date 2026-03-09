package eu.pb4.polymer.core.mixin.block.packet;

import eu.pb4.polymer.core.api.block.PolymerBlock;
import eu.pb4.polymer.core.api.block.PolymerBlockUtils;
import eu.pb4.polymer.core.api.utils.PolymerSyncedObject;
import net.minecraft.class_2248;
import net.minecraft.class_2673;
import net.minecraft.class_6088;
import net.minecraft.class_7923;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import xyz.nucleoid.packettweaker.PacketContext;

@Mixin(class_2673.class)
public class ClientboundLevelEventPacketMixin {
    @Shadow @Final private int type;
    @ModifyArg(method = "write", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/FriendlyByteBuf;writeInt(I)Lnet/minecraft/network/FriendlyByteBuf;", ordinal = 1))
    private int polymer$replaceValue(int data) {
        if (this.type == class_6088.field_31144) {
            var state = class_2248.method_9531(data);
            var player = PacketContext.get();

            if (PolymerSyncedObject.getSyncedObject(class_7923.field_41175, state.method_26204()) instanceof PolymerBlock polymerBlock) {
                state =  PolymerBlockUtils.getBlockBreakBlockStateSafely(polymerBlock, state,
                        PolymerBlockUtils.NESTED_DEFAULT_DISTANCE, player);
            }

            return class_2248.method_9507(PolymerBlockUtils.getServerSideBlockState(state, player));
        }

        return data;
    }
}
