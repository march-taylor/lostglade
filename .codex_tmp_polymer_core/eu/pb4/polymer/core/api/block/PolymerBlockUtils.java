package eu.pb4.polymer.core.api.block;

import eu.pb4.polymer.common.api.events.BooleanEvent;
import eu.pb4.polymer.common.api.events.SimpleEvent;
import eu.pb4.polymer.core.api.item.PolymerItem;
import eu.pb4.polymer.core.api.utils.PolymerSyncedObject;
import eu.pb4.polymer.core.impl.compat.polymc.PolyMcUtils;
import eu.pb4.polymer.core.impl.interfaces.BlockStateExtra;
import eu.pb4.polymer.core.impl.networking.PacketPatcher;
import eu.pb4.polymer.core.mixin.block.ClientboundBlockEntityDataPacketAccessor;
import org.jetbrains.annotations.Nullable;
import xyz.nucleoid.packettweaker.PacketContext;

import java.util.function.BiPredicate;
import java.util.function.Predicate;
import net.minecraft.class_1268;
import net.minecraft.class_1269;
import net.minecraft.class_1799;
import net.minecraft.class_2248;
import net.minecraft.class_2338;
import net.minecraft.class_2487;
import net.minecraft.class_2591;
import net.minecraft.class_2622;
import net.minecraft.class_2680;
import net.minecraft.class_3218;
import net.minecraft.class_3222;
import net.minecraft.class_3965;
import net.minecraft.class_4076;
import net.minecraft.class_7923;

public final class PolymerBlockUtils {
    public static final int NESTED_DEFAULT_DISTANCE = 32;
    public static final Predicate<class_2680> IS_POLYMER_BLOCK_STATE_PREDICATE = state -> PolymerSyncedObject.getSyncedObject(class_7923.field_41175, state.method_26204()) instanceof PolymerBlock;
    /**
     * This event allows you to force server side mining for any block/item
     */
    public static final BooleanEvent<MineEventListener> SERVER_SIDE_MINING_CHECK = new BooleanEvent<>();
    public static final SimpleEvent<BreakingProgressListener> BREAKING_PROGRESS_UPDATE = new SimpleEvent<>();
    public static final BooleanEvent<PolymerBlockInteractionListener> POLYMER_BLOCK_INTERACTION_CHECK = new BooleanEvent<>();
    public static final BooleanEvent<PolymerIgnoreSoundExceptionListener> POLYMER_IGNORE_SOUND_EXCEPTED_ENTITY = new BooleanEvent<>();
    /**
     * This event allows you to force syncing of light updates between server and clinet
     */
    public static final BooleanEvent<BiPredicate<class_3218, class_4076>> SEND_LIGHT_UPDATE_PACKET = new BooleanEvent<>();
    private static final class_2487 STATIC_COMPOUND = new class_2487();
    private PolymerBlockUtils() {
    }

    /**
     * Marks BlockEntity type as server-side only
     *
     * @param types BlockEntityTypes
     */
    public static void registerBlockEntity(class_2591<?>... types) {
        for (var type : types) {
            registerBlockEntity(type, (obj, ctx) -> null);
        }
    }

    public static void registerBlockEntity(class_2591<?> type, PolymerSyncedObject<class_2591<?>> syncedObject) {
        PolymerSyncedObject.setSyncedObject(class_7923.field_41181, type, syncedObject);
    }

    /**
     * Checks if BlockEntity is server-side only
     *
     * @param type BlockEntities type
     */
    public static boolean isPolymerBlockEntityType(class_2591<?> type) {
        return PolymerSyncedObject.getSyncedObject(class_7923.field_41181, type) != null;
    }

