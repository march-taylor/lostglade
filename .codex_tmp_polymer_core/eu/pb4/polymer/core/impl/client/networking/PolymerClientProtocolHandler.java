package eu.pb4.polymer.core.impl.client.networking;

import com.mojang.brigadier.StringReader;
import eu.pb4.polymer.common.impl.CommonImpl;
import eu.pb4.polymer.core.api.client.*;
import eu.pb4.polymer.core.api.utils.PolymerClientDecoded;
import eu.pb4.polymer.core.impl.ClientMetadataKeys;
import eu.pb4.polymer.core.impl.PolymerImpl;
import eu.pb4.polymer.core.impl.ServerMetadataKeys;
import eu.pb4.polymer.core.impl.client.InternalClientRegistry;
import eu.pb4.polymer.core.impl.client.interfaces.ClientBlockStorageInterface;
import eu.pb4.polymer.core.impl.client.interfaces.ClientEntityExtension;
import eu.pb4.polymer.core.impl.client.interfaces.ClientCreativeModeTabExtension;
import eu.pb4.polymer.core.impl.networking.S2CPackets;
import eu.pb4.polymer.core.impl.networking.entry.*;
import eu.pb4.polymer.core.impl.networking.payloads.PolymerGenericListPayload;
import eu.pb4.polymer.core.impl.networking.payloads.PolymerNoOpPayload;
import eu.pb4.polymer.core.impl.networking.payloads.s2c.*;
import eu.pb4.polymer.core.impl.other.EventRunners;
import eu.pb4.polymer.core.impl.other.ImplPolymerRegistry;
import eu.pb4.polymer.core.mixin.other.CreativeModeTabsAccessor;
import eu.pb4.polymer.networking.api.client.PolymerClientNetworking;
import eu.pb4.polymer.networking.impl.NetImpl;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.class_155;
import net.minecraft.class_1761;
import net.minecraft.class_2248;
import net.minecraft.class_2259;
import net.minecraft.class_2338;
import net.minecraft.class_2378;
import net.minecraft.class_2481;
import net.minecraft.class_2497;
import net.minecraft.class_2540;
import net.minecraft.class_2561;
import net.minecraft.class_2680;
import net.minecraft.class_2806;
import net.minecraft.class_2960;
import net.minecraft.class_310;
import net.minecraft.class_3532;
import net.minecraft.class_4076;
import net.minecraft.class_634;
import net.minecraft.class_7922;
import net.minecraft.class_7923;
import net.minecraft.class_8673;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import static eu.pb4.polymer.networking.api.client.PolymerClientNetworking.registerCommonHandler;
import static eu.pb4.polymer.networking.api.client.PolymerClientNetworking.registerPlayHandler;

@ApiStatus.Internal
@SuppressWarnings({"unused"})
@Environment(EnvType.CLIENT)
public class PolymerClientProtocolHandler {
    public static final Map<class_2960, Consumer<?>> GENERIC_LIST_HANDLERS = new HashMap<>();
    private static long syncStarted = -1;

