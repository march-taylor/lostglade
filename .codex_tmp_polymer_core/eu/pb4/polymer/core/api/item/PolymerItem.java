package eu.pb4.polymer.core.api.item;

import eu.pb4.polymer.core.api.utils.PolymerSyncedObject;
import eu.pb4.polymer.rsm.api.RegistrySyncUtils;
import org.jetbrains.annotations.Nullable;
import xyz.nucleoid.packettweaker.PacketContext;

import java.util.List;
import java.util.function.Consumer;
import net.minecraft.class_10712;
import net.minecraft.class_1268;
import net.minecraft.class_1269;
import net.minecraft.class_1297;
import net.minecraft.class_1792;
import net.minecraft.class_1799;
import net.minecraft.class_1836;
import net.minecraft.class_2338;
import net.minecraft.class_2561;
import net.minecraft.class_2680;
import net.minecraft.class_2960;
import net.minecraft.class_3218;
import net.minecraft.class_3222;
import net.minecraft.class_3965;
import net.minecraft.class_7923;
import net.minecraft.class_9334;

/**
 * Interface used for conversion of server items to their client side counterparts
 */
public interface PolymerItem extends PolymerSyncedObject<class_1792> {
    /**
     * Returns main/default item used on client for specific player
     *
     * @param itemStack ItemStack of virtual item
     * @param context    Context for which it's send
     * @return Vanilla (or other) Item instance
     */
    class_1792 getPolymerItem(class_1799 itemStack, PacketContext context);

    /**
     * Method used for creation of client-side ItemStack
     *
     * @param itemStack Server-side ItemStack
     * @param context    Player for which it's send
     * @return Client-side ItemStack
     */
    default class_1799 getPolymerItemStack(class_1799 itemStack, class_1836 tooltipType, PacketContext context) {
        return PolymerItemUtils.createItemStack(itemStack, tooltipType, context);
    }

    /**
     * Method used for selecting model to use. Invoked within PolymerItemUtils#createItemStack / default stack creation
     * before polymer-specific modifications
     *
     * @param stack Server-side ItemStack, used as reference
     * @param context    Player for which it's send
     * @return Identifier targetting item model or null to fallback to base item one
     */
    @Nullable
    default class_2960 getPolymerItemModel(class_1799 stack, PacketContext context) {
        return stack.method_58694(class_9334.field_54199);
    }

    /**
     * Method used for creation of client-side ItemStack.
     * Invoked within PolymerItemUtils#createItemStack / default stack creation before polymer-specific modifications.
     * For modifying after polymer, you should override {@link PolymerItem#getPolymerItemStack(class_1799, class_1836, PacketContext)}.
     *
     * @param out Client-side ItemStack, sent to the player (and one that should be modified)
     * @param stack Server-side ItemStack, used as reference
     * @param context    Player for which it's send
     */
    default void modifyBasePolymerItemStack(class_1799 out, class_1799 stack, PacketContext context) {
    }


    /**
     * This method allows to modify tooltip text. Invoked within PolymerItemUtils#createItemStack / default stack creation
     * If you just want to add your own one, use {@link class_1792#method_67187(class_1799, class_1792.class_9635, class_10712, Consumer, class_1836)}
     * or Fabric API provided events.
     *
     * @param tooltip Current tooltip text
     * @param stack   Server-side ItemStack
     * @param context  Target player
     */
    default void modifyClientTooltip(List<class_2561> tooltip, class_1799 stack, PacketContext context) {
    }

    @Override
    default class_1792 getPolymerReplacement(class_1792 item, PacketContext context) {
        return this.getPolymerItem(item.method_7854(), context);
    }

    /**
     * Makes the block mining logic run on the server instead of the client. Useful if the tool
     * changes how things are mined outside of vanilla {@link class_9334#field_50077} component.
     *
     * @param tool tool used
     * @param targetBlock targetted block
     * @param pos block's position
     * @param player user
     * @return true if logic should be handled server side, false otherwise
     */
    default boolean handleMiningOnServer(class_1799 tool, class_2680 targetBlock, class_2338 pos, class_3222 player) {
        return false;
    }

    /**
     * Makes it so real item count is stored in custom data, instead of client stack
     */
    default boolean shouldStorePolymerItemStackCount() {
        return false;
    }

    /**
     * Allows to run additional logic, making interactions work correctly server side,
     * emulating or preventing otherwise client dictated behaviour.
     *
     * @param state interacted block state
     * @param player the user
     * @param hand hand used for interaction
     * @param stack item stack in hand
     * @param world used world
     * @param blockHitResult hit result used for the interaction
     * @param actionResult the action result caused by such interaction.
     * @return whatever the interaction should be handled as polymer / server side one.
     */
    default boolean isPolymerBlockInteraction(class_2680 state, class_3222 player, class_1268 hand, class_1799 stack, class_3218 world, class_3965 blockHitResult, class_1269 actionResult) {
        return false;
    }

    /**
     * Allows to run additional logic, making interactions work correctly server side,
     * emulating or preventing otherwise client dictated behaviour.
     *
     * @param player the user
     * @param hand hand used for interaction
     * @param stack item stack in hand
     * @param world used world
     * @param entity interacted entity
     * @param actionResult the action result caused by such interaction.
     * @return whatever the interaction should be handled as polymer / server side one.
     */
    default boolean isPolymerEntityInteraction(class_3222 player, class_1268 hand, class_1799 stack, class_3218 world, class_1297 entity, class_1269 actionResult) {
        return false;
    }

    /**
     * Allows to run additional logic, making interactions work correctly server side,
     * emulating or preventing otherwise client dictated behaviour.
     *
     * @param player the user
     * @param hand hand used for interaction
     * @param stack item stack in hand
     * @param world used world
     * @param actionResult the action result caused by such interaction.
     * @return whatever the interaction should be handled as polymer / server side one.
     */
    default boolean isPolymerItemInteraction(class_3222 player, class_1268 hand, class_1799 stack, class_3218 world, class_1269 actionResult) {
        return true;
    }

    /**
     * Changes sound logic within the block interaction code to always play sounds to the client.
     *
     * @param state interacted block state
     * @param player the user
     * @param hand hand used for interaction
     * @param stack item stack used for block interaction
     * @param world used world
     * @param blockHitResult hit result used for block interaction
     * @return whatever the client only sounds should be played on server.
     */
    default boolean isIgnoringBlockInteractionPlaySoundExceptedEntity(class_2680 state, class_3222 player, class_1268 hand, class_1799 stack, class_3218 world, class_3965 blockHitResult) {
        return false;
    }

    /**
     * Changes sound logic within the item use interaction code to always play sounds to the client.
     *
     * @param player the user
     * @param hand hand used for interaction
     * @param stack item stack used for block interaction
     * @param world used world
     * @return whatever the client only sounds should be played on server.
     */
    default boolean isIgnoringItemInteractionPlaySoundExceptedEntity(class_3222 player, class_1268 hand, class_1799 stack, class_3218 world) {
        return false;
    }


    /**
     * Makes the target item use polymer logic for conversion, functionally the same as implementing
     * PolymerItem interface on custom Item class.
     *
     * @param item the target item
     * @param polymerItem instance of PolymerItem
     */
    static void registerOverlay(class_1792 item, PolymerItem polymerItem) {
        PolymerSyncedObject.setSyncedObject(class_7923.field_41178, item, polymerItem);
        RegistrySyncUtils.setServerEntry(class_7923.field_41178, item);
    }
}