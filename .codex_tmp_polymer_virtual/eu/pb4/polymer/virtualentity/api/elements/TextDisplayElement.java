package eu.pb4.polymer.virtualentity.api.elements;

import eu.pb4.polymer.virtualentity.api.tracker.DisplayTrackedData;
import net.minecraft.class_1299;
import net.minecraft.class_2561;
import net.minecraft.class_8113;

public class TextDisplayElement extends DisplayElement {
    public TextDisplayElement(class_2561 text) {
        this.setText(text);
    }

    public TextDisplayElement() {
    }

    public class_2561 getText() {
        return this.dataTracker.get(DisplayTrackedData.Text.TEXT);
    }

    public void setText(class_2561 text) {
        this.dataTracker.set(DisplayTrackedData.Text.TEXT, text);
    }

    public int getLineWidth() {
        return this.dataTracker.get(DisplayTrackedData.Text.LINE_WIDTH);
    }

    public void setLineWidth(int lineWidth) {
        this.dataTracker.set(DisplayTrackedData.Text.LINE_WIDTH, lineWidth);
    }

    public byte getTextOpacity() {
        return this.dataTracker.get(DisplayTrackedData.Text.TEXT_OPACITY);
    }

    public void setTextOpacity(byte textOpacity) {
        this.dataTracker.set(DisplayTrackedData.Text.TEXT_OPACITY, textOpacity);
    }

    public int getBackground() {
        return this.dataTracker.get(DisplayTrackedData.Text.BACKGROUND);
    }

    public void setBackground(int background) {
        this.dataTracker.set(DisplayTrackedData.Text.BACKGROUND, background);
    }

    public byte getDisplayFlags() {
        return this.dataTracker.get(DisplayTrackedData.Text.TEXT_DISPLAY_FLAGS);
    }

    public void setDisplayFlags(byte flags) {
        this.dataTracker.set(DisplayTrackedData.Text.TEXT_DISPLAY_FLAGS, flags);
    }

    public boolean getDisplayFlag(byte flag) {
        return (getDisplayFlags() & flag) != 0;
    }

    public void setDisplayFlag(byte flag, boolean value) {
        this.dataTracker.set(DisplayTrackedData.Text.TEXT_DISPLAY_FLAGS, flag(getDisplayFlags(), flag, value));
    }

    public void setTextAlignment(class_8113.class_8123.class_8124 alignment) {
        setDisplayFlags(switch (alignment) {
            case field_42450 -> flag(getDisplayFlags(), DisplayTrackedData.Text.LEFT_ALIGNMENT_FLAG | DisplayTrackedData.Text.RIGHT_ALIGNMENT_FLAG, false);
            case field_42451 -> flag(flag(getDisplayFlags(), DisplayTrackedData.Text.LEFT_ALIGNMENT_FLAG, true), DisplayTrackedData.Text.RIGHT_ALIGNMENT_FLAG, false);
            case field_42452 -> flag(flag(getDisplayFlags(), DisplayTrackedData.Text.LEFT_ALIGNMENT_FLAG, false), DisplayTrackedData.Text.RIGHT_ALIGNMENT_FLAG, true);
        });
    }

    public class_8113.class_8123.class_8124 getTextAlignment() {
        return class_8113.class_8123.method_48902(this.getDisplayFlags());
    }

    public void setShadow(boolean value) {
        setDisplayFlag(DisplayTrackedData.Text.SHADOW_FLAG, value);
    }

    public boolean getShadow() {
        return getDisplayFlag(DisplayTrackedData.Text.SHADOW_FLAG);
    }

    public void setSeeThrough(boolean value) {
        setDisplayFlag(DisplayTrackedData.Text.SEE_THROUGH_FLAG, value);
    }

    public boolean getSeeThrough() {
        return getDisplayFlag(DisplayTrackedData.Text.SEE_THROUGH_FLAG);
    }

    public void setDefaultBackground(boolean value) {
        setDisplayFlag(DisplayTrackedData.Text.DEFAULT_BACKGROUND_FLAG, value);
    }

    public boolean getDefaultBackground() {
        return getDisplayFlag(DisplayTrackedData.Text.DEFAULT_BACKGROUND_FLAG);
    }

    private static byte flag(int base, int flag, boolean value) {
        return (byte) (value ? base | flag : base & ~flag);
    }

    @Override
    protected final class_1299<? extends class_8113> getEntityType() {
        return class_1299.field_42457;
    }
}
