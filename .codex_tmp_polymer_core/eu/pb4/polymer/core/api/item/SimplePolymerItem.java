package eu.pb4.polymer.core.api.item;

import net.minecraft.class_1792;
import net.minecraft.class_1799;
import net.minecraft.class_1802;
import net.minecraft.class_2960;
import org.jetbrains.annotations.Nullable;
import xyz.nucleoid.packettweaker.PacketContext;

/**
 * Basic implementation of PolymerItem
 */
public class SimplePolymerItem extends class_1792 implements PolymerItem {
    private final class_1792 polymerItem;
    private final boolean polymerUseModel;

    public SimplePolymerItem(class_1793 settings) {
        this(settings, class_1802.field_47315, true);
    }

    public SimplePolymerItem(class_1793 settings, class_1792 polymerItem) {
        this(settings, polymerItem, false);
    }

    public SimplePolymerItem(class_1793 settings, class_1792 polymerItem, boolean useModel) {
        super(settings);
        this.polymerItem = polymerItem;
        this.polymerUseModel = useModel;
    }

    @Override
    public class_1792 getPolymerItem(class_1799 itemStack, PacketContext context) {
        return this.polymerItem;
    }

    @Override
    public @Nullable class_2960 getPolymerItemModel(class_1799 stack, PacketContext context) {
        return this.polymerUseModel ? PolymerItem.super.getPolymerItemModel(stack, context) : null;
    }
}
