package eu.pb4.polymer.core.api.item;

import net.minecraft.class_1268;
import net.minecraft.class_1269;
import net.minecraft.class_1747;
import net.minecraft.class_1792;
import net.minecraft.class_1799;
import net.minecraft.class_1802;
import net.minecraft.class_2248;
import net.minecraft.class_2680;
import net.minecraft.class_2960;
import net.minecraft.class_3218;
import net.minecraft.class_3222;
import net.minecraft.class_3965;
import org.jetbrains.annotations.Nullable;
import xyz.nucleoid.packettweaker.PacketContext;

/**
 * Basic implementation of PolymerItem for blocks
 */
public class PolymerBlockItem extends class_1747 implements PolymerItem {
    private final class_1792 polymerItem;
    private final boolean polymerUseModel;

    public PolymerBlockItem(class_2248 block, class_1793 settings) {
        this(block, settings, class_1802.field_47315, true);
    }

    public PolymerBlockItem(class_2248 block, class_1793 settings, class_1792 polymerItem) {
        this(block, settings, polymerItem, false);
    }

    public PolymerBlockItem(class_2248 block, class_1793 settings, class_1792 polymerItem, boolean useModel) {
        super(block, settings);
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

    @Override
    public boolean isPolymerBlockInteraction(class_2680 state, class_3222 player, class_1268 hand, class_1799 stack, class_3218 world, class_3965 blockHitResult, class_1269 actionResult) {
        return true;
    }

    @Override
    public boolean isIgnoringBlockInteractionPlaySoundExceptedEntity(class_2680 state, class_3222 player, class_1268 hand, class_1799 stack, class_3218 world, class_3965 blockHitResult) {
        return true;
    }
}
