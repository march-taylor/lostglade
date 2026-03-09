package eu.pb4.polymer.core.mixin.client.compat;

import eu.pb4.polymer.core.api.client.ClientPolymerItem;
import eu.pb4.polymer.core.api.item.PolymerItemUtils;
import eu.pb4.polymer.core.impl.PolymerImplUtils;
import eu.pb4.polymer.core.impl.client.compat.CompatUtils;
import me.shedaniel.rei.api.common.entry.EntryStack;
import me.shedaniel.rei.api.common.entry.comparison.ComparisonContext;
import me.shedaniel.rei.api.common.entry.comparison.ItemComparatorRegistry;
import me.shedaniel.rei.plugin.client.entry.ItemEntryDefinition;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.class_1799;
import net.minecraft.class_2960;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Yeah I know, but I wanted quick solution without requiring any changes on your side

@Pseudo
@Environment(EnvType.CLIENT)
@Mixin(ItemEntryDefinition.class)
public abstract class rei_ItemEntryDefinitionMixin {
    @Shadow public abstract class_1799 copy(EntryStack<class_1799> entry, class_1799 value);

    @Inject(method = "equals(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;Lme/shedaniel/rei/api/common/entry/comparison/ComparisonContext;)Z", at = @At("HEAD"), cancellable = true, require = 0)
    private void polymer$areEqual(class_1799 o1, class_1799 o2, ComparisonContext context, CallbackInfoReturnable<Boolean> cir) {
        var lId = PolymerItemUtils.getServerIdentifier(o1);
        var rId = PolymerItemUtils.getServerIdentifier(o2);
        if ((lId != null || rId != null) && CompatUtils.areSamePolymerType(o1, o2)) {
            if (context == ComparisonContext.FUZZY) {
                cir.setReturnValue(true);
            } else if (context == ComparisonContext.EXACT) {
                cir.setReturnValue(CompatUtils.areEqualItems(o1, o2));
            }
        }
    }

    @Inject(method = "hash(Lme/shedaniel/rei/api/common/entry/EntryStack;Lnet/minecraft/world/item/ItemStack;Lme/shedaniel/rei/api/common/entry/comparison/ComparisonContext;)J", at = @At("HEAD"), cancellable = true, require = 0)
    private void polymer$hash(EntryStack<class_1799> entry, class_1799 value, ComparisonContext context, CallbackInfoReturnable<Long> cir) {
        var id = PolymerItemUtils.getServerIdentifier(value);
        if (id != null) {
            long code = 1;
            code = 31 * code + id.hashCode();
            code = 31 * code + Long.hashCode(ItemComparatorRegistry.getInstance().hashOf(context, value));

            cir.setReturnValue(code);
        }
    }

    @Inject(method = "wildcard(Lme/shedaniel/rei/api/common/entry/EntryStack;Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/item/ItemStack;", at = @At("HEAD"), cancellable = true, require = 0)
    private void polymer$wildcard(EntryStack<class_1799> entry, class_1799 value, CallbackInfoReturnable<class_1799> cir) {
        var id1 = PolymerItemUtils.getServerIdentifier(value);
        if (id1 != null) {
            var item = ClientPolymerItem.REGISTRY.get(id1);

            if (item != null) {
                cir.setReturnValue(item.visualStack().method_7972());
            } else {
                cir.setReturnValue(value.method_46651(1));
            }
        }
    }

    @Inject(method = "getIdentifier(Lme/shedaniel/rei/api/common/entry/EntryStack;Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/resources/Identifier;", at = @At("HEAD"), cancellable = true, require = 0)
    private void polymer$getIdentifier(EntryStack<class_1799> entry, class_1799 value, CallbackInfoReturnable<@Nullable class_2960> cir) {
        var id1 = PolymerItemUtils.getServerIdentifier(value);
        if (id1 != null) {
            cir.setReturnValue(id1);
        }
    }
}
