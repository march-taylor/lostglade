package eu.pb4.polymer.core.mixin.entity;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import eu.pb4.polymer.core.api.entity.PolymerEntity;
import eu.pb4.polymer.core.api.entity.PolymerEntityUtils;
import eu.pb4.polymer.core.impl.interfaces.EntityAttachedPacket;
import eu.pb4.polymer.core.impl.interfaces.PossiblyInitialPacket;
import eu.pb4.polymer.core.impl.networking.TransformingPacketCodec;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import xyz.nucleoid.packettweaker.PacketContext;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.class_1299;
import net.minecraft.class_1309;
import net.minecraft.class_2781;
import net.minecraft.class_5135;
import net.minecraft.class_9129;
import net.minecraft.class_9139;

@Mixin(class_2781.class)
public abstract class ClientboundUpdateAttributesPacketMixin implements PossiblyInitialPacket {
    @Unique
    private boolean isInitial = false;

    @Override
    public boolean polymer$getInitial() {
        return this.isInitial;
    }

    @Override
    public void polymer$setInitial() {
        this.isInitial = true;
    }

    @SuppressWarnings("UnreachableCode")
    @ModifyExpressionValue(method = "<clinit>", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/codec/StreamCodec;composite(Lnet/minecraft/network/codec/StreamCodec;Ljava/util/function/Function;Lnet/minecraft/network/codec/StreamCodec;Ljava/util/function/Function;Ljava/util/function/BiFunction;)Lnet/minecraft/network/codec/StreamCodec;"))
    private static class_9139<class_9129, class_2781> patchCodec(class_9139<class_9129, class_2781> original) {
        return TransformingPacketCodec.encodeOnly(original, (buf, packet) -> {
            if (PolymerEntity.get(EntityAttachedPacket.get(packet, packet.method_11937())) instanceof PolymerEntity entity) {
                var context = PacketContext.get();
                var type = entity.getPolymerEntityType(context);
                var p = new class_2781(packet.method_11937(), List.of());
                var list = ((ClientboundUpdateAttributesPacketAccessor) p).getAttributes();
                //noinspection unchecked
                var vanillaContainer = class_5135.method_26873((class_1299<? extends class_1309>) type);
                var data = new ArrayList<>(packet.method_11938());
                entity.modifyRawEntityAttributeData(data, context.getPlayer(), ((PossiblyInitialPacket) packet).polymer$getInitial());
                for (var entry : data) {
                    if (vanillaContainer.method_27310(entry.comp_2177()) && !PolymerEntityUtils.isPolymerEntityAttribute(entry.comp_2177())) {
                        list.add(entry);
                    }
                }
                return p;
            } else {
                var p = new class_2781(packet.method_11937(), List.of());
                var list = ((ClientboundUpdateAttributesPacketAccessor) p).getAttributes();
                for (var entry : packet.method_11938()) {
                    if (!PolymerEntityUtils.isPolymerEntityAttribute(entry.comp_2177())) {
                        list.add(entry);
                    }
                }
                return p;
            }
        });
    }
}
