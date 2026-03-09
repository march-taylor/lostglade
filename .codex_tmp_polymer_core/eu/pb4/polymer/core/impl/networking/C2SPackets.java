package eu.pb4.polymer.core.impl.networking;

import eu.pb4.polymer.core.impl.networking.payloads.c2s.PolymerChangeTooltipC2SPayload;
import eu.pb4.polymer.networking.api.ContextByteBuf;
import eu.pb4.polymer.networking.api.PolymerNetworking;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.class_2960;
import net.minecraft.class_8710;
import net.minecraft.class_9139;

import static eu.pb4.polymer.core.impl.PolymerImplUtils.id;

public class C2SPackets {
    public static final class_2960 CHANGE_TOOLTIP = id("other/change_tooltip");

    public static <T extends class_8710> void register(class_2960 id, class_9139<ContextByteBuf, T> codec, int... ver) {
        PolymerNetworking.registerC2SVersioned(id, IntList.of(ver), codec);
    }

    static {
        register(CHANGE_TOOLTIP, PolymerChangeTooltipC2SPayload.CODEC, 6);
    }
}
