package eu.pb4.polymer.core.mixin.block.packet;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import eu.pb4.polymer.core.api.utils.PolymerSyncedObject;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import xyz.nucleoid.packettweaker.PacketContext;

import java.util.List;
import net.minecraft.class_2591;
import net.minecraft.class_6603;
import net.minecraft.class_7923;

@Mixin(class_6603.class)
public class ClientboundLevelChunkPacketDataMixin {
    @WrapWithCondition(method = "<init>(Lnet/minecraft/world/level/chunk/LevelChunk;)V", at = @At(value = "INVOKE", target = "Ljava/util/List;add(Ljava/lang/Object;)Z"))
    private boolean skipPolymerEntries(List<?> instance, Object e) {
        return !(PolymerSyncedObject.getSyncedObject(class_7923.field_41181, ((BlockEntityInfoAccessor) e).getType()) instanceof PolymerSyncedObject<class_2591<?>> obj
                && obj.getPolymerReplacement(((BlockEntityInfoAccessor) e).getType(), PacketContext.get()) == null);
    }
}
