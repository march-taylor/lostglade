package eu.pb4.polymer.core.api.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import eu.pb4.polymer.common.api.PolymerCommonUtils;
import eu.pb4.polymer.common.api.events.BooleanEvent;
import eu.pb4.polymer.common.api.events.FunctionEvent;
import eu.pb4.polymer.common.impl.CommonImpl;
import eu.pb4.polymer.common.impl.CompatStatus;
import eu.pb4.polymer.core.api.block.PolymerBlockUtils;
import eu.pb4.polymer.core.api.entity.PolymerEntityUtils;
import eu.pb4.polymer.core.api.other.PolymerComponent;
import eu.pb4.polymer.core.api.utils.PolymerSyncedObject;
import eu.pb4.polymer.core.api.utils.PolymerUtils;
import eu.pb4.polymer.core.impl.PolymerImpl;
import eu.pb4.polymer.core.impl.TransformingComponent;
import eu.pb4.polymer.core.impl.compat.polymc.PolyMcUtils;
import eu.pb4.polymer.core.impl.other.PacketTooltipContext;
import eu.pb4.polymer.core.mixin.CustomDataAccessor;
import it.unimi.dsi.fastutil.objects.ReferenceArrayList;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
import it.unimi.dsi.fastutil.objects.ReferenceSortedSets;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnmodifiableView;
import xyz.nucleoid.packettweaker.PacketContext;

import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import net.minecraft.class_10130;
import net.minecraft.class_10712;
import net.minecraft.class_124;
import net.minecraft.class_1268;
import net.minecraft.class_1269;
import net.minecraft.class_1299;
import net.minecraft.class_1759;
import net.minecraft.class_1792;
import net.minecraft.class_1799;
import net.minecraft.class_1809;
import net.minecraft.class_1812;
import net.minecraft.class_1833;
import net.minecraft.class_1836;
import net.minecraft.class_2487;
import net.minecraft.class_2509;
import net.minecraft.class_2520;
import net.minecraft.class_2561;
import net.minecraft.class_2583;
import net.minecraft.class_2960;
import net.minecraft.class_3218;
import net.minecraft.class_3222;
import net.minecraft.class_5135;
import net.minecraft.class_7225;
import net.minecraft.class_7923;
import net.minecraft.class_9279;
import net.minecraft.class_9290;
import net.minecraft.class_9299;
import net.minecraft.class_9304;
import net.minecraft.class_9331;
import net.minecraft.class_9334;
import net.minecraft.class_9701;

/**
 * General utility methods used for handling polymer items
 */
public final class PolymerItemUtils {
    public static final String POLYMER_STACK = "$polymer:stack";
    public static final String POLYMER_COUNTED = "$polymer:counted";
    private static final String POLYMC_STACK = "PolyMcOriginal";
    private static final Codec<class_2960> STACK_ID_CODEC = class_2960.field_25139.fieldOf("id").codec();

    private static final Codec<Map<class_2960, class_2520>> COMPONENTS_CODEC = Codec.unboundedMap(class_2960.field_25139,
            Codec.PASSTHROUGH.comapFlatMap((dynamic) -> {
                var nbt = dynamic.convert(class_2509.field_11560).getValue();
                return DataResult.success(nbt == dynamic.getValue() ? nbt.method_10707() : nbt);
            }, (nbt) -> new Dynamic<>(class_2509.field_11560, nbt.method_10707()))).optionalFieldOf("components", Map.of()).codec();
    public static final class_2583 CLEAN_STYLE = class_2583.field_24360.method_10978(false).method_10977(class_124.field_1068);
    /**
     * Allows to force rendering of some items as polymer one (for example vanilla ones)
     */
    public static final BooleanEvent<BiPredicate<class_1799, PacketContext>> CONTEXT_ITEM_CHECK = new BooleanEvent<>();

