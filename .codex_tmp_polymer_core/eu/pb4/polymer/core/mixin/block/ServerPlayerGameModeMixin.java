package eu.pb4.polymer.core.mixin.block;

import eu.pb4.polymer.core.api.block.PolymerBlock;
import eu.pb4.polymer.core.api.block.PolymerBlockUtils;
import eu.pb4.polymer.core.api.utils.PolymerSyncedObject;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.nucleoid.packettweaker.PacketContext;

import java.util.List;
import java.util.Objects;
import net.minecraft.class_1324;
import net.minecraft.class_2338;
import net.minecraft.class_2350;
import net.minecraft.class_2620;
import net.minecraft.class_2626;
import net.minecraft.class_2680;
import net.minecraft.class_2781;
import net.minecraft.class_2846;
import net.minecraft.class_3218;
import net.minecraft.class_3222;
import net.minecraft.class_3225;
import net.minecraft.class_5134;
import net.minecraft.class_7923;

@Mixin(class_3225.class)
public abstract class ServerPlayerGameModeMixin {
    @Final
    @Shadow
    protected class_3222 player;
    @Shadow
    protected class_3218 level;
    @Shadow
    private int destroyProgressStart;
    @Shadow public abstract void destroyAndAck(class_2338 pos, int sequence, String reason);

    @Unique
    private int polymer$sequence = 0;

    @Unique
    private float polymer$currentBreakingProgress;

    @Unique
    private int polymer$blockBreakingCooldown;

    @Unique
    private boolean polymer$hasMiningFatigue;

    @Unique
    @Nullable
    private class_2338 polymer$currentlyMinedPos;

    @Unique
    @Nullable
    private class_2680 polymer$currentlyMinedState;

    @Inject(method = "incrementDestroyProgress", at = @At("TAIL"))
    private void polymer_breakIfTakingTooLong(class_2680 state, class_2338 pos, int i, CallbackInfoReturnable<Float> cir) {
        if (this.polymer$shouldMineServerSide(pos, state)) {
            if (!pos.equals(this.polymer$currentlyMinedPos) && this.polymer$currentlyMinedPos != null) {
                PolymerBlockUtils.BREAKING_PROGRESS_UPDATE.invoke(x -> x.onBreakingProgressUpdate(player, this.polymer$currentlyMinedPos, this.polymer$currentlyMinedState, -1));
            }

            this.polymer$currentlyMinedState = state;
            this.polymer$currentlyMinedPos = pos;
            if (this.polymer$blockBreakingCooldown > 0) {
                --this.polymer$blockBreakingCooldown;
                return;
            }
            this.polymer$currentBreakingProgress += state.method_26165(this.player, this.player.method_51469(), pos);

            if (this.polymer$currentBreakingProgress >= 1.0F) {
                this.polymer$blockBreakingCooldown = 5;
                this.polymer$currentBreakingProgress = 0;
                this.player.field_13987.method_14364(new class_2620(-1, pos, -1));
                this.destroyAndAck(pos, this.polymer$sequence, "destroyed");
                this.player.field_13987.method_14364(new class_2626(this.level, pos));
                PolymerBlockUtils.BREAKING_PROGRESS_UPDATE.invoke(x -> x.onBreakingProgressUpdate(player, pos, state, -1));
                this.polymer$currentlyMinedState = null;
                this.polymer$currentlyMinedPos = null;
            } else {
                var k = this.polymer$currentBreakingProgress > 0.0F ? (int)(this.polymer$currentBreakingProgress * 10) : -1;
                this.player.field_13987.method_14364(new class_2620(-1, pos, k));
                polymer$sendMiningFatigue();
                PolymerBlockUtils.BREAKING_PROGRESS_UPDATE.invoke(x -> x.onBreakingProgressUpdate(player, pos, state, k));
            }
        } else if (this.polymer$hasMiningFatigue) {
            this.polymer$clearMiningEffect();
        }
    }

