package eu.pb4.polymer.core.api.other;

import eu.pb4.polymer.core.api.utils.PolymerSyncedObject;
import eu.pb4.polymer.core.api.utils.PolymerUtils;
import eu.pb4.polymer.core.impl.PolymerImplUtils;
import eu.pb4.polymer.rsm.api.RegistrySyncUtils;
import org.jetbrains.annotations.Nullable;
import xyz.nucleoid.packettweaker.PacketContext;

import java.util.UUID;
import net.minecraft.class_3414;
import net.minecraft.class_6880;
import net.minecraft.class_7923;

/**
 * This class allows for creation of custom sound effects
 * It can be used to play custom sounds for players with resourcepack while keeping fallback for vanilla clients
 */
public class PolymerSoundEvent implements PolymerSyncedObject<class_3414> {
    @Nullable
    protected final class_3414 polymerSound;
    @Nullable
    protected final UUID source;

    public static class_3414 registerOverlay(class_3414 event) {
        PolymerSyncedObject.setSyncedObject(class_7923.field_41172, event, (object, context) -> object);
        return event;
    }

    public static class_3414 registerOverlay(class_3414 event, @Nullable class_3414 fallback) {
        PolymerSyncedObject.setSyncedObject(class_7923.field_41172, event, of(fallback));
        return event;
    }

    public static class_3414 registerOverlay(class_3414 event, class_6880<class_3414> fallback) {
        PolymerSyncedObject.setSyncedObject(class_7923.field_41172, event, of(fallback.comp_349()));
        return event;
    }

    public static class_3414 registerOverlay(class_3414 event, @Nullable class_3414 fallback, @Nullable UUID resourcePackUuid) {
        PolymerSyncedObject.setSyncedObject(class_7923.field_41172, event, new PolymerSoundEvent(resourcePackUuid, fallback));
        return event;
    }

    public static class_3414 registerOverlay(class_3414 event, class_6880<class_3414> fallback, @Nullable UUID resourcePackUuid) {
        PolymerSyncedObject.setSyncedObject(class_7923.field_41172, event, new PolymerSoundEvent(resourcePackUuid, fallback.comp_349()));
        return event;
    }

    public static PolymerSoundEvent of(@Nullable class_3414 vanillaEvent) {
        return new PolymerSoundEvent(null, vanillaEvent);
    }

    public PolymerSoundEvent(@Nullable UUID uuid, @Nullable class_3414 vanillaEvent) {
        this.source = uuid;
        this.polymerSound = vanillaEvent;
    }

    @Override
    public class_3414 getPolymerReplacement(class_3414 event, PacketContext context) {
        return this.source == null || this.polymerSound == null || PolymerUtils.hasResourcePack(context.getPlayer(), this.source) ? event : this.polymerSound;
    }
}
