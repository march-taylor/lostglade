package eu.pb4.polymer.core.impl.networking.entry;

import eu.pb4.polymer.networking.api.ContextByteBuf;
import io.netty.buffer.ByteBuf;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.class_1657;
import net.minecraft.class_1799;
import net.minecraft.class_1922;
import net.minecraft.class_2248;
import net.minecraft.class_2338;
import net.minecraft.class_2561;
import net.minecraft.class_2680;
import net.minecraft.class_2960;
import net.minecraft.class_4970;
import net.minecraft.class_7923;
import net.minecraft.class_8824;
import net.minecraft.class_9135;
import net.minecraft.class_9139;
import org.jetbrains.annotations.ApiStatus;

import java.util.IdentityHashMap;
import java.util.Map;


@ApiStatus.Internal
public record PolymerBlockEntry(class_2960 identifier, int numId, float hardness, MiningDeltaLogic miningDeltaLogic,
                                class_2561 text, class_2680 visual, class_1799 visualStack) {
    private static final class_9139<ByteBuf, class_2680> STATE = class_9135.method_56371(class_2248.field_10651);
    public static final class_9139<ContextByteBuf, PolymerBlockEntry> CODEC = class_9139.method_56438(PolymerBlockEntry::write, PolymerBlockEntry::read);
    private static final String REMAPPED_calcBlockBreakingDelta = FabricLoader.getInstance().isDevelopmentEnvironment() ? FabricLoader.getInstance().getMappingResolver().mapMethodName("intermediary", "net.minecraft.class_4970", "method_9594", "(Lnet/minecraft/class_2680;Lnet/minecraft/class_1657;Lnet/minecraft/class_1922;Lnet/minecraft/class_2338;)F") : "method_9594";

    private static final Map<Class<?>, Boolean> HAS_OVERRIDDEN_DELTA = new IdentityHashMap<>();

    public static PolymerBlockEntry of(class_2248 block) {
        return new PolymerBlockEntry(class_7923.field_41175.method_10221(block), class_7923.field_41175.method_10206(block), block.method_36555(),
                HAS_OVERRIDDEN_DELTA.getOrDefault(block.getClass(), Boolean.TRUE)
                        ? MiningDeltaLogic.CUSTOM_SERVER
                        : (block.method_9564().method_29291()
                        ? MiningDeltaLogic.TOOL_REQUIRED
                        : MiningDeltaLogic.DEFAULT),
                block.method_9518(), block.method_9564(), block.method_8389() != null ? block.method_8389().method_7854() : class_1799.field_8037);
    }

    public static void cacheCalcDeltaOverride(class_2248 block) {
        var value = HAS_OVERRIDDEN_DELTA.get(block.getClass());
        if (value != null) {
            return;
        }

        Class<?> clazz = block.getClass();

        while (clazz != class_4970.class) {
            try {
                clazz.getDeclaredMethod(REMAPPED_calcBlockBreakingDelta, class_2680.class, class_1657.class, class_1922.class, class_2338.class);
                HAS_OVERRIDDEN_DELTA.put(block.getClass(), true);
                return;
            } catch (Throwable e) {
                //
            }
            clazz = clazz.getSuperclass();
        }
        HAS_OVERRIDDEN_DELTA.put(block.getClass(), false);
    }

    public static PolymerBlockEntry read(ContextByteBuf buf) {
        var id = buf.method_10810();
        var numId = buf.method_10816();
        var name = class_8824.field_49668.decode(buf);
        var visual = STATE.decode(buf);
        var visualStack = class_1799.field_49268.decode(buf);
        float hardness = -2;
        var miningDeltaLogic = MiningDeltaLogic.CUSTOM_SERVER;
        if (buf.version() >= 12) {
            hardness = buf.readFloat();
            miningDeltaLogic = buf.method_10818(MiningDeltaLogic.class);
        }

        return new PolymerBlockEntry(id, numId, hardness, miningDeltaLogic, name, visual, visualStack);
    }

    public void write(ContextByteBuf buf) {
        buf.method_10812(identifier);
        buf.method_10804(numId);
        class_8824.field_49668.encode(buf, text);
        STATE.encode(buf, visual);
        class_1799.field_49268.encode(buf, this.visualStack);
        if (buf.version() >= 12) {
            buf.method_52941(this.hardness);
            buf.method_10817(this.miningDeltaLogic);
        }
    }

    public enum MiningDeltaLogic {
        DEFAULT,
        TOOL_REQUIRED,
        CUSTOM_SERVER,
        VANILLA
    }
}
