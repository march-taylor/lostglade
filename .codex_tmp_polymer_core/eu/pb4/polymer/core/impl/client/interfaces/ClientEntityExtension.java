package eu.pb4.polymer.core.impl.client.interfaces;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.class_2960;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
@Environment(EnvType.CLIENT)
public interface ClientEntityExtension {
    void polymer$setId(class_2960 id);
    class_2960 polymer$getId();
}
