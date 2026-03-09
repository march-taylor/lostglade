package eu.pb4.polymer.core.impl.compat.polymc;

import eu.pb4.polymer.common.impl.CompatStatus;
import eu.pb4.polymer.core.api.block.PolymerBlockUtils;
import eu.pb4.polymer.core.api.item.PolymerItemUtils;
import eu.pb4.polymer.core.impl.PolymerImpl;
import io.github.theepicblock.polymc.api.item.ItemLocation;
import io.github.theepicblock.polymc.api.misc.PolyMapProvider;
import io.github.theepicblock.polymc.impl.Util;
import net.minecraft.class_1799;
import net.minecraft.class_2378;
import net.minecraft.class_2680;
import net.minecraft.class_3222;
import org.jetbrains.annotations.Nullable;

public class PolyMcUtils {

    public static class_2680 toVanilla(class_2680 state, @Nullable class_3222 player) {
        if (CompatStatus.POLYMC) {
            return Util.tryGetPolyMap(player).getClientState(state, player);
        }

        return state;
    }

    public static class_1799 toVanilla(class_1799 stack, @Nullable class_3222 player) {
        if (CompatStatus.POLYMC && !stack.method_7960()) {
            return Util.tryGetPolyMap(player).getClientItem(stack, player, ItemLocation.INVENTORY);
        }

        return stack;
    }

    public static boolean isServerSide(class_2378 reg, Object obj) {
        return !reg.method_10221(obj).method_12836().equals("minecraft");
    }

    public static void register() {
        if (CompatStatus.POLYMC && PolymerImpl.OVERRIDE_POLYMC_MINING) {
            var event = new PolymcInteractionReplacement();
            PolymerBlockUtils.SERVER_SIDE_MINING_CHECK.register(event);
            PolymerBlockUtils.POLYMER_BLOCK_INTERACTION_CHECK.register(event);
            PolymerItemUtils.POLYMER_ITEM_INTERACTION_CHECK.register(event);

            PolymerItemUtils.IS_SERVER_ITEM_EVENT.register(event);
        }
    }
}
