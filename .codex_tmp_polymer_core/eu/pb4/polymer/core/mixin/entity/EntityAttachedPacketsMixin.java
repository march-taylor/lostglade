package eu.pb4.polymer.core.mixin.entity;

import eu.pb4.polymer.core.impl.interfaces.EntityAttachedPacket;
import net.minecraft.class_1297;
import net.minecraft.class_2596;
import net.minecraft.class_2604;
import net.minecraft.class_2616;
import net.minecraft.class_2684;
import net.minecraft.class_2726;
import net.minecraft.class_2739;
import net.minecraft.class_2744;
import net.minecraft.class_2777;
import net.minecraft.class_2781;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin({
        class_2684.class,
        class_2604.class,
        class_2739.class,
        class_2616.class,
        class_2777.class,
        class_2726.class,
        class_2744.class,
        class_2781.class
})
public class EntityAttachedPacketsMixin implements EntityAttachedPacket {
    @Unique
    private class_1297 polymer$entity = null;

    @Override
    public class_1297 polymer$getEntity() {
        return this.polymer$entity;
    }

    @Override
    public class_2596<?> polymer$setEntity(class_1297 entity) {
        this.polymer$entity = entity;
        return (class_2596<?>) this;
    }
}
