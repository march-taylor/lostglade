package eu.pb4.polymer.core.impl.ui;

import net.minecraft.class_1263;
import net.minecraft.class_1657;
import net.minecraft.class_1661;
import net.minecraft.class_1703;
import net.minecraft.class_1713;
import net.minecraft.class_1735;
import net.minecraft.class_1799;
import net.minecraft.class_2561;
import net.minecraft.class_2653;
import net.minecraft.class_2765;
import net.minecraft.class_3222;
import net.minecraft.class_3414;
import net.minecraft.class_3419;
import net.minecraft.class_3908;
import net.minecraft.class_3917;
import net.minecraft.class_6880;
import net.minecraft.class_7923;
import org.jetbrains.annotations.Nullable;


/**
 * If you want to create ui in your mod you should just use sgui library instead!
 * It's more complete and has more functionality!
 *
 * This one is just simple util which most likely will be used only
 * for creative players and admins
 */
public class MicroUi {
    private final UiElement[] elements;
    private class_2561 title = class_2561.method_43473();
    private final class_3917<?> type;
    protected final int size;

    public MicroUi(int lines) {
        this.size = lines * 9;
        this.type = switch (lines) {
            case 1 -> class_3917.field_18664;
            case 2 -> class_3917.field_18665;
            case 3 -> class_3917.field_17326;
            case 4 -> class_3917.field_18666;
            case 5 -> class_3917.field_18667;
            default -> class_3917.field_17327;
        };
        this.elements = new UiElement[this.size];
    }

    public MicroUi title(class_2561 title) {
        this.title = title;
        return this;
    }

    public MicroUi slot(int index, class_1799 stack, PlayerClickAction action) {
        this.elements[index] = new UiElement(stack, action);
        return this;
    }

    protected void tick() {}

    public void open(class_3222 player) {
        player.method_17355(new class_3908() {
            @Override
            public class_2561 method_5476() {
                return MicroUi.this.title;
            }

            @Nullable
            @Override
            public class_1703 createMenu(int syncId, class_1661 inv, class_1657 player) {
                return new InternalScreenHandler(syncId, inv, player);
            }
        });
    }

    public MicroUi slot(int index, class_1799 stack) {
        return slot(index, stack, PlayerClickAction.NOOP);
    }

    public MicroUi clear() {
        for (int i = 0; i < this.size; i++) {
            this.elements[i] = null;
        }
        return this;
    }

    public static void playSound(class_3222 player, class_6880<class_3414> soundEvent) {
        playSound(player, soundEvent.comp_349());
    }
    public static void playSound(class_3222 player, class_3414 soundEvent) {
        player.field_13987.method_14364(new class_2765(
                class_7923.field_41172.method_47983(soundEvent), class_3419.field_15250, player,  0.2f, 1,
                player.method_59922().method_43055()
        ));
    }

    @FunctionalInterface
    public interface PlayerClickAction {
        PlayerClickAction NOOP = (a, b, c, d) -> {};
        void onClick(class_3222 player, int slotIndex, int button, class_1713 actionType);
    }

    private record UiElement(class_1799 stack, PlayerClickAction action) {
    }

    private class InternalScreenHandler extends class_1703 {
        protected InternalScreenHandler(int syncId, class_1661 playerInventory, class_1657 player) {
            super(MicroUi.this.type, syncId);

            var inv = new InternalInventory(MicroUi.this);
            for (int slot = 0; slot < MicroUi.this.size; slot++) {
                this.method_7621(new class_1735(inv, slot, 0, 0));
            }

            for(int i = 0; i < 3; ++i) {
                for(int j = 0; j < 9; ++j) {
                    this.method_7621(new class_1735(playerInventory, j + i * 9 + 9, 8 + j * 18, 84 + i * 18));
                }
            }

            for(int i = 0; i < 9; ++i) {
                this.method_7621(new class_1735(playerInventory, i, 8 + i * 18, 142));
            }
        }

        @Override
        public boolean method_7597(class_1657 player) {
            return true;
        }

        @Override
        public void method_7593(int slotIndex, int button, class_1713 actionType, class_1657 player) {
            if (slotIndex > -1 && slotIndex < MicroUi.this.size) {
                var slot = MicroUi.this.elements[slotIndex];
                if (slot != null) {
                    slot.action().onClick((class_3222) player, slotIndex, button, actionType);
                }
                ((class_3222) player).field_13987.method_14364(new class_2653(this.field_7763, 0, slotIndex, this.method_7611(slotIndex).method_7677()));
                ((class_3222) player).field_13987.method_14364(new class_2653(-1, 0, 0, this.method_34255()));
            } else if (actionType != class_1713.field_7794) {
                super.method_7593(slotIndex, button, actionType, player);
            }
        }

        @Override
        public void method_7623() {
            MicroUi.this.tick();
            super.method_7623();
        }

        @Override
        public class_1799 method_7601(class_1657 player, int slot) {
            return class_1799.field_8037;
        }
    }

    private record InternalInventory(MicroUi ui) implements class_1263 {
        @Override
        public int method_5439() {
            return ui.size;
        }

        @Override
        public boolean method_5442() {
            return false;
        }

        @Override
        public class_1799 method_5438(int slot) {
            return ui.elements[slot] != null ? ui.elements[slot].stack : class_1799.field_8037;
        }

        @Override
        public class_1799 method_5434(int slot, int amount) {
            return class_1799.field_8037;
        }

        @Override
        public class_1799 method_5441(int slot) {
            return class_1799.field_8037;
        }

        @Override
        public void method_5447(int slot, class_1799 stack) {

        }

        @Override
        public void method_5431() {

        }

        @Override
        public boolean method_5443(class_1657 player) {
            return true;
        }

        @Override
        public void method_5448() {

        }
    }
}
