package eu.pb4.polymer.virtualentity.api.tracker;

import eu.pb4.polymer.virtualentity.mixin.accessors.EntityAccessor;
import java.util.Optional;
import net.minecraft.class_2561;
import net.minecraft.class_2940;
import net.minecraft.class_4050;

public class EntityTrackedData {
    public static final class_2940<Byte> FLAGS = EntityAccessor.getDATA_SHARED_FLAGS_ID();
    public static final class_2940<Integer> FROZEN_TICKS = EntityAccessor.getDATA_TICKS_FROZEN();
    public static final class_2940<Boolean> NO_GRAVITY = EntityAccessor.getDATA_NO_GRAVITY();
    public static final class_2940<class_4050> POSE = EntityAccessor.getDATA_POSE();
    public static final class_2940<Integer> AIR = EntityAccessor.getDATA_AIR_SUPPLY_ID();
    public static final class_2940<Optional<class_2561>> CUSTOM_NAME = EntityAccessor.getDATA_CUSTOM_NAME();
    public static final class_2940<Boolean> NAME_VISIBLE = EntityAccessor.getDATA_CUSTOM_NAME_VISIBLE();
    public static final class_2940<Boolean> SILENT = EntityAccessor.getDATA_SILENT();

    public static final int ON_FIRE_FLAG_INDEX = EntityAccessor.getFLAG_ONFIRE();
    public static final int SNEAKING_FLAG_INDEX = EntityAccessor.getFLAG_SHIFT_KEY_DOWN();
    public static final int SPRINTING_FLAG_INDEX = EntityAccessor.getFLAG_SPRINTING();
    public static final int SWIMMING_FLAG_INDEX = EntityAccessor.getFLAG_SWIMMING();
    public static final int INVISIBLE_FLAG_INDEX = EntityAccessor.getFLAG_INVISIBLE();
    public static final int GLOWING_FLAG_INDEX = EntityAccessor.getFLAG_GLOWING();
    public static final int GLIDING_FLAG_INDEX = EntityAccessor.getFLAG_FALL_FLYING();
}