    public static void register() {
        registerCommonHandler(PolymerNoOpPayload.class, (client, handler, packet) -> {});
        registerPlayHandler(PolymerBlockUpdateS2CPayload.class, PolymerClientProtocolHandler::handleSetBlock);
        registerPlayHandler(PolymerSectionUpdateS2CPayload.class, PolymerClientProtocolHandler::handleWorldSectionUpdate);
        registerPlayHandler(PolymerEntityS2CPayload.class, PolymerClientProtocolHandler::handleEntity);

        registerCommonHandler(PolymerSyncStartedS2CPayload.class, (handler, version, buf) -> {
            syncStarted = System.currentTimeMillis();
            PolymerClientUtils.ON_SYNC_STARTED.invoke(EventRunners.RUN);
        });
        registerCommonHandler(PolymerSyncFinishedS2CPayload.class, (handler, version, buf) -> {
            if (PolymerImpl.LOG_SYNC_TIME_CLIENT) {
                PolymerImpl.LOGGER.info("Polymer Sync took {} ms", System.currentTimeMillis() - syncStarted);
            }
            InternalClientRegistry.updateBlockStatesPaletteProvider();

            PolymerClientUtils.ON_SYNC_FINISHED.invoke(EventRunners.RUN);
        });

        registerCommonHandler(PolymerItemGroupDefineS2CPayload.class, PolymerClientProtocolHandler::handleItemGroupDefine);
        registerCommonHandler(PolymerItemGroupContentAddS2CPayload.class, PolymerClientProtocolHandler::handleItemGroupContentsAdd);
        registerCommonHandler(PolymerItemGroupContentClearS2CPayload.class, PolymerClientProtocolHandler::handleItemGroupContentsClear);
        registerCommonHandler(PolymerItemGroupRemoveS2CPayload.class, PolymerClientProtocolHandler::handleItemGroupRemove);
        registerCommonHandler(PolymerItemGroupApplyUpdateS2CPayload.class, PolymerClientProtocolHandler::handleItemGroupApplyUpdates);
        registerCommonHandler(PolymerSyncClearS2CPayload.class, (client, handler, payload) -> {
            InternalClientRegistry.clear();
        });

        registerCommonHandler(PolymerSyncClearS2CPayload.class, (client, handler, payload) -> {
            InternalClientRegistry.clear();
        });

        registerCommonHandler(PolymerGenericListPayload.class, PolymerClientProtocolHandler::handleGenericList);

        registerGenericListHandler(S2CPackets.SYNC_BLOCK, PolymerBlockEntry.class, (entry) -> InternalClientRegistry.BLOCKS.set(entry.identifier(), entry.numId(),
                new ClientPolymerBlock(entry.identifier(), entry.numId(), entry.hardness(), switch (entry.miningDeltaLogic()) {
                    case DEFAULT -> ClientPolymerBlock.MiningDeltaLogic.DEFAULT;
                    case VANILLA -> ClientPolymerBlock.MiningDeltaLogic.VANILLA;
                    case CUSTOM_SERVER -> ClientPolymerBlock.MiningDeltaLogic.CUSTOM_SERVER;
                    case TOOL_REQUIRED -> ClientPolymerBlock.MiningDeltaLogic.TOOL_REQUIRED;
                }, entry.text(), entry.visual(), getNonDefault(class_7923.field_41175, entry.identifier()), entry.visualStack())));
        registerGenericListHandler(S2CPackets.SYNC_ITEM, PolymerItemEntry.class, (entry) -> {

                    InternalClientRegistry.ITEMS.set(entry.identifier(), entry.numId(),
                            new ClientPolymerItem(
                                    entry.identifier(),
                                    entry.representation(),
                                    getNonDefault(class_7923.field_41178, entry.identifier())
                            ));
                });
        registerGenericListHandler(S2CPackets.SYNC_BLOCKSTATE, PolymerBlockStateEntry.class,
                (entry) -> InternalClientRegistry.BLOCK_STATES.method_10203(new ClientPolymerBlock.State(entry.properties(), InternalClientRegistry.BLOCKS.method_10200(entry.blockId()), blockStateOrNull(entry.properties(), InternalClientRegistry.BLOCKS.method_10200(entry.blockId()))), entry.numId()));

        registerGenericListHandler(S2CPackets.SYNC_ENTITY, PolymerEntityEntry.class,
                (entry) -> InternalClientRegistry.ENTITY_TYPES.set(entry.identifier(), entry.rawId(), new ClientPolymerEntityType(entry.identifier(), entry.name(), getNonDefault(class_7923.field_41177, entry.identifier()))));

        registerGenericListHandler(S2CPackets.SYNC_VILLAGER_PROFESSION, InternalClientRegistry.VILLAGER_PROFESSIONS, class_7923.field_41195);
        registerGenericListHandler(S2CPackets.SYNC_BLOCK_ENTITY, InternalClientRegistry.BLOCK_ENTITY, class_7923.field_41181);
        registerGenericListHandler(S2CPackets.SYNC_STATUS_EFFECT, InternalClientRegistry.STATUS_EFFECT, class_7923.field_41174);
        registerGenericListHandler(S2CPackets.SYNC_FLUID, InternalClientRegistry.FLUID, class_7923.field_41173);
        registerGenericListHandler(S2CPackets.SYNC_DATA_COMPONENT_TYPE, InternalClientRegistry.DATA_COMPONENT_TYPE, class_7923.field_49658);
        registerGenericListHandler(S2CPackets.SYNC_ENCHANTMENT_COMPONENT_TYPE, InternalClientRegistry.ENCHANTMENT_COMPONENT_TYPE, class_7923.field_51832);


        registerGenericListHandler(S2CPackets.SYNC_TAGS, PolymerTagEntry.class, PolymerClientProtocolHandler::registerTag);

        registerGenericListHandler(S2CPackets.DEBUG_VALIDATE_STATES, DebugBlockStateEntry.class, PolymerClientProtocolHandler::handleDebugValidateStates);



        PolymerClientNetworking.AFTER_METADATA_RECEIVED.register(() -> {
            InternalClientRegistry.setVersion(PolymerClientNetworking.getServerVersion(),
                    PolymerClientNetworking.getMetadata(ServerMetadataKeys.MINECRAFT_PROTOCOL, class_2497.field_21037));
            var limitedF3 = PolymerClientNetworking.getMetadata(ServerMetadataKeys.LIMITED_F3, class_2481.field_21025);

            InternalClientRegistry.limitedF3 = limitedF3 != null && limitedF3.method_10698() != 0;
        });

        PolymerClientNetworking.AFTER_DISABLE.register(InternalClientRegistry::disable);

        PolymerClientNetworking.BEFORE_METADATA_SYNC.register(() -> {
            PolymerClientNetworking.setClientMetadata(ClientMetadataKeys.ADVANCED_TOOLTIP, class_2481.method_23234(class_310.method_1551().field_1690.field_1827));
            PolymerClientNetworking.setClientMetadata(ClientMetadataKeys.BLOCKSTATE_BITS, class_2497.method_23247(class_3532.method_15342(class_2248.field_10651.method_10204())));
            PolymerClientNetworking.setClientMetadata(ClientMetadataKeys.MINECRAFT_PROTOCOL, class_2497.method_23247(class_155.method_31372()));
        });
    }

