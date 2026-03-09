package eu.pb4.polymer.core.mixin.client.block;

import eu.pb4.polymer.core.api.client.ClientPolymerBlock;
import eu.pb4.polymer.core.impl.client.interfaces.ClientBlockStorageInterface;
import net.minecraft.class_2812;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(class_2812.class)
public class EmptyLevelChunkMixin implements ClientBlockStorageInterface {
    @Override
    public void polymer$setClientBlock(int x, int y, int z, ClientPolymerBlock.State block) {

    }

    @Override
    public ClientPolymerBlock.State polymer$getClientBlock(int x, int y, int z) {
        return ClientPolymerBlock.NONE_STATE;
    }
}
