package eu.pb4.polymer.core.api.item;

import eu.pb4.polymer.core.api.block.PolymerHeadBlock;
import eu.pb4.polymer.core.api.utils.PolymerUtils;
import net.minecraft.class_1747;
import net.minecraft.class_1792;
import net.minecraft.class_1799;
import net.minecraft.class_1802;
import net.minecraft.class_1836;
import net.minecraft.class_2248;
import net.minecraft.class_2338;
import net.minecraft.class_2960;
import net.minecraft.class_9334;
import org.jetbrains.annotations.Nullable;
import xyz.nucleoid.packettweaker.PacketContext;

/**
 * Basic implementation of PolymerItem for blocks implementing PolymerHeadBlock
 */
public class PolymerHeadBlockItem extends class_1747 implements PolymerItem {
    private final PolymerHeadBlock polymerBlock;

    public <T extends class_2248 & PolymerHeadBlock> PolymerHeadBlockItem(T block, class_1793 settings) {
        super(block, settings);
        this.polymerBlock = block;
    }

    @Override
    public class_1792 getPolymerItem(class_1799 itemStack, PacketContext context) {
        return class_1802.field_8575;
    }

    @Override
    public class_2960 getPolymerItemModel(class_1799 stack, PacketContext context) {
        return null;
    }

    @Override
    public void modifyBasePolymerItemStack(class_1799 out, class_1799 stack, PacketContext context) {
        out.method_57379(class_9334.field_49617, PolymerUtils.createProfileComponent(
                this.polymerBlock.getPolymerSkinValue(this.method_7711().method_9564(), class_2338.field_10980, context),
                this.polymerBlock.getPolymerSkinSignature(this.method_7711().method_9564(), class_2338.field_10980, context)
        ));
    }

    public class_1799 getPolymerItemStack(class_1799 itemStack, class_1836 tooltipType, PacketContext context) {
        return PolymerItem.super.getPolymerItemStack(itemStack, tooltipType, context);
    }
}
