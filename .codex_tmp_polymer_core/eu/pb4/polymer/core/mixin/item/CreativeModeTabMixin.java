package eu.pb4.polymer.core.mixin.item;

import eu.pb4.polymer.core.api.item.PolymerItemGroupUtils;
import eu.pb4.polymer.core.api.utils.PolymerObject;
import eu.pb4.polymer.core.impl.PolymerImplUtils;
import eu.pb4.polymer.core.impl.interfaces.CreativeModeTabExtra;
import eu.pb4.polymer.core.impl.other.ItemGroupEntriesImpl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;
import java.util.LinkedList;
import java.util.Set;
import net.minecraft.class_1761;
import net.minecraft.class_1799;
import net.minecraft.class_2960;
import net.minecraft.class_7225;
import net.minecraft.class_7699;
import net.minecraft.class_7706;

@Mixin(value = class_1761.class, priority = 800)
public abstract class CreativeModeTabMixin implements CreativeModeTabExtra {
    @Shadow @Final private class_1761.class_7914 displayItemsGenerator;

    @Shadow private Collection<class_1799> displayItems;

    @Shadow private Set<class_1799> displayItemsSearchTab;

    @Override
    public PolymerItemGroupUtils.Contents polymer$getContentsWith(class_2960 id, class_7699 enabledFeatures, boolean operatorEnabled, class_7225.class_7874 lookup) {
        var collector = new ItemGroupEntriesImpl((class_1761) (Object) this, enabledFeatures);
        var context = new class_1761.class_8128(enabledFeatures, operatorEnabled, lookup);
        this.displayItemsGenerator.accept(context, collector);
        var parent = new LinkedList<>(collector.parentTabStacks);
        var search = new LinkedList<>(collector.searchTabStacks);
        PolymerImplUtils.callItemGroupEvents(id, (class_1761) (Object) this, parent, search, context);
        parent.removeIf(class_1799::method_7960);
        search.removeIf(class_1799::method_7960);
        return new PolymerItemGroupUtils.Contents(parent, search);
    }

    @ModifyArg(method = "buildContents", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/Registry;getResourceKey(Ljava/lang/Object;)Ljava/util/Optional;"))
    private Object polymerCore$bypassServerSide(Object entry) {
        return PolymerItemGroupUtils.isPolymerItemGroup((class_1761) entry) ? class_7706.method_47328() : entry;
    }

    @Inject(method = "buildContents", at = @At(value = "TAIL"), cancellable = true)
    private void polymerCore$bypassFabricApiBS(class_1761.class_8128 displayContext, CallbackInfo ci) {
        if (PolymerItemGroupUtils.isPolymerItemGroup((class_1761) (Object) this) || this instanceof PolymerObject) {
            var parent = new LinkedList<>(this.displayItems);
            var search = new LinkedList<>(this.displayItemsSearchTab);
            PolymerImplUtils.callItemGroupEvents(PolymerItemGroupUtils.getId((class_1761) (Object) this), (class_1761) (Object) this, parent, search, displayContext);
            this.displayItems.clear();
            this.displayItems.addAll(parent);
            this.displayItemsSearchTab.clear();
            this.displayItemsSearchTab.addAll(search);
            ci.cancel();
        }
    }
}
