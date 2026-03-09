package eu.pb4.polymer.virtualentity.mixin.block;

import eu.pb4.polymer.virtualentity.api.BlockWithElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.BlockBoundAttachment;
import eu.pb4.polymer.virtualentity.api.attachment.HolderAttachment;
import eu.pb4.polymer.virtualentity.impl.HolderAttachmentHolder;
import eu.pb4.polymer.virtualentity.impl.HolderHolder;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.*;
import net.minecraft.class_11897;
import net.minecraft.class_1923;
import net.minecraft.class_1937;
import net.minecraft.class_2338;
import net.minecraft.class_243;
import net.minecraft.class_2680;
import net.minecraft.class_2791;
import net.minecraft.class_2818;
import net.minecraft.class_2826;
import net.minecraft.class_2843;
import net.minecraft.class_3218;
import net.minecraft.class_5539;
import net.minecraft.class_6749;
import net.minecraft.class_6755;

@Mixin(class_2818.class)
public abstract class WorldChunkMixin extends class_2791 implements HolderAttachmentHolder {

    @Unique
    private final Collection<HolderAttachment> polymerVE$holders = new ArrayList<>();
    @Unique
    private final Map<class_2338, BlockBoundAttachment> polymerVE$posHolders = new Object2ObjectOpenHashMap<>();
    @Shadow
    @Final
    private class_1937 level;

    public WorldChunkMixin(class_1923 pos, class_2843 upgradeData, class_5539 heightLimitView, class_11897 palettesFactory, long inhabitedTime, @Nullable class_2826[] sectionArray, @Nullable class_6749 blendingData) {
        super(pos, upgradeData, heightLimitView, palettesFactory, inhabitedTime, sectionArray, blendingData);
    }

    @Shadow
    public abstract class_1937 getLevel();

    @Inject(method = "<init>(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/world/level/chunk/UpgradeData;Lnet/minecraft/world/ticks/LevelChunkTicks;Lnet/minecraft/world/ticks/LevelChunkTicks;J[Lnet/minecraft/world/level/chunk/LevelChunkSection;Lnet/minecraft/world/level/chunk/LevelChunk$PostLoadProcessor;Lnet/minecraft/world/level/levelgen/blending/BlendingData;)V", at = @At("TAIL"))
    private void polymer$polymerBlocksInit(class_1937 world, class_1923 pos, class_2843 upgradeData, class_6755 blockTickScheduler, class_6755 fluidTickScheduler, long inhabitedTime, class_2826[] sectionArrayInitializer, class_2818.class_6829 entityLoader, class_6749 blendingData, CallbackInfo ci) {
        if (world instanceof class_3218 serverWorld) {
            var sections = this.method_12006();
            for (int i = 0; i < sections.length; i++) {
                var section = sections[i];
                if (section != null && !section.method_38292()) {
                    var container = section.method_12265();
                    if (container.method_19526(x -> BlockWithElementHolder.get(x) != null)) {
                        class_2680 state;
                        for (byte x = 0; x < 16; x++) {
                            for (byte z = 0; z < 16; z++) {
                                for (byte y = 0; y < 16; y++) {
                                    state = container.method_12321(x, y, z);

                                    var blockWithElementHolder = BlockWithElementHolder.get(state);
                                    if (blockWithElementHolder != null) {
                                        var blockPos = pos.method_35231(x, this.method_31604(i) * 16 + y, z);

                                        var holder = blockWithElementHolder.createElementHolder(serverWorld, blockPos, state);
                                        if (holder != null) {
                                            BlockBoundAttachment.of(holder, serverWorld, (class_2818) (Object) this, blockPos, state);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Inject(method = "setBlockState", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;is(Lnet/minecraft/world/level/block/Block;)Z", ordinal = 0))
    private void polymerVE$removeOld(class_2338 pos, class_2680 state, int flags, CallbackInfoReturnable<class_2680> cir) {
        var x = this.polymerVE$posHolders.get(pos);
        if (x != null) {
            if (x.getBlockState().method_26204() != state.method_26204()) {
                this.polymerVE$removePosHolder(pos);
            } else {
                x.setBlockState(state);
            }
        }
    }

    @Inject(method = "setBlockState", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;isClientSide()Z", ordinal = 1, shift = At.Shift.BEFORE))
    private void polymerVE$addNew(class_2338 pos, class_2680 state, int flags, CallbackInfoReturnable<class_2680> cir) {
        var x = this.polymerVE$posHolders.get(pos);
        var blockWithElementHolder = BlockWithElementHolder.get(state);
        if (x == null && blockWithElementHolder != null && this.level instanceof class_3218 serverWorld) {
            var holder = blockWithElementHolder.createElementHolder(serverWorld, pos, state);
            if (holder != null) {
                new BlockBoundAttachment(holder, (class_2818) (Object) this, state, pos.method_10062(), class_243.method_24953(pos).method_1019(blockWithElementHolder.getElementHolderOffset(serverWorld, pos, state)), blockWithElementHolder.tickElementHolder(serverWorld, pos, state));
            }
        }
    }

    @Inject(method = "setLoaded", at = @At("TAIL"))
    private void polymerVE$onChunkUnload(boolean loadedToWorld, CallbackInfo ci) {
        if (loadedToWorld) {
            return;
        }

        var holders = this.polymerVE$getHolders();
        if (!holders.isEmpty()) {
            var arr = holders.toArray(HolderHolder.HOLDER_ATTACHMENTS);
            for (int i = 0; i < arr.length; i++) {
                var holder = arr[i];
                if (holder != null) {
                    holder.destroy();
                }
            }
        }
    }

    @Override
    public void polymerVE$addHolder(HolderAttachment holderAttachment) {
        this.polymerVE$holders.add(holderAttachment);
        if (holderAttachment instanceof BlockBoundAttachment blockBoundAttachment) {
            this.polymerVE$posHolders.put(blockBoundAttachment.getBlockPos(), blockBoundAttachment);
        }
    }

    @Override
    public void polymerVE$removeHolder(HolderAttachment holderAttachment) {
        this.polymerVE$holders.remove(holderAttachment);
        if (holderAttachment instanceof BlockBoundAttachment blockBoundAttachment) {
            this.polymerVE$posHolders.remove(blockBoundAttachment);
        }
    }

    @Override
    public BlockBoundAttachment polymerVE$getPosHolder(class_2338 pos) {
        return this.polymerVE$posHolders.get(pos);
    }

    @Override
    public void polymerVE$removePosHolder(class_2338 pos) {
        var x = this.polymerVE$posHolders.remove(pos);
        if (x != null) {
            this.polymerVE$holders.remove(x);
            x.destroy();
        }
    }

    @Override
    public Collection<HolderAttachment> polymerVE$getHolders() {
        return this.polymerVE$holders;
    }
}