    @Deprecated(forRemoval = true)
    public static final BooleanEvent<Predicate<class_1799>> ITEM_CHECK = new BooleanEvent<>();
    /**
     * Allows to modify how virtual items looks before being sent to client (only if using build in methods!)
     * It can modify virtual version directly, as long as it's returned at the end.
     * You can also return new ItemStack, however please keep previous nbt so other modifications aren't removed if not needed!
     */
    public static final FunctionEvent<ItemModificationEventHandler, class_1799> ITEM_MODIFICATION_EVENT = new FunctionEvent<>();

    /**
     * Allows to run additional logic, making interactions work correctly server side,
     * emulating or preventing otherwise client dictated behaviour.
     *
     * See {@link PolymerItem#isPolymerItemInteraction(class_3222, class_1268, class_1799, class_3218, class_1269)}
     */
    public static final BooleanEvent<PolymerItemInteractionListener> POLYMER_ITEM_INTERACTION_CHECK = new BooleanEvent<>();
    /**
     * Changes sound logic within the item use interaction code to always play sounds to the client.
     *
     * See {@link PolymerItem#isIgnoringItemInteractionPlaySoundExceptedEntity(class_3222, class_1268, class_1799, class_3218)}
     */
    public static final BooleanEvent<PolymerIgnoreSoundExceptionListener> POLYMER_IGNORE_SOUND_EXCEPTED_ENTITY = new BooleanEvent<>();
    /**
     * Event for extending which items should be considered to be server items (have different data on the client).
     */
    public static final BooleanEvent<ServerItemPredicate> IS_SERVER_ITEM_EVENT = new BooleanEvent<>();

    private static final IdentityHashMap<class_1792, List<class_9331<?>>> FORCE_SYNCED_COMPONENTS = new IdentityHashMap<>();


    private static final class_9331<?>[] COMPONENTS_TO_COPY = {
            class_9334.field_49635,
            class_9334.field_49634,
            class_9334.field_49611,
            class_9334.field_49607,
            class_9334.field_50077,
            class_9334.field_50071,
            class_9334.field_49646,
            class_9334.field_49645,
            class_9334.field_49647,
            class_9334.field_49648,
            class_9334.field_50075,
            class_9334.field_54273,
            class_9334.field_49616,
            class_9334.field_49615,
            class_9334.field_49629,
            class_9334.field_50072,
            class_9334.field_49636,
            class_9334.field_49619,
            class_9334.field_49620,
            class_9334.field_49635,
            class_9334.field_49634,
            class_9334.field_49639,
            class_9334.field_49650,
            class_9334.field_54198,
            class_9334.field_50073,
            class_9334.field_49614,
            class_9334.field_49633,
            class_9334.field_49643,
            class_9334.field_49651,
            class_9334.field_49631,
            class_9334.field_52175,
            class_9334.field_49653,
            class_9334.field_49606,
            class_9334.field_49622,
            class_9334.field_53695,
            class_9334.field_53966,
            class_9334.field_53964,
            class_9334.field_54196,
            class_9334.field_54197,
            class_9334.field_49637,
            class_9334.field_49644,
            class_9334.field_53696,
            class_9334.field_56396,
            class_9334.field_56399,
            class_9334.field_56398,
            class_9334.field_56397,
            class_9334.field_49649,
            class_9334.field_55878,
            class_9334.field_56400,
            class_9334.field_63632,
            class_9334.field_63631,
            class_9334.field_64680,
            class_9334.field_63635,
            class_9334.field_63633,
            class_9334.field_63634
    };

    private static boolean stonecutterFix = PolymerImpl.FIX_STONECUTER;
    private static final ReferenceSet<class_9331<?>> FORCE_HIDE_TOOLTIP = ReferenceSet.of(
            class_9334.field_49630,
            class_9334.field_49636,
            class_9334.field_49611,
            class_9334.field_49635,
            class_9334.field_49634
    );

    private static final ReferenceSet<class_9331<?>> IGNORE_TOOLTIP_HIDING = ReferenceSet.of(
        class_9334.field_49632
    );


    private PolymerItemUtils() {
    }

