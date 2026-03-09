package eu.pb4.polymer.core.mixin.client.entity;

import eu.pb4.polymer.core.impl.client.InternalClientRegistry;
import eu.pb4.polymer.core.impl.client.interfaces.ClientEntityExtension;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.class_1297;
import net.minecraft.class_2561;
import net.minecraft.class_2960;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(class_1297.class)
public abstract class EntityMixin implements ClientEntityExtension {
    @Shadow public @Nullable abstract class_2561 getCustomName();

    @Unique private class_2960 polymer$entityId = null;

    @Override
    public void polymer$setId(class_2960 id) {
        this.polymer$entityId = id;
    }

    @Override
    public class_2960 polymer$getId() {
        return this.polymer$entityId;
    }

    @Inject(method = "getName", at = @At("HEAD"), cancellable = true)
    private void polymer$replaceName(CallbackInfoReturnable<class_2561> cir) {
        if (this.polymer$entityId != null && this.getCustomName() == null) {
            var type = InternalClientRegistry.ENTITY_TYPES.get(this.polymer$entityId);

            if (type != null) {
                cir.setReturnValue(type.name());
            }
        }
    }
}