    private static <T> T getNonDefault(class_7922<T> registry, class_2960 identifier) {
        return registry.method_10250(identifier) ? registry.method_63535(identifier) : null;
    }

    private static <T> void registerGenericListHandler(class_2960 id, Class<T> targetClass, Consumer<T> consumer) {
        GENERIC_LIST_HANDLERS.put(id, consumer);
    }

    private static <T> void registerGenericListHandler(class_2960 id, ImplPolymerRegistry<ClientPolymerEntry<T>> polymerRegistry, class_2378<T> vanillaRegistry) {
        registerGenericListHandler(id, IdValueEntry.class, (entry) -> polymerRegistry.set(entry.id(), entry.rawId(), ClientPolymerEntry.of(entry.id(), vanillaRegistry)));
    }

    private static void registerTag(PolymerTagEntry tagEntry) {
        var reg = InternalClientRegistry.BY_VANILLA_ID.get(tagEntry.registry());
        if (reg != null) {
            for (var tag : tagEntry.tags()) {
                reg.createTag(tag.id(), tag.ids());
            }
        }
    }

    private static void handleDebugValidateStates(DebugBlockStateEntry entry) {
        if (CommonImpl.DEVELOPER_MODE) {
            var chat = class_310.method_1551().field_1705.method_1743();

            var state = class_2248.field_10651.method_10200(entry.numId());

            if (state == null) {
                chat.method_1812(class_2561.method_43470("Missing BlockState! | " + entry.numId() + " | Server: " + entry.asString()));
            } else {
                var debug = DebugBlockStateEntry.of(state, null, 0);

                if (!debug.equals(entry)) {
                    chat.method_1812(class_2561.method_43470("Mismatched BlockState! | " + entry.numId() + " | Server: " + entry.asString() + " | Client: " + debug.asString()));
                }
            }
        }
    }
    @Nullable
    private static class_2680 blockStateOrNull(Map<String, String> states, ClientPolymerBlock clientPolymerBlock) {
        if (clientPolymerBlock.registryEntry() != null) {
            var path = new StringBuilder(clientPolymerBlock.identifier().toString());

            if (!states.isEmpty()) {
                path.append("[");
                var iterator = states.entrySet().iterator();
                while (iterator.hasNext()) {
                    var entry = iterator.next();
                    path.append(entry.getKey()).append("=").append(entry.getValue());

                    if (iterator.hasNext()) {
                        path.append(",");
                    }
                }
                path.append("]");
            }

            try {
                var parsed = class_2259.method_41955(class_7923.field_41175, new StringReader(path.toString()), false);

                return parsed.comp_622();
            } catch (Exception e) {
                // noop
            }
        }

        return null;
    }

