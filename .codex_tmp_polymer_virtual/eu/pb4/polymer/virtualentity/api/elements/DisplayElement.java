package eu.pb4.polymer.virtualentity.api.elements;

import eu.pb4.polymer.virtualentity.api.tracker.DisplayTrackedData;
import net.minecraft.class_1299;
import net.minecraft.class_4048;
import net.minecraft.class_4590;
import net.minecraft.class_7837;
import net.minecraft.class_8104;
import net.minecraft.class_8113;
import org.jetbrains.annotations.Nullable;
import org.joml.*;


@SuppressWarnings("ConstantConditions")
public abstract class DisplayElement extends GenericEntityElement {
    @Override
    protected abstract class_1299<? extends class_8113> getEntityType();

    public void setTransformation(class_4590 transformation) {
        this.dataTracker.set(DisplayTrackedData.TRANSLATION, transformation.method_35865());
        this.dataTracker.set(DisplayTrackedData.LEFT_ROTATION, transformation.method_22937());
        this.dataTracker.set(DisplayTrackedData.SCALE, transformation.method_35866());
        this.dataTracker.set(DisplayTrackedData.RIGHT_ROTATION, transformation.method_35867());
    }

    public void setTransformation(Matrix4f matrix) {
        float f = 1.0F / matrix.m33();
        var triple = class_7837.method_46412(new Matrix3f(matrix).scale(f));
        this.dataTracker.set(DisplayTrackedData.TRANSLATION, matrix.getTranslation(new Vector3f()));
        this.dataTracker.set(DisplayTrackedData.LEFT_ROTATION, new Quaternionf(triple.getLeft()));
        this.dataTracker.set(DisplayTrackedData.SCALE, new Vector3f(triple.getMiddle()));
        this.dataTracker.set(DisplayTrackedData.RIGHT_ROTATION, new Quaternionf(triple.getRight()));
    }

    public void setTransformation(Matrix4x3f matrix) {
        var triple = class_7837.method_46412(new Matrix3f().set(matrix));
        this.dataTracker.set(DisplayTrackedData.TRANSLATION, matrix.getTranslation(new Vector3f()));
        this.dataTracker.set(DisplayTrackedData.LEFT_ROTATION, new Quaternionf(triple.getLeft()));
        this.dataTracker.set(DisplayTrackedData.SCALE, new Vector3f(triple.getMiddle()));
        this.dataTracker.set(DisplayTrackedData.RIGHT_ROTATION, new Quaternionf(triple.getRight()));
    }

    public boolean isTransformationDirty() {
        return this.dataTracker.isDirty(DisplayTrackedData.TRANSLATION)
                || this.dataTracker.isDirty(DisplayTrackedData.LEFT_ROTATION)
                || this.dataTracker.isDirty(DisplayTrackedData.SCALE)
                || this.dataTracker.isDirty(DisplayTrackedData.RIGHT_ROTATION);
    }

    public void setTranslation(Vector3fc vector3f) {
        this.dataTracker.set(DisplayTrackedData.TRANSLATION, new Vector3f(vector3f));
    }

    public Vector3fc getTranslation() {
        return this.dataTracker.get(DisplayTrackedData.TRANSLATION);
    }

    public void setScale(Vector3fc vector3f) {
        this.dataTracker.set(DisplayTrackedData.SCALE, new Vector3f(vector3f));
    }

    public Vector3fc getScale() {
        return this.dataTracker.get(DisplayTrackedData.SCALE);
    }

    public void setLeftRotation(Quaternionfc quaternion) {
        this.dataTracker.set(DisplayTrackedData.LEFT_ROTATION, new Quaternionf(quaternion));
    }

    public Quaternionfc getLeftRotation() {
        return this.dataTracker.get(DisplayTrackedData.LEFT_ROTATION);
    }

    public void setRightRotation(Quaternionfc quaternion) {
        this.dataTracker.set(DisplayTrackedData.RIGHT_ROTATION, new Quaternionf(quaternion));
    }

    public Quaternionfc getRightRotation() {
        return this.dataTracker.get(DisplayTrackedData.RIGHT_ROTATION);
    }

