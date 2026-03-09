package eu.pb4.polymer.virtualentity.api.elements;

import eu.pb4.polymer.virtualentity.api.tracker.DisplayTrackedData;
import net.minecraft.class_1299;
import net.minecraft.class_1792;
import net.minecraft.class_1799;
import net.minecraft.class_811;
import net.minecraft.class_8113;

public class ItemDisplayElement extends DisplayElement {
    public ItemDisplayElement(class_1799 stack) {
        this.setItem(stack);
    }

    public ItemDisplayElement() {}

    public ItemDisplayElement(class_1792 item) {
        this.setItem(item.method_7854());
    }

    public void setItem(class_1799 stack) {
        this.dataTracker.set(DisplayTrackedData.Item.ITEM, stack);
    }

    public class_1799 getItem() {
        return this.dataTracker.get(DisplayTrackedData.Item.ITEM);
    }

    public void setItemDisplayContext(class_811 mode) {
        this.dataTracker.set(DisplayTrackedData.Item.ITEM_DISPLAY, mode.method_48961());
    }
    public class_811 getItemDisplayContext() {
        //noinspection DataFlowIssue
        return class_811.field_42469.apply(this.dataTracker.get(DisplayTrackedData.Item.ITEM_DISPLAY));
    }

    @Deprecated(forRemoval = true)
    public void setModelTransformation(class_811 mode) {
        setItemDisplayContext(mode);
    }
    @Deprecated(forRemoval = true)
    public class_811 getModelTransformation() {
        return getItemDisplayContext();
    }

    @Override
    protected final class_1299<? extends class_8113> getEntityType() {
        return class_1299.field_42456;
    }
}
