package eu.pb4.polymer.virtualentity.impl.attachment;

import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.BlockAwareAttachment;
import eu.pb4.polymer.virtualentity.api.attachment.ChunkAttachment;
import eu.pb4.polymer.virtualentity.impl.HolderAttachmentHolder;
import net.minecraft.class_2338;
import net.minecraft.class_2350;
import net.minecraft.class_243;
import net.minecraft.class_2680;
import net.minecraft.class_2818;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;


public final class PistonAttachment extends ChunkAttachment implements BlockAwareAttachment {
    private final class_2338 blockPos;
    private final class_2350 direction;
    private class_2680 blockState;

    public PistonAttachment(ElementHolder holder, class_2818 chunk, class_2680 state, class_2338 blockPos, class_2350 direction) {
        super(holder, chunk, class_243.method_24953(blockPos), false);
        this.blockPos = blockPos;
        this.direction = direction;
        this.blockState = state;
        this.attach();
    }

    @Override
    protected void attach() {
        if (this.blockPos != null) {
            super.attach();
        }
    }

    @Override
    public boolean canUpdatePosition() {
        return true;
    }

    public void update(float d) {
        this.pos = class_243.method_24953(this.blockPos).method_43206(this.direction, d);
        this.holder().tick();
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
}
