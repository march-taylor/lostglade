package eu.pb4.polymer.virtualentity.api.elements;

import eu.pb4.polymer.virtualentity.api.tracker.EntityTrackedData;
import eu.pb4.polymer.virtualentity.mixin.SlimeEntityAccessor;
import net.minecraft.class_1297;
import net.minecraft.class_1299;

public class MobAnchorElement extends GenericEntityElement {
    public MobAnchorElement() {
        this.dataTracker.set(SlimeEntityAccessor.getID_SIZE(), 0);
        this.dataTracker.set(EntityTrackedData.SILENT, true);
        this.dataTracker.set(EntityTrackedData.NO_GRAVITY, true);
        this.dataTracker.set(EntityTrackedData.FLAGS, (byte) ((1 << EntityTrackedData.INVISIBLE_FLAG_INDEX)));
    }

    @Override
    protected class_1299<? extends class_1297> getEntityType() {
        return class_1299.field_6069;
    }
}
