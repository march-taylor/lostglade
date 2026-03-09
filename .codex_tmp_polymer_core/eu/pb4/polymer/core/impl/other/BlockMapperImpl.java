package eu.pb4.polymer.core.impl.other;

import eu.pb4.polymer.core.api.block.BlockMapper;
import eu.pb4.polymer.core.api.block.PolymerBlock;
import eu.pb4.polymer.core.api.block.PolymerBlockUtils;
import eu.pb4.polymer.core.api.utils.PolymerSyncedObject;
import xyz.nucleoid.packettweaker.PacketContext;

import java.util.Map;
import net.minecraft.class_2246;
import net.minecraft.class_2680;
import net.minecraft.class_7923;

public class BlockMapperImpl {
    public static final BlockMapper DEFAULT = new BlockMapper() {
        @Override
        public class_2680 toClientSideState(class_2680 state, PacketContext player) {
            return PolymerSyncedObject.getSyncedObject(class_7923.field_41175, state.method_26204()) instanceof PolymerBlock polymerBlock ? PolymerBlockUtils.getBlockStateSafely(polymerBlock, state, player) : state;
        }

        @Override
        public String getMapperName() {
            return "polymer:default";
        }
    };

    public static BlockMapper getMap(Map<class_2680, class_2680> blockStateMap) {
        return new BlockMapper() {
            @Override
            public class_2680 toClientSideState(class_2680 state, PacketContext player) {
                var clientState = blockStateMap.get(state);
                return clientState != null ? DEFAULT.toClientSideState(clientState, player) : class_2246.field_10124.method_9564();
            }

            @Override
            public String getMapperName() {
                return "polymer:from_map";
            }
        };
    }

    public static BlockMapper createStack(BlockMapper overlay, BlockMapper base) {
        return new BlockMapper() {
            @Override
            public class_2680 toClientSideState(class_2680 state, PacketContext player) {
                return base.toClientSideState(overlay.toClientSideState(state, player), player);
            }

            @Override
            public String getMapperName() {
                return "polymer:stack [" + overlay.getMapperName() + " | " + base.getMapperName() + "]";
            }
        };
    }
}
