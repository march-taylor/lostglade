package eu.pb4.polymer.core.impl.other;

import eu.pb4.polymer.common.impl.CommonImplUtils;
import eu.pb4.polymer.core.api.item.PolymerItemGroupUtils;
import it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet;
import java.util.Set;
import net.minecraft.class_10134;
import net.minecraft.class_9331;

public class PolymerComponentImpl {
    public static final Set<class_9331<?>> UNSYNCED_COMPONENTS = new ObjectOpenCustomHashSet<>(CommonImplUtils.IDENTITY_HASH);
    public static final Set<class_10134.class_10135<?>> UNSYNCED_CONSUME_EFFECTS = new ObjectOpenCustomHashSet<>(CommonImplUtils.IDENTITY_HASH);
}
