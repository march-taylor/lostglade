package eu.pb4.polymer.core.mixin.block.storage;

import com.google.common.collect.ForwardingIterator;
import eu.pb4.polymer.core.api.block.PolymerBlockUtils;
import eu.pb4.polymer.core.impl.PolymerImplUtils;
import eu.pb4.polymer.core.impl.interfaces.PolymerBlockPosStorage;
import it.unimi.dsi.fastutil.shorts.ShortSet;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collections;
import java.util.Iterator;
import net.minecraft.class_11897;
import net.minecraft.class_1923;
import net.minecraft.class_1937;
import net.minecraft.class_2338;
import net.minecraft.class_2680;
import net.minecraft.class_2791;
import net.minecraft.class_2818;
import net.minecraft.class_2826;
import net.minecraft.class_2843;
import net.minecraft.class_3218;
import net.minecraft.class_4076;
import net.minecraft.class_5539;
import net.minecraft.class_6749;
import net.minecraft.class_6755;

@Mixin(class_2818.class)
public abstract class LevelChunkMixin extends class_2791 implements PolymerBlockPosStorage {

    public LevelChunkMixin(class_1923 pos, class_2843 upgradeData, class_5539 heightLimitView, class_11897 palettesFactory, long inhabitedTime, @Nullable class_2826[] sectionArray, @Nullable class_6749 blendingData) {
        super(pos, upgradeData, heightLimitView, palettesFactory, inhabitedTime, sectionArray, blendingData);
    }

    @Inject(
            method = "<init>(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/world/level/chunk/UpgradeData;Lnet/minecraft/world/ticks/LevelChunkTicks;Lnet/minecraft/world/ticks/LevelChunkTicks;J[Lnet/minecraft/world/level/chunk/LevelChunkSection;Lnet/minecraft/world/level/chunk/LevelChunk$PostLoadProcessor;Lnet/minecraft/world/level/levelgen/blending/BlendingData;)V",
            at = @At("TAIL")
    )
    private void polymer$polymerBlocksInit(class_1937 world, class_1923 pos, class_2843 upgradeData, class_6755 blockTickScheduler, class_6755 fluidTickScheduler, long inhabitedTime, class_2826[] sectionArrayInitializer, class_2818.class_6829 entityLoader, class_6749 blendingData, CallbackInfo ci) {
        if (world instanceof class_3218) {
            this.polymer$generatePolymerBlockSet();
        }
    }


    @Unique
    private void polymer$generatePolymerBlockSet() {
        for (var section : this.method_12006()) {
            if (section != null && !section.method_38292()) {
                var container = section.method_12265();
                if (container.method_19526(PolymerImplUtils.POLYMER_STATES::contains)) {
                    var storage = (PolymerBlockPosStorage) section;
                    class_2680 state;
                    for (byte x = 0; x < 16; x++) {
                        for (byte z = 0; z < 16; z++) {
                            for (byte y = 0; y < 16; y++) {
                                state = container.method_12321(x, y, z);
                                if (PolymerImplUtils.POLYMER_STATES.contains(state)) {
                                    storage.polymer$setSynced(x, y, z, PolymerBlockUtils.forceLightUpdates(state));
                                }
                            }
                        }
                    }
                }
            }
        }

    }


    @Inject(method = "setBlockState", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/LevelChunkSection;setBlockState(IIILnet/minecraft/world/level/block/state/BlockState;)Lnet/minecraft/world/level/block/state/BlockState;", shift = At.Shift.AFTER))
    private void polymer$addToList(class_2338 pos, class_2680 state, int flags, CallbackInfoReturnable<class_2680> cir) {
        if (PolymerImplUtils.POLYMER_STATES.contains(state)) {
            this.polymer$setSynced(pos.method_10263(), pos.method_10264(), pos.method_10260(), PolymerBlockUtils.forceLightUpdates(state));
        } else {
            this.polymer$removeSynced(pos.method_10263(), pos.method_10264(), pos.method_10260());
        }
    }

    @Override
    public @Nullable Iterator<class_2338.class_2339> polymer$iterator() {
        return new ForwardingIterator<>() {
            int current;
            Iterator<class_2338.class_2339> currentIterator = Collections.emptyIterator();

            @Override
            protected Iterator<class_2338.class_2339> delegate() {
                if (this.currentIterator == null || !this.currentIterator.hasNext()) {
                    var array = LevelChunkMixin.this.method_12006();
                    while (this.current < array.length) {
                        var id = this.current++;
                        var s = array[id];
                        var si = (PolymerBlockPosStorage) s;
                        if (s != null && si.polymer$hasAny()) {
                            this.currentIterator = si.polymer$iterator(class_4076.method_18681(LevelChunkMixin.this.method_12004(), LevelChunkMixin.this.method_31604(id)));
                            break;
                        }
                    }
                }

                return this.currentIterator;
            }
        };
    }

    @Override
    public void polymer$setSynced(int x, int y, int z, boolean lightSource) {
        this.polymer_getSectionStorage(y).polymer$setSynced(x, y, z, lightSource);
    }

    @Override
    public void polymer$removeSynced(int x, int y, int z) {
        this.polymer_getSectionStorage(y).polymer$removeSynced(x, y, z);
    }

    @Override
    public boolean polymer$isSynced(int x, int y, int z) {
        return this.polymer_getSectionStorage(y).polymer$isSynced(x, y, z);
    }

    @Override
    public boolean polymer$hasAny() {
        for (var s : this.method_12006()) {
            if (s != null && ((PolymerBlockPosStorage) s).polymer$hasAny()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public @Nullable ShortSet polymer$getBackendSet() {
        return null;
    }

    @Override
    public @Nullable Iterator<class_2338.class_2339> polymer$iterator(class_4076 sectionPos) {
        return null;
    }

    private PolymerBlockPosStorage polymer_getSectionStorage(int y) {
        return (PolymerBlockPosStorage) this.method_38259(this.method_31602(y));
    }
}
