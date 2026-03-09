package eu.pb4.polymer.core.mixin.item;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import eu.pb4.polymer.common.api.ScopedOverride;
import eu.pb4.polymer.common.impl.CommonImplUtils;
import eu.pb4.polymer.core.api.block.PolymerBlockUtils;
import eu.pb4.polymer.core.api.entity.PolymerEntityUtils;
import eu.pb4.polymer.core.api.item.PolymerItem;
import eu.pb4.polymer.core.api.item.PolymerItemUtils;
import eu.pb4.polymer.core.api.utils.PolymerSyncedObject;
import eu.pb4.polymer.core.api.utils.PolymerUtils;
import eu.pb4.polymer.core.impl.PolymerImpl;
import eu.pb4.polymer.core.impl.interfaces.LastActionResultStorer;
import eu.pb4.polymer.core.impl.networking.BlockPacketUtil;
import eu.pb4.polymer.core.impl.networking.PolymerServerProtocol;
import eu.pb4.polymer.core.impl.other.ActionSource;
import eu.pb4.polymer.core.mixin.entity.LivingEntityAccessor;
import net.minecraft.class_1268;
import net.minecraft.class_1269;
import net.minecraft.class_1297;
import net.minecraft.class_1747;
import net.minecraft.class_1755;
import net.minecraft.class_1799;
import net.minecraft.class_1937;
import net.minecraft.class_2350;
import net.minecraft.class_2535;
import net.minecraft.class_2653;
import net.minecraft.class_2739;
import net.minecraft.class_2790;
import net.minecraft.class_2803;
import net.minecraft.class_2824;
import net.minecraft.class_2885;
import net.minecraft.class_2886;
import net.minecraft.class_2945;
import net.minecraft.class_3218;
import net.minecraft.class_3222;
import net.minecraft.class_3225;
import net.minecraft.class_3244;
import net.minecraft.class_3965;
import net.minecraft.class_6864;
import net.minecraft.class_7923;
import net.minecraft.class_8609;
import net.minecraft.class_8792;
import net.minecraft.class_9836;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.*;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.nucleoid.packettweaker.PacketContext;

import java.util.EnumSet;
import java.util.List;

@Mixin(value = class_3244.class, priority = 1200)
public abstract class ServerGamePacketListenerImpMixin extends class_8609 implements LastActionResultStorer {
    @Shadow
    public class_3222 player;

    @Shadow
    public abstract void handleUseItem(class_2886 packet);

    @Shadow
    private int ackBlockChangesUpTo;

    @Shadow public abstract void ackBlockChangesUpTo(int sequence);

    @Shadow protected abstract boolean updateAwaitingTeleport();

    @Unique
    private String polymerCore$language;
    @Unique
    @Nullable
    private class_1269 lastActionResult = null;
    @Unique
    @Nullable
    private ActionSource lastActionSource = null;

    @Unique
    private final EnumSet<class_1268> itemActionUsedHands = EnumSet.noneOf(class_1268.class);


