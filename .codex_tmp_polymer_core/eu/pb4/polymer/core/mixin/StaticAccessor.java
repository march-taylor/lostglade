package eu.pb4.polymer.core.mixin;

import com.mojang.authlib.GameProfile;
import com.mojang.datafixers.util.Either;
import net.minecraft.class_8685;
import net.minecraft.class_9296;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(class_9296.class_11758.class)
public interface StaticAccessor {
    @Invoker("<init>")
    static class_9296.class_11758 createStatic(Either<GameProfile, class_9296.class_11757> profileOrData, class_8685.class_11892 override) {
        throw new UnsupportedOperationException();
    }
}
