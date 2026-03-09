package eu.pb4.polymer.virtualentity.api.attachment;

import eu.pb4.polymer.virtualentity.impl.HolderAttachmentHolder;
import net.minecraft.class_1297;
import net.minecraft.class_2960;
import org.jetbrains.annotations.Nullable;

public interface UniqueIdentifiableAttachment extends HolderAttachment {
    class_2960 getAttachmentId();

    @Nullable
    static UniqueIdentifiableAttachment get(class_1297 entity, class_2960 identifier) {
        return ((HolderAttachmentHolder) entity).polymerVE$getIdHolder(identifier);
    }
}