    public ServerGamePacketListenerImpMixin(MinecraftServer server, class_2535 connection, class_8792 clientData) {
        super(server, connection, clientData);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void polymerCore$storeLanguage(MinecraftServer server, class_2535 connection, class_3222 player, class_8792 clientData, CallbackInfo ci) {
        this.polymerCore$language = clientData.comp_1961().comp_1951();
    }

    @Inject(method = "handleClientInformation", at = @At("TAIL"))
    private void polymerCore$resendLanguage(class_2803 packet, CallbackInfo ci) {
        if (CommonImplUtils.isMainPlayer(this.player)) {
            return;
        }

        if (!this.polymerCore$language.equals(packet.comp_1963().comp_1951())) {
            this.polymerCore$language = packet.comp_1963().comp_1951();
            PolymerServerProtocol.sendSyncPackets(player.field_13987, true);
            this.method_14364(new class_2790(class_6864.method_40105(this.player.method_51469().method_8503().method_46221())));
            this.player.method_14253().method_14904(this.player);
        }
    }

    @Inject(method = "handleUseItemOn", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V", shift = At.Shift.AFTER), cancellable = true)
    private void polymer$resendHandOnPlace(class_2885 packet, CallbackInfo ci) {
        class_1799 itemStack = this.player.method_5998(packet.method_12546());

        if (this.lastActionResult != null && this.lastActionResult != class_1269.field_5811) {
            ci.cancel();
            this.method_14364(new class_2653(this.player.field_7498.field_7763, this.player.field_7498.method_37422(), packet.method_12546() == class_1268.field_5808 ? 36 + this.player.method_31548().method_67532() : 45, itemStack));
            return;
        }

        if (PolymerSyncedObject.getSyncedObject(class_7923.field_41178, itemStack.method_7909()) instanceof PolymerItem polymerItem) {
            var data = PolymerItemUtils.getItemSafely(polymerItem, itemStack, PacketContext.create(this.player));
            if (data.item() instanceof class_1747 || data.item() instanceof class_1755) {
                this.method_14364(new class_2653(this.player.field_7498.field_7763, this.player.field_7498.method_37422(), packet.method_12546() == class_1268.field_5808 ? 36 + this.player.method_31548().method_67532() : 45, itemStack));
            }
        }
    }

    @WrapOperation(method = "handleUseItemOn", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayerGameMode;useItemOn(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;"))
    private class_1269 captureBlockInteraction(class_3225 instance, class_3222 player, class_1937 world, class_1799 stack, class_1268 hand, class_3965 hitResult, Operation<class_1269> operation, @Local class_3218 serverWorld) {
        var oldState = this.player.method_51469().method_8320(hitResult.method_17777());

        ScopedOverride soundOverride;
        if (PolymerBlockUtils.isIgnoringPlaySoundExceptedEntity(this.player, stack, hand, oldState, hitResult, serverWorld)) {
            soundOverride = PolymerUtils.ignorePlaySoundExclusion();
        } else {
            soundOverride = ScopedOverride.NO_OP;
        }

        var original = operation.call(instance, player, world, stack, hand, hitResult);
        soundOverride.close();

        if (PolymerBlockUtils.isPolymerBlockInteraction(this.player, stack, hand, oldState, hitResult, serverWorld, original)) {
            if (original instanceof class_1269.class_9860 success && success.comp_2909() == class_1269.class_9861.field_52427) {
                original = new class_1269.class_9860(class_1269.class_9861.field_52428, success.comp_2910());
            }

            this.lastActionResult = original;
            this.lastActionSource = ActionSource.BLOCK;
        }
        return original;
    }

    @Inject(method = "handleUseItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V", shift = At.Shift.AFTER), cancellable = true)
    private void preventItemUse(class_2886 packet, CallbackInfo ci) {
        if (this.lastActionResult != null && this.lastActionResult != class_1269.field_5811) {
            this.method_14364(new class_2653(this.player.field_7498.field_7763, this.player.field_7498.method_37422(), packet.method_12551() == class_1268.field_5808 ? 36 + this.player.method_31548().method_67532() : 45, this.player.method_5998(packet.method_12551())));
            this.field_45012.execute(() -> this.ackBlockChangesUpTo(packet.method_42081()));
            ci.cancel();
        }
    }


    @WrapOperation(method = "handleUseItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayerGameMode;useItem(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResult;"))
    private class_1269 captureItemInteraction(class_3225 instance, class_3222 player, class_1937 world, class_1799 stack, class_1268 hand, Operation<class_1269> operation, @Local class_3218 serverWorld) {
        ScopedOverride soundOverride;
        if (PolymerItemUtils.isIgnoringPlaySoundExceptedEntity(this.player, stack, hand, serverWorld)) {
            soundOverride = PolymerUtils.ignorePlaySoundExclusion();
        } else {
            soundOverride = ScopedOverride.NO_OP;
        }

        var original = operation.call(instance, player, world, stack, hand);
        soundOverride.close();

        if (PolymerItemUtils.isPolymerItemInteraction(this.player, stack, hand, serverWorld, original)) {
            if (original instanceof class_1269.class_9860 success && success.comp_2909() == class_1269.class_9861.field_52427) {
                original = new class_1269.class_9860(class_1269.class_9861.field_52428, success.comp_2910());
            }
            this.lastActionResult = original;
            this.lastActionSource = ActionSource.ITEM;
        }
        this.itemActionUsedHands.add(hand);
        return original;
    }

    @Inject(method = "handleInteract", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V", shift = At.Shift.AFTER), cancellable = true)
    private void preventEntityUse(class_2824 packet, CallbackInfo ci) {
        if (this.lastActionResult != null && this.lastActionResult != class_1269.field_5811) {
            this.method_14364(new class_2653(this.player.field_7498.field_7763, this.player.field_7498.method_37422(), this.player.method_31548().method_67532(), this.player.method_5998(class_1268.field_5808)));
            ci.cancel();
        }
    }

    @Inject(method = "handleClientTickEnd", at = @At("TAIL"))
    private void onClientTickEndedPolymer(class_9836 packet, CallbackInfo ci) {
        if (this.lastActionSource != ActionSource.ITEM && this.lastActionResult == class_1269.field_5811) {
            try {
                var seq = this.ackBlockChangesUpTo != -1 ? this.ackBlockChangesUpTo : Integer.MAX_VALUE;
                for (var hand : class_1268.values()) {
                    if (!this.itemActionUsedHands.contains(hand)) {
                        this.handleUseItem(new class_2886(hand, seq, this.player.method_36454(), this.player.method_36455()));
                    }
                }
                this.itemActionUsedHands.clear();
            } catch (Throwable e) {
                //noinspection CallToPrintStackTrace
                e.printStackTrace();
            }
        }

        if (this.lastActionSource != null) {
            var f = LivingEntityAccessor.getDATA_LIVING_ENTITY_FLAGS();
            this.method_14364(new class_2739(this.player.method_5628(),
                    List.of(class_2945.class_7834.method_46360(f, this.player.method_5841().method_12789(f))
                    )));

            this.lastActionSource = null;
        }

        this.lastActionResult = null;
    }


    @Inject(method = "handleUseItemOn", at = @At("TAIL"))
    private void polymer$updateMoreBlocks(class_2885 packet, CallbackInfo ci) {
        if (PolymerImpl.RESEND_BLOCKS_AROUND_CLICK) {
            var base = packet.method_12543().method_17777();
            for (class_2350 direction : class_2350.values()) {
                BlockPacketUtil.sendUpdate(this.player, base.method_10093(direction));
            }
        }
    }

    @Mixin(targets = "net/minecraft/server/network/ServerGamePacketListenerImpl$1")
    public static class EntityHandlerMixin {
        @Shadow
        @Final
        class_3244 field_28963;
        @Shadow
        @Final
        class_1297 val$target;

        @ModifyExpressionValue(method = "performInteraction", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl$EntityInteraction;run(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResult;"))
        private class_1269 captureIEntityInteraction(class_1269 original, @Local(argsOnly = true) class_1268 hand) {
            if (PolymerEntityUtils.isPolymerEntityInteraction(this.field_28963.field_14140, hand, this.field_28963.field_14140.method_5998(hand), (class_3218) this.val$target.method_73183(), this.val$target, original)) {
                ((LastActionResultStorer) this.field_28963).polymer$setLastActionResult(original);
                ((LastActionResultStorer) this.field_28963).polymer$setLastActionSource(ActionSource.ENTITY);
            }
            return original;
        }
    }


    public void polymer$setLastActionResult(class_1269 lastActionResult) {
        this.lastActionResult = lastActionResult;
    }

    @Override
    public void polymer$setLastActionSource(ActionSource source) {
        this.lastActionSource = source;
    }
}
