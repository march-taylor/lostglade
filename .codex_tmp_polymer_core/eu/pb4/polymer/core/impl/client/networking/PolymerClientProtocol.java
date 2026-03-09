package eu.pb4.polymer.core.impl.client.networking;

import eu.pb4.polymer.core.impl.client.InternalClientRegistry;
import eu.pb4.polymer.core.impl.networking.C2SPackets;
import eu.pb4.polymer.core.impl.networking.payloads.c2s.PolymerChangeTooltipC2SPayload;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.class_2817;
import net.minecraft.class_310;
import net.minecraft.class_634;
import org.jetbrains.annotations.ApiStatus;


@ApiStatus.Internal
@Environment(EnvType.CLIENT)
public class PolymerClientProtocol {
    public static void sendTooltipContext(class_634 handler) {
        if (InternalClientRegistry.getClientProtocolVer(C2SPackets.CHANGE_TOOLTIP) != -1) {
            InternalClientRegistry.delayAction(C2SPackets.CHANGE_TOOLTIP.toString(), 200, () -> {
                handler.method_52787(new class_2817(new PolymerChangeTooltipC2SPayload(class_310.method_1551().field_1690.field_1827)));
            });
        }
    }
}
