package eu.pb4.polymer.core.mixin.client.syncreg;

import eu.pb4.polymer.core.impl.client.InternalClientRegistry;
import eu.pb4.polymer.core.impl.interfaces.IndexedNetwork;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;
import java.util.function.IntFunction;
import net.minecraft.class_2370;
import net.minecraft.class_2378;
import net.minecraft.class_6880;

@Mixin(class_2370.class)
public abstract class MappedRegistryMixin<T> implements IndexedNetwork<T>, class_2378<T> {
    @Unique
    private IntFunction<T> polymer$decoder;
    @Unique
    private boolean hasDecoder;

    @Inject(method = "byId(I)Ljava/lang/Object;", at = @At("HEAD"), cancellable = true)
    private void redirectGets(int i, CallbackInfoReturnable<T> cir) {
        if (this.hasDecoder && InternalClientRegistry.enabled) {
            var x = this.polymer$decoder.apply(i);

            if (x != null) {
                cir.setReturnValue(x);
            }
        }
    }

    @Inject(method = "get(I)Ljava/util/Optional;", at = @At("HEAD"), cancellable = true)
    private void redirectGets2(int i, CallbackInfoReturnable<Optional<class_6880<T>>> cir) {
        if (this.hasDecoder && InternalClientRegistry.enabled) {
            var x = this.polymer$decoder.apply(i);

            if (x != null) {
                cir.setReturnValue(Optional.of(this.method_47983(x)));
            }
        }
    }

    @Override
    public void polymer$setDecoder(IntFunction<T> decoder) {
        this.polymer$decoder = decoder;
        this.hasDecoder = true;
    }
}
