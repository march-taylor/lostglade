package eu.pb4.polymer.core.impl.networking;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import eu.pb4.polymer.common.impl.CompatStatus;
import eu.pb4.polymer.common.impl.entity.InternalEntityHelpers;
import eu.pb4.polymer.core.api.block.PolymerBlockUtils;
import eu.pb4.polymer.core.api.entity.PolymerEntity;
import eu.pb4.polymer.core.api.item.PolymerItemUtils;
import eu.pb4.polymer.core.api.other.PolymerComponent;
import eu.pb4.polymer.core.api.utils.PolymerSyncedObject;
import eu.pb4.polymer.core.api.utils.PolymerUtils;
import eu.pb4.polymer.core.impl.PolymerImpl;
import eu.pb4.polymer.core.impl.PolymerImplUtils;
import eu.pb4.polymer.core.impl.TransformingComponent;
import eu.pb4.polymer.core.impl.compat.ImmersivePortalsUtils;
import eu.pb4.polymer.core.impl.interfaces.EntityAttachedPacket;
import eu.pb4.polymer.core.impl.interfaces.StatusEffectPacketExtension;
import org.jetbrains.annotations.Nullable;
import xyz.nucleoid.packettweaker.PacketContext;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import net.minecraft.class_10266;
import net.minecraft.class_1799;
import net.minecraft.class_2487;
import net.minecraft.class_2499;
import net.minecraft.class_2509;
import net.minecraft.class_2591;
import net.minecraft.class_2596;
import net.minecraft.class_2602;
import net.minecraft.class_2616;
import net.minecraft.class_2622;
import net.minecraft.class_2744;
import net.minecraft.class_2781;
import net.minecraft.class_2960;
import net.minecraft.class_3244;
import net.minecraft.class_7225;
import net.minecraft.class_7696;
import net.minecraft.class_7699;
import net.minecraft.class_7701;
import net.minecraft.class_7832;
import net.minecraft.class_7923;
import net.minecraft.class_8042;
import net.minecraft.class_8609;
import net.minecraft.class_8735;
import net.minecraft.class_9323;
import net.minecraft.class_9326;
import net.minecraft.class_9331;

public class PacketPatcher {

