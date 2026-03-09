package eu.pb4.polymer.core.api.block;

import net.minecraft.class_2248;
import net.minecraft.class_2680;
import xyz.nucleoid.packettweaker.PacketContext;

/**
 * Minimalistic implementation of PolymerBlock
*/
public class SimplePolymerBlock extends class_2248 implements PolymerBlock {
    private final class_2248 polymerBlock;

    public SimplePolymerBlock(class_2251 settings, class_2248 polymerBlock) {
        super(settings);
        this.polymerBlock = polymerBlock;
    }

    @Override
    public class_2680 getPolymerBlockState(class_2680 state, PacketContext context) {
        return this.polymerBlock.method_9564();
    }
}
