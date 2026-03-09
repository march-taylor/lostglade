package eu.pb4.polymer.core.api.entity;

import eu.pb4.polymer.common.api.events.BooleanEvent;
import eu.pb4.polymer.common.impl.CommonImplUtils;
import eu.pb4.polymer.common.impl.entity.InternalEntityHelpers;
import eu.pb4.polymer.core.api.item.PolymerItem;
import eu.pb4.polymer.core.api.utils.PolymerSyncedObject;
import eu.pb4.polymer.core.impl.entity.OneOfPolymerEntityConstructors;
import eu.pb4.polymer.core.impl.interfaces.EntityAttachedPacket;
import eu.pb4.polymer.core.impl.interfaces.PolymerEntityProvider;
import eu.pb4.polymer.core.impl.networking.PolymerServerProtocol;
import eu.pb4.polymer.core.mixin.block.packet.ServerMapAccessor;
import eu.pb4.polymer.core.mixin.entity.EntityAccessor;
import eu.pb4.polymer.core.mixin.entity.TrackedEntityAccessor;
import eu.pb4.polymer.core.mixin.entity.ClientboundPlayerInfoUpdatePacketAccessor;
import eu.pb4.polymer.rsm.api.RegistrySyncUtils;
import it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Function;
import net.minecraft.class_1268;
import net.minecraft.class_1269;
import net.minecraft.class_1297;
import net.minecraft.class_1299;
import net.minecraft.class_1320;
import net.minecraft.class_1799;
import net.minecraft.class_2596;
import net.minecraft.class_2602;
import net.minecraft.class_2703;
import net.minecraft.class_2945;
import net.minecraft.class_3218;
import net.minecraft.class_3222;
import net.minecraft.class_3852;
import net.minecraft.class_6880;
import net.minecraft.class_7923;

public final class PolymerEntityUtils {
    private PolymerEntityUtils() {
    }
    public static final BooleanEvent<PolymerEntityInteractionListener> POLYMER_ENTITY_INTERACTION_CHECK = new BooleanEvent<>();

    private static final Map<class_1299<?>, Function<class_1297, PolymerEntity>> POLYMER_ENTITY_CONSTRUCTORS = new IdentityHashMap<>();
    private static final Set<class_1320> ENTITY_ATTRIBUTES = new ObjectOpenCustomHashSet<>(CommonImplUtils.IDENTITY_HASH);

    /**
     * Allows to get next free entity id you can use for networking
     *
     * @return free entity id
     */
    public static int requestFreeId() {
        return EntityAccessor.getENTITY_COUNTER().incrementAndGet();
    }

    /**
     * Marks EntityTypes as server-side only
     *
     * @param types Entity Types
     */
    public static void registerType(class_1299<?>... types) {
        for (var type : types) {
            registerPolymerEntityConstructor(type, entity -> entity instanceof PolymerEntity polymerEntity ? polymerEntity : null);
        }

        for (var type : types) {
            PolymerSyncedObject.setSyncedObject(class_7923.field_41177, type, (ent, ctx) -> class_1299.field_33456);
        }
    }

    public static void registerType(class_1299<?> type, PolymerSyncedObject<class_1299<?>> syncedObject) {
        registerPolymerEntityConstructor(type, entity -> entity instanceof PolymerEntity polymerEntity ? polymerEntity : (context -> syncedObject.getPolymerReplacement(((class_1297) entity).method_5864(), context)));
        PolymerSyncedObject.setSyncedObject(class_7923.field_41177, type, syncedObject);
    }

    public static <T extends class_1297> void registerOverlay(class_1299<T> type, Function<T, PolymerEntity> constructor) {
        registerPolymerEntityConstructor(type, constructor);
        PolymerSyncedObject.setSyncedObject(class_7923.field_41177, type, (ent, ctx) -> class_1299.field_33456);
    }

