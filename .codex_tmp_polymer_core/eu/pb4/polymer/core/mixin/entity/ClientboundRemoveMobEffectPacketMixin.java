package eu.pb4.polymer.core.mixin.entity;

import eu.pb4.polymer.core.impl.interfaces.StatusEffectPacketExtension;
import net.minecraft.class_1291;
import net.minecraft.class_2718;
import net.minecraft.class_6880;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(class_2718.class)
public class ClientboundRemoveMobEffectPacketMixin implements StatusEffectPacketExtension {


    @Shadow @Final private class_6880<class_1291> effect;

    @Override
    public class_1291 polymer$getStatusEffect() {
        return this.effect.comp_349();
    }
}
