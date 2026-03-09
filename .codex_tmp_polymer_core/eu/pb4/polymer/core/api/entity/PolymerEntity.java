package eu.pb4.polymer.core.api.entity;

import com.mojang.datafixers.util.Pair;
import eu.pb4.polymer.core.api.utils.PolymerObject;
import eu.pb4.polymer.core.impl.interfaces.PolymerEntityProvider;
import org.jetbrains.annotations.Nullable;
import xyz.nucleoid.packettweaker.PacketContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.class_1268;
import net.minecraft.class_1269;
import net.minecraft.class_1297;
import net.minecraft.class_1299;
import net.minecraft.class_1304;
import net.minecraft.class_1799;
import net.minecraft.class_2596;
import net.minecraft.class_2781;
import net.minecraft.class_2945;
import net.minecraft.class_3218;
import net.minecraft.class_3222;
import net.minecraft.class_5629;

/**
 * Interface used for creation of server-side entities
 */
public interface PolymerEntity extends PolymerObject {
    /**
     * This method is used to determine what this entity will look like on client for specific player
     *
     * @return Vanilla/Modded entity type
     */
    class_1299<?> getPolymerEntityType(PacketContext context);

    /**
     * This method is used for replacing entity's equipment on client for a player
     *
     * @param items List of a Pair of EquipmentSlot and ItemStack on entity server-side
     * @return List of a Pair of EquipmentSlot and ItemStack sent to client
     */
    default List<Pair<class_1304, class_1799>> getPolymerVisibleEquipment(List<Pair<class_1304, class_1799>> items, class_3222 player) {
        return items;
    }

    /**
     * Allows sending packets before entity's spawn packet, useful for Player Entities
     */
    default void onBeforeSpawnPacket(class_3222 player, Consumer<class_2596<?>> packetConsumer) {}

    /**
     * This method allows to modify raw serialized DataTracker entries before they are send to the client
     * @param data Current values
     * @param initial
     */
    default void modifyRawTrackedData(List<class_2945.class_7834<?>> data, class_3222 player, boolean initial) {

    }

    default void modifyRawEntityAttributeData(List<class_2781.class_2782> data, class_3222 player, boolean initial) {

    }


    default void onEntityPacketSent(Consumer<class_2596<?>> consumer, class_2596<?> packet) {
        consumer.accept(packet);
    }

    /**
     * Allows disabling sending packets to player
     * @param player
     * @return true to allow, false to disable
     */
    default boolean sendPacketsTo(class_3222 player) {
        return true;
    }

    /**
     * This method is executed after tracker tick
     */
    default void onEntityTrackerTick(Set<class_5629> listeners) {};

    default void beforeEntityTrackerTick(Set<class_5629> listeners) {}

    /**
     * Sends real id to clients with polymer
     */
    default boolean canSynchronizeToPolymerClient(class_3222 player) {
        return true;
    }

    default boolean sendEmptyTrackerUpdates(class_3222 player) {
        return true;
    }

    default boolean isPolymerEntityInteraction(class_3222 player, class_1268 hand, class_1799 stack, class_3218 world, class_1269 actionResult) {
        return true;
    }
    @Nullable
    static PolymerEntity get(@Nullable class_1297 entity) {
        return entity != null ? ((PolymerEntityProvider) entity).polymer$getPolymerEntity() : null;
    }
}
