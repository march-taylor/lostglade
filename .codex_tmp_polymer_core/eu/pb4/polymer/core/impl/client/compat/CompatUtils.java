package eu.pb4.polymer.core.impl.client.compat;

import eu.pb4.polymer.core.api.item.PolymerItemUtils;
import eu.pb4.polymer.core.impl.client.InternalClientRegistry;
import eu.pb4.polymer.core.impl.client.interfaces.ClientCreativeModeTabExtension;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.class_1761;
import net.minecraft.class_1792;
import net.minecraft.class_1799;
import net.minecraft.class_2520;
import net.minecraft.class_2960;
import net.minecraft.class_7706;
import net.minecraft.class_7708;
import net.minecraft.class_7923;
import net.minecraft.class_9279;
import net.minecraft.class_9334;

@ApiStatus.Internal
public class CompatUtils {
    public static boolean areSamePolymerType(class_1799 a, class_1799 b) {
        return Objects.equals(getItemId(a.method_7909(), a.method_58694(class_9334.field_49628)), getItemId(b.method_7909(), b.method_58694(class_9334.field_49628)));
    }

    public static boolean areSamePolymerType(Object ai, class_9279 a, Object bi, class_9279 b) {
        return Objects.equals(getItemId(ai, a), getItemId(bi, b));
    }

    public static boolean areEqualItems(class_1799 a, class_1799 b) {
        if (!areSamePolymerType(a, b)) {
            return false;
        }
        var nbtA = getBackingComponents(a);
        var nbtB = getBackingComponents(b);
        return Objects.equals(nbtA, nbtB);
    }

    @Nullable
    public static Map<class_2960, class_2520> getBackingComponents(class_1799 stack) {
        return PolymerItemUtils.getServerComponents(stack);
    }

    public static boolean isServerSide(class_1799 stack) {
        return PolymerItemUtils.getServerIdentifier(stack) != null;
    }

    public static boolean isServerSide(@Nullable class_9279 component) {
        return PolymerItemUtils.getServerIdentifier(component) != null;
    }

    @Nullable
    public static Object getKey(class_1799 stack) {
        return getKey(stack.method_58694(class_9334.field_49628));
    }
    public static Object getKey(@Nullable class_9279 component) {
        var id = PolymerItemUtils.getServerIdentifier(component);
        if (id == null) {
            return null;
        }

        if (InternalClientRegistry.ITEMS.contains(id)) {
            return InternalClientRegistry.ITEMS.getKey(id);
        }

        return class_7923.field_41178.method_63535(id);
    }

    private static class_2960 getItemId(Object item, @Nullable class_9279 nbtComponent) {
        var id = PolymerItemUtils.getServerIdentifier(nbtComponent);

        if (id == null && item instanceof class_1792 item1) {
            return item1.method_40131().method_40237().method_29177();
        }

        return id;
    }


    public static void iterateItems(Consumer<class_1799> consumer) {
        var stacks = class_7708.method_47572();

        for (var group : class_7706.method_47341()) {
            if (group.method_47312() != class_1761.class_7916.field_41052) {
                continue;
            }
            stacks.addAll(((ClientCreativeModeTabExtension) group).polymer$getStacksGroup());
            stacks.addAll(((ClientCreativeModeTabExtension) group).polymer$getStacksSearch());
        }

        for (var stack : stacks) {
            consumer.accept(stack);
        }
    }

    public static class_2960 getId(@Nullable class_9279 nbt) {
        return PolymerItemUtils.getServerIdentifier(nbt);
    }
}

