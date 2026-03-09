package eu.pb4.polymer.core.api.other;

import eu.pb4.polymer.common.impl.CommonImplUtils;
import eu.pb4.polymer.rsm.api.RegistrySyncUtils;
import it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet;
import java.util.Set;
import net.minecraft.class_3917;
import net.minecraft.class_7923;

public final class PolymerScreenHandlerUtils {
    private static final Set<class_3917<?>> POLYMER_TYPES = new ObjectOpenCustomHashSet<>(CommonImplUtils.IDENTITY_HASH);

    private PolymerScreenHandlerUtils() {}

    public static void registerType(class_3917<?>... types) {
        for (var type : types) {
            POLYMER_TYPES.add(type);
            RegistrySyncUtils.setServerEntry(class_7923.field_41187, type);
        }
    }

    public static boolean isPolymerType(class_3917<?> type) {
        return POLYMER_TYPES.contains(type);
    }
}
