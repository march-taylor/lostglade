package eu.pb4.polymer.core.impl.networking;

import eu.pb4.polymer.core.api.item.PolymerItemGroupUtils;
import eu.pb4.polymer.core.api.utils.PolymerSyncUtils;
import eu.pb4.polymer.core.api.utils.PolymerSyncedObject;
import eu.pb4.polymer.core.impl.PolymerImpl;
import eu.pb4.polymer.core.impl.PolymerImplUtils;
import eu.pb4.polymer.core.impl.interfaces.PolymerBlockPosStorage;
import eu.pb4.polymer.core.impl.interfaces.PolymerIdMapper;
import eu.pb4.polymer.core.impl.interfaces.RegistryExtension;
import eu.pb4.polymer.core.impl.networking.entry.*;
import eu.pb4.polymer.core.impl.networking.payloads.*;
import eu.pb4.polymer.core.impl.networking.payloads.s2c.*;
import eu.pb4.polymer.networking.api.server.PolymerServerNetworking;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.shorts.ShortArrayList;
import org.jetbrains.annotations.ApiStatus;
import xyz.nucleoid.packettweaker.PacketContext;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import net.minecraft.class_1297;
import net.minecraft.class_1299;
import net.minecraft.class_1761;
import net.minecraft.class_2248;
import net.minecraft.class_2338;
import net.minecraft.class_2359;
import net.minecraft.class_2378;
import net.minecraft.class_2658;
import net.minecraft.class_2680;
import net.minecraft.class_2818;
import net.minecraft.class_3244;
import net.minecraft.class_4076;
import net.minecraft.class_7923;
import net.minecraft.class_8710;

@ApiStatus.Internal
public class PolymerServerProtocol {
    public static void sendBlockUpdate(class_3244 player, class_2338 pos, class_2680 state) {
        var version = PolymerServerNetworking.getSupportedVersion(player, S2CPackets.WORLD_SET_BLOCK_UPDATE);

        if (PolymerImplUtils.POLYMER_STATES.contains(state) && version > -1) {
            player.method_14364(new class_2658(new PolymerBlockUpdateS2CPayload(pos, class_2248.field_10651.method_10206(state))));
        }
    }

    public static void sendMultiBlockUpdate(class_3244 player, class_4076 chunkPos, short[] positions, class_2680[] blockStates) {
        var version = PolymerServerNetworking.getSupportedVersion(player, S2CPackets.WORLD_CHUNK_SECTION_UPDATE);

        if (version > -1) {
            var blocks = new IntArrayList();
            var pos = new ShortArrayList();

            for (int i = 0; i < blockStates.length; i++) {
                if (PolymerImplUtils.POLYMER_STATES.contains(blockStates[i])) {
                    blocks.add(class_2248.field_10651.method_10206(blockStates[i]));
                    pos.add(positions[i]);
                }
            }

            if (!blocks.isEmpty()) {
                player.method_14364(new class_2658(new PolymerSectionUpdateS2CPayload(chunkPos, pos.toShortArray(), blocks.toIntArray())));
            }
        }
    }

    public static void sendSectionUpdate(class_3244 player, class_2818 chunk) {
        var version = PolymerServerNetworking.getSupportedVersion(player, S2CPackets.WORLD_CHUNK_SECTION_UPDATE);

        if (version > -1) {
            var wci = (PolymerBlockPosStorage) chunk;
            if (wci.polymer$hasAny()) {
                var sections = chunk.method_12006();
                for (var i = 0; i < sections.length; i++) {
                    var section = sections[i];
                    var storage = (PolymerBlockPosStorage) section;

                    if (section != null && storage.polymer$hasAny()) {
                        var set = storage.polymer$getBackendSet();
                        var blocks = new IntArrayList();


                        assert set != null;
                        for (var pos : set) {
                            int x = class_4076.method_30551(pos);
                            int y = class_4076.method_30552(pos);
                            int z = class_4076.method_30553(pos);
                            var state = section.method_12254(x, y, z);
                            blocks.add(class_2248.field_10651.method_10206(state));
                        }

                        player.method_14364(new class_2658(new PolymerSectionUpdateS2CPayload(class_4076.method_18681(chunk.method_12004(), chunk.method_31604(i)),
                                set.toShortArray(), blocks.toIntArray())));
                    }
                }
            }
        }
    }


