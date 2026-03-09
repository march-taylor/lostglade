package eu.pb4.polymer.virtualentity.api.attachment;

import eu.pb4.polymer.virtualentity.api.BlockWithElementHolder;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.impl.HolderAttachmentHolder;
import net.minecraft.class_1937;
import net.minecraft.class_2338;
import net.minecraft.class_243;
import net.minecraft.class_2680;
import net.minecraft.class_2818;
import net.minecraft.class_3218;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

public final class BlockBoundAttachment extends ChunkAttachment implements BlockAwareAttachment {
    public static final UpdateType BLOCK_STATE_UPDATE = BlockAwareAttachment.BLOCK_STATE_UPDATE;

    private final class_2338 blockPos;
    private class_2680 blockState;

    @ApiStatus.Internal
    public BlockBoundAttachment(ElementHolder holder, class_2818 chunk, class_2680 state, class_2338 blockPos, class_243 position, boolean autoTick) {
        super(holder, chunk, position, autoTick);
        this.blockPos = blockPos;
        this.blockState = state;
        this.attach();
    }

    @ApiStatus.Experimental
    @Nullable
    public static BlockBoundAttachment of(ElementHolder holder, class_3218 serverWorld, class_2338 blockPos, class_2680 state) {
        return of(holder, serverWorld, serverWorld.method_8500(blockPos), blockPos, state);
    }
    @ApiStatus.Experimental
    @Nullable
    public static BlockBoundAttachment of(ElementHolder holder, class_3218 serverWorld, class_2818 worldChunk, class_2338 blockPos, class_2680 state) {
        var blockWithElementHolder = BlockWithElementHolder.get(state);
        if (blockWithElementHolder != null) {
            return new BlockBoundAttachment(holder, worldChunk, state, blockPos,
                    class_243.method_24953(blockPos).method_1019(blockWithElementHolder.getElementHolderOffset(serverWorld, blockPos, state)),
                    blockWithElementHolder.tickElementHolder(serverWorld, blockPos, state)
            );
        }
        return null;
    }

    @ApiStatus.Experimental
    @Nullable
    public static BlockBoundAttachment fromMoving(ElementHolder movingHolder, class_3218 world, class_2338 pos, class_2680 state) {
        var withElementHolder = BlockWithElementHolder.get(state);
        if (withElementHolder != null) {
            var x = withElementHolder.createStaticElementHolder(world, pos, state, movingHolder);
            if (x != movingHolder) {
                movingHolder.destroy();
            } else if (movingHolder.getAttachment() != null) {
                var y = movingHolder.getAttachment();
                movingHolder.setAttachment(null);
                y.destroy();
            }

            return of(x, world, pos, state);
        } else if (movingHolder.getAttachment() != null) {
            movingHolder.destroy();
        }
        return null;
    }

    @Override
    protected void attach() {
        if (this.blockPos != null) {
            super.attach();
        }
    }

    public class_2338 getBlockPos() {
        return this.blockPos;
    }

    @ApiStatus.Internal
    public void setBlockState(class_2680 blockState) {
        this.blockState = blockState;
        if (this == this.holder().getAttachment()) {
            this.holder().notifyUpdate(BLOCK_STATE_UPDATE);
        }
    }

    public class_2680 getBlockState() {
        return this.blockState;
    }

    @Override
    public boolean isPartOfTheWorld() {
        return true;
    }

    @Nullable
    public static BlockBoundAttachment get(class_1937 world, class_2338 pos) {
        var chunk = world.method_22350(pos);
        return chunk instanceof class_2818 worldChunk ? get(worldChunk, pos) : null;
    }

    @Nullable
    public static BlockBoundAttachment get(class_2818 chunk, class_2338 pos) {
        return ((HolderAttachmentHolder) chunk).polymerVE$getPosHolder(pos);
    }

    @Nullable
    public static BlockBoundAttachment get(ElementHolder holder) {
        return holder.getAttachment() instanceof BlockBoundAttachment blockBoundAttachment ? blockBoundAttachment : null;
    }
}
