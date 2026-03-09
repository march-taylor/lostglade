package eu.pb4.polymer.core.mixin.item.packet;

import eu.pb4.polymer.core.impl.interfaces.SkipCheck;
import net.minecraft.class_10302;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(class_10302.class_10311.class)
public abstract class TagSlotDisplayMixin implements SkipCheck {
    @Unique
    private boolean skipped = false;

    @Override
    public boolean polymer$skipped() {
        return this.skipped;
    }

    @Override
    public void polymer$setSkipped() {
        this.skipped = true;
    }
}
