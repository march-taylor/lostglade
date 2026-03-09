package eu.pb4.polymer.core.impl.networking;


import eu.pb4.polymer.core.impl.networking.entry.*;
import eu.pb4.polymer.core.impl.networking.payloads.*;
import eu.pb4.polymer.core.impl.networking.payloads.s2c.*;
import eu.pb4.polymer.networking.api.ContextByteBuf;
import eu.pb4.polymer.networking.api.PolymerNetworking;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.ApiStatus;

import java.util.function.Supplier;
import net.minecraft.class_2960;
import net.minecraft.class_8710;
import net.minecraft.class_9139;

import static eu.pb4.polymer.core.impl.PolymerImplUtils.id;

@ApiStatus.Internal
public class S2CPackets {
    public static final class_2960 SYNC_STARTED = id("sync/started");
    public static final class_2960 SYNC_FINISHED = id("sync/finished");
    public static final class_2960 SYNC_BLOCK = id("sync/blocks");
    public static final class_2960 SYNC_BLOCK_ENTITY = id("sync/block_entities");
    public static final class_2960 SYNC_ITEM = id("sync/items");
    public static final class_2960 SYNC_FLUID = id("sync/fluid");
    public static final class_2960 SYNC_ENTITY = id("sync/entities");
    public static final class_2960 SYNC_STATUS_EFFECT = id("sync/status_effect");
    public static final class_2960 SYNC_DATA_COMPONENT_TYPE = id("sync/data_component_type");
    public static final class_2960 SYNC_ENCHANTMENT_COMPONENT_TYPE = id("sync/enchantment_component_type");
    public static final class_2960 SYNC_VILLAGER_PROFESSION = id("sync/villager_profession");
    public static final class_2960 SYNC_ITEM_GROUP_DEFINE = id("sync/item_group/define");
    public static final class_2960 SYNC_ITEM_GROUP_REMOVE = id("sync/item_group/remove");
    public static final class_2960 SYNC_ITEM_GROUP_CONTENTS_ADD = id("sync/item_group/contents/add");
    public static final class_2960 SYNC_ITEM_GROUP_CONTENTS_CLEAR = id("sync/item_group/contents/clear");
    public static final class_2960 SYNC_BLOCKSTATE = id("sync/blockstate");
    public static final class_2960 SYNC_TAGS = id("sync/tags");
    public static final class_2960 SYNC_ITEM_GROUP_APPLY_UPDATE = id("sync/item_group/apply_update");
    public static final class_2960 SYNC_CLEAR = id("sync/clear_all");
    public static final class_2960 WORLD_SET_BLOCK_UPDATE = id("world/set_block");
    public static final class_2960 WORLD_CHUNK_SECTION_UPDATE = id("world/section");
    public static final class_2960 WORLD_ENTITY = id("world/entity");

    public static final class_2960 DEBUG_VALIDATE_STATES = id("debug/validate_states");

    public static <T extends class_8710> void register(class_2960 id, class_9139<ContextByteBuf, T> codec, int... ver) {
        PolymerNetworking.registerS2CVersioned(id, IntList.of(ver), PayloadUtil.protocolSecured(codec));
    }

    public static <T extends class_8710> void register(class_2960 id, Supplier<T> t, int... ver) {
        PolymerNetworking.registerS2CVersioned(id, IntList.of(ver), class_9139.method_56431(t.get()));
    }

    public static <T> class_8710.class_9154<PolymerGenericListPayload<T>> registerList(class_2960 id, class_9139<ContextByteBuf, T> entry, int... ver) {
        var ide = new class_8710.class_9154<PolymerGenericListPayload<T>>(id);
        PolymerNetworking.registerS2CVersioned(ide, IntList.of(ver), PayloadUtil.protocolSecured(PolymerGenericListPayload.codec(ide, entry)));
        return ide;
    }

