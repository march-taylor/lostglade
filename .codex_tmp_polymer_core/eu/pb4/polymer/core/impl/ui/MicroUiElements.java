package eu.pb4.polymer.core.impl.ui;

import net.minecraft.class_124;
import net.minecraft.class_1799;
import net.minecraft.class_1802;
import net.minecraft.class_2561;
import net.minecraft.class_2583;
import net.minecraft.class_9334;

public class MicroUiElements {
    public static final MicroUi.PlayerClickAction EMPTY_ACTION = (player, slotIndex, button, actionType) -> { };

    public static final class_1799 EMPTY;
    public static final class_1799 BUTTON_PREVIOUS;
    public static final class_1799 BUTTON_PREVIOUS_LOCK;
    public static final class_1799 BUTTON_NEXT;
    public static final class_1799 BUTTON_NEXT_LOCK;
    public static final class_1799 BUTTON_BACK;
    public static final class_1799 BUTTON_SEARCH;

    static {
        EMPTY = class_1802.field_8871.method_7854();

        BUTTON_PREVIOUS = class_1802.field_8656.method_7854();
        BUTTON_PREVIOUS.method_57379(class_9334.field_49631, class_2561.method_43471("spectatorMenu.previous_page").method_10862(class_2583.field_24360.method_10978(false).method_10977(class_124.field_1060)));

        BUTTON_PREVIOUS_LOCK = class_1802.field_8736.method_7854();
        BUTTON_PREVIOUS_LOCK.method_57379(class_9334.field_49631, class_2561.method_43471("spectatorMenu.previous_page").method_10862(class_2583.field_24360.method_10978(false).method_10977(class_124.field_1063)));

        BUTTON_NEXT = class_1802.field_8656.method_7854();
        BUTTON_NEXT.method_57379(class_9334.field_49631, class_2561.method_43471("spectatorMenu.next_page").method_10862(class_2583.field_24360.method_10978(false).method_10977(class_124.field_1060)));

        BUTTON_NEXT_LOCK = class_1802.field_8736.method_7854();
        BUTTON_NEXT_LOCK.method_57379(class_9334.field_49631, class_2561.method_43471("spectatorMenu.next_page").method_10862(class_2583.field_24360.method_10978(false).method_10977(class_124.field_1063)));

        BUTTON_BACK = class_1802.field_8077.method_7854();
        BUTTON_BACK.method_57379(class_9334.field_49631, class_2561.method_43471("gui.back").method_10862(class_2583.field_24360.method_10978(false).method_10977(class_124.field_1061)));

        BUTTON_SEARCH = class_1802.field_8251.method_7854();
        BUTTON_SEARCH.method_57379(class_9334.field_49631, class_2561.method_43471("itemGroup.search").method_10862(class_2583.field_24360.method_10978(false)));
    }
}
