package eu.pb4.polymer.core.impl.ui;

import eu.pb4.polymer.core.api.item.PolymerItemGroupUtils;
import net.minecraft.class_1713;
import net.minecraft.class_1761;
import net.minecraft.class_1799;
import net.minecraft.class_2371;
import net.minecraft.class_3222;
import net.minecraft.class_3417;
import net.minecraft.class_7706;
import net.minecraft.class_7708;

public class CreativeTabUi extends MicroUi {
    private static final int ITEMS_PER_PAGE = 45;

    private final class_1761 itemGroup;
    private final class_2371<class_1799> items;
    private int page;

    public CreativeTabUi(class_3222 player, class_1761 itemGroup) {
        super(6);
        this.title(itemGroup.method_7737());
        this.itemGroup = itemGroup;
        this.items = class_2371.method_10211();
        if (itemGroup == class_7706.method_47344()) {
            var set = class_7708.method_47572();

            for (var group : PolymerItemGroupUtils.getItemGroups(player)) {
                set.addAll(PolymerItemGroupUtils.getContentsFor(player, group).search());
            }
            this.items.addAll(set);
        } else {
            this.items.addAll(PolymerItemGroupUtils.getContentsFor(player, itemGroup).main());
        }
        this.page = 0;
        this.drawUi();

        this.open(player);
    }

    private void drawUi() {
        int start = page * ITEMS_PER_PAGE;
        int end = Math.min((page + 1) * ITEMS_PER_PAGE, this.items.size());
        for (int i = start; i < end; i++) {
            var stack = this.items.get(i);
            this.slot(i - start, stack, (player, slotIndex, button, actionType) -> {
                onMouseClick(stack, slotIndex, button, actionType, player);
            });
        }

        for (int i = Math.max(end - start, 0); i < ITEMS_PER_PAGE; i++) {
            this.slot(i, class_1799.field_8037, (player, slotIndex, button, actionType) -> {
                onMouseClick(class_1799.field_8037, slotIndex, button, actionType, player);
            });
        }

        this.slot(ITEMS_PER_PAGE + 0, MicroUiElements.EMPTY, MicroUiElements.EMPTY_ACTION);

        if (this.page == 0) {
            this.slot(ITEMS_PER_PAGE + 1, MicroUiElements.BUTTON_PREVIOUS_LOCK, MicroUiElements.EMPTY_ACTION);
        } else {
            this.slot(ITEMS_PER_PAGE + 1, MicroUiElements.BUTTON_PREVIOUS, (player, slotIndex, button, actionType) -> {
                CreativeTabUi.this.page--;
                playSound(player, class_3417.field_15015);
                this.drawUi();
            });
        }

        this.slot(ITEMS_PER_PAGE + 2, MicroUiElements.EMPTY, MicroUiElements.EMPTY_ACTION);
        this.slot(ITEMS_PER_PAGE + 3, MicroUiElements.EMPTY, MicroUiElements.EMPTY_ACTION);
        this.slot(ITEMS_PER_PAGE + 4, MicroUiElements.BUTTON_BACK, (player, slotIndex, button, actionType) -> {
            playSound(player, class_3417.field_15015);
            new CreativeTabListUi(player);
        });
        this.slot(ITEMS_PER_PAGE + 5, MicroUiElements.EMPTY, MicroUiElements.EMPTY_ACTION);
        this.slot(ITEMS_PER_PAGE + 6, MicroUiElements.EMPTY, MicroUiElements.EMPTY_ACTION);
        if (this.page >= this.items.size() / ITEMS_PER_PAGE) {
            this.slot(ITEMS_PER_PAGE + 7, MicroUiElements.BUTTON_NEXT_LOCK, MicroUiElements.EMPTY_ACTION);
        } else {
            this.slot(ITEMS_PER_PAGE + 7, MicroUiElements.BUTTON_NEXT, (player, slotIndex, button, actionType) -> {
                CreativeTabUi.this.page++;
                playSound(player, class_3417.field_15015);
                this.drawUi();
            });
        }
        this.slot(ITEMS_PER_PAGE + 8, MicroUiElements.EMPTY, MicroUiElements.EMPTY_ACTION);
    }

    protected void onMouseClick(class_1799 itemStack, int slotId, int button, class_1713 actionType, class_3222 player) {
        boolean bl = actionType == class_1713.field_7794;
        actionType = slotId == -999 && actionType == class_1713.field_7790 ? class_1713.field_7795 : actionType;

        var handler = player.field_7512;

        if (actionType != class_1713.field_7789) {
            class_1799 i = handler.method_34255();
            if (actionType == class_1713.field_7791) {
                if (!itemStack.method_7960()) {
                    class_1799 itemStack2 = itemStack.method_7972();
                    itemStack2.method_7939(itemStack2.method_7914());
                    player.method_31548().method_5447(button, itemStack2);
                    player.field_7498.method_7623();
                }

                return;
            }

            if (actionType == class_1713.field_7796) {
                if (handler.method_34255().method_7960() && !itemStack.method_7960()) {
                    class_1799 itemStack2 = itemStack.method_7972();
                    itemStack2.method_7939(itemStack2.method_7914());
                    handler.method_34254(itemStack2);
                }

                return;
            }

            if (actionType == class_1713.field_7795) {
                if (!itemStack.method_7960()) {
                    class_1799 itemStack2 = itemStack.method_7972();
                    itemStack2.method_7939(button == 0 ? 1 : itemStack2.method_7914());
                    player.method_7328(itemStack2, true);
                    //this.client.interactionManager.dropCreativeStack(itemStack2);
                }

                return;
            }

            if (!i.method_7960() && !itemStack.method_7960() && class_1799.method_31577(itemStack, i)) {
                if (button == 0) {
                    if (bl) {
                        i.method_7939(i.method_7914());
                    } else if (i.method_7947() < i.method_7914()) {
                        i.method_7933(1);
                    }
                } else {
                    i.method_7934(1);
                }
            } else if (!itemStack.method_7960() && i.method_7960()) {
                handler.method_34254(itemStack.method_7972());
                i = handler.method_34255();
                if (bl) {
                    i.method_7939(i.method_7914());
                }
            } else if (button == 0) {
                handler.method_34254(class_1799.field_8037);
            } else {
                handler.method_34255().method_7934(1);
            }
        }
    }
}