    /**
     * This method creates a client side ItemStack representation
     *
     * @param itemStack Server side ItemStack
     * @param context   Networking context
     * @return Client side ItemStack
     */
    public static class_1799 getPolymerItemStack(class_1799 itemStack, PacketContext context) {
        return getPolymerItemStack(itemStack, PolymerUtils.getTooltipType(context.getPlayer()), context);
    }

    /**
     * This method creates a client side ItemStack representation
     *
     * @param itemStack      Server side ItemStack
     * @param tooltipContext Tooltip Context
     * @param context        Player being sent to
     * @return Client side ItemStack
     */
    public static class_1799 getPolymerItemStack(class_1799 itemStack, class_1836 tooltipContext, PacketContext context) {
        if (getPolymerIdentifier(itemStack) != null) {
            return itemStack;
        } else if (PolymerSyncedObject.getSyncedObject(class_7923.field_41178, itemStack.method_7909()) instanceof PolymerItem item) {
            return item.getPolymerItemStack(itemStack, tooltipContext, context);
        } else if (isPolymerServerItem(itemStack, context)) {
            return createItemStack(itemStack, tooltipContext, context);
        }

        if (CONTEXT_ITEM_CHECK.invoke((x) -> x.test(itemStack, context))) {
            return createItemStack(itemStack, tooltipContext, context);
        }

        return itemStack;
    }

    /**
     * This method gets real ItemStack from Virtual/Client side one
     *
     * @param itemStack Client side ItemStack
     * @return Server side ItemStack
     */
    public static class_1799 getRealItemStack(class_1799 itemStack, class_7225.class_7874 lookup) {
        var custom = itemStack.method_58694(class_9334.field_49628);


        if (custom != null) {
            var val = ((CustomDataAccessor) (Object) custom).polymer$getNbtUnsafe();

            if (!val.method_10545(POLYMER_STACK)) {
                return itemStack;
            }

            try {
                var counted = val.method_68566(POLYMER_COUNTED, false);

                var x = val.method_67492(POLYMER_STACK, (counted ? class_1799.field_24671 : class_1799.field_49747), lookup.method_57093(class_2509.field_11560)).orElseGet(itemStack::method_7972);

                if (!counted) {
                    x.method_7939(itemStack.method_7947());
                }

                return x;
            } catch (Throwable e) {
                if (PolymerImpl.LOG_MORE_ERRORS) {
                    PolymerImpl.LOGGER.warn("Failed to decode Item Stack!", e);
                }
            }
        }

        return itemStack;
    }

    /**
     * Returns stored identifier of Polymer ItemStack. If it's invalid, null is returned instead.
     */
    @Nullable
    public static class_2960 getPolymerIdentifier(class_1799 itemStack) {
        return getPolymerIdentifier(itemStack.method_58694(class_9334.field_49628));
    }

    public static class_2960 getPolymerIdentifier(@Nullable class_9279 custom) {
        if (custom != null) {
            var val = ((CustomDataAccessor) (Object) custom).polymer$getNbtUnsafe();
            if (!val.method_10545(POLYMER_STACK)) {
                return null;
            }
            try {
                return val.method_67491(POLYMER_STACK, STACK_ID_CODEC).orElse(null);
            } catch (Throwable ignored) {

            }
        }

        return null;
    }

    /**
     * Returns stored identifier of Polymer/other supported server mod ItemStack. If it's invalid, null is returned instead.
     */
    @Nullable
    public static class_2960 getServerIdentifier(class_1799 itemStack) {
        return getServerIdentifier(itemStack.method_58694(class_9334.field_49628));
    }

    @Nullable
    public static class_2960 getServerIdentifier(@Nullable class_9279 nbtData) {
        if (nbtData == null) {
            return null;
        }
        var x = getPolymerIdentifier(nbtData);
        if (x != null) {
            return x;
        }

        try {
            //noinspection DataFlowIssue
            var nbt = ((CustomDataAccessor) (Object) nbtData).polymer$getNbtUnsafe();
            if (nbt.method_10545(POLYMC_STACK)) {
                return nbt.method_67491(POLYMC_STACK, STACK_ID_CODEC).orElse(null);
            }
        } catch (Throwable ignored) {
        }

        return null;
    }

