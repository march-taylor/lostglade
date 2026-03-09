package eu.pb4.polymer.virtualentity.api.elements;

import eu.pb4.polymer.virtualentity.api.tracker.EntityTrackedData;
import eu.pb4.polymer.virtualentity.mixin.SlimeEntityAccessor;
import net.minecraft.class_1297;
import net.minecraft.class_1299;
import net.minecraft.class_1531;

public class MarkerElement extends GenericEntityElement {
    public MarkerElement() {
        this.dataTracker.set(class_1531.field_7107, (byte) (class_1531.field_30444 | class_1531.field_30452));
        this.dataTracker.set(EntityTrackedData.SILENT, true);
        this.dataTracker.set(EntityTrackedData.NO_GRAVITY, true);
        this.dataTracker.set(EntityTrackedData.FLAGS, (byte) ((1 << EntityTrackedData.INVISIBLE_FLAG_INDEX)));
    }

    @Override
    protected class_1299<? extends class_1297> getEntityType() {
        return class_1299.field_6131;
    }
}
