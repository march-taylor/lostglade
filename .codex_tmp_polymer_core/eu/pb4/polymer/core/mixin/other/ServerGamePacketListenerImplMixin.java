package eu.pb4.polymer.core.mixin.other;

import eu.pb4.polymer.core.api.block.BlockMapper;
import eu.pb4.polymer.core.impl.ClientMetadataKeys;
import eu.pb4.polymer.core.impl.interfaces.PolymerGamePacketListenerExtension;
import eu.pb4.polymer.networking.api.PolymerNetworking;
import net.minecraft.class_2481;
import net.minecraft.class_2535;
import net.minecraft.class_3222;
import net.minecraft.class_3244;
import net.minecraft.class_8792;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.nucleoid.packettweaker.PacketContext;

import java.util.ArrayList;
import java.util.List;

@Mixin(class_3244.class)
public abstract class ServerGamePacketListenerImplMixin implements PolymerGamePacketListenerExtension {
    @Shadow
    public class_3222 player;
    @Unique
    private boolean polymer$advancedTooltip = false;
    @Unique
    private BlockMapper polymer$blockMapper;
    @Unique
    private final List<Runnable> polymer$afterSequence = new ArrayList<>();

    @Shadow
    public abstract class_3222 getPlayer();

    @Shadow private int ackBlockChangesUpTo;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void polymer$setupInitial(MinecraftServer server, class_2535 connection, class_3222 player, class_8792 clientData, CallbackInfo ci) {
        this.polymer$blockMapper = BlockMapper.getDefault(PacketContext.create(player));
        var advTool = PolymerNetworking.getMetadata(connection, ClientMetadataKeys.ADVANCED_TOOLTIP, class_2481.field_21025);

        this.polymer$advancedTooltip = advTool != null && advTool.method_10701() > 0;
    }


    @Override
    public BlockMapper polymer$getBlockMapper() {
        return this.polymer$blockMapper;
    }

    @Override
    public void polymer$setBlockMapper(BlockMapper mapper) {
        this.polymer$blockMapper = mapper;
    }

    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;send(Lnet/minecraft/network/protocol/Packet;)V", ordinal = 0, shift = At.Shift.AFTER))
    private void polymer$sendSequencePackets(CallbackInfo ci) {
        if (!this.polymer$afterSequence.isEmpty()) {
            for (var entry : this.polymer$afterSequence) {
                entry.run();
            }
            this.polymer$afterSequence.clear();
        }
    }

    @Override
    public void polymer$setAdvancedTooltip(boolean value) {
        this.polymer$advancedTooltip = value;
    }

    @Override
    public boolean polymer$advancedTooltip() {
        return this.polymer$advancedTooltip;
    }

    @Override
    public void polymer$delayAfterSequence(Runnable runnable) {
        if (this.ackBlockChangesUpTo == -1) {
            runnable.run();
        } else {
            this.polymer$afterSequence.add(runnable);
        }
    }
}