    @Nullable
    public static Map<class_2960, class_2520> getServerComponents(class_1799 stack) {
        return getServerComponents(stack.method_58694(class_9334.field_49628));
    }

    @Nullable
    public static Map<class_2960, class_2520> getPolymerComponents(class_1799 stack) {
        return getPolymerComponents(stack.method_58694(class_9334.field_49628));
    }

    @Nullable
    public static Map<class_2960, class_2520> getServerComponents(@Nullable class_9279 nbtData) {
        if (nbtData == null) {
            return null;
        }
        var x = getPolymerComponents(nbtData);
        if (x != null) {
            return x;
        }


        var nbt = ((CustomDataAccessor) (Object) nbtData).polymer$getNbtUnsafe();
        if (nbt.method_10545(POLYMC_STACK)) {
            return nbt.method_67491(POLYMC_STACK, COMPONENTS_CODEC).orElse(Map.of());
        }

        return null;
    }

    @Nullable
    public static Map<class_2960, class_2520> getPolymerComponents(@Nullable class_9279 nbtData) {
        if (nbtData == null || getPolymerIdentifier(nbtData) == null) {
            return null;
        }
        var nbt = ((CustomDataAccessor) (Object) nbtData).polymer$getNbtUnsafe();
        if (!nbt.method_10545(POLYMER_STACK)) {
            return null;
        }

        return nbt.method_67491(POLYMER_STACK, COMPONENTS_CODEC).orElse(Map.of());
    }
    public static void registerOverlay(class_1792 item, PolymerItem polymerItem) {
        PolymerItem.registerOverlay(item, polymerItem);
    }

    public static boolean isPolymerServerItem(class_1799 itemStack) {
        return isPolymerServerItem(itemStack, PacketContext.get());
    }

    public static boolean isPolymerServerItem(class_1799 itemStack, PacketContext context) {
        if (getPolymerIdentifier(itemStack) != null) {
            return false;
        }
        if (PolymerSyncedObject.getSyncedObject(class_7923.field_41178, itemStack.method_7909()) instanceof PolymerItem) {
            return true;
        }

        for (var x : itemStack.method_57380().method_57846()) {
            if (!PolymerComponent.canSync(x.getKey(), x.getValue().orElse(null), context)) {
                return true;
            } else if (x.getValue() != null && x.getValue().isPresent()
                    && x.getValue().get() instanceof TransformingComponent t
                    && t.polymer$requireModification(context)) {
                return true;
            }
        }

        if (itemStack.method_57826(class_9334.field_49633) && itemStack.method_58695(class_9334.field_56400, class_10712.field_56318).method_67214(class_9334.field_49636)) {
            for (var ench : itemStack.method_58695(class_9334.field_49633, class_9304.field_49385).method_57534()) {
                var attributes = ench.comp_349().method_60034(class_9701.field_51668);
                if (attributes != null) {
                    for (var attr : attributes) {
                        if (PolymerEntityUtils.isPolymerEntityAttribute(attr.comp_2718())
                                && class_5135.method_26873(class_1299.field_6097).method_27310(attr.comp_2718())) {
                            return true;
                        }
                    }
                }
            }
        }

        return CONTEXT_ITEM_CHECK.invoke((x) -> x.test(itemStack, context));
    }

    /**
     * This method creates full (vanilla like) representation of ItemStack
     *
     * @param itemStack Server side ItemStack
     * @param context   Player seeing it
     * @return Client side ItemStack
     */

    public static class_1799 createItemStack(class_1799 itemStack, PacketContext context) {
        return createItemStack(itemStack, PolymerUtils.getTooltipType(context.getPlayer()), context);
    }

