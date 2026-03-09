package eu.pb4.polymer.virtualentity.api.elements;

import eu.pb4.polymer.virtualentity.api.tracker.InteractionTrackedData;
import net.minecraft.class_1297;
import net.minecraft.class_1299;
import net.minecraft.class_4048;

public class InteractionElement extends GenericEntityElement {

    public InteractionElement() {
    }

    public InteractionElement(InteractionHandler handler) {
        this.setHandler(handler);
    }

    public static InteractionElement redirect(class_1297 redirectedEntity) {
        return new InteractionElement(InteractionHandler.redirect(redirectedEntity));
    }

    @Deprecated
    public void setHandler(InteractionHandler handler) {
        this.setInteractionHandler(handler);
    }

    @Override
    protected final class_1299<? extends class_1297> getEntityType() {
        return class_1299.field_42623;
    }

    public float getWidth() {
        return this.dataTracker.get(InteractionTrackedData.WIDTH);
    }

    public void setWidth(float width) {
        this.dataTracker.set(InteractionTrackedData.WIDTH, width);
    }

    public float getHeight() {
        return this.dataTracker.get(InteractionTrackedData.HEIGHT);
    }

    public void setHeight(float height) {
        this.dataTracker.set(InteractionTrackedData.HEIGHT, height);
    }

    public void setResponse(boolean response) {
        this.dataTracker.set(InteractionTrackedData.RESPONSE, response);
    }

    public void setSize(float width, float height) {
        setWidth(width);
        setHeight(height);
    }

    public void setSize(class_4048 dimensions) {
        setWidth(dimensions.comp_2185());
        setHeight(dimensions.comp_2186());
    }

    public boolean shouldRespond() {
        return this.dataTracker.get(InteractionTrackedData.RESPONSE);
    }
}
