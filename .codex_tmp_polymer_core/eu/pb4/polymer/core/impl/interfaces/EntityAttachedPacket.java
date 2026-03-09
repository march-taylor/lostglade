package eu.pb4.polymer.core.impl.interfaces;

import eu.pb4.polymer.core.api.entity.PolymerEntity;
import net.minecraft.class_1297;
import net.minecraft.class_2596;
import net.minecraft.class_3222;
import org.jetbrains.annotations.Nullable;

public interface EntityAttachedPacket {
    @Nullable
    static class_1297 get(Object packet, int entityId) {
        var entity = get(packet);
        return entity != null && entity.method_5628() == entityId ? entity : null;
    }

    class_1297 polymer$getEntity();
    class_2596<?> polymer$setEntity(class_1297 entity);

    @Nullable
    static class_1297 get(Object packet) {
        return packet instanceof EntityAttachedPacket e ? e.polymer$getEntity() : null;
    }

    static <T> T setIfEmpty(T packet, class_1297 entity) {
        return packet instanceof EntityAttachedPacket e && e.polymer$getEntity() == null ? (T) e.polymer$setEntity(entity) : packet;
    }

    static <T> T set(T packet, class_1297 entity) {
        return packet instanceof EntityAttachedPacket e ? (T) e.polymer$setEntity(entity) : packet;
    }

    static boolean shouldSend(class_2596<?> packet, class_3222 player) {
        var x = PolymerEntity.get(get(packet));
        return x == null || x.sendPacketsTo(player);
    }
}
