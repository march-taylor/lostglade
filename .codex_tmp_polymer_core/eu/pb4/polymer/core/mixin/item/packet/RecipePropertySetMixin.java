package eu.pb4.polymer.core.mixin.item.packet;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import eu.pb4.polymer.core.api.utils.PolymerSyncedObject;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import xyz.nucleoid.packettweaker.PacketContext;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.class_10290;
import net.minecraft.class_1792;
import net.minecraft.class_6880;
import net.minecraft.class_7923;

@Mixin(class_10290.class)
public class RecipePropertySetMixin {
    @ModifyReturnValue(method = "method_64703", at = @At("TAIL"))
    private static List<class_6880<class_1792>> removePolymerEntries(List<class_6880<class_1792>> original) {
        var x = new ArrayList<>(original);
        x.removeIf(a -> !PolymerSyncedObject.canSyncRawToClient(class_7923.field_41178, a.comp_349(), PacketContext.get()));
        return x;
    }
}