    public static final class_8710.class_9154<PolymerGenericListPayload<PolymerBlockEntry>> SYNC_BLOCK_ID;
    public static final class_8710.class_9154<PolymerGenericListPayload<PolymerBlockStateEntry>> SYNC_BLOCKSTATE_ID;
    public static final class_8710.class_9154<PolymerGenericListPayload<PolymerItemEntry>> SYNC_ITEM_ID;
    public static final class_8710.class_9154<PolymerGenericListPayload<PolymerEntityEntry>> SYNC_ENTITY_ID;
    public static final class_8710.class_9154<PolymerGenericListPayload<PolymerTagEntry>> SYNC_TAGS_ID;
    public static final class_8710.class_9154<PolymerGenericListPayload<DebugBlockStateEntry>> DEBUG_VALIDATE_STATES_ID;
    public static final class_8710.class_9154<PolymerGenericListPayload<IdValueEntry>> SYNC_FLUID_ID;
    public static final class_8710.class_9154<PolymerGenericListPayload<IdValueEntry>> SYNC_VILLAGER_PROFESSION_ID;
    public static final class_8710.class_9154<PolymerGenericListPayload<IdValueEntry>> SYNC_DATA_COMPONENT_TYPE_ID;
    public static final class_8710.class_9154<PolymerGenericListPayload<IdValueEntry>> SYNC_ENCHANTMENT_COMPONENT_TYPE_ID;
    public static final class_8710.class_9154<PolymerGenericListPayload<IdValueEntry>> SYNC_BLOCK_ENTITY_ID;
    public static final class_8710.class_9154<PolymerGenericListPayload<IdValueEntry>> SYNC_STATUS_EFFECT_ID;

    static {
        register(SYNC_STARTED, PolymerSyncStartedS2CPayload::new, 6);
        register(SYNC_FINISHED, PolymerSyncFinishedS2CPayload::new, 6);
        register(SYNC_CLEAR, PolymerSyncClearS2CPayload::new, 6);

        int baseVersion = 13;

        SYNC_BLOCK_ID = registerList(SYNC_BLOCK, PolymerBlockEntry.CODEC, baseVersion);
        SYNC_BLOCKSTATE_ID = registerList(SYNC_BLOCKSTATE, PolymerBlockStateEntry.CODEC, baseVersion);
        SYNC_ITEM_ID = registerList(SYNC_ITEM, PolymerItemEntry.CODEC, baseVersion);
        SYNC_ENTITY_ID = registerList(SYNC_ENTITY, PolymerEntityEntry.CODEC,baseVersion);
        SYNC_TAGS_ID = registerList(SYNC_TAGS, PolymerTagEntry.CODEC, baseVersion);
        DEBUG_VALIDATE_STATES_ID = registerList(DEBUG_VALIDATE_STATES, DebugBlockStateEntry.CODEC, baseVersion);

        SYNC_DATA_COMPONENT_TYPE_ID = registerList(SYNC_DATA_COMPONENT_TYPE, IdValueEntry.CODEC, baseVersion);
        SYNC_ENCHANTMENT_COMPONENT_TYPE_ID = registerList(SYNC_ENCHANTMENT_COMPONENT_TYPE, IdValueEntry.CODEC, baseVersion);
        SYNC_FLUID_ID = registerList(SYNC_FLUID, IdValueEntry.CODEC, baseVersion);
        SYNC_VILLAGER_PROFESSION_ID = registerList(SYNC_VILLAGER_PROFESSION, IdValueEntry.CODEC, baseVersion);
        SYNC_BLOCK_ENTITY_ID = registerList(SYNC_BLOCK_ENTITY, IdValueEntry.CODEC, baseVersion);
        SYNC_STATUS_EFFECT_ID = registerList(SYNC_STATUS_EFFECT, IdValueEntry.CODEC, baseVersion);


        register(SYNC_ITEM_GROUP_DEFINE, PolymerItemGroupDefineS2CPayload.CODEC,baseVersion);
        register(SYNC_ITEM_GROUP_CONTENTS_CLEAR, PolymerItemGroupContentClearS2CPayload.CODEC, baseVersion);
        register(SYNC_ITEM_GROUP_REMOVE, PolymerItemGroupRemoveS2CPayload.CODEC,baseVersion);
        register(SYNC_ITEM_GROUP_CONTENTS_ADD, PolymerItemGroupContentAddS2CPayload.CODEC,baseVersion);
        register(SYNC_ITEM_GROUP_APPLY_UPDATE, PolymerItemGroupApplyUpdateS2CPayload::new, baseVersion);

        register(WORLD_SET_BLOCK_UPDATE, PolymerBlockUpdateS2CPayload.CODEC,baseVersion);
        register(WORLD_CHUNK_SECTION_UPDATE, PolymerSectionUpdateS2CPayload.CODEC, baseVersion);
        register(WORLD_ENTITY, PolymerEntityS2CPayload.CODEC, baseVersion);
    }
}
