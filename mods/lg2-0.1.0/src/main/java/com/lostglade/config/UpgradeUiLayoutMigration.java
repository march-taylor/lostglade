package com.lostglade.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Applies the fixed visual layout and concise material hints for the drone tuning screen. */
public final class UpgradeUiLayoutMigration {
    private static final String SCREEN = "it_drones";
    private static final String[] IDS = {
            "it_drone_kamikaze", "it_drone_combat", "it_drone_auto_aim",
            "it_drone_night_vision", "it_drone_paint", "it_drone_microphone"
    };
    // User supplied x;y positions: 4;1, 6;1, 2;3, 4;3, 6;3, 8;3.
    // Inventory slots are zero-based, so slot = (y - 1) * 9 + (x - 1).
    private static final int[] SLOTS = {3, 5, 19, 21, 23, 25};

    private static final String[] ICONS = {"\uE100", "\uE101", "\uE102", "\uE103", "\uE104", "\uE105"};
    private static final String[] RU = {
            "Материал: \uE100 TNT",
            "Материал: \uE101 арбалет",
            "Материал: \uE102 мишень",
            "Материал: \uE103 золотая морковь",
            "Краситель: \uE104 красный",
            "Материал: \uE105 нотный блок"
    };
    private static final String[] EN = {
            "Material: \uE100 TNT",
            "Material: \uE101 crossbow",
            "Material: \uE102 target",
            "Material: \uE103 golden carrot",
            "Dye: \uE104 red",
            "Material: \uE105 note block"
    };

    private UpgradeUiLayoutMigration() {}

    public static void apply() {
        UpgradeUiConfig.ConfigData config = UpgradeUiConfig.get();
        UpgradeUiConfig.ScreenConfig screen = config.screens.get(SCREEN);
        if (screen == null) return;
        if (screen.buttons == null) screen.buttons = new java.util.LinkedHashMap<>();

        boolean changed = false;
        for (int i = 0; i < IDS.length; i++) {
            UpgradeUiConfig.ButtonConfig button = find(screen.buttons, IDS[i]);
            if (button == null) {
                if (!"it_drone_microphone".equals(IDS[i])) continue;
                button = createMicrophone();
                screen.buttons.put("drone_microphone", button);
                changed = true;
            }
            if (button.slot != SLOTS[i]) { button.slot = SLOTS[i]; changed = true; }
            if (button.hitboxWidth != 1) { button.hitboxWidth = 1; changed = true; }
            if (button.hitboxHeight != 1) { button.hitboxHeight = 1; changed = true; }

            UpgradeUiConfig.LocalizedLines lore = new UpgradeUiConfig.LocalizedLines();
            lore.values.put("ru_ru", List.of(RU[i]));
            lore.values.put("en_us", List.of(EN[i]));
            if (!lore.values.equals(button.lore == null ? null : button.lore.values)) {
                button.lore = lore;
                changed = true;
            }
        }
        if (changed) UpgradeUiConfig.save();
    }

    private static UpgradeUiConfig.ButtonConfig find(Map<String, UpgradeUiConfig.ButtonConfig> buttons, String id) {
        for (UpgradeUiConfig.ButtonConfig button : buttons.values()) {
            if (button != null && id.equals(button.upgradeId)) return button;
        }
        return null;
    }

    private static UpgradeUiConfig.ButtonConfig createMicrophone() {
        UpgradeUiConfig.ButtonConfig button = new UpgradeUiConfig.ButtonConfig();
        button.type = UpgradeUiConfig.ButtonType.PURCHASE_UPGRADE.id;
        button.upgradeId = "it_drone_microphone";
        button.pricesBitcoins = new ArrayList<>(List.of(900));
        button.requirements = new ArrayList<>();
        UpgradeUiConfig.RequirementConfig requirement = new UpgradeUiConfig.RequirementConfig();
        requirement.upgradeId = "it_drone_scout";
        requirement.minLevel = 1;
        button.requirements.add(requirement);
        button.icon = new UpgradeUiConfig.IconConfig();
        button.icon.fallbackItem = "lg2:microphone";
        button.icon.packModel = "lg2:gui/button/invisible";
        button.name = UpgradeUiConfig.LocalizedText.singleLanguage("en_us", "Microphone Module");
        button.name.values.put("ru_ru", "Микрофонный модуль");
        button.lore = new UpgradeUiConfig.LocalizedLines();
        button.lore.values.put("en_us", List.of(EN[5]));
        button.lore.values.put("ru_ru", List.of(RU[5]));
        return button;
    }
}
