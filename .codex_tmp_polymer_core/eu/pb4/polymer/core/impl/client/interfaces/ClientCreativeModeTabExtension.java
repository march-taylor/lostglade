package eu.pb4.polymer.core.impl.client.interfaces;

import eu.pb4.polymer.core.impl.networking.payloads.s2c.PolymerItemGroupContentAddS2CPayload;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.class_1761;
import net.minecraft.class_1799;
import org.jetbrains.annotations.ApiStatus;

import java.util.Collection;
import java.util.List;

@ApiStatus.Internal
@Environment(EnvType.CLIENT)
@SuppressWarnings({"unused"})
public interface ClientCreativeModeTabExtension {
    void polymer$handleEntries(List<PolymerItemGroupContentAddS2CPayload.Entry> main, List<PolymerItemGroupContentAddS2CPayload.Entry> search);
    void polymer$clearStacks();
    Collection<class_1799> polymer$getStacksGroup();
    Collection<class_1799> polymer$getStacksSearch();

    void polymerCore$setPos(class_1761.class_7915 row, int slot);
    void polymerCore$setPage(int page);
    int polymerCore$getPage();
}
