package eu.pb4.polymer.core.mixin.item.component;

import eu.pb4.polymer.core.api.other.PolymerConsumeEffect;
import eu.pb4.polymer.core.impl.TransformingComponent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import xyz.nucleoid.packettweaker.PacketContext;

import java.util.List;
import net.minecraft.class_10124;
import net.minecraft.class_10134;
import net.minecraft.class_1839;
import net.minecraft.class_3414;
import net.minecraft.class_6880;

@Mixin(class_10124.class)
public abstract class ConsumableMixin implements TransformingComponent {

    @Shadow @Final private float consumeSeconds;

    @Shadow @Final private class_6880<class_3414> sound;

    @Shadow @Final private boolean hasConsumeParticles;

    @Shadow @Final private List<class_10134> onConsumeEffects;

    @Shadow @Final private class_1839 animation;

    @Override
    public Object polymer$getTransformed(PacketContext context) {
        if (!polymer$requireModification(context)) {
            return this;
        }

        return new class_10124(this.consumeSeconds, this.animation, this.sound, this.hasConsumeParticles, List.of());
    }

    @Override
    public boolean polymer$requireModification(PacketContext context) {
        for (var effect : this.onConsumeEffects) {
            if (effect instanceof TransformingComponent t && t.polymer$requireModification(context) || !PolymerConsumeEffect.canSync(effect.method_62864(), effect, context)) {
                return true;
            }
        }
        return false;
    }
}