    public static <T extends class_1297> void registerOverlay(class_1299<T> type, PolymerSyncedObject<class_1299<?>> syncedObject, Function<T, PolymerEntity> constructor) {
        //noinspection unchecked
        registerPolymerEntityConstructor(type, constructor);
        PolymerSyncedObject.setSyncedObject(class_7923.field_41177, type, syncedObject);
    }

    public static <T extends class_1297> void registerPolymerEntityConstructor(class_1299<T> type, Function<T, @Nullable PolymerEntity> constructor) {
        if (POLYMER_ENTITY_CONSTRUCTORS.containsKey(type)) {
            var old = POLYMER_ENTITY_CONSTRUCTORS.get(type);
            //noinspection rawtypes,unchecked
            POLYMER_ENTITY_CONSTRUCTORS.put(type, new OneOfPolymerEntityConstructors(constructor, old));
        } else {
            //noinspection unchecked
            POLYMER_ENTITY_CONSTRUCTORS.put(type, (Function<class_1297, PolymerEntity>) constructor);
        }
    }

    @Nullable
    public static <T extends class_1297> Function<T, @Nullable PolymerEntity> getPolymerEntityConstructor(class_1299<T> type) {
        //noinspection unchecked
        return (Function<T, PolymerEntity>) POLYMER_ENTITY_CONSTRUCTORS.get(type);
    }

    /**
     * Marks EntityAttribute as server-side only
     */
    @SafeVarargs
    public static void registerAttribute(class_6880<class_1320>... attributes) {
        for (var type : attributes) {
            ENTITY_ATTRIBUTES.add(type.comp_349());
            RegistrySyncUtils.setServerEntry(class_7923.field_41190, type.comp_349());
        }
    }

    /**
     * Marks EntityTypes as server-side only
     *
     * @param profession VillagerProfession to server side
     * @param mapper object managing mapping to client compatible one
     */
    public static void registerProfession(class_3852 profession, PolymerSyncedObject<class_3852> mapper) {
        PolymerSyncedObject.setSyncedObject(class_7923.field_41195, profession, mapper);
    }

    @Nullable
    public static PolymerSyncedObject<class_3852> getPolymerProfession(class_3852 profession) {
        return PolymerSyncedObject.getSyncedObject(class_7923.field_41195, profession);
    }

    /**
     * Checks if EntityType is server-side only
     *
     * @param type EntityType
     */
    public static boolean isPolymerEntityType(class_1299<?> type) {
        return PolymerSyncedObject.getSyncedObject(class_7923.field_41177, type) != null;
    }

    public static boolean isPolymerEntityAttribute(class_6880<class_1320> type) {
        return ENTITY_ATTRIBUTES.contains(type.comp_349());
    }

    /**
     * @param type EntityType
     * @return Array of default DataTracker entries for entity type
     */
    public static class_2945.class_2946<?>[] getDefaultTrackedData(class_1299<?> type) {
        return InternalEntityHelpers.getExampleTrackedDataOfEntityType(type);
    }

    /**
     * @param type EntityType
     * @return Entity Class associated with EntityType
     */
    public static <T extends class_1297> Class<T> getEntityClass(class_1299<T> type) {
        return InternalEntityHelpers.getEntityClass(type);
    }

    /**
     * @param type EntityType
     * @return True if EntityType is LivingEntity;
     */
    public static boolean isLivingEntity(class_1299<?> type) {
        return InternalEntityHelpers.isLivingEntity(type);
    }

    /**
     * @param type EntityType
     * @return True if EntityType is MobEntity;
     */
    public static boolean isMobEntity(class_1299<?> type) {
        return InternalEntityHelpers.isMobEntity(type);
    }

    /**
     * @return Creates PlayerEntity spawn packet, that can be used by VirtualEntities
     */
    public static class_2703 createMutablePlayerListPacket(EnumSet<class_2703.class_5893> actions) {
        var packet = new class_2703(actions, List.of());
        ((ClientboundPlayerInfoUpdatePacketAccessor) packet).setEntries(new ArrayList<>());
        return packet;
    }

