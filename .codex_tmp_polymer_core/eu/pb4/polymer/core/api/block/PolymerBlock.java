package eu.pb4.polymer.core.api.block;

import eu.pb4.polymer.core.api.utils.PolymerSyncedObject;
import eu.pb4.polymer.rsm.api.RegistrySyncUtils;
import net.minecraft.class_1268;
import net.minecraft.class_1269;
import net.minecraft.class_1799;
import net.minecraft.class_1922;
import net.minecraft.class_2248;
import net.minecraft.class_2338;
import net.minecraft.class_2680;
import net.minecraft.class_3218;
import net.minecraft.class_3222;
import net.minecraft.class_3965;
import net.minecraft.class_7923;
import xyz.nucleoid.packettweaker.PacketContext;

/**
 * Interface used for creation of server side blocks
 */
public interface PolymerBlock extends PolymerSyncedObject<class_2248> {
    /**
     * Main method used for replacing BlockStates for players
     * Keep in mind you should ideally use blocks with the same hitbox as generic/non-player ones!
     *
     * @param state Server side BlocksState
     * @param context PacketContext this method is called with, might be empty!
     * @return Client side BlockState
     */
    class_2680 getPolymerBlockState(class_2680 state, PacketContext context);

    /**
     * This method is called when block gets send to player
     * Allows to add client-only BlockEntities (for signs, heads, etc)
     *
     * @param blockState Real BlockState of block
     * @param pos Position of block. Keep in mind it's mutable,
 *            so make sure to use {@link class_2338.class_2339#method_10062()}
 *            in case of using in packets, as it's reused for other positions!
     * @param contexts Context packet is sent to. Should always contain a player
     */
    default void onPolymerBlockSend(class_2680 blockState, class_2338.class_2339 pos, PacketContext.NotNullWithPlayer contexts) { }

    /**
     * You can override this method in case of issues with light updates of this block. In most cases it's not needed.
     * @param blockState
     */
    default boolean forceLightUpdates(class_2680 blockState) { return false; }

    /**
     * Overrides breaking particle used by the block
     * @param state
     * @param context
     * @return
     */
    default class_2680 getPolymerBreakEventBlockState(class_2680 state, PacketContext context) {
        return state;
    }

    @Override
    default class_2248 getPolymerReplacement(class_2248 block, PacketContext context) {
        return PolymerBlockUtils.getPolymerBlock(block, context);
    }

    default boolean handleMiningOnServer(class_1799 tool, class_2680 state, class_2338 pos, class_3222 player) {
        return true;
    }

    default boolean isPolymerBlockInteraction(class_2680 state, class_3222 player, class_1268 hand, class_1799 stack, class_3218 world, class_3965 blockHitResult, class_1269 actionResult) {
        return true;
    }

    default boolean isIgnoringBlockInteractionPlaySoundExceptedEntity(class_2680 state, class_3222 player, class_1268 hand, class_1799 stack, class_3218 world, class_3965 blockHitResult) {
        return false;
    }

    default boolean playSoundToSelf(class_2680 state, class_3222 player, class_3218 world, class_2338 pos) {
        return false;
    }

    default boolean overridePlayerCollisionsWithPolymer(class_1922 level, class_2338 pos, class_2680 blockState, class_3222 player) {
        return true;
    }

    static void registerOverlay(class_2248 block, PolymerBlock polymerBlock) {
        PolymerSyncedObject.setSyncedObject(class_7923.field_41175, block, polymerBlock);
        RegistrySyncUtils.setServerEntry(class_7923.field_41175, block);
    }
}
