package eu.pb4.polymer.core.impl.other;


import net.minecraft.class_1836;
import org.jetbrains.annotations.Nullable;

public record PolymerTooltipType(boolean advanced, boolean creative) implements class_1836 {
    public static final PolymerTooltipType BASIC = new PolymerTooltipType(false, false);
    public static final PolymerTooltipType ADVANCED = new PolymerTooltipType(true, false);

    public PolymerTooltipType withCreative() {
        return new PolymerTooltipType(this.advanced, true);
    }

    public static PolymerTooltipType of(class_1836 context) {
        return new PolymerTooltipType(context.method_8035(), context.method_47370());
    }

    @Override
    public boolean method_8035() {
        return this.advanced;
    }

    @Override
    public boolean method_47370() {
        return this.creative;
    }
}
