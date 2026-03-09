package eu.pb4.polymer.core.impl.client;

import eu.pb4.polymer.common.api.PolymerCommonUtils;
import eu.pb4.polymer.common.api.events.SimpleEvent;
import eu.pb4.polymer.common.impl.CommonImpl;
import eu.pb4.polymer.common.impl.CompatStatus;
import eu.pb4.polymer.core.api.block.PolymerBlockUtils;
import eu.pb4.polymer.core.api.client.*;
import eu.pb4.polymer.core.api.utils.PolymerClientDecoded;
import eu.pb4.polymer.core.api.utils.PolymerRegistry;
import eu.pb4.polymer.core.impl.PolymerImpl;
import eu.pb4.polymer.core.impl.client.debug.LookingAtPolymerBlockDebugHudEntry;
import eu.pb4.polymer.core.impl.client.debug.LookingAtPolymerEntityDebugHudEntry;
import eu.pb4.polymer.core.impl.client.debug.PolymerInfoDebugHudEntry;
import eu.pb4.polymer.core.impl.client.interfaces.ClientBlockStorageInterface;
import eu.pb4.polymer.core.impl.client.interfaces.ClientCreativeModeTabExtension;
import eu.pb4.polymer.core.impl.interfaces.IndexedNetwork;
import eu.pb4.polymer.core.impl.interfaces.PolymerIdMapper;
import eu.pb4.polymer.core.impl.other.DelayedAction;
import eu.pb4.polymer.core.impl.other.EventRunners;
import eu.pb4.polymer.core.impl.other.FixedIdList;
import eu.pb4.polymer.core.impl.other.ImplPolymerRegistry;
import eu.pb4.polymer.core.mixin.client.CreativeModeInventoryScreenAccessor;
import eu.pb4.polymer.core.mixin.other.CreativeModeTabsAccessor;
import eu.pb4.polymer.networking.api.client.PolymerClientNetworking;

