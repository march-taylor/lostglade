package eu.pb4.polymer.core.api.client;

import eu.pb4.polymer.core.api.utils.PolymerRegistry;
import eu.pb4.polymer.core.impl.PolymerImplUtils;
import eu.pb4.polymer.core.impl.client.InternalClientRegistry;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.class_1799;
import net.minecraft.class_2246;
import net.minecraft.class_2248;
import net.minecraft.class_2561;
import net.minecraft.class_2680;
import net.minecraft.class_2960;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;

@Environment(EnvType.CLIENT)
public record ClientPolymerBlock(class_2960 identifier, int numId, float hardness, MiningDeltaLogic miningDeltaLogic, class_2561 name, class_2680 defaultBlockState,
                                 @Nullable class_2248 registryEntry, class_1799 displayStack) implements ClientPolymerEntry<class_2248> {
    public static final ClientPolymerBlock NONE = new ClientPolymerBlock(PolymerImplUtils.id("none"), 0, -2, MiningDeltaLogic.VANILLA, class_2561.method_43473(), class_2246.field_10124.method_9564(), null, class_1799.field_8037);
    public static final State NONE_STATE = new State(Collections.emptyMap(), NONE);
    public static final PolymerRegistry<ClientPolymerBlock> REGISTRY = InternalClientRegistry.BLOCKS;

    public ClientPolymerBlock(class_2960 identifier, int numId, class_2561 name, class_2680 defaultBlockState, @Nullable class_2248 registryEntry) {
        this(identifier, numId, name, defaultBlockState, registryEntry, defaultBlockState.method_26204().method_8389().method_7854());
    }

    public ClientPolymerBlock(class_2960 identifier, int numId, class_2561 name, class_2680 defaultBlockState) {
        this(identifier, numId, name, defaultBlockState, null);
    }

    public boolean isEmpty() {
        return this == NONE;
    }

    public ClientPolymerBlock(class_2960 identifier, int numId, class_2561 name, class_2680 defaultBlockState,
                              @Nullable class_2248 registryEntry, class_1799 displayStack) {
        this(identifier, numId, -2, MiningDeltaLogic.CUSTOM_SERVER, name, defaultBlockState, registryEntry, displayStack);
    }

    public record State(Map<String, String> states, ClientPolymerBlock block, @Nullable class_2680 blockState) {
        public State(Map<String, String> states, ClientPolymerBlock block) {
            this(states, block, null);
        }

        public boolean isEmpty() {
            return this == NONE_STATE;
        }
    }

    public enum MiningDeltaLogic {
        DEFAULT,
        TOOL_REQUIRED,
        CUSTOM_SERVER,
        VANILLA
    }
}
