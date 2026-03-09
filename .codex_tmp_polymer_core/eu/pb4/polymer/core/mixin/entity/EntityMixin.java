package eu.pb4.polymer.core.mixin.entity;

import eu.pb4.polymer.core.api.entity.PolymerEntity;
import eu.pb4.polymer.core.api.entity.PolymerEntityUtils;
import eu.pb4.polymer.core.impl.interfaces.PolymerEntityProvider;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Function;
import net.minecraft.class_1297;
import net.minecraft.class_1299;
import net.minecraft.class_1937;

@Mixin(class_1297.class)
public abstract class EntityMixin implements PolymerEntityProvider {
    @Shadow public abstract class_1299<?> getType();

    @Unique
    @Nullable
    private PolymerEntity polymerEntity;


    @Inject(method = "<init>", at = @At("TAIL"))
    private void updatePolymerEntity(class_1299 type, class_1937 world, CallbackInfo ci) {
        polymer$recreatePolymerEntity();
    }

    @Override
    public @Nullable PolymerEntity polymer$getPolymerEntity() {
        return this.polymerEntity;
    }

    @Override
    public void polymer$recreatePolymerEntity() {
        //noinspection unchecked
        var constructor = (Function<Object, PolymerEntity>) PolymerEntityUtils.getPolymerEntityConstructor(getType());
        this.polymerEntity = constructor != null ? constructor.apply(this) : null;
    }

    @Override
    public void polymer$setPolymerEntity(PolymerEntity polymerEntity) {
        this.polymerEntity = polymerEntity;
    }
}
