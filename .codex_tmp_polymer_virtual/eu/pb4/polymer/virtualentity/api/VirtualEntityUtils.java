package eu.pb4.polymer.virtualentity.api;

import eu.pb4.polymer.common.api.PolymerCommonUtils;
import eu.pb4.polymer.common.impl.CompatStatus;
import eu.pb4.polymer.virtualentity.impl.EntityExt;
import eu.pb4.polymer.virtualentity.impl.compat.ImmersivePortalsUtils;
import eu.pb4.polymer.virtualentity.mixin.EntityPassengersSetS2CPacketAccessor;
import eu.pb4.polymer.virtualentity.mixin.SetCameraEntityS2CPacketAccessor;
import eu.pb4.polymer.virtualentity.mixin.accessors.EntityAccessor;
import eu.pb4.polymer.virtualentity.mixin.accessors.ClientboundSetEntityLinkPacketAccessor;
import eu.pb4.polymer.virtualentity.mixin.accessors.ClientboundSoundEntityPacketAccessor;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.class_10182;
import net.minecraft.class_10264;
import net.minecraft.class_1297;
import net.minecraft.class_243;
import net.minecraft.class_2596;
import net.minecraft.class_2602;
import net.minecraft.class_2684;
import net.minecraft.class_2734;
import net.minecraft.class_2740;
import net.minecraft.class_2752;
import net.minecraft.class_2765;
import net.minecraft.class_2818;
import net.minecraft.class_3218;
import net.minecraft.class_3222;
import net.minecraft.class_3414;
import net.minecraft.class_3419;
import net.minecraft.class_3532;
import net.minecraft.class_6880;
import org.jetbrains.annotations.Nullable;


public final class VirtualEntityUtils {
    private VirtualEntityUtils() {}
    public static int requestEntityId() {
        return EntityAccessor.getENTITY_COUNTER().incrementAndGet();
    }

    public static void addVirtualPassenger(class_1297 entity, int passengerId) {
        ((EntityExt) entity).polymerVE$getVirtualRidden().add(passengerId);
        ((EntityExt) entity).polymerVE$markVirtualRiddenDirty();
    }

    public static void addVirtualPassenger(class_1297 entity, int... passengerId) {
        for (var i : passengerId) {
            ((EntityExt) entity).polymerVE$getVirtualRidden().add(i);
        }
        ((EntityExt) entity).polymerVE$markVirtualRiddenDirty();
    }

    public static void removeVirtualPassenger(class_1297 entity, int passengerId) {
        ((EntityExt) entity).polymerVE$getVirtualRidden().rem(passengerId);
        ((EntityExt) entity).polymerVE$markVirtualRiddenDirty();
    }

    public static void removeVirtualPassenger(class_1297 entity, int... passengerId) {
        for (var i : passengerId) {
            ((EntityExt) entity).polymerVE$getVirtualRidden().rem(i);
        }
        ((EntityExt) entity).polymerVE$markVirtualRiddenDirty();
    }

    public static class_2740 createEntityAttachPacket(int attachedId, int holdingId) {
        var packet = PolymerCommonUtils.createUnsafe(class_2740.class);
        var ac = (ClientboundSetEntityLinkPacketAccessor) packet;
        ac.setSourceId(attachedId);
        ac.setDestId(holdingId);
        return packet;
    }
    public static class_2734 createSetCameraEntityPacket(int entityId) {
        var packet = PolymerCommonUtils.createUnsafe(class_2734.class);
        var ac = (SetCameraEntityS2CPacketAccessor) packet;
        ac.setCameraId(entityId);
        return packet;
    }

    public static class_2765 createPlaySoundFromEntityPacket(int entityId, class_6880<class_3414> sound, class_3419 category, float volume, float pitch, long seed) {
        var packet = PolymerCommonUtils.createUnsafe(class_2765.class);
        var ac = (ClientboundSoundEntityPacketAccessor) packet;
        ac.setId(entityId);
        ac.setSound(sound);
        ac.setSource(category);
        ac.setVolume(volume);
        ac.setPitch(pitch);
        ac.setSeed(seed);
        return packet;
    }


    @Nullable
    public static class_2596<class_2602> createMovePacket(int id, class_243 oldPos, class_243 newPos, boolean rotate, float yaw, float pitch) {
        var byteYaw = class_3532.method_15375(yaw * 256.0F / 360.0F);
        var bytePitch = class_3532.method_15375(pitch * 256.0F / 360.0F);
        boolean areDifferentEnough = oldPos.method_1020(newPos).method_1027() >= 7.62939453125E-6D;
        long newX = Math.round((newPos.field_1352 - oldPos.field_1352) * 4096.0D);
        long newY = Math.round((newPos.field_1351 - oldPos.field_1351) * 4096.0D);
        long newZ = Math.round((newPos.field_1350 - oldPos.field_1350) * 4096.0D);
        boolean bl5 = newX < -32768L || newX > 32767L || newY < -32768L || newY > 32767L || newZ < -32768L || newZ > 32767L;
        if (!bl5) {
            if ((!areDifferentEnough || !rotate)) {
                if (areDifferentEnough) {
                    return new class_2684.class_2685(id, (short) ((int) newX), (short) ((int) newY), (short) ((int) newZ), false);
                } else if (rotate) {
                    return new class_2684.class_2687(id, (byte) byteYaw, (byte) bytePitch, false);
                }
            } else {
                return new class_2684.class_2686(id, (short) ((int) newX), (short) ((int) newY), (short) ((int) newZ), (byte) byteYaw, (byte) bytePitch, false);
            }

            return null;
        } else {
            return new class_10264(id, new class_10182(newPos, class_243.field_1353, yaw, pitch), false);
        }
    }

    public static class_2752 createRidePacket(int id, IntList list) {
        return createRidePacket(id, list.toIntArray());
    }

    public static class_2752 createRidePacket(int id, int[] list) {
        var packet = PolymerCommonUtils.createUnsafe(class_2752.class);
        ((EntityPassengersSetS2CPacketAccessor) packet).setVehicle(id);
        ((EntityPassengersSetS2CPacketAccessor) packet).setPassengers(list);
        return packet;
    }

    public static boolean isPlayerTracking(class_3222 player, class_2818 chunk) {
        if (CompatStatus.IMMERSIVE_PORTALS) {
            return ImmersivePortalsUtils.isPlayerTracking(player, chunk);
        }

        if (player.method_51469() != chunk.method_12200()) {
            return false;
        }

        return player.method_52372().method_52356(chunk.method_12004().field_9181, chunk.method_12004().field_9180);
    }

    /**
     * Purely for compatibility with immersive portals.
     */
    public static void wrapCallWithContext(class_3218 world, Runnable call) {
        if (CompatStatus.IMMERSIVE_PORTALS) {
            ImmersivePortalsUtils.callRedirected(world, call);
        } else {
            call.run();
        }
    }
}