    /**
     * This method creates full (vanilla like) representation of ItemStack
     *
     * @param itemStack      Server side ItemStack
     * @param tooltipContext TooltipContext
     * @param context        Player seeing it
     * @return Client side ItemStack
     */
    public static class_1799 createItemStack(class_1799 itemStack, class_1836 tooltipContext, PacketContext context) {
        class_1792 item = itemStack.method_7909();
        class_2960 model = null;
        boolean storeCount;
        if (PolymerSyncedObject.getSyncedObject(class_7923.field_41178, itemStack.method_7909()) instanceof PolymerItem virtualItem) {
            var data = PolymerItemUtils.getItemSafely(virtualItem, itemStack, context);
            item = data.item();
            storeCount = virtualItem.shouldStorePolymerItemStackCount();
            model = data.itemModel != null ? data.itemModel : item.method_57347().method_58694(class_9334.field_54199);
        } else {
            storeCount = false;
            model = itemStack.method_58694(class_9334.field_54199);
        }

        class_1799 out = new class_1799(item, itemStack.method_7947());
        for (var x : out.method_57353().method_57831()) {
            if (itemStack.method_57353().method_58694(x) == null) {
                out.method_57379(x, null);
            }
        }

        if (model != null) {
            out.method_57379(class_9334.field_54199, model);
        }

        for (var i = 0; i < COMPONENTS_TO_COPY.length; i++) {
            var key = COMPONENTS_TO_COPY[i];
            var x = itemStack.method_58694(key);

            if (x instanceof TransformingComponent t) {
                //noinspection unchecked,rawtypes
                out.method_57379((class_9331) key, t.polymer$getTransformed(context));
            } else {
                //noinspection unchecked,rawtypes
                out.method_57379((class_9331) key, (Object) itemStack.method_58694(key));
            }
        }

        if (PolymerSyncedObject.getSyncedObject(class_7923.field_41178, itemStack.method_7909()) instanceof PolymerItem polymerItem) {
            polymerItem.modifyBasePolymerItemStack(out, itemStack, context);
        }

        var lookup = context.getRegistryWrapperLookup();

        {
            var current = itemStack.method_58694(class_9334.field_53966);
            if (current == null) {
                out.method_57379(class_9334.field_53966, new class_10130(0.00001f, Optional.of(class_7923.field_41178.method_10221(itemStack.method_7909()))));
            } else if (current.comp_3092().isEmpty()) {
                out.method_57379(class_9334.field_53966, new class_10130(current.comp_3091(), Optional.of(class_7923.field_41178.method_10221(itemStack.method_7909()))));
            }
        }


        out.method_57379(class_9334.field_49641, itemStack.method_7958());


        // Set item name
        {
            var name = itemStack.method_63693();
            out.method_57379(class_9334.field_50239, name);

            if (!out.method_57826(class_9334.field_49631)) {
                if (
                        (item instanceof class_1759 && out.method_57826(class_9334.field_49614))
                                || ((item instanceof class_1812 || item instanceof class_1833) && out.method_57826(class_9334.field_49651))
                                || (item instanceof class_1809 && out.method_57826(class_9334.field_49617) && Objects.requireNonNull(out.method_58694(class_9334.field_49617)).method_73317().isPresent())

                ) {
                    out.method_57379(class_9334.field_49631, class_2561.method_43473().method_10852(name).method_10862(class_2583.field_24360.method_10978(false)));
                }
            }
        }


        try {
            out.method_57379(class_9334.field_49628, PolymerCommonUtils.executeWithoutNetworkingLogic(() -> {
                var nbt = new class_2487();

                nbt.method_67493(POLYMER_STACK, storeCount ? class_1799.field_24671 : class_1799.field_49747, lookup.method_57093(class_2509.field_11560), itemStack);

                if (storeCount) {
                    nbt.method_10556(POLYMER_COUNTED, true);
                }

                return class_9279.method_57456(nbt);
            }));
        } catch (Throwable e) {
            var profile = context.getGameProfile();
            CommonImpl.LOGGER.error("Failed to encode Polymer item stack data {} for {}", itemStack, profile != null ? profile.name() : "<Unknown>");
        }


        var display = out.method_58695(class_9334.field_56400, class_10712.field_56318);

        for (var x : out.method_57353()) {
            if (!IGNORE_TOOLTIP_HIDING.contains(x.comp_2443()) && (x.comp_2444() instanceof class_9299 || FORCE_HIDE_TOOLTIP.contains(x.comp_2443()))) {
                display = display.method_67215(x.comp_2443(), true);
            }
        }
        if (out.method_57826(class_9334.field_49629) && !itemStack.method_57826(class_9334.field_49629)) {
            display = display.method_67215(class_9334.field_49629, true);
        }

        display.comp_3601().removeIf(PolymerComponent::isPolymerComponent);
        out.method_57379(class_9334.field_56400, display);

        try {
            var tooltip = itemStack.method_7950(new PacketTooltipContext(context), context.getPlayer(), tooltipContext);
            if (!tooltip.isEmpty()) {
                tooltip.removeFirst();

                if (PolymerSyncedObject.getSyncedObject(class_7923.field_41178, itemStack.method_7909()) instanceof PolymerItem polymerItem) {
                    polymerItem.modifyClientTooltip(tooltip, itemStack, context);
                }
                if (!tooltip.isEmpty()) {
                    var lore = new ArrayList<class_2561>();
                    for (class_2561 t : tooltip) {
                        lore.add(class_2561.method_43473().method_10852(t).method_10862(PolymerItemUtils.CLEAN_STYLE));
                    }
                    out.method_57379(class_9334.field_49632, new class_9290(lore));
                }
            } else {
                out.method_57379(class_9334.field_56400, new class_10712(true, ReferenceSortedSets.emptySet()));
            }
        } catch (Throwable e) {
            if (PolymerImpl.LOG_MORE_ERRORS) {
                PolymerImpl.LOGGER.error("Failed to get tooltip of " + itemStack, e);
            }
        }
        return ITEM_MODIFICATION_EVENT.invoke((col) -> {
            var custom = out;

            for (var in : col) {
                custom = in.modifyItem(itemStack, custom, context);
            }

            return custom;
        });
    }

