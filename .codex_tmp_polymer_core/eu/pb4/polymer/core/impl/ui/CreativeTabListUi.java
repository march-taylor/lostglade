package eu.pb4.polymer.core.impl.ui;

import eu.pb4.polymer.core.api.item.PolymerItemGroupUtils;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.class_1761;
import net.minecraft.class_1799;
import net.minecraft.class_2561;
import net.minecraft.class_3222;
import net.minecraft.class_3417;
import net.minecraft.class_7706;
import net.minecraft.class_9334;

public class CreativeTabListUi extends MicroUi {
    private static final int ITEMS_PER_PAGE = 45;
    private final List<class_1761> items;

    private int page;

    public CreativeTabListUi(class_3222 player) {
        super(6);
        this.title(class_2561.method_43470("Creative Item Groups"));
        this.items = new ArrayList<>();
        this.items.addAll(PolymerItemGroupUtils.getItemGroups(player));
        this.page = 0;
        this.drawUi();

        this.open(player);
    }

    private void drawUi() {
        int start = page * ITEMS_PER_PAGE;
        int end = Math.min((page + 1) * ITEMS_PER_PAGE, this.items.size());
        for (int i = start; i < end; i++) {
            var itemGroup = this.items.get(i);
            var icon = itemGroup.method_7747().method_7972();
            var text = itemGroup.method_7737().method_27661();
            if (!text.method_10866().method_10966()) {
                text.method_10862(text.method_10866().method_10978(false));
            }
            icon.method_57379(class_9334.field_49631, text);
            //icon.getOrCreateNbt().putInt("HideFlags", 255);
            this.slot(i - start, icon, (player, slotIndex, button, actionType) -> {
                playSound(player, class_3417.field_15015);
                new CreativeTabUi(player, itemGroup);
            });
        }

        for (int i = Math.max(end - start, 0); i < ITEMS_PER_PAGE; i++) {
            this.slot(i, class_1799.field_8037, MicroUiElements.EMPTY_ACTION);
        }

        this.slot(ITEMS_PER_PAGE + 0, MicroUiElements.EMPTY, MicroUiElements.EMPTY_ACTION);

        if (this.page == 0) {
            this.slot(ITEMS_PER_PAGE + 1, MicroUiElements.BUTTON_PREVIOUS_LOCK, MicroUiElements.EMPTY_ACTION);
        } else {
            this.slot(ITEMS_PER_PAGE + 1, MicroUiElements.BUTTON_PREVIOUS, (player, slotIndex, button, actionType) -> {
                CreativeTabListUi.this.page--;
                playSound(player, class_3417.field_15015);
                this.drawUi();
            });
        }

        this.slot(ITEMS_PER_PAGE + 2, MicroUiElements.EMPTY, MicroUiElements.EMPTY_ACTION);
        this.slot(ITEMS_PER_PAGE + 3, MicroUiElements.EMPTY, MicroUiElements.EMPTY_ACTION);
        this.slot(ITEMS_PER_PAGE + 4, MicroUiElements.BUTTON_SEARCH, (player, slotIndex, button, actionType) -> {
            playSound(player, class_3417.field_15015);
            new CreativeTabUi(player, class_7706.method_47344());
        });
        this.slot(ITEMS_PER_PAGE + 5, MicroUiElements.EMPTY, MicroUiElements.EMPTY_ACTION);
        this.slot(ITEMS_PER_PAGE + 6, MicroUiElements.EMPTY, MicroUiElements.EMPTY_ACTION);
        if (this.page >= this.items.size() / ITEMS_PER_PAGE) {
            this.slot(ITEMS_PER_PAGE + 7, MicroUiElements.BUTTON_NEXT_LOCK, MicroUiElements.EMPTY_ACTION);
        } else {
            this.slot(ITEMS_PER_PAGE + 7, MicroUiElements.BUTTON_NEXT, (player, slotIndex, button, actionType) -> {
                CreativeTabListUi.this.page++;
                playSound(player, class_3417.field_15015);
                this.drawUi();
            });
        }
        this.slot(ITEMS_PER_PAGE + 8, MicroUiElements.EMPTY, MicroUiElements.EMPTY_ACTION);
    }
}
