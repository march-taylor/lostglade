package eu.pb4.polymer.core.impl.client;

import eu.pb4.polymer.common.impl.CommonImplUtils;
import eu.pb4.polymer.core.api.client.ClientPolymerItem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.class_10712;
import net.minecraft.class_1792;
import net.minecraft.class_1799;
import net.minecraft.class_1836;
import net.minecraft.class_2561;
import net.minecraft.class_6880;
import net.minecraft.class_7699;
import net.minecraft.class_9290;
import net.minecraft.class_9323;
import net.minecraft.class_9334;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

@ApiStatus.Internal
@ApiStatus.Experimental
@Environment(EnvType.CLIENT)
public class VirtualClientItem extends class_1792 {
    private ClientPolymerItem polymerItem;

    public static VirtualClientItem of(ClientPolymerItem item) {
        var obj = CommonImplUtils.createUnsafe(VirtualClientItem.class);
        obj.polymerItem = item;
        return obj;
    }

    @Override
    public class_6880.class_6883<class_1792> method_40131() {
        return this.polymerItem.visualStack().method_7909().method_40131();
    }

    @Override
    public class_1799 method_7854() {
        return this.polymerItem.visualStack().method_7972();
    }
    @Override
    public class_2561 method_7864(class_1799 stack) {
        return this.polymerItem.visualStack().method_7964();
    }

    public ClientPolymerItem getPolymerEntry() {
        return this.polymerItem;
    }

    @Override
    public class_9323 method_57347() {
        return this.polymerItem.visualStack().method_57353();
    }

    @Override
    public int method_7882() {
        return this.polymerItem.visualStack().method_7914();
    }

    @Override
    public void method_67187(class_1799 stack, class_9635 context, class_10712 displayComponent, Consumer<class_2561> textConsumer, class_1836 type) {
        if (this.polymerItem.visualStack().method_57826(class_9334.field_49632)) {
            this.polymerItem.visualStack().method_58695(class_9334.field_49632, class_9290.field_49340).method_57409(context, textConsumer, type, stack.method_57353());
        }
    }

    @Override
    public class_7699 method_45322() {
        return class_7699.method_45397();
    }

    private VirtualClientItem() {
        super(null);
    }
}