    /**
     * This method is minimal wrapper around {@link PolymerItem#getPolymerItem(class_1799, PacketContext)} to make sure
     * It gets replaced if it represents other PolymerItem
     *
     * @param item        PolymerItem
     * @param stack       Server side ItemStack
     * @param maxDistance Maximum number of checks for nested virtual blocks
     * @return Client side ItemStack
     */
    public static ItemWithMetadata getItemSafely(PolymerItem item, class_1799 stack, PacketContext context, int maxDistance) {
        class_1792 out = item.getPolymerItem(stack, context);
        PolymerItem lastVirtual = item;

        int req = 0;
        while (PolymerSyncedObject.getSyncedObject(class_7923.field_41178, out) instanceof PolymerItem newItem && newItem != item && req < maxDistance) {
            out = newItem.getPolymerItem(stack, context);
            lastVirtual = newItem;
            req++;
        }
        return new ItemWithMetadata(out, lastVirtual.getPolymerItemModel(stack, context));
    }

    /**
     * This method is minimal wrapper around {@link PolymerItem#getPolymerItem(class_1799, PacketContext)} to make sure
     * It gets replaced if it represents other PolymerItem
     *
     * @param item  PolymerItem
     * @param stack Server side ItemStack
     * @return Client side ItemStack
     */
    public static ItemWithMetadata getItemSafely(PolymerItem item, class_1799 stack, PacketContext context) {
        return getItemSafely(item, stack, context, PolymerBlockUtils.NESTED_DEFAULT_DISTANCE);
    }

    public static class_1799 getClientItemStack(class_1799 stack, PacketContext context) {
        var out = getPolymerItemStack(stack, context);
        if (CompatStatus.POLYMC) {
            out = PolyMcUtils.toVanilla(out, context.getPlayer());
        }
        return out;
    }

    public static boolean isPolymerItemInteraction(class_3222 player, class_1799 stack, class_1268 hand, class_3218 world, class_1269 actionResult) {
        if (PolymerSyncedObject.getSyncedObject(class_7923.field_41178, stack.method_7909()) instanceof PolymerItem polymerItem && polymerItem.isPolymerItemInteraction(player, hand, stack, world, actionResult)) {
            return true;
        }
        return POLYMER_ITEM_INTERACTION_CHECK.invoke((x) -> x.isPolymerItemInteraction(player, hand, stack, world, actionResult));
    }

