package eu.pb4.polymer.core.mixin.command;

import net.minecraft.class_2960;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(targets = "net/minecraft/network/protocol/game/ClientboundCommandsPacket$ArgumentNodeStub")
public class ArgumentNodeStubMixin {
    @Unique
    private static final class_2960 SUMMONABLE_ENTITIES = class_2960.method_60656("summonable_entities");
    @Unique
    private static final class_2960 ASK_SERVER = class_2960.method_60656("ask_server");

    @ModifyArg(method = "write(Lnet/minecraft/network/FriendlyByteBuf;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/FriendlyByteBuf;writeIdentifier(Lnet/minecraft/resources/Identifier;)Lnet/minecraft/network/FriendlyByteBuf;"))
    private class_2960 polymer$changeId(class_2960 id) {
        if (id.equals(SUMMONABLE_ENTITIES)) {
            return ASK_SERVER;
        }
        return id;
    }
}
