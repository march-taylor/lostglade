package eu.pb4.polymer.core.mixin.client.block;

import eu.pb4.polymer.core.api.client.ClientPolymerBlock;
import eu.pb4.polymer.core.impl.client.interfaces.ClientBlockStorageInterface;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.class_11897;
import net.minecraft.class_1923;
import net.minecraft.class_2791;
import net.minecraft.class_2818;
import net.minecraft.class_2826;
import net.minecraft.class_2843;
import net.minecraft.class_5539;
import net.minecraft.class_6749;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

@Environment(EnvType.CLIENT)
@Mixin(class_2818.class)
public abstract class LevelChunkMixin extends class_2791 implements ClientBlockStorageInterface {

    public LevelChunkMixin(class_1923 pos, class_2843 upgradeData, class_5539 heightLimitView, class_11897 palettesFactory, long inhabitedTime, @Nullable class_2826[] sectionArray, @Nullable class_6749 blendingData) {
        super(pos, upgradeData, heightLimitView, palettesFactory, inhabitedTime, sectionArray, blendingData);
    }

    @Override
    public void polymer$setClientBlock(int x, int y, int z, ClientPolymerBlock.State block) {
        var id = this.method_31602(y);

        if (id >= 0 && id < this.field_34545.length) {
            var section = this.method_38259(id);

            if (section != null && !section.method_38292()) {
                ((ClientBlockStorageInterface) section).polymer$setClientBlock(x, y, z, block);
            }
        }
    }

    @Override
    public ClientPolymerBlock.State polymer$getClientBlock(int x, int y, int z) {
        var id = this.method_31602(y);
        if (id >= 0 && id < this.field_34545.length) {
            var section = this.method_38259(id);

            if (section != null && !section.method_38292()) {
                return ((ClientBlockStorageInterface) section).polymer$getClientBlock(x, y, z);
            }
        }

        return ClientPolymerBlock.NONE_STATE;
    }
}
