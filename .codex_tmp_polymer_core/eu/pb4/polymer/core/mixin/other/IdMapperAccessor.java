package eu.pb4.polymer.core.mixin.other;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;
import net.minecraft.class_2361;

@Mixin(class_2361.class)
public interface IdMapperAccessor {
    @Accessor
    List<?> getIdToT();
}