    private static void handleItemGroupApplyUpdates(class_310 client, class_8673 handler, PolymerItemGroupApplyUpdateS2CPayload payload) {
        if (InternalClientRegistry.enabled) {
            class_310.method_1551().execute(() -> {
                if (CreativeModeTabsAccessor.getCACHED_PARAMETERS() != null) {
                    CreativeModeTabsAccessor.callBuildAllTabContents(CreativeModeTabsAccessor.getCACHED_PARAMETERS());
                }
                PolymerClientUtils.ON_SEARCH_REBUILD.invoke(EventRunners.RUN);
            });
        }
    }

    private static void handleItemGroupDefine(class_310 client, class_8673 handler, PolymerItemGroupDefineS2CPayload payload) {
        if ( InternalClientRegistry.enabled) {
            class_310.method_1551().execute(() -> {
                InternalClientRegistry.clearTabs((t) -> t.getIdentifier().equals(payload.groupId()));
                InternalClientRegistry.createItemGroup(payload.groupId(), payload.name(), payload.icon());
            });

        }
    }

    private static void handleItemGroupRemove(class_310 client, class_8673 handler, PolymerItemGroupRemoveS2CPayload payload) {
        if (InternalClientRegistry.enabled) {
            class_310.method_1551().execute(() -> {
                InternalClientRegistry.clearTabs((x) -> x.getIdentifier().equals(payload.groupId()));
            });
        }

    }

    private static void handleItemGroupContentsAdd(class_310 client, class_8673 handler, PolymerItemGroupContentAddS2CPayload payload) {
        if (InternalClientRegistry.enabled) {
            class_310.method_1551().execute(() -> {
                class_1761 group = InternalClientRegistry.getItemGroup(payload.groupId());

                if (group != null) {
                    var groupAccess = (ClientCreativeModeTabExtension) group;

                    groupAccess.polymer$handleEntries(payload.stacksMain(), payload.stacksSearch());
                }
            });
        }
    }

    private static void handleItemGroupContentsClear(class_310 client, class_8673 handler, PolymerItemGroupContentClearS2CPayload payload) {
        if (InternalClientRegistry.enabled) {
            class_310.method_1551().execute(() -> {
                class_1761 group = InternalClientRegistry.getItemGroup(payload.groupId());

                if (group != null) {
                    var groupAccess = (ClientCreativeModeTabExtension) group;
                    groupAccess.polymer$clearStacks();
                }

            });
        }
    }

