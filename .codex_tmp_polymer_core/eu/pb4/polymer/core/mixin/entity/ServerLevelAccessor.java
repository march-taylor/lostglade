package eu.pb4.polymer.core.mixin.entity;

import net.minecraft.class_1297;
import net.minecraft.class_3218;
import net.minecraft.class_5579;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(class_3218.class)
public interface ServerLevelAccessor {
    @Accessor("entityManager")
    class_5579<class_1297> polymer_getEntityManager();
}
