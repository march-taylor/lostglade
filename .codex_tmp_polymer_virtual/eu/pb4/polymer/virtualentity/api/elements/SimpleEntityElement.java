package eu.pb4.polymer.virtualentity.api.elements;

import eu.pb4.polymer.virtualentity.api.tracker.DataTrackerLike;
import eu.pb4.polymer.virtualentity.api.tracker.SimpleDataTracker;
import net.minecraft.class_1297;
import net.minecraft.class_1299;

public class SimpleEntityElement extends GenericEntityElement {
    private static final ThreadLocal<class_1299<?>> LOCAL_TYPE = new ThreadLocal<>();
    private final class_1299<?> type;

    public SimpleEntityElement(class_1299<?> type) {
        this(type, hackyHack(type));
        LOCAL_TYPE.remove();
    }

    private static Object hackyHack(class_1299<?> type) {
        LOCAL_TYPE.set(type);
        return type;
    }

    private SimpleEntityElement(class_1299<?> type, Object hack) {
        super();
        this.type = type;
    }

    @Override
    protected DataTrackerLike createDataTracker() {
        if (this.type != null) {
            return super.createDataTracker();
        } else {
            return new SimpleDataTracker(LOCAL_TYPE.get());
        }
    }

    @Override
    protected class_1299<? extends class_1297> getEntityType() {
        return this.type;
    }
}
