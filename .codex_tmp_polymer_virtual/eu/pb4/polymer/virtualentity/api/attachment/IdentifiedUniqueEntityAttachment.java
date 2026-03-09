package eu.pb4.polymer.virtualentity.api.attachment;

import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.impl.HolderAttachmentHolder;
import net.minecraft.class_1297;
import net.minecraft.class_2960;
import org.jetbrains.annotations.Nullable;

public class IdentifiedUniqueEntityAttachment extends EntityAttachment implements UniqueIdentifiableAttachment {
    private final class_2960 id;

    public IdentifiedUniqueEntityAttachment(class_2960 identifier, ElementHolder holder, class_1297 entity, boolean autoTick) {
        super(holder, entity, autoTick);
        this.id = identifier;
        if (this.getClass() == IdentifiedUniqueEntityAttachment.class) {
            this.attach();
        }
    }

    public static IdentifiedUniqueEntityAttachment of(class_2960 identifier, ElementHolder holder, class_1297 entity) {
        return new IdentifiedUniqueEntityAttachment(identifier, holder, entity, false);
    }

    public static IdentifiedUniqueEntityAttachment ofTicking(class_2960 identifier,ElementHolder holder, class_1297 entity) {
        return new IdentifiedUniqueEntityAttachment(identifier, holder, entity, true);
    }

    @Nullable
    static UniqueIdentifiableAttachment get(class_1297 entity, class_2960 identifier) {
        return ((HolderAttachmentHolder) entity).polymerVE$getIdHolder(identifier);
    }

    @Override
    public class_2960 getAttachmentId() {
        return this.id;
    }
}
