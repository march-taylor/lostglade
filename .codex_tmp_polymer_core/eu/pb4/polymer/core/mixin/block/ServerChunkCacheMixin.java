package eu.pb4.polymer.core.mixin.block;

import eu.pb4.polymer.common.impl.CompatStatus;
import eu.pb4.polymer.core.api.block.PolymerBlockUtils;
import eu.pb4.polymer.core.impl.PolymerImpl;
import eu.pb4.polymer.core.impl.compat.ImmersivePortalsUtils;
import eu.pb4.polymer.core.impl.interfaces.PolymerBlockPosStorage;
import it.unimi.dsi.fastutil.objects.Object2LongArrayMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.BitSet;
import java.util.List;
import net.minecraft.class_1923;
import net.minecraft.class_1944;
import net.minecraft.class_2596;
import net.minecraft.class_2676;
import net.minecraft.class_2818;
import net.minecraft.class_3215;
import net.minecraft.class_3218;
import net.minecraft.class_3222;
import net.minecraft.class_3227;
import net.minecraft.class_3898;
import net.minecraft.class_4076;

@Mixin(class_3215.class)
public abstract class ServerChunkCacheMixin {

    @Unique
    private final Object2LongMap<class_4076> polymer$scheduledLightUpdates = new Object2LongArrayMap<>();
    @Shadow
    @Final
    public class_3898 chunkMap;
    @Shadow
    @Final
    private class_3218 level;
    @Shadow
    @Final
    private class_3227 lightEngine;

    @Shadow
    @Nullable
    public abstract class_2818 getChunkNow(int chunkX, int chunkZ);

    @Inject(method = "tickChunks()V", at = @At("TAIL"))
    private void polymer$sendChunkUpdates(CallbackInfo ci) {
        if (this.polymer$scheduledLightUpdates.isEmpty()) {
            return;
        }

        var currentTime = this.level.method_8503().method_3780();

        this.polymer$scheduledLightUpdates.object2LongEntrySet().removeIf(entry -> {
            var sectionPos = entry.getKey();
            var sendAfterTime = entry.getLongValue();
            if (currentTime <= sendAfterTime) {
                return false;
            }

            var chunk = this.getChunkNow(sectionPos.method_10263(), sectionPos.method_10260());
            if (chunk == null) {
                return true;
            }

            // This might not be the section that had a changing light source, but by now all sections that are affected
            // should have been scheduled to send to clients - so if marked, it's safe to clear
            var sections = chunk.method_12006();
            int sectionIndex = chunk.method_31603(sectionPos.method_18683());
            // As there is an additional light section above and below the world, there might not even be a block section here
            if (sectionIndex >= 0 && sectionIndex < sections.length) {
                if (sections[sectionIndex] instanceof PolymerBlockPosStorage section) {
                    section.polymer$setRequireLights(false);
                }
            }

            polymer$broadcastBlockLightForSection(sectionPos);

            return true;
        });
    }

    @Unique
    private List<class_3222> getPlayersWatchingChunk(class_1923 chunkPos) {
        if (CompatStatus.IMMERSIVE_PORTALS) {
            return ImmersivePortalsUtils.getPlayerTracking(this.level.method_27983(), chunkPos);
        } else {
            return this.chunkMap.method_17210(chunkPos, false);
        }
    }

    @Unique
    private void polymer$broadcastBlockLightForSection(class_4076 pos) {
        List<class_3222> players = getPlayersWatchingChunk(pos.method_18692());
        if (players.isEmpty()) {
            return;
        }
        BitSet dirtyBlockLightSections = new BitSet();
        dirtyBlockLightSections.set(pos.method_18683() - this.lightEngine.method_31929());
        class_2596<?> packet = new class_2676(pos.method_18692(), this.lightEngine, new BitSet(), dirtyBlockLightSections);
        for (class_3222 player : players) {
            player.field_13987.method_14364(packet);
        }
    }

    @Inject(method = "onLightUpdate", at = @At("TAIL"))
    private void polymer$scheduleChunkUpdates(class_1944 type, class_4076 pos, CallbackInfo ci) {
        if (type == class_1944.field_9282) {
            this.level.method_8503().execute(() -> {
                if (polymer$hasPendingLightUpdateAround(pos) || PolymerBlockUtils.SEND_LIGHT_UPDATE_PACKET.invoke((c) -> c.test(this.level, pos))) {
                    var sendAfterTime = this.level.method_8503().method_3780() + PolymerImpl.LIGHT_UPDATE_TICK_DELAY;
                    this.polymer$scheduledLightUpdates.put(pos, sendAfterTime);
                }
            });
        }
    }

    @Unique
    private boolean polymer$hasPendingLightUpdateAround(class_4076 pos) {
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                var chunk = this.getChunkNow(pos.method_10263() + x, pos.method_10260() + z);
                if (chunk != null) {
                    var sections = chunk.method_12006();
                    var max = Math.min(chunk.method_31603(pos.method_18683() + 1), sections.length - 1);

                    for (var i = Math.max(0, chunk.method_31603(pos.method_18683() - 1)); i <= max; i++) {
                        var section = sections[i];
                        if (section != null && !section.method_38292() && ((PolymerBlockPosStorage) section).polymer$requireLights()) {
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }
}
