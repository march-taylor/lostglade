package eu.pb4.polymer.virtualentity.api.elements;

import eu.pb4.polymer.virtualentity.api.tracker.DisplayTrackedData;
import net.minecraft.class_1299;
import net.minecraft.class_2680;
import net.minecraft.class_8113;

public class BlockDisplayElement extends DisplayElement {
    public BlockDisplayElement(class_2680 state) {
        this.setBlockState(state);
    }

    public BlockDisplayElement() {}

    public void setBlockState(class_2680 state) {
        this.dataTracker.set(DisplayTrackedData.Block.BLOCK_STATE, state);
    }

    public class_2680 getBlockState() {
        return this.dataTracker.get(DisplayTrackedData.Block.BLOCK_STATE);
    }

    @Override
    protected final class_1299<? extends class_8113> getEntityType() {
        return class_1299.field_42460;
    }
}
