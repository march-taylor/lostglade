package eu.pb4.polymer.core.api.block;

import net.minecraft.class_2248;
import net.minecraft.class_2680;
import xyz.nucleoid.packettweaker.PacketContext;

public interface StatelessPolymerBlock extends PolymerBlock {
    /**
     * Returns block used on client for player
     *
     * @return Vanilla (or other) Block instance
     */
    class_2248 getPolymerBlock(class_2680 state, PacketContext context);

    @Override
    default class_2680 getPolymerBlockState(class_2680 state, PacketContext context) {
        return this.getPolymerBlock(state, context).method_9564();
    }
}
