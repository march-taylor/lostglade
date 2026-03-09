package eu.pb4.polymer.core.mixin.client;

import eu.pb4.polymer.common.impl.CommonImpl;
import eu.pb4.polymer.core.impl.PolymerImplUtils;
import eu.pb4.polymer.core.impl.client.ClientDebugFlags;
import eu.pb4.polymer.core.impl.client.InternalClientRegistry;
import eu.pb4.polymer.core.impl.client.networking.PolymerClientProtocol;
import eu.pb4.polymer.core.impl.networking.C2SPackets;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.class_11908;
import net.minecraft.class_2561;
import net.minecraft.class_309;
import net.minecraft.class_310;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(class_309.class)
public abstract class KeyboardHandlerMixin {
    @Shadow @Final private class_310 minecraft;

    @Shadow protected abstract void debugFeedbackComponent(class_2561 message);

    @Inject(method = "debugFeedback(Ljava/lang/String;)V", at = @At("HEAD"))
    private void polymer_catchChange(String key, CallbackInfo ci) {
        if (key.startsWith("debug.advanced_tooltips")) {
            InternalClientRegistry.delayAction(C2SPackets.CHANGE_TOOLTIP + "|pre", 1000, () -> {
                PolymerClientProtocol.sendTooltipContext(this.minecraft.method_1562());
            });
        }
    }


    @Inject(method = "handleDebugKeys", at = @At("TAIL"), cancellable = true)
    private void polymer_processF3(class_11908 keyInput, CallbackInfoReturnable<Boolean> cir) {
        if (!CommonImpl.DEVELOPER_MODE) {
            return;
        }

        var key = keyInput.comp_4795();

        if (key == GLFW.GLFW_KEY_0) {
            PolymerImplUtils.dumpRegistry();
            this.debugFeedbackComponent(class_2561.method_43470("Dumped Polymer Client registry!"));
            cir.setReturnValue(true);
        } else if (key == GLFW.GLFW_KEY_LEFT_BRACKET) {
            ClientDebugFlags.customItemModels = !ClientDebugFlags.customItemModels;
            this.debugFeedbackComponent(class_2561.method_43470("Component item models: " + ClientDebugFlags.customItemModels));
            cir.setReturnValue(true);
        } else if (key == GLFW.GLFW_KEY_RIGHT_BRACKET) {
            ClientDebugFlags.customFonts = !ClientDebugFlags.customFonts;
            this.debugFeedbackComponent(class_2561.method_43470("Custom fonts: " + ClientDebugFlags.customFonts));
            cir.setReturnValue(true);
        }
    }
}
