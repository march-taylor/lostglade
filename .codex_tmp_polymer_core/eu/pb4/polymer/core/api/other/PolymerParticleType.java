package eu.pb4.polymer.core.api.other;

import eu.pb4.polymer.core.api.utils.PolymerSyncedObject;
import net.minecraft.class_2394;
import net.minecraft.class_2396;
import net.minecraft.class_2398;
import net.minecraft.class_7923;
import org.jspecify.annotations.Nullable;
import xyz.nucleoid.packettweaker.PacketContext;

public interface PolymerParticleType<T extends class_2394> extends PolymerSyncedObject<class_2396<?>> {
    @Override
    default class_2396<?> getPolymerReplacement(class_2396<?> object, PacketContext context) {
        return class_2398.field_11231;
    }

    class_2394 getPolymerParticleReplacement(T options, PacketContext context);

    static <T extends class_2394> void setOverlay(class_2396<T> type, PolymerParticleType<T> overlay) {
        PolymerSyncedObject.setSyncedObject(class_7923.field_41180, type, overlay);
    }

    @Nullable
    static <T extends class_2394> PolymerParticleType<T> getOverlay(class_2396<T> type) {
        //noinspection unchecked
        return PolymerSyncedObject.getSyncedObject(class_7923.field_41180, type) instanceof PolymerParticleType<?> polymerParticleType ? (PolymerParticleType<T>) polymerParticleType : null;
    }
}
