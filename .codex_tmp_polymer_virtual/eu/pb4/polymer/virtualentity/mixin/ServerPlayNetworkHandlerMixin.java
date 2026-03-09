package eu.pb4.polymer.virtualentity.mixin;


import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.impl.HolderHolder;
import eu.pb4.polymer.virtualentity.impl.PacketInterHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Collection;
import net.minecraft.class_10371;
import net.minecraft.class_1297;
import net.minecraft.class_2824;
import net.minecraft.class_3222;
import net.minecraft.class_3244;

@Mixin(class_3244.class)
public class ServerPlayNetworkHandlerMixin implements HolderHolder {
    @Unique
    private final Collection<ElementHolder> polymerVE$holders = new ArrayList<>();
    @Shadow
    public class_3222 player;

    @Override
    public void polymer$addHolder(ElementHolder holderAttachment) {
        this.polymerVE$holders.add(holderAttachment);
    }

    @Override
    public void polymer$removeHolder(ElementHolder holderAttachment) {
        this.polymerVE$holders.remove(holderAttachment);
    }

    @Override
    public Collection<ElementHolder> polymer$getHolders() {
        return this.polymerVE$holders;
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void polymerVE$tick(CallbackInfo ci) {
        try {
            for (var holder : new ArrayList<>(this.polymerVE$holders)) {
                if (holder.getAttachment() == null) {
                    holder.stopWatching(this.player);
                }
            }
        } catch (Throwable e) {
        }
    }
    @ModifyVariable(method = "handlePickItemFromEntity", at = @At(value = "STORE", ordinal = 0))
    private class_1297 polymerVE$onPickEntity(class_1297 entity, class_10371 packet) {
        if (entity == null && !this.polymerVE$holders.isEmpty()) {
            for (var x : this.polymerVE$holders) {
                if (x.isPartOf(packet.comp_3328())) {
                    var i = x.getInteraction(packet.comp_3328(), this.player);
                    if (i != null) {
                        i.pickItem(this.player, packet.comp_3329());
                        break;
                    }
                }
            }
        }
        return entity;
    }


    @ModifyVariable(method = "handleInteract", at = @At(value = "STORE", ordinal = 0))
    private class_1297 polymerVE$onInteract(class_1297 entity, class_2824 packet) {
        if (entity == null && !this.polymerVE$holders.isEmpty()) {
            var id = ((PlayerInteractEntityC2SPacketAccessor) packet).getEntityId();
            for (var x : this.polymerVE$holders) {
                if (x.isPartOf(id)) {
                    var i = x.getInteraction(id, this.player);
                    if (i != null) {
                        packet.method_34209(new PacketInterHandler(this.player, i));
                        break;
                    }
                }
            }
        }
        return entity;
    }
}
