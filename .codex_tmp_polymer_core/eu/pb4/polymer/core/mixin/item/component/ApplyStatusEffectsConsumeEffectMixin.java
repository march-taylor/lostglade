package eu.pb4.polymer.core.mixin.item.component;

import eu.pb4.polymer.core.api.utils.PolymerSyncedObject;
import eu.pb4.polymer.core.impl.TransformingComponent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import xyz.nucleoid.packettweaker.PacketContext;

import java.util.List;
import net.minecraft.class_10132;
import net.minecraft.class_1293;
import net.minecraft.class_7923;

@Mixin(class_10132.class)
public abstract class ApplyStatusEffectsConsumeEffectMixin implements TransformingComponent {
    @Shadow @Final private List<class_1293> effects;

    @Override
    public Object polymer$getTransformed(PacketContext context) {
        if (!polymer$requireModification(context)) {
            return this;
        }

        return new class_10132(List.of());
    }

    @Override
    public boolean polymer$requireModification(PacketContext context) {
        for (var effect : this.effects) {
            if (!PolymerSyncedObject.canSyncRawToClient(class_7923.field_41174, effect.method_5579().comp_349(), context)) {
                return true;
            }
        }
        return false;
    }
}