import it.unimi.dsi.fastutil.objects.*;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.class_11631;
import net.minecraft.class_1291;
import net.minecraft.class_1299;
import net.minecraft.class_155;
import net.minecraft.class_1761;
import net.minecraft.class_1792;
import net.minecraft.class_1799;
import net.minecraft.class_2248;
import net.minecraft.class_2338;
import net.minecraft.class_2359;
import net.minecraft.class_2378;
import net.minecraft.class_2497;
import net.minecraft.class_2561;
import net.minecraft.class_2591;
import net.minecraft.class_2680;
import net.minecraft.class_2806;
import net.minecraft.class_2960;
import net.minecraft.class_310;
import net.minecraft.class_3611;
import net.minecraft.class_3852;
import net.minecraft.class_3917;
import net.minecraft.class_4076;
import net.minecraft.class_481;
import net.minecraft.class_6563;
import net.minecraft.class_7706;
import net.minecraft.class_7923;
import net.minecraft.class_9331;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;
import xyz.nucleoid.packettweaker.PacketContext;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@ApiStatus.Internal
@Environment(EnvType.CLIENT)
public class InternalClientRegistry {
    public static final SimpleEvent<Runnable> TICK = new SimpleEvent<>();
    public static final Object2IntMap<String> CLIENT_PROTOCOL = new Object2IntOpenHashMap<>();
    public static final ImplPolymerRegistry<ClientPolymerBlock> BLOCKS = new ImplPolymerRegistry<>("block", "B", ClientPolymerBlock.NONE.identifier(), ClientPolymerBlock.NONE);
    public static final FixedIdList<ClientPolymerBlock.State> BLOCK_STATES = new FixedIdList<>();
    public static class_6563<ClientPolymerBlock.State> blockStatesPaletteProvider = class_6563.method_74162(BLOCK_STATES);
    public static final ImplPolymerRegistry<ClientPolymerItem> ITEMS = new ImplPolymerRegistry<>("item", "I");
    public static final ImplPolymerRegistry<ClientPolymerEntityType> ENTITY_TYPES = new ImplPolymerRegistry<>("entity_type", "E");
    public static final ImplPolymerRegistry<ClientPolymerEntry<class_3852>> VILLAGER_PROFESSIONS = new ImplPolymerRegistry<>("villager_profession", "VP");
    public static final ImplPolymerRegistry<ClientPolymerEntry<class_2591<?>>> BLOCK_ENTITY = new ImplPolymerRegistry<>("block_entity", "BE");
    public static final ImplPolymerRegistry<ClientPolymerEntry<class_1291>> STATUS_EFFECT = new ImplPolymerRegistry<>("status_effect", "SE");
    public static final ImplPolymerRegistry<ClientPolymerEntry<class_3611>> FLUID = new ImplPolymerRegistry<>("fluid", "FL");
    public static final ImplPolymerRegistry<ClientPolymerEntry<class_3917<?>>> SCREEN_HANDLER = new ImplPolymerRegistry<>("screen_handler", "SH");
    public static final ImplPolymerRegistry<ClientPolymerEntry<class_9331<?>>> DATA_COMPONENT_TYPE = new ImplPolymerRegistry<>("data_component_type", "DC");
    public static final ImplPolymerRegistry<ClientPolymerEntry<class_9331<?>>> ENCHANTMENT_COMPONENT_TYPE = new ImplPolymerRegistry<>("enchantment_component_type", "EC");
    public static final ImplPolymerRegistry<InternalClientItemGroup> ITEM_GROUPS = new ImplPolymerRegistry<>("item_groups", "IG");
    public static final List<ImplPolymerRegistry<?>> REGISTRIES = List.of(ITEMS, BLOCKS, BLOCK_ENTITY, ENTITY_TYPES, STATUS_EFFECT, VILLAGER_PROFESSIONS, FLUID, SCREEN_HANDLER, ITEM_GROUPS, DATA_COMPONENT_TYPE, ENCHANTMENT_COMPONENT_TYPE);
    public static final Map<class_2378<?>, ImplPolymerRegistry<ClientPolymerEntry<?>>> BY_VANILLA = createRegMap();
    public static final Map<class_2960, ImplPolymerRegistry<ClientPolymerEntry<?>>> BY_VANILLA_ID = createRegMapId(BY_VANILLA);
    private static final Object2ObjectMap<String, DelayedAction> DELAYED_ACTIONS = new Object2ObjectArrayMap<>();
    private static final Map<ClientPolymerItem, VirtualClientItem> VIRTUAL_ITEM_CACHE = new Object2ObjectOpenHashMap<>();
    public static boolean enabled = false;
    public static int syncRequests = 0;
    public static int syncRequestsPostGameJoin = 0;
    public static String serverVersion = "";
    public static String debugRegistryInfo = "";
    public static String debugServerInfo = "";
    public static boolean serverHasPolymer;
    public static boolean limitedF3;

    private static Map<class_2378<?>, ImplPolymerRegistry<ClientPolymerEntry<?>>> createRegMap() {
        var map = new HashMap<class_2378<?>, ImplPolymerRegistry<?>>();
        map.put(class_7923.field_41175, BLOCKS);
        map.put(class_7923.field_41177, ENTITY_TYPES);
        map.put(class_7923.field_41178, ITEMS);
        map.put(class_7923.field_41174, STATUS_EFFECT);
        map.put(class_7923.field_41195, VILLAGER_PROFESSIONS);
        map.put(class_7923.field_41181, BLOCK_ENTITY);
        map.put(class_7923.field_41173, FLUID);
        map.put(class_7923.field_49658, DATA_COMPONENT_TYPE);
        map.put(class_7923.field_51832, ENCHANTMENT_COMPONENT_TYPE);
        return (Map<class_2378<?>, ImplPolymerRegistry<ClientPolymerEntry<?>>>) (Object) map;
    }

