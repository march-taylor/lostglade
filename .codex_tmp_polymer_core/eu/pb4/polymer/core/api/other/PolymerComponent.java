package eu.pb4.polymer.core.api.other;

import eu.pb4.polymer.core.api.utils.PolymerObject;
import eu.pb4.polymer.core.api.utils.PolymerSyncedObject;
import eu.pb4.polymer.core.impl.other.PolymerComponentImpl;
import eu.pb4.polymer.rsm.api.RegistrySyncUtils;
import net.minecraft.class_7923;
import net.minecraft.class_9331;
import org.jetbrains.annotations.Nullable;
import xyz.nucleoid.packettweaker.PacketContext;

public interface PolymerComponent extends PolymerObject {
    static void registerDataComponent(class_9331<?>... types) {
        for (var type : types) {
            RegistrySyncUtils.setServerEntry(class_7923.field_49658, type);
            PolymerComponentImpl.UNSYNCED_COMPONENTS.add(type);
        }
    }

    static void registerEnchantmentEffectComponent(class_9331<?>... types) {
        for (var type : types) {
            RegistrySyncUtils.setServerEntry(class_7923.field_51832, type);
            PolymerComponentImpl.UNSYNCED_COMPONENTS.add(type);
        }
    }

    static boolean isPolymerComponent(class_9331<?> type) {
        return PolymerComponentImpl.UNSYNCED_COMPONENTS.contains(type) || type instanceof PolymerObject;
    }

    static boolean canSync(class_9331<?> key, @Nullable Object entry, PacketContext context) {
        if (entry instanceof PolymerComponent component && component.canSyncRawToClient(context)) {
            return true;
        } else if (key instanceof PolymerSyncedObject<?> syncedObject && syncedObject.canSyncRawToClient(context)) {
            return true;
        }

        return !isPolymerComponent(key);
    }

    default boolean canSyncRawToClient(PacketContext context) {
        return false;
    }
}