    private static void handleEntity(class_310 client, class_634 handler, PolymerEntityS2CPayload payload) {
        if (InternalClientRegistry.enabled) {
            class_310.method_1551().execute(() -> {
                var entity = handler.method_2890().method_8469(payload.entityId());
                if (entity != null) {
                    ((ClientEntityExtension) entity).polymer$setId(payload.typeId());
                }
            });
        }
    }

    private static void handleSetBlock(class_310 client, class_634 handler, PolymerBlockUpdateS2CPayload payload) {
        if (InternalClientRegistry.enabled) {
            class_310.method_1551().execute(() -> {
                var block = InternalClientRegistry.BLOCK_STATES.method_10200(payload.blockId());
                if (block != null) {
                    var pos = payload.pos();
                    var chunk = class_310.method_1551().field_1687.method_2935().method_2857(
                            class_4076.method_18675(pos.method_10263()), class_4076.method_18675(pos.method_10260()),
                            class_2806.field_12803,
                            false
                    );

                    if (chunk != null) {
                        ((ClientBlockStorageInterface) chunk).polymer$setClientBlock(pos.method_10263(), pos.method_10264(), pos.method_10260(), block);
                        PolymerClientUtils.ON_BLOCK_UPDATE.invoke(c -> c.accept(pos, block));

                        if (block.blockState() != null && PolymerClientDecoded.checkDecode(block.blockState().method_26204())) {
                            chunk.method_66480(pos, block.blockState());
                        }
                    }
                }
            });

        }
    }

    private static void handleWorldSectionUpdate(class_310 client, class_634 handler, PolymerSectionUpdateS2CPayload payload) {
        if (InternalClientRegistry.enabled) {
            var sectionPos = payload.chunkPos();

            class_310.method_1551().execute(() -> {
                var chunk = class_310.method_1551().field_1687.method_2935().method_2857(
                        sectionPos.method_10263(), sectionPos.method_10260(),
                        class_2806.field_12803,
                        false
                );
                var blockPos = payload.pos();
                var states = payload.blocks();

                if (chunk != null) {
                    var section = chunk.method_38259(chunk.method_31603(sectionPos.method_10264()));
                    if (section instanceof ClientBlockStorageInterface storage) {
                        var mutableBlockPos = new class_2338.class_2339(0, 0, 0);
                        for (int i = 0; i < states.length; i++) {
                            var pos = blockPos[i];
                            var block = InternalClientRegistry.BLOCK_STATES.method_10200(states[i]);
                            if (block != null) {
                                var x = class_4076.method_30551(pos);
                                var y = class_4076.method_30552(pos);
                                var z = class_4076.method_30553(pos);
                                mutableBlockPos.method_10103(sectionPos.method_19527() + x, sectionPos.method_19527() + y, sectionPos.method_19527() + z);
                                PolymerClientUtils.ON_BLOCK_UPDATE.invoke(c -> c.accept(mutableBlockPos, block));
                                storage.polymer$setClientBlock(x, y, z, block);

                                if (block.blockState() != null && PolymerClientDecoded.checkDecode(block.blockState().method_26204())) {
                                    section.method_16675(x, y, z, block.blockState());
                                }
                            }
                        }
                    }
                }
            });

        }
    }


    private static <T> void handleGenericList(class_310 client, class_8673 handle, PolymerGenericListPayload<?> payload) {
        if (!InternalClientRegistry.enabled) {
            return;
        }

        //noinspection unchecked
        var consumer = (Consumer<Object>) GENERIC_LIST_HANDLERS.get(payload.id().comp_2242());

        if (consumer != null) {
            try {
                for (var entry : payload.entries()) {
                    consumer.accept(entry);
                }
            } catch (Throwable e) {
                NetImpl.LOGGER.error("Handing of packet '" + payload.id() +"' failed!", e);
            }
        }
    }

    interface EntryReader<T> {
        @Nullable
        T read(class_2540 buf, int version);
    }
}