    private static Map<class_2960, ImplPolymerRegistry<ClientPolymerEntry<?>>> createRegMapId(Map<class_2378<?>, ImplPolymerRegistry<ClientPolymerEntry<?>>> byVanilla) {
        return byVanilla.entrySet().stream().map(x -> Map.entry(x.getKey().method_46765().method_29177(), x.getValue())).collect(Collectors.toMap(x -> x.getKey(), x -> x.getValue()));
    }

    public static ClientPolymerBlock.State getBlockAt(class_2338 pos) {
        try {
            if (class_310.method_1551().field_1687 != null) {
                var chunk = class_310.method_1551().field_1687.method_2935().method_2857(
                        class_4076.method_18675(pos.method_10263()), class_4076.method_18675(pos.method_10260()),
                        class_2806.field_12803,
                        true
                );

                return ((ClientBlockStorageInterface) chunk).polymer$getClientBlock(pos.method_10263(), pos.method_10264(), pos.method_10260());
            }
        } catch (Throwable e) {}


        return ClientPolymerBlock.NONE_STATE;
    }

    public static void setBlockAt(class_2338 pos, ClientPolymerBlock.State state) {
        if (class_310.method_1551().field_1687 != null) {
            var chunk = class_310.method_1551().field_1687.method_2935().method_2857(
                    class_4076.method_18675(pos.method_10263()), class_4076.method_18675(pos.method_10260()),
                    class_2806.field_12803,
                    true
            );

            if (chunk != null) {
                ((ClientBlockStorageInterface) chunk).polymer$setClientBlock(pos.method_10263(), pos.method_10264(), pos.method_10260(), state);
            }
        }
    }

    public static void setVersion(String version, @Nullable class_2497 protocolVersion) {
        serverVersion = version;
        serverHasPolymer = !version.isEmpty();
        enabled = serverHasPolymer && protocolVersion != null && protocolVersion.method_10701() == class_155.method_31372();
    }

    public static void disable() {
        setVersion("", null);
        clear();
        DELAYED_ACTIONS.clear();
        CLIENT_PROTOCOL.clear();
        syncRequests = 0;
        syncRequestsPostGameJoin = 0;
        PolymerClientUtils.ON_DISABLE.invoke(Runnable::run);
    }

    @Nullable
    public static class_2680 getRealBlockState(int rawPolymerId) {
        var state = InternalClientRegistry.BLOCK_STATES.method_10200(rawPolymerId);
        if (state != null && state.blockState() != null) {
            if (PolymerClientDecoded.checkDecode(state.blockState().method_26204())) {
                return state.blockState();
            } else {
                return PolymerBlockUtils.getPolymerBlockState(state.blockState(), PacketContext.create());
            }
        }

        return null;
    }

    private static void setDecoders() {
        IndexedNetwork.set(class_2248.field_10651, InternalClientRegistry::getRealBlockState);
        IndexedNetwork.set(class_7923.field_41178, InternalClientRegistry::decodeItem);

        setSimpleDecoder((class_2378<class_1299>) (Object) class_7923.field_41177, (PolymerRegistry<ClientPolymerEntry<class_1299>>) (Object) ENTITY_TYPES);
        setSimpleDecoder(class_7923.field_41175, (PolymerRegistry<ClientPolymerEntry<class_2248>>) (Object) BLOCKS);

        setSimpleDecoder(class_7923.field_41195, VILLAGER_PROFESSIONS);
        setSimpleDecoder(class_7923.field_41174, STATUS_EFFECT);
        setSimpleDecoder(class_7923.field_41181, BLOCK_ENTITY);
        setSimpleDecoder(class_7923.field_41173, FLUID);
        setSimpleDecoder(class_7923.field_41187, SCREEN_HANDLER);
        setSimpleDecoder(class_7923.field_49658, DATA_COMPONENT_TYPE);
        setSimpleDecoder(class_7923.field_51832, ENCHANTMENT_COMPONENT_TYPE);
    }


    public static Object decodeRegistry(class_2359<?> instance, int i) {
        if (serverHasPolymer) {
            return PolymerCommonUtils.executeWithNetworkingLogic(() -> instance.method_39974(i));
        }

        return instance.method_39974(i);
    }

