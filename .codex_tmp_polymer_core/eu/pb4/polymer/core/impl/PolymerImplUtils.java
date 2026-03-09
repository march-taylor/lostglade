package eu.pb4.polymer.core.impl;

import eu.pb4.polymer.common.impl.CompatStatus;
import eu.pb4.polymer.core.api.item.PolymerItemUtils;
import eu.pb4.polymer.core.api.utils.PolymerSyncedObject;
import eu.pb4.polymer.core.api.utils.PolymerUtils;
import eu.pb4.polymer.core.impl.client.InternalClientRegistry;
import eu.pb4.polymer.core.impl.compat.ServerTranslationUtils;
import eu.pb4.polymer.core.impl.compat.polymc.PolyMcUtils;
import eu.pb4.polymer.core.impl.interfaces.PolymerIdMapper;
import eu.pb4.polymer.core.impl.interfaces.PolymerGamePacketListenerExtension;
import eu.pb4.polymer.core.impl.other.ImplPolymerRegistry;
import eu.pb4.polymer.core.impl.other.PolymerTooltipType;
import eu.pb4.polymer.rsm.impl.RegistrySyncExtension;
import net.fabricmc.fabric.api.event.registry.RegistryAttribute;
import net.fabricmc.fabric.api.event.registry.RegistryAttributeHolder;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroupEntries;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.class_1761;
import net.minecraft.class_1799;
import net.minecraft.class_1836;
import net.minecraft.class_2248;
import net.minecraft.class_2378;
import net.minecraft.class_2680;
import net.minecraft.class_2769;
import net.minecraft.class_2960;
import net.minecraft.class_3222;
import net.minecraft.class_3532;
import net.minecraft.class_3902;
import net.minecraft.class_5321;
import net.minecraft.class_5455;
import net.minecraft.class_7225;
import net.minecraft.class_7923;
import net.minecraft.class_7924;
import org.jetbrains.annotations.Nullable;
import xyz.nucleoid.packettweaker.PacketContext;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public class PolymerImplUtils {
    public static final ThreadLocal<class_3902> IS_RELOADING_WORLD = new ThreadLocal<>();
    public static final ThreadLocal<class_3902> IGNORE_PLAY_SOUND_EXCLUSION = new ThreadLocal<>();
    public static final Collection<class_2680> POLYMER_STATES = ((PolymerIdMapper<class_2680>) class_2248.field_10651).polymer$getPolymerEntries();
    public static final class_7225.class_7874 FALLBACK_LOOKUP = class_5455.method_40302(class_7923.field_41167);

    public static class_2960 id(String path) {
        return class_2960.method_60655(PolymerUtils.ID, path);
    }

    public static String getAsString(class_2680 state) {
        var builder = new StringBuilder();

        builder.append(class_7923.field_41175.method_10221(state.method_26204()));

        if (!state.method_11656().isEmpty()) {
            builder.append("[");
            var iterator = state.method_11656().entrySet().iterator();

            while (iterator.hasNext()) {
                var entry = iterator.next();
                builder.append(entry.getKey().method_11899());
                builder.append("=");
                builder.append(((class_2769) entry.getKey()).method_11901(entry.getValue()));

                if (iterator.hasNext()) {
                    builder.append(",");
                }
            }
            builder.append("]");
        }

        return builder.toString();
    }

    @Nullable
    public static String dumpRegistry() {
        BufferedWriter writer = null;
        try {
            var path = "./polymer-dump-" + FabricLoader.getInstance().getEnvironmentType().name().toLowerCase(Locale.ROOT) + ".txt";
            writer = new BufferedWriter(new FileWriter(path));
            BufferedWriter finalWriter = writer;
            Consumer<String> msg = (str) -> {
                try {
                    finalWriter.write(str);
                    finalWriter.newLine();
                } catch (Exception e) {
                    // Silence;
                }
            };


            {
                msg.accept("== Vanilla Registries");
                for (var reg : ((class_2378<class_2378<Object>>) class_7923.field_41167)) {
                    msg.accept("");
                    msg.accept("== Registry: " + ((class_2378<Object>) (Object) class_7923.field_41167).method_10221(reg).toString());
                    msg.accept("");
                    if (reg instanceof RegistrySyncExtension regEx) {
                        msg.accept("= Status: " + regEx.polymer_registry_sync$getStatus().name());
                        msg.accept("");
                    }

                    if (CompatStatus.FABRIC_SYNC) {
                        msg.accept("= Synced: " + RegistryAttributeHolder.get(reg).hasAttribute(RegistryAttribute.SYNCED));
                        msg.accept("");
                    }

                    for (var entry : reg) {
                        msg.accept("" + reg.method_10206(entry) + " | " + reg.method_10221(entry).toString() + " | Polymer? " + PolymerUtils.isServerOnly(reg, entry));
                    }
                }
                msg.accept("");
                msg.accept("== BlockStates");
                msg.accept("");
                msg.accept("= Polymer Starts: " + PolymerImplUtils.getBlockStateOffset());
                msg.accept("");
                msg.accept("= All States: " + class_2248.field_10651.method_10204());

                //noinspection unchecked
                var pl = (PolymerIdMapper<class_2680>) class_2248.field_10651;
                msg.accept("= Polymer States: " + pl.polymer$getPolymerEntries().size());
                msg.accept("= Server Bits: " + class_3532.method_15342(class_2248.field_10651.method_10204()));
                msg.accept("= Vanilla Bits: " + pl.polymer$getVanillaBitCount());
                msg.accept("= NonPolymer Bits: " + pl.polymer$getNonPolymerBitCount());
                msg.accept("");

                for (var state : class_2248.field_10651) {
                    msg.accept(class_2248.field_10651.method_10206(state) + " | " + getAsString(state) + " | Polymer? " + (PolymerSyncedObject.getSyncedObject(class_7923.field_41175, state.method_26204())));
                }
            }

            msg.accept("");
            msg.accept("== Server/Local Polymer Item Groups");
            msg.accept("");
            for (var entry : InternalServerRegistry.ITEM_GROUPS) {
                msg.accept(InternalServerRegistry.ITEM_GROUPS.getEntryId(entry).toString());
            }

            {
                msg.accept("");
                msg.accept("== Polymer Registries");
                msg.accept("");

                if (PolymerImpl.IS_CLIENT) {
                    for (var reg2 : ((Collection<ImplPolymerRegistry<Object>>) (Object) InternalClientRegistry.REGISTRIES)) {
                        msg.accept("");
                        msg.accept("== Registry: " + reg2.getName() + " (Client)");
                        msg.accept("");
                        for (var entry : reg2) {
                            msg.accept(reg2.method_10206(entry) + " | " + reg2.method_10206(entry));
                        }
                        msg.accept("");
                        msg.accept("=== Tags:");
                        msg.accept("");
                        for (var tag : reg2.getTags()) {
                            msg.accept(tag + " | :");
                            for (var entry : reg2.getTag(tag)) {
                                msg.accept("  " + reg2.method_10206(entry));
                            }
                        }
                    }

                    msg.accept("");
                    msg.accept("== Registry: BlockState (Client)");
                    msg.accept("");

                    for (var entry : InternalClientRegistry.BLOCK_STATES) {
                        msg.accept(InternalClientRegistry.BLOCK_STATES.method_10206(entry) + " | " + entry.block().identifier());
                    }
                }
            }

            try {
                writer.close();
            } catch (Exception e) {
            }

            return path;
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (writer != null) {
            try {
                writer.close();
            } catch (Exception e) {
            }
        }
        return null;
    }

    public static int getBlockStateOffset() {
        return ((PolymerIdMapper) class_2248.field_10651).polymer$getOffset();
    }

    public static boolean removeFromItemGroup(class_1799 stack) {
        if (stack == null) {
            return true;
        }
        return isPolymerControlled(stack);
    }
    public static boolean isPolymerControlled(class_1799 stack) {
        return PolymerItemUtils.isPolymerServerItem(stack) || PolymerItemUtils.getServerIdentifier(stack) != null;
    }

    public static PolymerTooltipType getTooltipContext(class_3222 player) {
        return player != null && player.field_13987 instanceof PolymerGamePacketListenerExtension h && h.polymer$advancedTooltip() ? PolymerTooltipType.ADVANCED : PolymerTooltipType.BASIC;
    }

    public static boolean isServerSideSyncableEntry(@SuppressWarnings("rawtypes") class_2378 reg, Object obj) {
        return PolymerUtils.isServerOnly(reg, obj) || (PolymerImpl.SYNC_MODDED_ENTRIES_POLYMC && PolyMcUtils.isServerSide(reg, obj));
    }

    public static class_1799 convertStack(class_1799 representation, class_3222 player) {
        return convertStack(representation, player, PolymerUtils.getTooltipType(player));
    }

    public static class_1799 convertStack(class_1799 representation, class_3222 player, class_1836 context) {
        return ServerTranslationUtils.parseFor(player.field_13987, PolyMcUtils.toVanilla(PolymerItemUtils.getPolymerItemStack(representation, context, PacketContext.create(player)), player));
    }

    public static void callItemGroupEvents(class_2960 id, class_1761 itemGroup, List<class_1799> parentTabStacks, List<class_1799> searchTabStacks, class_1761.class_8128 context) {
        if (CompatStatus.FABRIC_ITEM_GROUP) {
            try {
                var fabricCollector = new FabricItemGroupEntries(context, parentTabStacks, searchTabStacks);
                ItemGroupEvents.modifyEntriesEvent(class_5321.method_29179(class_7924.field_44688, id)).invoker().modifyEntries(fabricCollector);
                ItemGroupEvents.MODIFY_ENTRIES_ALL.invoker().modifyEntries(itemGroup, fabricCollector);
            } catch (Throwable e) {
                if (PolymerImpl.LOG_MORE_ERRORS) {
                    PolymerImpl.LOGGER.warn("Failed to execute Fabric Item Group event!", e);
                }
            }
        }
    }

    @Nullable
    public static String getModName(class_1799 stack) {
        var id = PolymerItemUtils.getServerIdentifier(stack);
        if (id != null) {
            return getModName(id);
        }
        return null;
    }

    public static String getModName(class_2960 id) {
        var container = FabricLoader.getInstance().getModContainer(id.method_12836());
        return container.isPresent() ? container.get().getMetadata().getName() : (id.method_12836() + "*");
    }
}
