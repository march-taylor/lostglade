package eu.pb4.polymer.virtualentity.api.attachment;

import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.impl.HolderAttachmentHolder;
import net.minecraft.class_1937;
import net.minecraft.class_2338;
import net.minecraft.class_2680;
import net.minecraft.class_2818;
import org.jetbrains.annotations.Nullable;

public interface BlockAwareAttachment extends HolderAttachment {
    UpdateType BLOCK_STATE_UPDATE = UpdateType.of("BlockState");

    class_2338 getBlockPos();
    class_2680 getBlockState();
    boolean isPartOfTheWorld();

    @Nullable
    static BlockAwareAttachment get(class_1937 world, class_2338 pos) {
        var chunk = world.method_22350(pos);
        return chunk instanceof class_2818 worldChunk ? get(worldChunk, pos) : null;
    }

    @Nullable
    static BlockAwareAttachment get(class_2818 chunk, class_2338 pos) {
        return ((HolderAttachmentHolder) chunk).polymerVE$getPosHolder(pos);
    }

    @Nullable
    static BlockAwareAttachment get(ElementHolder holder) {
        return holder.getAttachment() instanceof BlockAwareAttachment blockBoundAttachment ? blockBoundAttachment : null;
    }
}