    public int getInterpolationDuration() {
        return this.dataTracker.get(DisplayTrackedData.INTERPOLATION_DURATION);
    }

    public void setInterpolationDuration(int interpolationDuration) {
        this.dataTracker.set(DisplayTrackedData.INTERPOLATION_DURATION, interpolationDuration);
    }

    public int getTeleportDuration() {
        return this.dataTracker.get(DisplayTrackedData.TELEPORTATION_DURATION);
    }

    public void setTeleportDuration(int interpolationDuration) {
        this.dataTracker.set(DisplayTrackedData.TELEPORTATION_DURATION, interpolationDuration);
    }

    public int getStartInterpolation() {
        return this.dataTracker.get(DisplayTrackedData.START_INTERPOLATION);
    }

    public void startInterpolation() {
        this.dataTracker.setDirty(DisplayTrackedData.START_INTERPOLATION, true);
    }

    public void setStartInterpolation(int startInterpolation) {
        this.dataTracker.set(DisplayTrackedData.START_INTERPOLATION, startInterpolation, true);
    }

    public void startInterpolationIfDirty() {
        if (this.isTransformationDirty()) {
            this.startInterpolation();
        }
    }

    public class_8113.class_8114 getBillboardMode() {
        return class_8113.class_8114.field_42411.apply(this.dataTracker.get(DisplayTrackedData.BILLBOARD));
    }

    public void setBillboardMode(class_8113.class_8114 billboardMode) {
        this.dataTracker.set(DisplayTrackedData.BILLBOARD, (byte) billboardMode.ordinal());
    }

    @Nullable
    public class_8104 getBrightness() {
        int i = this.dataTracker.get(DisplayTrackedData.BRIGHTNESS);
        return i != -1 ? class_8104.method_48764(i) : null;
    }

    public void setBrightness(@Nullable class_8104 brightness) {
        this.dataTracker.set(DisplayTrackedData.BRIGHTNESS, brightness != null ? brightness.method_48763() : -1);
    }

    public float getViewRange() {
        return this.dataTracker.get(DisplayTrackedData.VIEW_RANGE);
    }

    public void setViewRange(float viewRange) {
        this.dataTracker.set(DisplayTrackedData.VIEW_RANGE, viewRange);
    }

    public float getShadowRadius() {
        return this.dataTracker.get(DisplayTrackedData.SHADOW_RADIUS);
    }

    public void setShadowRadius(float shadowRadius) {
        this.dataTracker.set(DisplayTrackedData.SHADOW_RADIUS, shadowRadius);
    }

    public float getShadowStrength() {
        return this.dataTracker.get(DisplayTrackedData.SHADOW_STRENGTH);
    }

    public void setShadowStrength(float shadowStrength) {
        this.dataTracker.set(DisplayTrackedData.SHADOW_STRENGTH, shadowStrength);
    }

    public float getDisplayWidth() {
        return this.dataTracker.get(DisplayTrackedData.WIDTH);
    }

    public float getDisplayHeight() {
        return this.dataTracker.get(DisplayTrackedData.HEIGHT);
    }

    public void setDisplayWidth(float width) {
        this.dataTracker.set(DisplayTrackedData.WIDTH, width);
    }

    public void setDisplayHeight(float height) {
        this.dataTracker.set(DisplayTrackedData.HEIGHT, height);
    }

    public void setDisplaySize(float width, float height) {
        this.setDisplayWidth(width);
        this.setDisplayHeight(height);
    }

    public void setDisplaySize(class_4048 dimensions) {
        this.setDisplayWidth(dimensions.comp_2185());
        this.setDisplayHeight(dimensions.comp_2186());
    }

    public int getGlowColorOverride() {
        return this.dataTracker.get(DisplayTrackedData.GLOW_COLOR_OVERRIDE);
    }

    public void setGlowColorOverride(int glowColorOverride) {
        this.dataTracker.set(DisplayTrackedData.GLOW_COLOR_OVERRIDE, glowColorOverride);
    }
}