    /**
     * This method is used to check if BlockState should force sending of light updates to client
     *
     * @param blockState
     * @return
     */
    public static boolean forceLightUpdates(class_2680 blockState) {
        if (PolymerSyncedObject.getSyncedObject(class_7923.field_41175, blockState.method_26204()) instanceof PolymerBlock virtualBlock) {
            if (virtualBlock.forceLightUpdates(blockState)) {
                return true;
            }

            return ((BlockStateExtra) blockState).polymer$isPolymerLightSource();
        }
        return false;
    }

    /**
     * Gets BlockState used on client side
     *
     * @param state server side BlockState
     * @param context context
     * @return Client side BlockState
     */
    public static class_2680 getPolymerBlockState(class_2680 state, PacketContext context) {
        return BlockMapper.getFrom(context.getPlayer()).toClientSideState(state, context);
    }

    public static class_2248 getPolymerBlock(class_2248 block, PacketContext context) {
        return BlockMapper.getFrom(context.getPlayer()).toClientSideState(block.method_9564(), context).method_26204();
    }

    public static void registerOverlay(class_2248 block, PolymerBlock polymerBlock) {
        PolymerBlock.registerOverlay(block, polymerBlock);
    }

    /**
     * This method is minimal wrapper around {@link PolymerBlock#getPolymerBlockState(class_2680, PacketContext)} )} to make sure
     * It gets replaced if it represents other PolymerBlock
     *
     * @param block       PolymerBlock
     * @param blockState  Server side BlockState
     * @param maxDistance Maximum number of checks for nested virtual blocks
     * @param context Packet context
     * @return Client side BlockState
     */
    public static class_2680 getBlockStateSafely(PolymerBlock block, class_2680 blockState, int maxDistance, PacketContext context) {
        class_2680 out = block.getPolymerBlockState(blockState, context);

        int req = 0;
        while (PolymerSyncedObject.getSyncedObject(class_7923.field_41175, out.method_26204()) instanceof PolymerBlock newBlock && newBlock != block && req < maxDistance) {
            out = newBlock.getPolymerBlockState(out, context);
            req++;
        }
        return out;
    }

    public static class_2680 getBlockBreakBlockStateSafely(PolymerBlock block, class_2680 blockState, int maxDistance, PacketContext context) {
        class_2680 out = block.getPolymerBreakEventBlockState(blockState, context);

        int req = 0;
        while (PolymerSyncedObject.getSyncedObject(class_7923.field_41175, out.method_26204()) instanceof PolymerBlock newBlock && newBlock != block && req < maxDistance) {
            out = newBlock.getPolymerBreakEventBlockState(blockState, context);
            req++;
        }
        return out;
    }

    /**
     * This method is minimal wrapper around {@link PolymerBlock#getPolymerBlockState(class_2680, PacketContext)} )} to make sure
     * It gets replaced if it represents other PolymerBlock
     *
     * @param block       PolymerBlock
     * @param blockState  Server side BlockState
     * @param context      Possible target player
     * @return Client side BlockState
     */
    public static class_2680 getBlockStateSafely(PolymerBlock block, class_2680 blockState, PacketContext context) {
        return getBlockStateSafely(block, blockState, NESTED_DEFAULT_DISTANCE, context);
    }

    public static class_2622 createBlockEntityPacket(class_2338 pos, class_2591<?> type, @Nullable class_2487 nbtCompound) {
        return ClientboundBlockEntityDataPacketAccessor.createBlockEntityUpdateS2CPacket(pos.method_10062(), type, nbtCompound != null ? nbtCompound : STATIC_COMPOUND);
    }

    public static boolean shouldMineServerSide(class_3222 player, class_2338 pos, class_2680 state) {
        return (PolymerSyncedObject.getSyncedObject(class_7923.field_41175, state.method_26204()) instanceof PolymerBlock block && block.handleMiningOnServer(player.method_6047(), state, pos, player))
                || (PolymerSyncedObject.getSyncedObject(class_7923.field_41178, player.method_6047().method_7909()) instanceof PolymerItem item && item.handleMiningOnServer(player.method_6047(), state, pos, player))
                || PolymerBlockUtils.SERVER_SIDE_MINING_CHECK.invoke((x) -> x.onBlockMine(state, pos, player));
    }

