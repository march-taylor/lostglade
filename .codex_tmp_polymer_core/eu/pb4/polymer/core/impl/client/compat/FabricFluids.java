package eu.pb4.polymer.core.impl.client.compat;

import eu.pb4.polymer.core.api.utils.PolymerObject;
import eu.pb4.polymer.core.impl.ImplPolymerRegistryEvent;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandler;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandlerRegistry;
import net.minecraft.class_1058;
import net.minecraft.class_1920;
import net.minecraft.class_2338;
import net.minecraft.class_310;
import net.minecraft.class_3610;
import net.minecraft.class_7923;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
@Environment(EnvType.CLIENT)
public class FabricFluids {
    public static void register() {

        var renderer = new FluidRenderHandler() {
            @Override
            public class_1058[] getFluidSprites(@Nullable class_1920 view, @Nullable class_2338 pos, class_3610 state) {
                return new class_1058[] { class_310.method_1551().method_1554().method_68046().method_68511() };
            }
        };

        ImplPolymerRegistryEvent.iterateAndRegister(class_7923.field_41173, (fluid) -> {
            if (fluid instanceof PolymerObject && FluidRenderHandlerRegistry.INSTANCE.get(fluid) == null) {
                FluidRenderHandlerRegistry.INSTANCE.register(fluid, renderer);
            }
        });
    }
}
