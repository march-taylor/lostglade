package eu.pb4.polymer.core.mixin.block;

import eu.pb4.polymer.core.api.block.PolymerBlockUtils;
import eu.pb4.polymer.core.api.utils.PolymerSyncedObject;
import eu.pb4.polymer.core.impl.PolymerImplUtils;
import eu.pb4.polymer.core.impl.interfaces.PolymerIdMapper;
import net.minecraft.class_1657;
import net.minecraft.class_1937;
import net.minecraft.class_2248;
import net.minecraft.class_2338;
import net.minecraft.class_2361;
import net.minecraft.class_2378;
import net.minecraft.class_2673;
import net.minecraft.class_2680;
import net.minecraft.class_3222;
import net.minecraft.class_6088;
import net.minecraft.class_7923;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(class_2248.class)
public class BlockMixin {
    @Shadow
    @Final
    public static class_2361<class_2680> BLOCK_STATE_REGISTRY;

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void polymer$enableMapping(CallbackInfo ci) {
        ((PolymerIdMapper<class_2680>) BLOCK_STATE_REGISTRY).polymer$setChecker(
                x -> PolymerSyncedObject.getSyncedObject(class_7923.field_41175, x.method_26204()) != null,
                x -> PolymerImplUtils.isServerSideSyncableEntry((class_2378<Object>) (Object) class_7923.field_41175, x.method_26204()),
                x -> "(Block) " + class_7923.field_41175.method_10221(x.method_26204())
        );
    }

    @Inject(method = "spawnDestroyParticles", at = @At("HEAD"))
    private void addPolymerParticles(class_1937 world, class_1657 player, class_2338 pos, class_2680 state, CallbackInfo ci) {
        if (player instanceof class_3222 serverPlayer
                && PolymerBlockUtils.shouldMineServerSide(serverPlayer, pos, state)) {
            serverPlayer.field_13987.method_14364(new class_2673(class_6088.field_31144, pos, class_2248.method_9507(state), false));
        }
    }
}
