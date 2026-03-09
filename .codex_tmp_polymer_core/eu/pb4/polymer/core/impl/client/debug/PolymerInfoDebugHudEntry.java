package eu.pb4.polymer.core.impl.client.debug;

import eu.pb4.polymer.core.api.client.PolymerClientUtils;
import eu.pb4.polymer.core.impl.PolymerImpl;
import eu.pb4.polymer.core.impl.client.InternalClientRegistry;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.class_11630;
import net.minecraft.class_11632;
import net.minecraft.class_1937;
import net.minecraft.class_2818;
import net.minecraft.class_2960;

public class PolymerInfoDebugHudEntry implements class_11632 {
    private static final class_2960 SECTION_ID = class_2960.method_60655("polymer", "info");

    @Override
    public void method_72751(class_11630 lines, @Nullable class_1937 world, @Nullable class_2818 clientChunk, @Nullable class_2818 chunk) {
        if (InternalClientRegistry.serverHasPolymer && PolymerImpl.DISPLAY_DEBUG_INFO_CLIENT) {
            var list = new ArrayList<String>();
            list.add(InternalClientRegistry.debugServerInfo);
            list.add(InternalClientRegistry.debugRegistryInfo);
            lines.method_72744(SECTION_ID, list);
        }
    }
}
