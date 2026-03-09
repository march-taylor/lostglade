package eu.pb4.polymer.core.impl.client.debug;

import eu.pb4.polymer.core.api.client.ClientPolymerBlock;
import eu.pb4.polymer.core.impl.client.InternalClientRegistry;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.class_11630;
import net.minecraft.class_11632;
import net.minecraft.class_124;
import net.minecraft.class_1297;
import net.minecraft.class_1937;
import net.minecraft.class_239;
import net.minecraft.class_2818;
import net.minecraft.class_2960;
import net.minecraft.class_310;
import net.minecraft.class_3965;

public class LookingAtPolymerBlockDebugHudEntry implements class_11632 {
    private static final class_2960 SECTION_ID = class_2960.method_60656("looking_at_block");

    @Override
    public void method_72751(class_11630 lines, @Nullable class_1937 world, @Nullable class_2818 clientChunk, @Nullable class_2818 chunk) {
        class_1297 entity = class_310.method_1551().method_1560();

        if (world == null || entity == null || !InternalClientRegistry.enabled) {
            return;
        }

        class_239 hitResult = entity.method_5745(20.0, 0.0F, false);
        List<String> list = new ArrayList<>();
        if (hitResult.method_17783() == class_239.class_240.field_1332) {
            var blockPos = ((class_3965)hitResult).method_17777();
            var block = InternalClientRegistry.getBlockAt(blockPos);
            var worldState = world.method_8320(blockPos);
            if (block != ClientPolymerBlock.NONE_STATE && block.blockState() != worldState) {
                list.add(class_124.field_1073 + "Targeted Client Block: " + blockPos.method_10263() + ", " + blockPos.method_10264() + ", " + blockPos.method_10260());
                list.add(block.block().identifier().toString());
                for (var entry : block.states().entrySet()) {
                    list.add(entry.getKey() + ": " + switch (entry.getValue()) {
                        case "true" -> class_124.field_1060 + "true";
                        case "false" -> class_124.field_1061 + "false";
                        default -> entry.getValue();
                    });
                }
            }
            lines.method_72744(SECTION_ID, list);
        }
    }

    @Override
    public boolean method_72753(boolean reducedDebugInfo) {
        return class_11632.super.method_72753(reducedDebugInfo) && InternalClientRegistry.enabled;
    }
}
