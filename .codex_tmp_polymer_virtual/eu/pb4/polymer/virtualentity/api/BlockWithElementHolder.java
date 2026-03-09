package eu.pb4.polymer.virtualentity.api;

import eu.pb4.polymer.virtualentity.impl.BlockExt;
import eu.pb4.polymer.virtualentity.impl.VirtualEntityMod;
import net.minecraft.class_2248;
import net.minecraft.class_2338;
import net.minecraft.class_243;
import net.minecraft.class_2680;
import net.minecraft.class_3218;
import org.jetbrains.annotations.Nullable;

/**
 * Allows for automatic creation of element holders bound to blocks.
 * Can be used by either implementing this interface on top of custom Block class,
 * or by calling BlockWithElementHolder#registerOverlay to register it as overlay.
 *
 * Block can have only single controlling BlockWithElementHolder
 */
public interface BlockWithElementHolder {
    @Nullable
    default ElementHolder createElementHolder(class_3218 world, class_2338 pos, class_2680 initialBlockState) {
        return null;
    }

    default class_243 getElementHolderOffset(class_3218 world, class_2338 pos, class_2680 initialBlockState) {
        return class_243.field_1353;
    }

    default boolean tickElementHolder(class_3218 world, class_2338 pos, class_2680 initialBlockState) {
        return false;
    }

    @Nullable
    default ElementHolder createMovingElementHolder(class_3218 world, class_2338 blockPos, class_2680 blockState, @Nullable ElementHolder oldStaticElementHolder) {
        return oldStaticElementHolder != null ? oldStaticElementHolder : createElementHolder(world, blockPos, blockState);
    }

    @Nullable
    default ElementHolder createStaticElementHolder(class_3218 world, class_2338 blockPos, class_2680 blockState, @Nullable ElementHolder oldMovingElementHolder) {
        return oldMovingElementHolder != null ? oldMovingElementHolder : createElementHolder(world, blockPos, blockState);
    }

    @Nullable
    static BlockWithElementHolder get(class_2680 state) {
        return ((BlockExt) state.method_26204()).polymerVE$getElementHolderCreator();
    }

    static boolean registerOverlay(class_2248 block, BlockWithElementHolder holder) {
        return ((BlockExt) block).polymerVE$setElementHolderCreator(holder);
    }
}
