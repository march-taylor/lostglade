package eu.pb4.polymer.core.mixin.other;

import eu.pb4.polymer.core.api.utils.PolymerObject;
import eu.pb4.polymer.core.api.utils.PolymerSyncedObject;
import eu.pb4.polymer.core.impl.ImplPolymerRegistryEvent;
import eu.pb4.polymer.core.impl.PolymerImplUtils;
import eu.pb4.polymer.core.impl.interfaces.RegistryExtension;
import eu.pb4.polymer.rsm.api.RegistrySyncUtils;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.class_2370;
import net.minecraft.class_2378;
import net.minecraft.class_5321;
import net.minecraft.class_6862;
import net.minecraft.class_6880;
import net.minecraft.class_6885;
import net.minecraft.class_9248;

@Mixin(class_2370.class)
public abstract class MappedRegistryMixin<T> implements RegistryExtension<T>, class_2378<T> {
    @Shadow @Final private Map<class_6862<T>, class_6885.class_6888<T>> frozenTags;
    @Nullable
    @Unique
    private List<T> polymer$objects = null;
    @Unique
    private final IdentityHashMap<T, PolymerSyncedObject<T>> overlays = new IdentityHashMap<>();

    @Inject(method = "register(Lnet/minecraft/resources/ResourceKey;Ljava/lang/Object;Lnet/minecraft/core/RegistrationInfo;)Lnet/minecraft/core/Holder$Reference;", at = @At("TAIL"))
    private <V extends T> void polymer$storeStatus(class_5321<T> key, T value, class_9248 info, CallbackInfoReturnable<class_6880.class_6883<T>> cir) {
        this.polymer$objects = null;
        if (PolymerObject.is(value)) {
            RegistrySyncUtils.setServerEntry(this, value);
        }

        ImplPolymerRegistryEvent.invokeRegistered(this, value);
    }

    @Override
    public List<T> polymer$getEntries() {
        if (this.polymer$objects == null) {
            this.polymer$objects = new ArrayList<>();
            for (var obj : this) {
                if (PolymerImplUtils.isServerSideSyncableEntry(this, obj)) {
                    this.polymer$objects.add(obj);
                }
            }
        }

        return this.polymer$objects;
    }

    @Override
    public Map<class_6862<T>, class_6885.class_6888<T>> polymer$getTagsInternal() {
        return this.frozenTags;
    }

    @Override
    public void polymer$setOverlay(T value, PolymerSyncedObject<T> syncedObject) {
        this.overlays.put(value, syncedObject);
    }

    @Override
    public PolymerSyncedObject<T> polymer$getOverlay(T value) {
        return this.overlays.get(value);
    }
}
