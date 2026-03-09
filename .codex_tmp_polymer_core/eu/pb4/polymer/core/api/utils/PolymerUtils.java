package eu.pb4.polymer.core.api.utils;

import com.google.common.collect.ImmutableMultimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.datafixers.util.Either;
import eu.pb4.polymer.common.api.PolymerCommonUtils;
import eu.pb4.polymer.common.api.ScopedOverride;
import eu.pb4.polymer.common.impl.CommonImpl;
import eu.pb4.polymer.core.api.block.PolymerBlockUtils;
import eu.pb4.polymer.core.api.entity.PolymerEntityUtils;
import eu.pb4.polymer.core.api.item.PolymerItemUtils;
import eu.pb4.polymer.core.api.other.PolymerComponent;
import eu.pb4.polymer.core.impl.PolymerImpl;
import eu.pb4.polymer.core.impl.PolymerImplUtils;
import eu.pb4.polymer.core.impl.interfaces.PolymerGamePacketListenerExtension;
import eu.pb4.polymer.core.impl.networking.PacketPatcher;
import eu.pb4.polymer.core.mixin.StaticAccessor;
import eu.pb4.polymer.core.mixin.block.packet.ServerMapAccessor;
import eu.pb4.polymer.core.mixin.entity.ServerLevelAccessor;
import eu.pb4.polymer.rsm.api.RegistrySyncUtils;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.*;
import net.minecraft.class_1299;
import net.minecraft.class_1320;
import net.minecraft.class_156;
import net.minecraft.class_1799;
import net.minecraft.class_1802;
import net.minecraft.class_1836;
import net.minecraft.class_1937;
import net.minecraft.class_2378;
import net.minecraft.class_2591;
import net.minecraft.class_2596;
import net.minecraft.class_2960;
import net.minecraft.class_3222;
import net.minecraft.class_3244;
import net.minecraft.class_3852;
import net.minecraft.class_3902;
import net.minecraft.class_5321;
import net.minecraft.class_6880;
import net.minecraft.class_7696;
import net.minecraft.class_8609;
import net.minecraft.class_8685;
import net.minecraft.class_9296;
import net.minecraft.class_9331;
import net.minecraft.class_9334;

/**
 * General use case utils that can be useful in multiple situations
 */
public final class PolymerUtils {
    public static final String ID = "polymer";
    public static final String NO_TEXTURE_HEAD_VALUE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNGUyY2UzMzcyYTNhYzk3ZmRkYTU2MzhiZWYyNGIzYmM0OWY0ZmFjZjc1MWZlOWNhZDY0NWYxNWE3ZmI4Mzk3YyJ9fX0=";
    private static final Set<class_7696> ENABLED_FEATURE_FLAGS = new HashSet<>();
    private static final Set<class_5321<? extends class_2378<?>>> SERVER_ONLY_REGISTRIES = new HashSet<>();

    private PolymerUtils() {
    }

    public static String getVersion() {
        return CommonImpl.VERSION;
    }

    public static void addClientEnabledFeatureFlags(class_7696... flags) {
        ENABLED_FEATURE_FLAGS.addAll(List.of(flags));
    }

    public static Collection<class_7696> getClientEnabledFeatureFlags() {
        return ENABLED_FEATURE_FLAGS;
    }

    public static ScopedOverride ignorePlaySoundExclusion() {
        if (PolymerImplUtils.IGNORE_PLAY_SOUND_EXCLUSION.get() != null) {
            return ScopedOverride.NO_OP;
        }
        PolymerImplUtils.IGNORE_PLAY_SOUND_EXCLUSION.set(class_3902.field_17274);
        return PolymerImplUtils.IGNORE_PLAY_SOUND_EXCLUSION::remove;
    }

    /**
     * Schedules a packet sending
     *
     * @param handler  used for packet sending
     * @param packet   sent packet
     * @param duration time (in ticks) waited before packet is send
     */
    public static void schedulePacket(class_3244 handler, class_2596<?> packet, int duration) {
        ((PolymerGamePacketListenerExtension) handler).polymer$schedulePacket(packet, duration);
    }