    public static void sendSyncPackets(class_3244 handler, boolean fullSync) {
        if (PolymerServerNetworking.getSupportedVersion(handler, S2CPackets.SYNC_STARTED) == -1) {
            return;
        }

        var startTime = System.nanoTime();
        int version;

        handler.method_14364(new class_2658(new PolymerSyncStartedS2CPayload()));
        PolymerSyncUtils.ON_SYNC_STARTED.invoke((c) -> c.accept(handler));

        version = PolymerServerNetworking.getSupportedVersion(handler, S2CPackets.SYNC_CLEAR);
        if (version != -1) {
            handler.method_14364(new class_2658(new PolymerSyncClearS2CPayload()));
        }

        PolymerSyncUtils.BEFORE_ITEM_SYNC.invoke((listener) -> listener.accept(handler, fullSync));
        sendSync(handler, S2CPackets.SYNC_ITEM_ID, getServerSideEntries(class_7923.field_41178), false, PolymerItemEntry::of);
        PolymerSyncUtils.AFTER_ITEM_SYNC.invoke((listener) -> listener.accept(handler, fullSync));

        if (fullSync) {
            PolymerSyncUtils.BEFORE_ITEM_GROUP_SYNC.invoke((listener) -> listener.accept(handler, true));

            sendCreativeSyncPackets(handler);

            PolymerSyncUtils.AFTER_ITEM_GROUP_SYNC.invoke((listener) -> listener.accept(handler, true));
        }

        PolymerSyncUtils.BEFORE_BLOCK_SYNC.invoke((listener) -> listener.accept(handler, fullSync));
        sendSync(handler, S2CPackets.SYNC_BLOCK_ID, getServerSideEntries(class_7923.field_41175), false, PolymerBlockEntry::of);
        PolymerSyncUtils.AFTER_BLOCK_SYNC.invoke((listener) -> listener.accept(handler, fullSync));

        PolymerSyncUtils.BEFORE_BLOCK_STATE_SYNC.invoke((listener) -> listener.accept(handler, fullSync));
        sendSync(handler, S2CPackets.SYNC_BLOCKSTATE_ID, getServerSideEntries(class_2248.field_10651), false, PolymerBlockStateEntry::of);
        PolymerSyncUtils.AFTER_BLOCK_STATE_SYNC.invoke((listener) -> listener.accept(handler, fullSync));


        PolymerSyncUtils.BEFORE_ENTITY_SYNC.invoke((listener) -> listener.accept(handler, fullSync));
        sendSync(handler, S2CPackets.SYNC_ENTITY_ID, getServerSideEntries(class_7923.field_41177), false, PolymerEntityEntry::of);
        PolymerSyncUtils.AFTER_ENTITY_SYNC.invoke((listener) -> listener.accept(handler, fullSync));


        sendSync(handler, S2CPackets.SYNC_VILLAGER_PROFESSION_ID, class_7923.field_41195);
        sendSync(handler, S2CPackets.SYNC_STATUS_EFFECT_ID, class_7923.field_41174);
        sendSync(handler, S2CPackets.SYNC_BLOCK_ENTITY_ID, class_7923.field_41181);
        sendSync(handler, S2CPackets.SYNC_FLUID_ID, class_7923.field_41173);
        sendSync(handler, S2CPackets.SYNC_DATA_COMPONENT_TYPE_ID, class_7923.field_49658);
        sendSync(handler, S2CPackets.SYNC_ENCHANTMENT_COMPONENT_TYPE_ID, class_7923.field_51832);

        if (fullSync) {
            sendSync(handler, S2CPackets.SYNC_TAGS_ID, (class_2378<class_2378<Object>>) class_7923.field_41167, true, PolymerTagEntry::of);
        }

        PolymerSyncUtils.ON_SYNC_CUSTOM.invoke((c) -> c.accept(handler, fullSync));

        PolymerSyncUtils.ON_SYNC_FINISHED.invoke((c) -> c.accept(handler));

        handler.method_14364(new class_2658(new PolymerSyncFinishedS2CPayload()));


        if (PolymerImpl.LOG_SYNC_TIME) {
            PolymerImpl.LOGGER.info((fullSync ? "Full" : "Partial") + " sync for {} took {} ms", handler.field_14140.method_7334().name(), ((System.nanoTime() - startTime) / 10000) / 100d);
        }
    }

    private static <T> Collection<T> getServerSideEntries(class_2359<T> registry) {
        if (registry instanceof class_2378<T> registry1) {
            return RegistryExtension.getPolymerEntries(registry1);
        } else if (registry instanceof PolymerIdMapper<?>) {
            return ((PolymerIdMapper<T>) registry).polymer$getPolymerEntries();
        }

        return List.of();
    }

    private static void sendSync(class_3244 handler, class_8710.class_9154<PolymerGenericListPayload<IdValueEntry>> packetId, class_2378 registry) {
        sendSync(handler, packetId, getServerSideEntries(registry), false,
                type -> new IdValueEntry(registry.method_10206(type), registry.method_10221(type)));
    }

    public static void sendCreativeSyncPackets(class_3244 handler) {
        var version = PolymerServerNetworking.getSupportedVersion(handler, S2CPackets.SYNC_ITEM_GROUP_DEFINE);

        if (version != -1) {
            for (var group : PolymerItemGroupUtils.getItemGroups(handler.method_32311())) {
                syncItemGroup(group, handler);
            }

            handler.method_14364(new class_2658(new PolymerItemGroupApplyUpdateS2CPayload()));
        }
    }

