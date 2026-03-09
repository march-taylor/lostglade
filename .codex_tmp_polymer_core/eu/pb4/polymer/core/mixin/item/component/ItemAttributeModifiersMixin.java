package eu.pb4.polymer.core.mixin.item.component;

import eu.pb4.polymer.core.api.entity.PolymerEntityUtils;
import eu.pb4.polymer.core.impl.TransformingComponent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import xyz.nucleoid.packettweaker.PacketContext;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.class_9285;

@Mixin(class_9285.class)
public class ItemAttributeModifiersMixin implements TransformingComponent {
    @Shadow @Final private List<class_9285.class_9287> modifiers;

    @Override
    public Object polymer$getTransformed(PacketContext context) {
        if (!polymer$requireModification(context)) {
            return this;
        }
        var list = new ArrayList<class_9285.class_9287>();
        for (var entry : this.modifiers) {
            if (!PolymerEntityUtils.isPolymerEntityAttribute(entry.comp_2395())) {
                list.add(entry);
            }
        }

        return new class_9285(list);
    }

    @Override
    public boolean polymer$requireModification(PacketContext context) {
        for (var x : this.modifiers) {
            if (PolymerEntityUtils.isPolymerEntityAttribute(x.comp_2395())) {
                return true;
            }
        }
        return false;
    }
}
