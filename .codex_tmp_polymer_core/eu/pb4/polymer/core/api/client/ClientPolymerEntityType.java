package eu.pb4.polymer.core.api.client;

import eu.pb4.polymer.core.api.utils.PolymerRegistry;
import eu.pb4.polymer.core.impl.client.InternalClientRegistry;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.class_1299;
import net.minecraft.class_2561;
import net.minecraft.class_2960;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public record ClientPolymerEntityType(class_2960 identifier, class_2561 name, @Nullable class_1299<?> registryEntry) implements ClientPolymerEntry<class_1299<?>> {

    public ClientPolymerEntityType (class_2960 identifier, class_2561 name) {
        this(identifier, name, null);
    }
    public static final PolymerRegistry<ClientPolymerEntityType> REGISTRY = InternalClientRegistry.ENTITY_TYPES;
}
