package eu.pb4.polymer.core.impl.ui;

import eu.pb4.polymer.core.api.other.PolymerStatusEffect;
import eu.pb4.polymer.core.api.utils.PolymerSyncedObject;
import java.util.List;
import java.util.Optional;
import net.minecraft.class_10712;
import net.minecraft.class_124;
import net.minecraft.class_1292;
import net.minecraft.class_1799;
import net.minecraft.class_1802;
import net.minecraft.class_1814;
import net.minecraft.class_1844;
import net.minecraft.class_2561;
import net.minecraft.class_2583;
import net.minecraft.class_3222;
import net.minecraft.class_7923;
import net.minecraft.class_9334;


public class PotionUi extends MicroUi {
    private final class_3222 player;
    private int tickVal;

    public PotionUi(class_3222 player) {
        super(6);
        this.title(class_2561.method_43470("Status Effects"));
        this.player = player;
        this.drawUi();

        this.open(player);
    }

    private void drawUi() {
        int id = 0;
        this.clear();
        for (var effectInstance : this.player.method_6026()) {
            if (id == this.size) {
                return;
            }
            class_1799 icon;
            if (PolymerSyncedObject.getSyncedObject(class_7923.field_41174, effectInstance.method_5579().comp_349()) instanceof PolymerStatusEffect polymerStatusEffect) {
                icon = polymerStatusEffect.getPolymerIcon(effectInstance.method_5579().comp_349(), this.player);
                if (icon == null) {
                    continue;
                }
            } else {
                icon = class_1802.field_8574.method_7854();
                icon.method_57379(class_9334.field_49651, new class_1844(Optional.empty(), Optional.of(effectInstance.method_5579().comp_349().method_5556()), List.of(), Optional.empty()));
            }
            icon.method_57379(class_9334.field_56400, class_10712.field_56318.method_67215(class_9334.field_49651, true));
            icon.method_57379(class_9334.field_50073, class_1814.field_8906);
            icon.method_57379(class_9334.field_49631, class_2561.method_43473()
                    .method_10852(effectInstance.method_5579().comp_349().method_5560())
                    .method_10852(class_2561.method_43470(" (")
                            .method_10852(class_1292.method_5577(effectInstance, 1.0F, this.player.method_51469().method_8503().method_54833().method_54748()))
                            .method_27693(")")
                            .method_27692(class_124.field_1080))
                    .method_10862(class_2583.field_24360.method_10978(false))
            );

            //icon.getNbt().putInt("HideFlags", 255);
            this.slot(id++, icon);
        }
    }

    @Override
    protected void tick() {
        this.tickVal++;

        if (this.tickVal == 20) {
            this.tickVal = 0;
            this.drawUi();
        }
    }
}
