package eu.pb4.polymer.core.impl.networking;

import eu.pb4.polymer.core.api.utils.PolymerSyncUtils;
import eu.pb4.polymer.core.api.utils.PolymerUtils;
import eu.pb4.polymer.core.impl.ServerMetadataKeys;
import eu.pb4.polymer.core.impl.interfaces.PolymerGamePacketListenerExtension;
import eu.pb4.polymer.core.impl.networking.payloads.c2s.PolymerChangeTooltipC2SPayload;
import eu.pb4.polymer.networking.api.server.PolymerServerNetworking;
import net.minecraft.class_3244;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public class PolymerServerProtocolHandler {
    @SuppressWarnings("ResultOfMethodCallIgnored")
    public static void register() {
        PolymerServerNetworking.registerPlayHandler(PolymerChangeTooltipC2SPayload.class, PolymerServerProtocolHandler::handleTooltipChange);

        PolymerServerNetworking.ON_PLAY_SYNC.register((handler, x) -> {
            PolymerServerProtocol.sendSyncPackets(handler, true);
        });

        ServerMetadataKeys.setup();
        S2CPackets.SYNC_BLOCK.method_12836();
        C2SPackets.CHANGE_TOOLTIP.method_12836();
    }

    private static void handleTooltipChange(MinecraftServer server, class_3244 handler, PolymerChangeTooltipC2SPayload payload) {
        handler.method_32311().method_51469().method_8503().execute(() -> {
            PolymerGamePacketListenerExtension.of(handler).polymer$setAdvancedTooltip(payload.advanced());

            if (PolymerServerNetworking.getLastPacketReceivedTime(handler, C2SPackets.CHANGE_TOOLTIP) + 1000 < System.currentTimeMillis()) {
                PolymerSyncUtils.synchronizeCreativeTabs(handler);
                PolymerUtils.reloadInventory(handler.field_14140);
            }
        });
    }
}
