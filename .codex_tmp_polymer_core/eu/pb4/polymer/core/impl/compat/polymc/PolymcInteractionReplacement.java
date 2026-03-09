package eu.pb4.polymer.core.impl.compat.polymc;

import eu.pb4.polymer.core.api.block.PolymerBlockUtils;
import eu.pb4.polymer.core.api.item.PolymerItemUtils;
import io.github.theepicblock.polymc.api.misc.PolyMapProvider;
import io.github.theepicblock.polymc.impl.Util;
import net.minecraft.class_1268;
import net.minecraft.class_1269;
import net.minecraft.class_1799;
import net.minecraft.class_2338;
import net.minecraft.class_2680;
import net.minecraft.class_3218;
import net.minecraft.class_3222;
import net.minecraft.class_3965;
import xyz.nucleoid.packettweaker.PacketContext;

public class PolymcInteractionReplacement implements PolymerBlockUtils.MineEventListener, PolymerBlockUtils.PolymerBlockInteractionListener, PolymerItemUtils.PolymerItemInteractionListener, PolymerItemUtils.ServerItemPredicate {
    @Override
    public boolean onBlockMine(class_2680 state, class_2338 pos, class_3222 player) {
        return !player.method_68878() && (isPolyMcBlock(state, player) || isPolyMcItem(player.method_6047(), player));
    }

    @Override
    public boolean isPolymerBlockInteraction(class_2680 state, class_3222 player, class_1268 hand, class_1799 stack, class_3218 world, class_3965 blockHitResult, class_1269 actionResult) {
        return isPolyMcBlock(state, player) || isPolyMcItem(stack, player);
    }

    @Override
    public boolean isPolymerItemInteraction(class_3222 player, class_1268 hand, class_1799 stack, class_3218 world, class_1269 actionResult) {
        return isPolyMcItem(stack, player);
    }

    private boolean isPolyMcItem(class_1799 itemStack, class_3222 player) {
        if (!Util.isPolyMapVanillaLike(player) ) {
            return false;
        }

        var polyMap = PolyMapProvider.getPolyMap(player);
        var tool = polyMap.getItemPoly(itemStack.method_7909());
        return (tool != null && !(tool instanceof PassthroughPoly));
    }

    private boolean isPolyMcBlock(class_2680 state, class_3222 player) {
        if (!Util.isPolyMapVanillaLike(player)) {
            return false;
        }

        var polyMap = PolyMapProvider.getPolyMap(player);
        var block = polyMap.getBlockPoly(state.method_26204());
        return (block != null && !(block instanceof PassthroughPoly));
    }

    @Override
    public boolean isServerItem(class_1799 itemStack, PacketContext context) {
        if (!Util.isPolyMapVanillaLike(context.getPlayer() ) ) {
            return false;
        }

        var polyMap = Util.tryGetPolyMap(context.getClientConnection());
        var tool = polyMap.getItemPoly(itemStack.method_7909());
        return (tool != null && !(tool instanceof PassthroughPoly));
    }
}
