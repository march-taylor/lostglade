package eu.pb4.polymer.core.mixin.client;

import eu.pb4.polymer.common.impl.client.ClientUtils;
import eu.pb4.polymer.core.api.utils.PolymerUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.class_3262;
import net.minecraft.class_3304;
import net.minecraft.class_3902;
import net.minecraft.class_4011;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Environment(EnvType.CLIENT)
@Mixin(class_3304.class)
public class ReloadableResourceManagerMixin {
    @Inject(method = "createReload", at = @At("RETURN"))
    private void polymer$onReload(Executor prepareExecutor, Executor applyExecutor, CompletableFuture<class_3902> initialStage, List<class_3262> packs, CallbackInfoReturnable<class_4011> cir) {
        var player = ClientUtils.getPlayer();
        if (player != null) {
            player.method_51469().method_8503().execute(() -> PolymerUtils.reloadWorld(player));
        }
    }
}
