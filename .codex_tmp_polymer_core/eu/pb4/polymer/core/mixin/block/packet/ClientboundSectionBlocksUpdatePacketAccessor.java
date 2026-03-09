package eu.pb4.polymer.core.mixin.block.packet;

import net.minecraft.class_2637;
import net.minecraft.class_2680;
import net.minecraft.class_4076;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(class_2637.class)
public interface ClientboundSectionBlocksUpdatePacketAccessor {
    @Accessor("sectionPos")
    class_4076 polymer_getSectionPos();

    @Accessor("positions")
    short[] polymer_getPositions();

    @Accessor("states")
    class_2680[] polymer_getBlockStates();
}