    @Inject(method = "handleBlockBreakAction", at = @At("HEAD"))
    private void polymer_packetReceivedInject(class_2338 pos, class_2846.class_2847 action, class_2350 direction, int worldHeight, int sequence, CallbackInfo ci) {
        this.polymer$sequence = sequence;
        var serverState = this.player.method_51469().method_8320(pos);
        if (this.polymer$shouldMineServerSide(pos, serverState)) {
            if (action == class_2846.class_2847.field_12968) {
                this.polymer$currentBreakingProgress = 0;
                this.polymer$currentlyMinedState = serverState;
                this.polymer$currentlyMinedPos = pos;
                var serverDelta = serverState.method_26165(this.player, this.level, pos);
                var clientState = serverState;
                if (PolymerSyncedObject.getSyncedObject(class_7923.field_41175, serverState.method_26204()) instanceof PolymerBlock virtualBlock) {
                    clientState = PolymerBlockUtils.getBlockStateSafely(virtualBlock, serverState, PacketContext.create(this.player));
                }

                float clientDelta = clientState.method_26165(this.player, this.level, pos);

                if (clientDelta >= 1.0f && serverDelta < 1.0f) {
                    this.player.field_13987.method_14364(new class_2626(pos, serverState));
                }
                PolymerBlockUtils.BREAKING_PROGRESS_UPDATE.invoke(x -> x.onBreakingProgressUpdate(player, pos, serverState, 0));

                if (serverDelta < 1.0f) {
                    polymer$sendMiningFatigue();
                }
            } else if (action == class_2846.class_2847.field_12971) {
                if (this.polymer$hasMiningFatigue) {
                    this.polymer$clearMiningEffect();
                }
                this.player.field_13987.method_14364(new class_2620(-1, pos, -1));
            }
        } else if (this.polymer$hasMiningFatigue) {
            this.polymer$clearMiningEffect();
        }
    }

    @Inject(method = "handleBlockBreakAction", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;destroyBlockProgress(ILnet/minecraft/core/BlockPos;I)V", ordinal = 0))
    private void polymer$clearBreakingTime(class_2338 pos, class_2846.class_2847 action, class_2350 direction, int worldHeight, int sequence, CallbackInfo ci) {
        this.polymer$currentBreakingProgress = 0;
    }

    @Inject(method = "handleBlockBreakAction", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayerGameMode;destroyAndAck(Lnet/minecraft/core/BlockPos;ILjava/lang/String;)V", ordinal = 1))
    private void polymer$clearBreakingTimeInstaMine(class_2338 pos, class_2846.class_2847 action, class_2350 direction, int worldHeight, int sequence, CallbackInfo ci) {
        this.polymer$currentBreakingProgress = 0;
    }

    @Inject(method = "handleBlockBreakAction", at = @At("TAIL"))
    private void polymer$enforceBlockBreakingCooldown(class_2338 pos, class_2846.class_2847 action, class_2350 direction, int worldHeight, int sequence, CallbackInfo ci) {
        if (this.polymer$shouldMineServerSide(pos, this.player.method_51469().method_8320(pos))) {
            if (action == class_2846.class_2847.field_12968) {
                this.destroyProgressStart += polymer$blockBreakingCooldown;
            }
        } else if (this.polymer$hasMiningFatigue) {
            this.polymer$clearMiningEffect();
        }
    }

    @Inject(method = "destroyAndAck", at = @At("HEAD"))
    private void polymer$clearEffects(class_2338 pos, int sequence, String reason, CallbackInfo ci) {
        this.polymer$clearMiningEffect();
    }

    @Unique
    private boolean polymer$shouldMineServerSide(class_2338 pos, class_2680 state) {
        return PolymerBlockUtils.shouldMineServerSide(this.player, pos, state);
    }

    @Unique
    private void polymer$sendMiningFatigue() {
        this.polymer$hasMiningFatigue = true;
        var x = new class_1324(class_5134.field_49076, (a) -> {});
        x.method_6192(-9999);
        this.player.field_13987.method_14364(new class_2781(this.player.method_5628(), List.of(x)));
    }

    @Unique
    private void polymer$clearMiningEffect() {
        this.polymer$hasMiningFatigue = false;
        this.player.field_13987.method_14364(new class_2781(this.player.method_5628(),
                List.of(Objects.requireNonNull(this.player.method_5996(class_5134.field_49076)))));

        if (this.polymer$currentlyMinedPos != null) {
            PolymerBlockUtils.BREAKING_PROGRESS_UPDATE.invoke(x -> x.onBreakingProgressUpdate(player, this.polymer$currentlyMinedPos, this.polymer$currentlyMinedState, -1));
            this.polymer$currentlyMinedPos = null;
            this.polymer$currentlyMinedState = null;
        }
    }

    @Redirect(method = "handleBlockBreakAction", at = @At(value = "INVOKE", target = "Lorg/slf4j/Logger;warn(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;)V"), require = 0)
    private void polymer$noOneCaresAboutMismatch(Logger instance, String s, Object o, Object o2) {
    }
}