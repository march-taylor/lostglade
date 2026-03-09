package eu.pb4.polymer.core.impl.compat.polymc;

import eu.pb4.polymer.core.api.entity.PolymerEntityUtils;
import eu.pb4.polymer.core.api.utils.PolymerSyncedObject;
import io.github.theepicblock.polymc.api.PolyRegistry;
import net.minecraft.class_7923;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public class PolyMcEntrypoint implements io.github.theepicblock.polymc.api.PolyMcEntrypoint {
    @Override
    public void registerPolys(PolyRegistry registry) {
        for (var entityType : class_7923.field_41177) {
            if (PolymerEntityUtils.isPolymerEntityType(entityType)) {
                registry.registerEntityPoly(entityType, PassthroughPoly.entity());
            }
        }

        for (var item : class_7923.field_41178) {
            if (PolymerSyncedObject.getSyncedObject(class_7923.field_41178, item) != null) {
                registry.registerItemPoly(item, PassthroughPoly.ITEM);
            }
        }
    }
}
