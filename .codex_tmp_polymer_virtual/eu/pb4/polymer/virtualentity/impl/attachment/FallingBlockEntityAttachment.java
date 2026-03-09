package eu.pb4.polymer.virtualentity.impl.attachment;

import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.BlockAwareAttachment;
import eu.pb4.polymer.virtualentity.api.attachment.EntityAttachment;
import net.minecraft.class_1540;
import net.minecraft.class_2338;
import net.minecraft.class_243;
import net.minecraft.class_2680;

public class FallingBlockEntityAttachment extends EntityAttachment implements BlockAwareAttachment {
    public FallingBlockEntityAttachment(ElementHolder holder, class_1540 entity) {
        super(holder, entity, true);
    }

    @Override
    public class_2338 getBlockPos() {
        return ((class_1540) this.entity).method_6964();
    }

    @Override
    public class_2680 getBlockState() {
        return ((class_1540) this.entity).method_6962();
    }

    @Override
    public boolean isPartOfTheWorld() {
        return false;
    }

    @Override
    public class_243 getPos() {
        return super.getPos().method_1031(0, 0.5, 0);
    }
}
