package eu.pb4.polymer.core.mixin.item.packet;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import eu.pb4.polymer.common.impl.CompatStatus;
import eu.pb4.polymer.core.api.item.PolymerItem;
import eu.pb4.polymer.core.api.utils.PolymerSyncedObject;
import eu.pb4.polymer.core.impl.interfaces.SkipCheck;
import eu.pb4.polymer.core.impl.networking.TransformingPacketCodec;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.ArrayList;
import net.minecraft.class_10302;
import net.minecraft.class_7923;
import net.minecraft.class_7924;
import net.minecraft.class_9129;
import net.minecraft.class_9139;

@Mixin(class_10302.class)
public interface SlotDisplayMixin {
    @SuppressWarnings("DataFlowIssue")
    @ModifyExpressionValue(method = "<clinit>", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/codec/StreamCodec;dispatch(Ljava/util/function/Function;Ljava/util/function/Function;)Lnet/minecraft/network/codec/StreamCodec;"))
    private static class_9139<class_9129, class_10302> transformDisplays(class_9139<class_9129, class_10302> original) {
        return TransformingPacketCodec.encodeOnly(original, (buf, display) -> switch (display) {
            case class_10302.class_10306 item when PolymerSyncedObject.getSyncedObject(class_7923.field_41178, item.comp_3273().comp_349()) instanceof PolymerItem ->
                    new class_10302.class_10307(item.comp_3273().comp_349().method_7854());
            case class_10302.class_10311 tagSlot when !((SkipCheck) (Object) tagSlot).polymer$skipped() -> {
                var tag = buf.method_56349().method_30530(class_7924.field_41197).method_46733(tagSlot.comp_3275());
                if (tag.isEmpty()) {
                    yield tagSlot;
                }

                var array = new ArrayList<class_10302>();
                for (var entry : tag.get()) {
                    if (PolymerSyncedObject.getSyncedObject(class_7923.field_41178, entry.comp_349()) instanceof PolymerItem) {
                        array.add(new class_10302.class_10307(entry.comp_349().method_7854()));
                    }
                }
                if (!array.isEmpty()) {
                    var out = new class_10302.class_10311(tagSlot.comp_3275());
                    ((SkipCheck) (Object) out).polymer$setSkipped();

                    if (CompatStatus.POLYMC) {
                        if (((SkipCheck) (Object) tagSlot).polymc$skipped()) {
                            ((SkipCheck) (Object) out).polymc$setSkipped();
                        }
                    }

                    array.addFirst(out);
                    yield new class_10302.class_10304(array);
                }
                yield tagSlot;
            }
            default -> display;
        });
    }
}