    public static boolean isIgnoringPlaySoundExceptedEntity(class_3222 player, class_1799 stack, class_1268 hand, class_3218 world) {
        if (PolymerSyncedObject.getSyncedObject(class_7923.field_41178, stack.method_7909()) instanceof PolymerItem polymerItem && polymerItem.isIgnoringItemInteractionPlaySoundExceptedEntity(player, hand, stack, world)) {
            return true;
        }
        return POLYMER_IGNORE_SOUND_EXCEPTED_ENTITY.invoke((x) -> x.isIgnoringItemInteractionPlaySoundExceptedEntity(player, hand, stack, world));
    }

    /**
     * This method allows to define Data Component Types, which need to be always synced to clients,
     * even if they have the default value for sent ItemStack.
     * This can be used with combination with Fabric's DefaultItemComponentEvents to synchronize modified components values to clients without the mod.
     *
     * @param item item this effect should apply to
     * @param types Component types that need to be always synced to client
     */
    public static void syncDefaultComponent(class_1792 item, class_9331<?>... types) {
        var list = FORCE_SYNCED_COMPONENTS.computeIfAbsent(item, (i) -> new ReferenceArrayList<>());
        for (var type : types) {
            if (!list.contains(type)) {
                list.add(type);
            }
        }
    }


    public static boolean isStonecutterFixEnabled() {
        return stonecutterFix;
    }

    public static void enableStonecutterFix() {
        stonecutterFix = true;
    }

    @UnmodifiableView
    public static List<class_9331<?>> getSyncedDefaultComponents(class_1792 item) {
        return FORCE_SYNCED_COMPONENTS.getOrDefault(item, List.of());
    }

    public static boolean isServerItem(class_1799 stack, PacketContext context) {
        if (isPolymerServerItem(stack, context)) {
            return true;
        }

        if (CompatStatus.POLYMC && PolyMcUtils.isServerSide(class_7923.field_41178, stack.method_7909())) {
            return true;
        }

        var container = stack.method_58694(class_9334.field_49622);
        if (container != null) {
            for (var inner : container.method_59714()) {
                if (isServerItem(inner, context)) {
                    return true;
                }
            }
        }

        var bundle = stack.method_58694(class_9334.field_49650);
        if (bundle != null) {
            for (var inner : bundle.method_57421()) {
                if (isServerItem(inner, context)) {
                    return true;
                }
            }
        }

        var remainder = stack.method_58694(class_9334.field_53965);
        if (remainder != null) {
            if (isServerItem(remainder.comp_3093(), context)) {
                return true;
            }
        }

        var projectile = stack.method_58694(class_9334.field_49649);
        if (projectile != null) {
            for (var inner : projectile.method_57437()) {
                if (isServerItem(inner, context)) {
                    return true;
                }
            }
        }

        return IS_SERVER_ITEM_EVENT.invoke(x -> x.isServerItem(stack, context));
    }

    @FunctionalInterface
    public interface ItemModificationEventHandler {
        class_1799 modifyItem(class_1799 original, class_1799 client, PacketContext context);
    }

    @FunctionalInterface
    public interface PolymerItemInteractionListener {
        boolean isPolymerItemInteraction(class_3222 player, class_1268 hand, class_1799 stack, class_3218 world, class_1269 actionResult);
    }

    @FunctionalInterface
    public interface PolymerIgnoreSoundExceptionListener {
        boolean isIgnoringItemInteractionPlaySoundExceptedEntity(class_3222 player, class_1268 hand, class_1799 stack, class_3218 world);
    }

    @FunctionalInterface
    public interface ServerItemPredicate {
        boolean isServerItem(class_1799 stack, PacketContext context);
    }

    public record ItemWithMetadata(class_1792 item, @Nullable class_2960 itemModel) {
    }

    static {
        CONTEXT_ITEM_CHECK.register((stack, context) -> ITEM_CHECK.invoke(x -> x.test(stack)));
    }
}