    private static final Codec<class_1799> ITEM_VARIANT_FORMATTED_ITEM_STACK_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            class_7923.field_41178.method_40294().fieldOf("item").forGetter(class_1799::method_41409),
            class_9326.field_49589.optionalFieldOf("components", class_9326.field_49588).forGetter(class_1799::method_57380)
    ).apply(instance, (item, components) -> new class_1799(item, 1, components)));

    public static class_2596<?> replace(class_8609 handler, class_2596<?> packet) {
        if (handler instanceof class_3244 handler1) {
            if (packet instanceof class_2744 original) {
                var entity = EntityAttachedPacket.get(original, original.method_11820());
                var polymerEntity = PolymerEntity.get(entity);
                if (polymerEntity != null) {
                    return EntityAttachedPacket.setIfEmpty(
                            new class_2744(entity.method_5628(), polymerEntity.getPolymerVisibleEquipment(original.method_30145(), handler1.method_32311())),
                            entity
                    );
                }
            }

            if (packet instanceof class_8042 bundleS2CPacket) {
                var list = new ArrayList<class_2596<? super class_2602>>();
                for (var value : bundleS2CPacket.method_48324()) {
                    var x = replace(handler, value);
                    if (!prevent(handler, x)) {
                        //noinspection unchecked
                        list.add((class_2596<class_2602>) x);
                    }
                }

                return new class_8042(list);
            }
        } else if (handler instanceof class_8735) {
            if (packet instanceof class_7832 featuresS2CPacket) {
                var x = PolymerUtils.getClientEnabledFeatureFlags();

                if (x.isEmpty()) {
                    return packet;
                }

                class_7699 set = class_7701.field_40180.method_45390(x.toArray(new class_7696[0]));

                if (featuresS2CPacket.comp_1113().getClass() == HashSet.class) {
                    featuresS2CPacket.comp_1113().addAll(class_7701.field_40180.method_45392(set));
                } else {
                    var y = new HashSet<class_2960>();
                    y.addAll(featuresS2CPacket.comp_1113());
                    y.addAll(class_7701.field_40180.method_45392(set));
                    return new class_7832(y);
                }
            }

        }

        return packet;
    }

    public static void sendExtra(class_8609 handler, class_2596<?> packet) {
        if (handler.getClass() == class_3244.class) {
            if (CompatStatus.IMMERSIVE_PORTALS) {
                ImmersivePortalsUtils.sendBlockPackets((class_3244) handler, packet);
            } else {
                BlockPacketUtil.sendFromPacket(packet, (class_3244) handler);
            }
        }
    }

    public static boolean prevent(class_8609 handler, class_2596<?> packet) {
        if (handler.getClass() == class_3244.class) {
            var player = PacketContext.create(handler);
            //noinspection DataFlowIssue
            if ((
                    packet instanceof StatusEffectPacketExtension packet2
                            && ((PolymerSyncedObject.getSyncedObject(class_7923.field_41174, packet2.polymer$getStatusEffect()) != null
                            && PolymerSyncedObject.getSyncedObject(class_7923.field_41174, packet2.polymer$getStatusEffect()).getPolymerReplacement(packet2.polymer$getStatusEffect(), player) == null))
            ) || !EntityAttachedPacket.shouldSend(packet, player.getPlayer())
            ) {
                return true;
            } else if ((packet instanceof class_2744 original && original.method_30145().isEmpty()) || !EntityAttachedPacket.shouldSend(packet, player.getPlayer())) {
                return true;
            } else if ((packet instanceof class_2781 original
                    && PolymerEntity.get(EntityAttachedPacket.get(packet, original.method_11937())) instanceof PolymerEntity entity
                    && !InternalEntityHelpers.isLivingEntity(entity.getPolymerEntityType(player)))) {
                return true;
            } else if (packet instanceof class_2622 be) {
                return PolymerSyncedObject.getSyncedObject(class_7923.field_41181, be.method_11291()) instanceof PolymerSyncedObject<class_2591<?>> obj
                        && obj.getPolymerReplacement(be.method_11291(), player) == null;
            } else if (packet instanceof class_10266 recipeBook && PolymerImpl.SPLIT_RECIPE_PACKETS > 0 && recipeBook.comp_3232().size() > PolymerImpl.SPLIT_RECIPE_PACKETS) {
                var list = new ArrayList<class_10266.class_10267>();
                if (recipeBook.comp_3294()) {
                    handler.method_14364(new class_10266(List.of(), true));
                }
                for (var entry : recipeBook.comp_3232()) {
                    list.add(entry);
                    if (list.size() >= PolymerImpl.SPLIT_RECIPE_PACKETS) {
                        handler.method_14364(new class_10266(list, false));
                        list = new ArrayList<>();
                    }
                }
                if (!list.isEmpty()) {
                    handler.method_14364(new class_10266(list, false));
                }

                return true;
            } else if (packet instanceof class_2616 animationS2CPacket && PolymerEntity.get(EntityAttachedPacket.get(packet, animationS2CPacket.method_11269())) instanceof PolymerEntity polymerEntity
                    && !InternalEntityHelpers.isLivingEntity(polymerEntity.getPolymerEntityType(PacketContext.create(handler)))) {
                return true;
            }
        }

        return false;
    }

    @Nullable
    private static class_1799 silentItemStackFromNbt(class_7225.class_7874 lookup, class_2487 nbt) {
        if (nbt.method_33133()) {
            return null;
        }
        var ops = lookup.method_57093(class_2509.field_11560);
        var result = class_1799.field_24671.parse(ops, nbt);
        if (result.isSuccess()) {
            return result.getOrThrow();
        }
        return null;
    }

    @Nullable
    private static class_1799 silentItemVariantFromNbt(class_7225.class_7874 lookup, class_2487 nbt) {
        if (nbt.method_33133()) {
            return null;
        }

        var ops = lookup.method_57093(class_2509.field_11560);
        var result = ITEM_VARIANT_FORMATTED_ITEM_STACK_CODEC.parse(ops, nbt);
        if (result.isSuccess()) {
            return result.getOrThrow();
        }
        return null;
    }

    public static class_2487 transformBlockEntityNbt(PacketContext context, class_2591<?> type, class_2487 original) {
        if (original.method_33133()) {
            return original;
        }
        class_2487 override = null;

        var lookup = context.getRegistryWrapperLookup() != null ? context.getRegistryWrapperLookup() : PolymerImplUtils.FALLBACK_LOOKUP;
        var ops = lookup.method_57093(class_2509.field_11560);
        if (original.method_10580("shared_data") instanceof class_2487 shared) {
            if (shared.method_10580("display_item") instanceof class_2487 itemNbt) {
                var stack = silentItemStackFromNbt(lookup, itemNbt);
                if (stack != null && PolymerItemUtils.isPolymerServerItem(stack, context)) {
                    //noinspection ConstantValue
                    if (override == null) {
                        override = original.method_10553();
                    }

                    try {
                        override.method_68568("shared_data").method_67493("display_item",
                                class_1799.field_49266, ops, PolymerItemUtils.getPolymerItemStack(stack, context));
                    } catch (Throwable e) {
                        e.printStackTrace();
                    }
                }
            }
        }


        if (original.method_10580("Items") instanceof class_2499 list) {
            for (int i = 0; i < list.size(); i++) {
                var nbt = list.method_68582(i);
                var stack = silentItemStackFromNbt(lookup, nbt);
                if (stack != null && PolymerItemUtils.isPolymerServerItem(stack, context)) {
                    if (override == null) {
                        override = original.method_10553();
                    }
                    nbt = nbt.method_10553();
                    nbt.method_10551("id");
                    nbt.method_10551("components");
                    nbt.method_10551("count");
                    stack = PolymerItemUtils.getPolymerItemStack(stack, context);
                    override.method_68569("Items").method_68585(i, class_1799.field_49266.encode(stack, ops, nbt).getOrThrow());
                }
            }
        }

        if (original.method_10580("item") instanceof class_2487 nbt) {
            var stack = silentItemStackFromNbt(lookup, nbt);
            boolean variant = false;
            if (stack == null) {
                stack = silentItemVariantFromNbt(lookup, nbt);
                variant = stack != null;
            }

            if (stack != null && PolymerItemUtils.isPolymerServerItem(stack, context)) {
                if (override == null) {
                    override = original.method_10553();
                }
                stack = PolymerItemUtils.getPolymerItemStack(stack, context);
                override.method_10566("item", variant
                        ? ITEM_VARIANT_FORMATTED_ITEM_STACK_CODEC.encodeStart(lookup.method_57093(class_2509.field_11560), stack).getOrThrow()
                        : class_1799.field_49266.encodeStart(ops, stack).getOrThrow());
            }
        }

        if (original.method_10580("components") instanceof class_2487 compound) {
            var comp = class_9323.field_50234.decode(ops, compound);
            if (comp.isSuccess()) {
                var map = comp.getOrThrow().getFirst();
                class_9323.class_9324 builder = null;

                for (var component : map) {
                    if (component.comp_2444() instanceof TransformingComponent transformingComponent && transformingComponent.polymer$requireModification(context)) {
                        if (builder == null) {
                            builder = class_9323.method_57827();
                            builder.method_57839(map);
                        }
                        //noinspection unchecked
                        builder.method_57840((class_9331<? super Object>) component.comp_2443(), transformingComponent.polymer$getTransformed(context));
                    } else if (!PolymerComponent.canSync(component.comp_2443(), component.comp_2444(), context)) {
                        if (builder == null) {
                            builder = class_9323.method_57827();
                            builder.method_57839(map);
                        }
                        builder.method_57840(component.comp_2443(), null);
                    }
                }

                if (builder != null) {
                    if (override == null) {
                        override = original.method_10553();
                    }
                    override.method_10566("components", class_9323.field_50234.encodeStart(ops, builder.method_57838()).result().orElse(new class_2487()));
                }
            }
        }

        return override != null ? override : original;
    }
}
