package eu.pb4.polymer.core.impl.other;

import net.minecraft.class_1267;
import net.minecraft.class_1792;
import net.minecraft.class_22;
import net.minecraft.class_7225;
import net.minecraft.class_9209;
import org.jspecify.annotations.Nullable;
import xyz.nucleoid.packettweaker.PacketContext;

public record PacketTooltipContext(PacketContext context) implements class_1792.class_9635 {
    @Override
    public class_7225.class_7874 method_59527() {
        return context.getRegistryWrapperLookup();
    }

    @Override
    public float method_59531() {
        if (context.getPlayer() != null) {
            return context.getPlayer().method_51469().method_54719().method_54748();
        }

        return 20;
    }

    @Override
    public @Nullable class_22 method_59529(class_9209 mapId) {
        try {
            if (context.getPlayer() != null) {
                return context.getPlayer().method_51469().method_17891(mapId);
            }
        } catch (Throwable e) {
            // Failed to get data.
        }

        return null;
    }

    @Override
    public boolean method_72500() {
        if (context.getPlayer() != null) {
            return context.getPlayer().method_51469().method_8407() == class_1267.field_5801;
        }


        return false;
    }
}

