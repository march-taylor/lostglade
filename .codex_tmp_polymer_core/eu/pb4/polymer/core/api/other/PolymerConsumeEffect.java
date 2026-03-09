package eu.pb4.polymer.core.api.other;

import eu.pb4.polymer.core.api.utils.PolymerObject;
import eu.pb4.polymer.core.impl.other.PolymerComponentImpl;
import eu.pb4.polymer.rsm.api.RegistrySyncUtils;
import net.minecraft.class_10134;
import net.minecraft.class_7923;
import org.jetbrains.annotations.Nullable;
import xyz.nucleoid.packettweaker.PacketContext;

public interface PolymerConsumeEffect extends PolymerObject {
    static void registerConsumeEffect(class_10134.class_10135<?>... types) {
        for (var type : types) {
            RegistrySyncUtils.setServerEntry(class_7923.field_53967, type);
            PolymerComponentImpl.UNSYNCED_CONSUME_EFFECTS.add(type);
        }
    }

    static boolean canSync(class_10134.class_10135<?> key, @Nullable class_10134 entry, PacketContext context) {
        if (entry instanceof PolymerConsumeEffect component && component.canSyncRawToClient(context)) {
            return true;
        }

        return !PolymerComponentImpl.UNSYNCED_CONSUME_EFFECTS.contains(key);
    }

    default boolean canSyncRawToClient(PacketContext context) {
        return false;
    }
}