    private static class_1792 decodeItem(int id) {
        if (InternalClientRegistry.enabled) {
            var item = InternalClientRegistry.ITEMS.method_10200(id);
            if (item != null) {
                if (item.registryEntry() != null) {
                    return item.registryEntry();
                } else if (PolymerImpl.USE_UNSAFE_ITEMS_CLIENT) {
                    return VIRTUAL_ITEM_CACHE.computeIfAbsent(item, VirtualClientItem::of);
                }
            }
        }

        return null;
    }

    private static <T> void setSimpleDecoder(final class_2359<T> registry, final PolymerRegistry<ClientPolymerEntry<T>> polymerRegistry) {
        IndexedNetwork.set(registry, (id) -> {
            if (InternalClientRegistry.enabled) {
                var item = polymerRegistry.method_10200(id);

                if (item != null && item.registryEntry() != null) {
                    return item.registryEntry();
                }
            }

            return null;
        });
    }

    public static void tick() {
        if (!enabled) {
            debugServerInfo = "[Polymer] C: " + CommonImpl.VERSION + ", S: " + InternalClientRegistry.serverVersion;
            debugRegistryInfo = "[Polymer] §cMismatched protocol versions!";
            return;
        }

        DELAYED_ACTIONS.object2ObjectEntrySet().removeIf(stringDelayedActionEntry -> stringDelayedActionEntry.getValue().tryDoing());
        TICK.invoke(Runnable::run);

        debugServerInfo = "[Polymer] C: " + CommonImpl.VERSION + ", S: " + InternalClientRegistry.serverVersion;
        if (limitedF3) {
            debugRegistryInfo = "";
            return;
        }
        var regInfo = new StringBuilder();
        regInfo.append("[Polymer] ");
        for (var reg : REGISTRIES) {
            regInfo.append(reg.getShortName());
            regInfo.append(": ");
            regInfo.append(reg.method_10204());
            regInfo.append(", ");
        }

        regInfo.append("BS: ").append(InternalClientRegistry.BLOCK_STATES.mapSize());

        debugRegistryInfo = regInfo.toString();
    }

    public static void clear() {
        for (var reg : REGISTRIES) {
            reg.clear();
        }

        VIRTUAL_ITEM_CACHE.clear();

        BLOCKS.set(ClientPolymerBlock.NONE.identifier(), ClientPolymerBlock.NONE);
        ((PolymerIdMapper) BLOCK_STATES).polymer$clear();
        BLOCK_STATES.method_10203(ClientPolymerBlock.NONE_STATE, 0);
        updateBlockStatesPaletteProvider();

        class_310.method_1551().execute(() -> {
            clearTabs(i -> true);

            for (var group : class_7923.field_44687) {
                if (group.method_47312() == class_1761.class_7916.field_41052) {
                    try {
                        ((ClientCreativeModeTabExtension) group).polymer$clearStacks();
                    } catch (Throwable e) {
                        PolymerImpl.LOGGER.warn("Can't clear stacks of ItemGroup!", e);
                    }
                }
            }
            try {
                if (CreativeModeTabsAccessor.getCACHED_PARAMETERS() != null) {
                    CreativeModeTabsAccessor.callBuildAllTabContents(CreativeModeTabsAccessor.getCACHED_PARAMETERS());
                }
            } catch (Throwable e) {
                PolymerImpl.LOGGER.warn("Can't update entries of ItemGroups!", e);
            }
        });
        PolymerClientUtils.ON_CLEAR.invoke(EventRunners.RUN);
    }

    private static final int TABS_PER_PAGE = 10;

