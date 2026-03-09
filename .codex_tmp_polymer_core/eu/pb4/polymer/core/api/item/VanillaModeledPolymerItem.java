package eu.pb4.polymer.core.api.item;

import net.minecraft.class_1799;
import net.minecraft.class_2960;
import xyz.nucleoid.packettweaker.PacketContext;

public interface VanillaModeledPolymerItem extends PolymerItem {
    @Override
    default class_2960 getPolymerItemModel(class_1799 stack, PacketContext context) {
        return null;
    }
}
