package eu.pb4.polymer.core.mixin.block;

import net.minecraft.class_2338;
import net.minecraft.class_2487;
import net.minecraft.class_2591;
import net.minecraft.class_2622;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(class_2622.class)
public interface ClientboundBlockEntityDataPacketAccessor {
    @Invoker("<init>")
    static class_2622 createBlockEntityUpdateS2CPacket(class_2338 pos, class_2591<?> blockEntityType, class_2487 nbt) {
        throw new UnsupportedOperationException();
    }
}
