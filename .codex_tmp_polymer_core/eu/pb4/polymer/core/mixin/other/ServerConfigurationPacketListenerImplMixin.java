package eu.pb4.polymer.core.mixin.other;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import eu.pb4.polymer.common.api.PolymerCommonUtils;
import net.minecraft.class_2535;
import net.minecraft.class_2596;
import net.minecraft.class_8609;
import net.minecraft.class_8610;
import net.minecraft.class_8792;
import net.minecraft.class_9223;
import net.minecraft.class_9226;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;
import java.util.function.Consumer;

@Mixin(class_8610.class)
public abstract class ServerConfigurationPacketListenerImplMixin extends class_8609 {
    public ServerConfigurationPacketListenerImplMixin(MinecraftServer server, class_2535 connection, class_8792 clientData) {
        super(server, connection, clientData);
    }

    @WrapOperation(method = "handleSelectKnownPacks", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/config/SynchronizeRegistriesTask;handleResponse(Ljava/util/List;Ljava/util/function/Consumer;)V"))
    private void wrapWithContext(class_9223 instance, List<class_9226> clientKnownPacks, Consumer<class_2596<?>> sender, Operation<Void> original) {
        PolymerCommonUtils.executeWithNetworkingLogic(this, () -> {
            original.call(instance, clientKnownPacks, sender);
        });
    }
}
