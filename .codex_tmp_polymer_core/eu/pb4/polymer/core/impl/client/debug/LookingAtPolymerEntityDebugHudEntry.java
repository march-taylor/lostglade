package eu.pb4.polymer.core.impl.client.debug;

import eu.pb4.polymer.core.api.client.ClientPolymerBlock;
import eu.pb4.polymer.core.api.client.PolymerClientUtils;
import eu.pb4.polymer.core.impl.client.InternalClientRegistry;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.class_11630;
import net.minecraft.class_11632;
import net.minecraft.class_124;
import net.minecraft.class_1937;
import net.minecraft.class_2818;
import net.minecraft.class_2960;
import net.minecraft.class_310;

public class LookingAtPolymerEntityDebugHudEntry implements class_11632 {
    private static final class_2960 SECTION_ID = class_2960.method_60656("looking_at_entity");

    @Override
    public void method_72751(class_11630 lines, @Nullable class_1937 world, @Nullable class_2818 clientChunk, @Nullable class_2818 chunk) {
        if (!InternalClientRegistry.enabled) {
            return;
        }

        class_310 minecraftClient = class_310.method_1551();
        var type = PolymerClientUtils.getEntityType(minecraftClient.field_1692);

        List<String> list = new ArrayList<>();
        if (type != null) {
            list.add(class_124.field_1073 + "Targeted Client Entity");
            list.add(String.valueOf(type.identifier()));
        }

        lines.method_72744(SECTION_ID, list);
    }

    @Override
    public boolean method_72753(boolean reducedDebugInfo) {
        return class_11632.super.method_72753(reducedDebugInfo) && InternalClientRegistry.enabled;
    }
}
