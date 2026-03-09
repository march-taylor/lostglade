package eu.pb4.polymer.core.mixin.item.component;

import eu.pb4.polymer.core.api.utils.PolymerSyncedObject;
import eu.pb4.polymer.core.impl.TransformingComponent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import xyz.nucleoid.packettweaker.PacketContext;

import java.util.List;
import java.util.Optional;
import net.minecraft.class_1293;
import net.minecraft.class_1842;
import net.minecraft.class_1844;
import net.minecraft.class_6880;
import net.minecraft.class_7923;

@Mixin(class_1844.class)
public abstract class PotionContentsMixin implements TransformingComponent {

    @Shadow @Final private List<class_1293> customEffects;

    @Shadow public abstract int getColor();

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    @Shadow @Final private Optional<class_6880<class_1842>> potion;

    @Shadow @Final private Optional<String> customName;

    @Shadow public abstract Optional<class_6880<class_1842>> potion();

    @Override
    public Object polymer$getTransformed(PacketContext context) {
        if (!polymer$requireModification(context)) {
            return this;
        }

        return new class_1844(Optional.empty(), Optional.of(this.getColor()), List.of(),
                this.customName.or(() -> this.potion().map(class_6880::comp_349).map(class_1842::method_63990)));
    }

    @Override
    public boolean polymer$requireModification(PacketContext context) {
        if (this.potion.isPresent() && !PolymerSyncedObject.canSyncRawToClient(class_7923.field_41179, this.potion.get().comp_349(), context)) {
            return true;
        }

        for (class_1293 effect : this.customEffects) {
            if (!PolymerSyncedObject.canSyncRawToClient(class_7923.field_41174, effect.method_5579().comp_349(), context)) {
                return true;
            }
        }
        return false;
    }
}
