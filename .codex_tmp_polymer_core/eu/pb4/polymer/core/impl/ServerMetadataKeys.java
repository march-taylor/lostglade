package eu.pb4.polymer.core.impl;

import eu.pb4.polymer.networking.api.server.PolymerServerNetworking;
import net.minecraft.class_155;
import net.minecraft.class_2481;
import net.minecraft.class_2497;
import net.minecraft.class_2519;
import net.minecraft.class_2960;
import org.jetbrains.annotations.ApiStatus;

import static eu.pb4.polymer.core.impl.PolymerImplUtils.id;

@ApiStatus.Internal
public class ServerMetadataKeys {
    public static final class_2960 MINECRAFT_VERSION = id("minecraft_version");
    public static final class_2960 MINECRAFT_PROTOCOL = id("minecraft_protocol");
    public static final class_2960 LIMITED_F3 = id("settings/limited_f3");

    public static void setup() {
        PolymerServerNetworking.setServerMetadata(MINECRAFT_VERSION, class_2519.method_23256(class_155.method_16673().comp_4025()));
        PolymerServerNetworking.setServerMetadata(MINECRAFT_PROTOCOL, class_2497.method_23247(class_155.method_31372()));
        PolymerServerNetworking.setServerMetadata(LIMITED_F3, class_2481.method_23234(false));
    }
}
