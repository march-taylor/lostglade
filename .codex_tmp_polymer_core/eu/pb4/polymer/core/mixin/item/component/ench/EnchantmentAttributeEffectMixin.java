package eu.pb4.polymer.core.mixin.item.component.ench;

import eu.pb4.polymer.core.api.entity.PolymerEntityUtils;
import eu.pb4.polymer.core.impl.PolymericObject;
import net.minecraft.class_1320;
import net.minecraft.class_6880;
import net.minecraft.class_9720;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(class_9720.class)
public class EnchantmentAttributeEffectMixin implements PolymericObject {
    @Shadow @Final private class_6880<class_1320> attribute;

    @Override
    public boolean polymer$isPolymeric() {
        return PolymerEntityUtils.isPolymerEntityAttribute(this.attribute);
    }
}