    /**
     * Resends world to player. It's useful to run this after player changes resource packs
     */
    public static void reloadWorld(class_3222 player) {
        player.method_51469().method_8503().execute(() -> {
            PolymerImplUtils.IS_RELOADING_WORLD.set(class_3902.field_17274);
            try {
                player.field_7512.method_34252();

                var world = player.method_51469();
                var tacsAccess = ((ServerMapAccessor) world.method_14178().field_17254);

                for (var e : ((ServerLevelAccessor) world).polymer_getEntityManager().method_31841().method_31803()) {
                    var tracker = tacsAccess.polymer$getEntityTrackers().get(e.method_5628());
                    if (tracker != null) {
                        tracker.method_18733(player);
                    }
                }


                player.method_52372().method_52363((chunkPos) -> {
                    var chunk = world.method_8497(chunkPos.field_9181, chunkPos.field_9180);
                    player.field_13987.field_45026.method_52387(player, chunk.method_12004());
                    player.field_13987.field_45026.method_52390(chunk);
                });
            } catch (Throwable e) {
                PolymerImpl.LOGGER.warn("Failed to reload player's world view!", e);
            }

            PolymerImplUtils.IS_RELOADING_WORLD.remove();
        });
    }

    /**
     * Resends inventory to player
     */
    public static void reloadInventory(class_3222 player) {
        player.field_7512.method_34252();
    }

    /**
     * Returns current TooltipContext of player,
     */
    public static class_1836 getTooltipType(@Nullable class_3222 player) {
        return PolymerImplUtils.getTooltipContext(player);
    }

    /**
     * Returns current TooltipContext of player,
     */
    public static class_1836 getCreativeTooltipType(@Nullable class_3222 player) {
        return PolymerImplUtils.getTooltipContext(player).withCreative();
    }


    public static class_9296 createProfileComponent(String value) {
        return createProfileComponent(value, null);
    }
    public static class_9296 createProfileComponent(String value, @Nullable String signature) {
        var profile = new PropertyMap(ImmutableMultimap.of("textures", new Property("textures", value, signature)));
        return class_9296.method_73307(new GameProfile(class_156.field_25140, "", profile));
    }

    public static class_9296 createProfileComponent(class_8685.class_11892 override) {
        return StaticAccessor.createStatic(Either.right(class_9296.class_11757.field_63032), override);
    }


    public static class_1799 createPlayerHead(String value) {
        return createPlayerHead(value, null);
    }

    public static class_1799 createPlayerHead(String value, String signature) {
        var stack = new class_1799(class_1802.field_8575);
        stack.method_57379(class_9334.field_49617, createProfileComponent(value, signature));
        return stack;
    }

    public static class_1937 getFakeWorld() {
        return PolymerCommonUtils.getFakeWorld();
    }

    @Nullable
    public static Path getClientJar() {
        return PolymerCommonUtils.getClientJar();
    }

    @SuppressWarnings("unchecked")
    @Deprecated(forRemoval = true)
    public static boolean isServerOnly(Object obj) {
        return obj instanceof PolymerObject
                || (obj instanceof class_1799 stack && PolymerItemUtils.isPolymerServerItem(stack))
                || (obj instanceof class_1299<?> type && PolymerEntityUtils.isPolymerEntityType(type))
                || (obj instanceof class_2591<?> typeBE && PolymerBlockUtils.isPolymerBlockEntityType(typeBE))
                || (obj instanceof class_6880<?> entry && (
                        (entry.comp_349() instanceof class_1320 && PolymerEntityUtils.isPolymerEntityAttribute((class_6880<class_1320>) entry))))
                || (obj instanceof class_9331<?> componentType && PolymerComponent.isPolymerComponent(componentType))
                || (obj instanceof class_3852 villagerProfession && PolymerEntityUtils.getPolymerProfession(villagerProfession) != null);
    }

    public static <T> boolean isServerOnly(class_2378<T> registry, T obj) {
        return RegistrySyncUtils.isServerEntry(registry, obj) || isServerOnly(obj);
    }

    public static boolean hasResourcePack(@Nullable class_3222 player, UUID uuid) {
        return PolymerCommonUtils.hasResourcePack(player, uuid);
    }

    public static class_2596<?> replacePacket(class_8609 handler, class_2596<?> packet) {
        return PacketPatcher.replace(handler, packet);
    }

    public static boolean shouldPreventPacket(class_8609 handler, class_2596<?> packet) {
        return PacketPatcher.prevent(handler, packet);
    }

    public static boolean isServerOnlyRegistry(class_5321<? extends class_2378<?>> key) {
        return SERVER_ONLY_REGISTRIES.contains(key);
    }

    public static void markAsServerOnlyRegistry(class_5321<? extends class_2378<?>> key) {
        if (key.method_29177().method_12836().equals(class_2960.field_33381)) {
            return;
        }
        SERVER_ONLY_REGISTRIES.add(key);
    }
}