    public static void syncItemGroup(class_1761 group, class_3244 handler) {
        if (PolymerImpl.SYNC_MODDED_ENTRIES_POLYMC || PolymerItemGroupUtils.isPolymerItemGroup(group)) {
            removeItemGroup(group, handler);
            syncItemGroupDefinition(group, handler);
        }

        syncItemGroupContents(group, handler);
    }

    public static void syncItemGroupContents(class_1761 group, class_3244 handler) {
        var version = PolymerServerNetworking.getSupportedVersion(handler, S2CPackets.SYNC_ITEM_GROUP_CONTENTS_ADD);

        if (version != -1) {
            var id = PolymerItemGroupUtils.getId(group);
            if (id == null) {
                return;
            }
            handler.method_14364(new class_2658(new PolymerItemGroupContentClearS2CPayload(id)));

            try {
                var entry = PolymerItemGroupContentAddS2CPayload.of(version, group, handler);
                if (entry.isNonEmpty()) {
                    handler.method_14364(new class_2658(entry));
                }
            } catch (Exception e) {

            }
        }

    }

    public static void syncItemGroupDefinition(class_1761 group, class_3244 handler) {
        var version = PolymerServerNetworking.getSupportedVersion(handler, S2CPackets.SYNC_ITEM_GROUP_DEFINE);

        if (version > -1 && (PolymerImpl.SYNC_MODDED_ENTRIES_POLYMC || PolymerItemGroupUtils.isPolymerItemGroup(group))) {
            var id = PolymerItemGroupUtils.getId(group);
            if (id != null) {
                handler.method_14364(new class_2658(new PolymerItemGroupDefineS2CPayload(id, group.method_7737(), group.method_7747())));
            }
        }
    }

    public static void removeItemGroup(class_1761 group, class_3244 player) {
        var version = PolymerServerNetworking.getSupportedVersion(player, S2CPackets.SYNC_ITEM_GROUP_REMOVE);

        if (version > -1 && PolymerItemGroupUtils.isPolymerItemGroup(group)) {
            var x = PolymerItemGroupUtils.REGISTRY.getEntryId(group);
            if (x != null) {
                player.method_14364(new class_2658(new PolymerItemGroupRemoveS2CPayload(x)));
            }
        }
    }

    public static void sendEntityInfo(class_3244 player, class_1297 entity) {
        sendEntityInfo(player, entity.method_5628(), entity.method_5864());
    }

    public static void sendEntityInfo(class_3244 player, int id, class_1299<?> type) {
        var version = PolymerServerNetworking.getSupportedVersion(player, S2CPackets.WORLD_ENTITY);

        if (version != -1) {
            player.method_14364(new class_2658(new PolymerEntityS2CPayload(id, class_7923.field_41177.method_10221(type))));
        }
    }


    private static <T> void sendSync(class_3244 handler, class_8710.class_9154<PolymerGenericListPayload<T>> id, List<T> entries) {
        handler.method_14364(new class_2658(new PolymerGenericListPayload<>(id, List.copyOf(entries))));
        entries.clear();
    }

    private static <T, A> void sendSync(class_3244 handler, class_8710.class_9154<PolymerGenericListPayload<A>> packetId, Iterable<T> iterable, boolean bypassPolymerCheck, Function<T, A> writableFunction) {
        sendSync(handler, packetId, iterable, bypassPolymerCheck, (a, b, c) -> writableFunction.apply(a));
    }

    private static <T, A> void sendSync(class_3244 handler, class_8710.class_9154<PolymerGenericListPayload<A>> packetId, Iterable<T> iterable, boolean bypassPolymerCheck, BufferWritableCreator<T, A> writableFunction) {
        var version = PolymerServerNetworking.getSupportedVersion(handler, packetId.comp_2242());

        class_2378<T> registry = null;

        if (iterable instanceof RegistryExtension && !bypassPolymerCheck) {
            iterable = ((RegistryExtension<T>) iterable).polymer$getEntries();
            registry = (class_2378<T>) iterable;
        }

        if (version != -1) {
            var entries = new ArrayList<A>();
            var ctx = PacketContext.create(handler);
            for (var entry : iterable) {
                if (!bypassPolymerCheck || PolymerSyncedObject.canSynchronizeToPolymerClient(registry, entry, ctx)) {
                    var val = writableFunction.serialize(entry, handler, version);
                    if (val != null) {
                        entries.add(val);
                    }

                    if (entries.size() > 100) {
                        sendSync(handler, packetId, entries);
                    }
                }
            }

            if (!entries.isEmpty()) {
                sendSync(handler, packetId, entries);
            }
        }
    }

    public static void sendDebugValidateStatesPackets(class_3244 handler) {
        var version = PolymerServerNetworking.getSupportedVersion(handler, S2CPackets.DEBUG_VALIDATE_STATES);

        if (version != -1) {
            sendSync(handler, S2CPackets.DEBUG_VALIDATE_STATES_ID, class_2248.field_10651, true, DebugBlockStateEntry::of);
        }
    }

    public interface BufferWritableCreator<T, A> {
        A serialize(T object, class_3244 handler, int version);
    }
}
