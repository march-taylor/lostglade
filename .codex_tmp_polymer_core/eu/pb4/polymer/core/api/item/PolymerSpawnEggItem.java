package eu.pb4.polymer.core.api.item;

import net.minecraft.class_1792;
import net.minecraft.class_1799;
import net.minecraft.class_1826;
import net.minecraft.class_2960;
import org.jetbrains.annotations.Nullable;
import xyz.nucleoid.packettweaker.PacketContext;

public class PolymerSpawnEggItem extends class_1826 implements PolymerItem {

    private final class_1792 polymerItem;
    private final boolean polymerUseModel;

    public PolymerSpawnEggItem(class_1792 polymerItem, class_1793 settings) {
        this(polymerItem, false, settings);
    }
    public PolymerSpawnEggItem(class_1792 polymerItem, boolean useModel, class_1793 settings) {
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
