package eu.pb4.polymer.virtualentity.impl.compat;

import org.jetbrains.annotations.ApiStatus;
import qouteall.imm_ptl.core.chunk_loading.ImmPtlChunkTracking;
import qouteall.imm_ptl.core.network.PacketRedirection;

import java.util.List;
import net.minecraft.class_2818;
import net.minecraft.class_3218;
import net.minecraft.class_3222;


@ApiStatus.Internal
public class ImmersivePortalsUtils {
    public static boolean isPlayerTracking(class_3222 player, class_2818 chunk) {
        return ImmPtlChunkTracking.isPlayerWatchingChunk(player, chunk.method_12200().method_27983(), chunk.method_12004().field_9181, chunk.method_12004().field_9180);
    }

    public static List<class_3222> getPlayerTracking(class_2818 chunk) {
        return ImmPtlChunkTracking.getPlayersViewingChunk(chunk.method_12200().method_27983(), chunk.method_12004().field_9181, chunk.method_12004().field_9180, false);
    }

    public static void callRedirected(class_3218 world, Runnable runnable) {
        PacketRedirection.withForceRedirect(world, runnable);
    }
}
