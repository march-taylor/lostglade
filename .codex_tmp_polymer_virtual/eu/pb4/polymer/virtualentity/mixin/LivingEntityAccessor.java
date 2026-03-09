package eu.pb4.polymer.virtualentity.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Map;
import net.minecraft.class_1304;
import net.minecraft.class_1309;
import net.minecraft.class_1799;

@Mixin(class_1309.class)
public interface LivingEntityAccessor {
    @Invoker
    Map<class_1304, class_1799> callCollectEquipmentChanges();
}
