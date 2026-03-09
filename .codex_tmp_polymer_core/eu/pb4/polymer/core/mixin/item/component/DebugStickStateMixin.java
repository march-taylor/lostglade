package eu.pb4.polymer.core.mixin.item.component;

import eu.pb4.polymer.core.api.utils.PolymerSyncedObject;
import eu.pb4.polymer.core.impl.TransformingComponent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import xyz.nucleoid.packettweaker.PacketContext;

import java.util.Map;
import net.minecraft.class_2248;
import net.minecraft.class_2769;
import net.minecraft.class_6880;
import net.minecraft.class_7923;
import net.minecraft.class_9281;

@Mixin(class_9281.class)
public class DebugStickStateMixin implements TransformingComponent {
    @Shadow @Final private Map<class_6880<class_2248>, class_2769<?>> properties;

    @Override
    public Object polymer$getTransformed(PacketContext context) {
        if (polymer$requireModification(context)) {
            return class_9281.field_49310;
        }
        return this;
    }

    @Override
    public boolean polymer$requireModification(PacketContext context) {
        for (var key : this.properties.keySet()) {
            if (!PolymerSyncedObject.canSyncRawToClient(class_7923.field_41175, key.comp_349(), context)) {
                return true;
            }
        }
        return false;
    }
}
