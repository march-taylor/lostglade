package eu.pb4.polymer.core.impl.interfaces;

import eu.pb4.polymer.core.api.block.BlockMapper;
import net.minecraft.class_3222;
import net.minecraft.class_3244;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
@SuppressWarnings({"unused"})
public interface PolymerGamePacketListenerExtension extends PolymerCommonPacketListenerExtension {
    boolean polymer$advancedTooltip();
    void polymer$setAdvancedTooltip(boolean value);

    BlockMapper polymer$getBlockMapper();
    void polymer$setBlockMapper(BlockMapper mapper);

    static PolymerGamePacketListenerExtension of(class_3222 player) {
        return (PolymerGamePacketListenerExtension) player.field_13987;
    }

    static PolymerGamePacketListenerExtension of(class_3244 handler) {
        return (PolymerGamePacketListenerExtension) handler;
    }

    void polymer$delayAfterSequence(Runnable runnable);
}
