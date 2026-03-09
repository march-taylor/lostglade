package eu.pb4.polymer.core.mixin.item.component;

import eu.pb4.polymer.core.api.utils.PolymerSyncedObject;
import eu.pb4.polymer.core.impl.TransformingComponent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import xyz.nucleoid.packettweaker.PacketContext;

import java.util.List;
import net.minecraft.class_7923;
import net.minecraft.class_9298;

@Mixin(class_9298.class)
public abstract class SuspiciousStewEffectsMixin implements TransformingComponent {

    @Shadow @Final private List<class_9298.class_8751> effects;

    @Override
    public Object polymer$getTransformed(PacketContext context) {
        if (!polymer$requireModification(context)) {
            return this;
        }

        return new class_9298(List.of());
    }

    @Override
    public boolean polymer$requireModification(PacketContext context) {
        for (var effect : this.effects) {
            if (!PolymerSyncedObject.canSyncRawToClient(class_7923.field_41174, effect.comp_1838().comp_349(), context)) {
                return true;
            }
        }
        return false;
    }
}
