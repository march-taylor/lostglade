package eu.pb4.polymer.core.mixin.client.item;

import eu.pb4.polymer.common.impl.client.ClientUtils;
import eu.pb4.polymer.core.api.item.PolymerItemUtils;
import eu.pb4.polymer.core.impl.PolymerImplUtils;
import eu.pb4.polymer.core.impl.client.InternalClientItemGroup;
import eu.pb4.polymer.core.impl.client.interfaces.ClientCreativeModeTabExtension;
import eu.pb4.polymer.core.impl.networking.payloads.s2c.PolymerItemGroupContentAddS2CPayload;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.class_1761;
import net.minecraft.class_1799;
import net.minecraft.class_7706;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.*;

@SuppressWarnings("ConstantConditions")
@Environment(EnvType.CLIENT)
@Mixin(value = class_1761.class, priority = 1200)
public abstract class CreativeModeTabMixin implements ClientCreativeModeTabExtension {
    @Shadow private Collection<class_1799> displayItems;
    @Shadow private Set<class_1799> displayItemsSearchTab;

    @Shadow @Final private class_1761.class_7916 type;
    @Mutable
    @Shadow @Final private class_1761.class_7915 row;
    @Mutable
    @Shadow @Final private int column;


    @Unique private final List<class_1799> polymer$itemsGroup = new ArrayList<>();
    @Unique private final List<class_1799> polymer$itemsSearch = new ArrayList<>();

    @Unique private final List<PolymerItemGroupContentAddS2CPayload.Entry> polymer$entriesMain = new ArrayList<>();
    @Unique private final List<PolymerItemGroupContentAddS2CPayload.Entry> polymer$entriesSearch = new ArrayList<>();
    @Unique
    private int polymerCore$page;

    @ModifyArg(method = "buildContents", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/Registry;getResourceKey(Ljava/lang/Object;)Ljava/util/Optional;"))
    private Object polymerCore$bypassServerSide(Object entry) {
        return entry instanceof InternalClientItemGroup ? class_7706.method_47328() : entry;
    }

    @Inject(method = "buildContents", at = @At("HEAD"), cancellable = true)
    private void polymer$injectEntriesCustom(class_1761.class_8128 arg, CallbackInfo ci) {
        if (((Object) this) instanceof InternalClientItemGroup) {
            this.displayItems.clear();
            this.displayItemsSearchTab.clear();
            this.displayItems.addAll(this.polymer$itemsGroup);
            this.displayItemsSearchTab.addAll(this.polymer$itemsSearch);
            ci.cancel();
        }
    }

    @Inject(method = "buildContents", at = @At("TAIL"))
    private void polymer$injectEntriesVanilla(class_1761.class_8128 arg, CallbackInfo ci) {
        if (this.type == class_1761.class_7916.field_41052 && ClientUtils.isClientThread()) {
            this.displayItems.removeIf(PolymerImplUtils::removeFromItemGroup);
            this.displayItemsSearchTab.removeIf(PolymerImplUtils::removeFromItemGroup);

            if (!this.polymer$entriesMain.isEmpty()) {
                applyPolymerGroups(this.displayItems, this.polymer$entriesMain);
            }

            if (!this.polymer$entriesSearch.isEmpty()) {
                applyPolymerGroups(this.displayItemsSearchTab, this.polymer$entriesSearch);
            }
        }
    }

    @Unique
    private static void applyPolymerGroups(Collection<class_1799> stacks, List<PolymerItemGroupContentAddS2CPayload.Entry> entries) {
        if (stacks.isEmpty()) {
            for (var entry : entries) {
                stacks.addAll(entry.stacks());
            }
            return;
        }

        var set = new LinkedList<>(stacks);
        for (var entry : entries) {
            switch (entry.mode()) {
                case INSERT_BEGINNING -> set.addAll(0, entry.stacks());
                case INSERT_END -> set.addAll(entry.stacks());
                case RELATIVE -> {
                    boolean found = false;
                    for (int i = 0; i < set.size() - 1; i++) {
                        var stack = set.get(i);
                        if (PolymerItemUtils.getServerIdentifier(stack) == null && class_1799.method_31577(stack, entry.relative())) {
                            found = true;
                            set.addAll(i + 1, entry.stacks());
                            break;
                        }
                    }

                    if (!found) {
                        if (PolymerItemUtils.getServerIdentifier(set.getLast()) == null && class_1799.method_31577(set.getLast(), entry.relative())) {
                            set.addAll(entry.stacks());
                        } else {
                            for (int i = 0; i < set.size() - 1; i++) {
                                var stack = set.get(i);
                                if (PolymerItemUtils.getServerIdentifier(stack) == null && class_1799.method_7984(stack, entry.relative())) {
                                    found = true;
                                    set.addAll(i + 1, entry.stacks());
                                    break;
                                }
                            }
                            if (!found) {
                                set.addAll(entry.stacks());
                            }
                        }
                    }
                }
            }
        }

        stacks.clear();
        stacks.addAll(set);
    }

    @Override
    public void polymer$handleEntries(List<PolymerItemGroupContentAddS2CPayload.Entry> main, List<PolymerItemGroupContentAddS2CPayload.Entry> search) {
        this.polymer$entriesMain.addAll(main);
        this.polymer$entriesSearch.addAll(search);
        for (var entry : main) {
            this.polymer$itemsGroup.addAll(entry.stacks());
        }
        for (var entry : search) {
            this.polymer$itemsSearch.addAll(entry.stacks());
        }
    }

    @Override
    public void polymer$clearStacks() {
        this.polymer$itemsGroup.clear();
        this.polymer$itemsSearch.clear();
        this.polymer$entriesMain.clear();
        this.polymer$entriesSearch.clear();
    }

    @Override
    public Collection<class_1799> polymer$getStacksGroup() {
        return this.polymer$itemsGroup;
    }

    public Collection<class_1799> polymer$getStacksSearch() {
        return this.polymer$itemsSearch;
    }

    @Override
    public void polymerCore$setPos(class_1761.class_7915 row, int slot) {
        this.row = row;
        this.column = slot;
    }

    @Override
    public void polymerCore$setPage(int page) {
        this.polymerCore$page = page;
    }

    @Override
    public int polymerCore$getPage() {
        return this.polymerCore$page;
    }
}