    public static class_2680 getServerSideBlockState(class_2680 state, PacketContext context) {
        return PolyMcUtils.toVanilla(getPolymerBlockState(state, context), context.getPlayer());
    }

    public static class_2487 transformBlockEntityNbt(PacketContext context, class_2591<?> type, class_2487 original) {
        return PacketPatcher.transformBlockEntityNbt(context, type, original);
    }

    public static boolean isPolymerBlockInteraction(class_3222 player, class_1799 stack, class_1268 hand, class_2680 preInteractionState, class_3965 blockHitResult, class_3218 world, class_1269 actionResult) {
        var blockState = world.method_8320(blockHitResult.method_17777());
        if (PolymerSyncedObject.getSyncedObject(class_7923.field_41175, blockState.method_26204()) instanceof PolymerBlock polymerBlock && polymerBlock.isPolymerBlockInteraction(blockState, player, hand, stack, world, blockHitResult, actionResult)) {
            return true;
        } else if (!blockState.method_27852(preInteractionState.method_26204()) && PolymerSyncedObject.getSyncedObject(class_7923.field_41175, preInteractionState.method_26204()) instanceof PolymerBlock polymerBlock && polymerBlock.isPolymerBlockInteraction(preInteractionState, player, hand, stack, world, blockHitResult, actionResult)) {
            return true;
        } else if (PolymerSyncedObject.getSyncedObject(class_7923.field_41178, stack.method_7909()) instanceof PolymerItem polymerItem && polymerItem.isPolymerBlockInteraction(blockState, player, hand, stack, world, blockHitResult, actionResult)) {
            return true;
        }

        return POLYMER_BLOCK_INTERACTION_CHECK.invoke(x -> x.isPolymerBlockInteraction(blockState, player, hand, stack, world, blockHitResult, actionResult));
    }

    public static boolean isIgnoringPlaySoundExceptedEntity(class_3222 player, class_1799 stack, class_1268 hand, class_2680 state, class_3965 blockHitResult, class_3218 world) {
        if (PolymerSyncedObject.getSyncedObject(class_7923.field_41175, state.method_26204()) instanceof PolymerBlock polymerBlock && polymerBlock.isIgnoringBlockInteractionPlaySoundExceptedEntity(state, player, hand, stack, world, blockHitResult)) {
            return true;
        } else if (PolymerSyncedObject.getSyncedObject(class_7923.field_41178, stack.method_7909()) instanceof PolymerItem polymerItem && polymerItem.isIgnoringBlockInteractionPlaySoundExceptedEntity(state, player, hand, stack, world, blockHitResult)) {
            return true;
        }

        return POLYMER_IGNORE_SOUND_EXCEPTED_ENTITY.invoke(x -> x.isIgnoringBlockInteractionPlaySoundExceptedEntity(state, player, hand, stack, world, blockHitResult));
    }

    @FunctionalInterface
    public interface MineEventListener {
        boolean onBlockMine(class_2680 state, class_2338 pos, class_3222 player);
    }

    @FunctionalInterface
    public interface BreakingProgressListener {
        void onBreakingProgressUpdate(class_3222 player, class_2338 pos, class_2680 finalState, int i);
    }

    @FunctionalInterface
    public interface PolymerBlockInteractionListener {
        boolean isPolymerBlockInteraction(class_2680 state, class_3222 player, class_1268 hand, class_1799 stack, class_3218 world, class_3965 blockHitResult, class_1269 actionResult);
    }

    @FunctionalInterface
    public interface PolymerIgnoreSoundExceptionListener {
        boolean isIgnoringBlockInteractionPlaySoundExceptedEntity(class_2680 state, class_3222 player, class_1268 hand, class_1799 stack, class_3218 world, class_3965 blockHitResult);
    }
}