    public static boolean canHoldEntityContext(class_2596<?> packet) {
        return packet instanceof EntityAttachedPacket;
    }

    public static <T extends class_2596<class_2602>> T setEntityContext(T packet, class_1297 entity) {
        return EntityAttachedPacket.setIfEmpty(packet, entity);
    }

    public static <T extends class_2596<class_2602>> T forceSetEntityContext(T packet, class_1297 entity) {
        return EntityAttachedPacket.set(packet, entity);
    }

    @Nullable
    public static class_1297 getEntityContext(class_2596<?> packet) {
        return EntityAttachedPacket.get(packet);
    }

    public static void sendEntityType(class_3222 player, int entityId, class_1299<?> entityType) {
        PolymerServerProtocol.sendEntityInfo(player.field_13987, entityId, entityType);
    }

    public static void recreatePolymerEntity(class_1297 entity) {
        ((PolymerEntityProvider) entity).polymer$recreatePolymerEntity();
    }

    @ApiStatus.Experimental
    public static void setPolymerEntity(class_1297 entity, PolymerEntity polymerEntity) {
        ((PolymerEntityProvider) entity).polymer$setPolymerEntity(polymerEntity);
    }

    public static void refreshEntity(class_3222 player, class_1297 entity) {
        if (entity.method_73183() instanceof class_3218 world) {
            var tracker = ((ServerMapAccessor) world.method_14178().field_17254).polymer$getEntityTrackers().get(entity.method_5628());
            if (tracker != null) {
                tracker.method_18733(player);
                tracker.method_18736(player);
            }
        }
    }

    public static void refreshEntity(class_1297 entity) {
        if (entity.method_73183() instanceof class_3218 world) {
            var tracker = ((ServerMapAccessor) world.method_14178().field_17254).polymer$getEntityTrackers().get(entity.method_5628());
            if (tracker != null) {
                for (var player : ((TrackedEntityAccessor) tracker).getSeenBy()) {
                    ((TrackedEntityAccessor) tracker).getServerEntity().method_14302(player.method_32311());
                    ((TrackedEntityAccessor) tracker).getServerEntity().method_18760(player.method_32311());
                }
            }
        }
    }

    public static boolean isPolymerEntityInteraction(class_3222 player, class_1268 hand, class_1799 stack, class_3218 world, class_1297 entity, class_1269 actionResult) {
        var polymerEntity = PolymerEntity.get(entity);
        if (polymerEntity != null && polymerEntity.isPolymerEntityInteraction(player, hand, stack, world, actionResult)) {
            return true;
        } else if (PolymerSyncedObject.getSyncedObject(class_7923.field_41178, stack.method_7909()) instanceof PolymerItem polymerItem && polymerItem.isPolymerEntityInteraction(player, hand, stack, world, entity, actionResult)) {
            return true;
        }

        return POLYMER_ENTITY_INTERACTION_CHECK.invoke(x -> x.isPolymerEntityInteraction(player, hand, stack, world, entity, actionResult));
    }


    public static <T extends class_1297> void registerOverlay(class_1299<T> type, it.unimi.dsi.fastutil.Function<T, PolymerEntity> constructor) {
        registerOverlay(type, (Function<T, PolymerEntity>) constructor);
    }

    public static <T extends class_1297> void registerOverlay(class_1299<T> type, PolymerSyncedObject<class_1299<?>> syncedObject, it.unimi.dsi.fastutil.Function<T, PolymerEntity> constructor) {
        registerOverlay(type, syncedObject, (Function<T, PolymerEntity>) constructor);
    }


    @FunctionalInterface
    public interface PolymerEntityInteractionListener {
        boolean isPolymerEntityInteraction(class_3222 player, class_1268 hand, class_1799 stack, class_3218 world, class_1297 entity, class_1269 actionResult);
    }
}

