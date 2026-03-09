package eu.pb4.polymer.core.mixin.client.rendering;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import eu.pb4.polymer.core.api.entity.PolymerEntityUtils;
import eu.pb4.polymer.core.impl.client.rendering.NullEntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.class_1299;
import net.minecraft.class_5617;
import net.minecraft.class_5619;
import net.minecraft.class_7923;
import net.minecraft.class_897;

@Mixin(class_5619.class)
public class EntityRenderersMixin {
    @ModifyReturnValue(method = "createEntityRenderers", at = @At("TAIL"))
    private static Map<class_1299<?>, class_897<?, ?>> polymer$replaceEntityRenderer(Map<class_1299<?>, class_897<?, ?>> original,
                                                                                          @Local(argsOnly = true) class_5617.class_5618 ctx) {
        var entityMap = new IdentityHashMap<class_1299<?>, class_897<?, ?>>();

        for (var ent : class_7923.field_41177) {
            if (PolymerEntityUtils.isPolymerEntityType(ent) && !original.containsKey(ent)) {
                if (entityMap.isEmpty()) {
                    entityMap.putAll(original);
                }
                entityMap.put(ent, new NullEntityRenderer(ctx));
            }
        }
        return entityMap.isEmpty() ? original : entityMap;
    }
}