    public static void clearTabs(Predicate<InternalClientItemGroup> removePredicate) {
        try {
            ITEM_GROUPS.removeIf(removePredicate);
            CreativeModeInventoryScreenAccessor.setSelectedTab(class_7706.method_47328());

            if (CompatStatus.FABRIC_ITEM_GROUP || CompatStatus.QUILT_ITEM_GROUP) {
                try {
                    for (var f1 : class_481.class.getDeclaredFields()) {
                        if (f1.getName().contains("currentPage")) {
                            f1.setAccessible(true);
                            f1.setInt(null, 0);
                            break;
                        }
                    }
                } catch (Throwable e) {
                    if (PolymerImpl.LOG_MORE_ERRORS) {
                        PolymerImpl.LOGGER.error("Failed to change item group page (FABRIC / QUILT)!", e);
                    }
                }
            }

            int count = class_7923.field_44687.method_10204() - 4;
            for (var x : ITEM_GROUPS) {
                var page = (count / TABS_PER_PAGE);
                int pageIndex = count % TABS_PER_PAGE;
                class_1761.class_7915 row = pageIndex < (TABS_PER_PAGE / 2) ? class_1761.class_7915.field_41049 : class_1761.class_7915.field_41050;
                var c = row == class_1761.class_7915.field_41049 ? pageIndex % TABS_PER_PAGE : (pageIndex - TABS_PER_PAGE / 2) % (TABS_PER_PAGE);
                ((ClientCreativeModeTabExtension) x).polymerCore$setPos(row, c);
                setItemGroupPage(x, page);
                count++;
            }
        } catch (Throwable e) {

        }
    }

    private static void setItemGroupPage(class_1761 group, int page) {
        ((ClientCreativeModeTabExtension) group).polymerCore$setPage(page);
        if (CompatStatus.FABRIC_ITEM_GROUP) {
            try {
                ((net.fabricmc.fabric.impl.itemgroup.FabricItemGroupImpl) group).fabric_setPage(page);
            } catch (Throwable e) {
                PolymerImpl.LOGGER.warn("Couldn't set page of ItemGroup (FABRIC)", e);
            }
        }
    }

    public static void createItemGroup(class_2960 id, class_2561 name, class_1799 icon) {
        try {
            var existing = class_7923.field_44687.method_63535(id);
            if (existing != null) {
                return;
            }
            int count = (class_7923.field_44687.method_10204() - 4) + ITEM_GROUPS.method_10204();

            var page = (count / TABS_PER_PAGE);
            int pageIndex = count % TABS_PER_PAGE;
            class_1761.class_7915 row = pageIndex < (TABS_PER_PAGE / 2) ? class_1761.class_7915.field_41049 : class_1761.class_7915.field_41050;
            var c = row == class_1761.class_7915.field_41049 ? pageIndex % TABS_PER_PAGE : (pageIndex - TABS_PER_PAGE / 2) % (TABS_PER_PAGE);

            var group = new InternalClientItemGroup(row, c, id, name, icon);
            ITEM_GROUPS.set(id, group);

            setItemGroupPage(group, page);
        } catch(Throwable e) {

        }
    }

    public static class_1761 getItemGroup(class_2960 id) {
        var x = ITEM_GROUPS.get(id);
        if (x != null) {
            return x;
        }
        return class_7923.field_44687.method_63535(id);
    }

    public static int getClientProtocolVer(class_2960 identifier) {
        return PolymerClientNetworking.getSupportedVersion(identifier);
    }

    public static void delayAction(String id, int time, Runnable action) {
        if (enabled) {
            DELAYED_ACTIONS.put(id, new DelayedAction(id, time, action));
        }
    }

    static {
        setDecoders();
    }

    public static void register() {
        class_11631.method_72763(class_2960.method_60655("polymer", "looking_at_server_block"), new LookingAtPolymerBlockDebugHudEntry());
        class_11631.method_72763(class_2960.method_60655("polymer", "looking_at_server_entity"), new LookingAtPolymerEntityDebugHudEntry());
        class_11631.method_72763(class_2960.method_60655("polymer", "server_info"), new PolymerInfoDebugHudEntry());
    }

    public static void updateBlockStatesPaletteProvider() {
        blockStatesPaletteProvider = class_6563.method_74162(InternalClientRegistry.BLOCK_STATES);
    }
}
