package com.lostglade.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Applies the fixed visual layout for the drone tuning screen. */
public final class UpgradeUiLayoutMigration {
    private static final String DRONE_SCREEN_ID = "it_drones";
    private static final String[] DRONE_UPGRADES = {
            "it_drone_kamikaze",
            "it_drone_combat",
            "it_drone_paint",
            "it_drone_night_vision",
            "it_drone_microphone",
            "it_drone_auto_aim"
    };
    private static final int[] DRONE_SLOTS = {
            11, 13, 15,
            29, 31, 33
    };

    private UpgradeUiLayoutMigration() {
    }

    public static void apply() {
        UpgradeUiConfig.ConfigData config = UpgradeUiConfig.get();
        UpgradeUiConfig.ScreenConfig screen = config.screens.get(DRONE_SCREEN_ID);
        if (screen == null) {
            return;
        }
        if (screen.buttons == null) {
            screen.buttons = new java.util.LinkedHashMap<>();
        }

        boolean changed = false;
        for (int i = 0; i < DRONE_UPGRADES.length; i++) {
            UpgradeUiConfig.ButtonConfig button = findByUpgradeId(screen.buttons, DRONE_UPGRADES[i]);
            if (button == null) {
                if (!"it_drone_microphone".equals(DRONE_UPGRADES[i])) {
                    continue;
                }
                button = createMissingDroneMicrophoneButton();
                screen.buttons.put("drone_microphone", button);
                changed = true;
            }
            if (button.slot != DRONE_SLOTS[i]) {
                button.slot = DRONE_SLOTS[i];
                changed = true;
            }
            if (button.hitboxWidth != 1) {
                button.hitboxWidth = 1;
                changed = true;
            }
            if (button.hitboxHeight != 1) {
                button.hitboxHeight = 1;
                changed = true;
            }
        }

        if (changed) {
            UpgradeUiConfig.save();
        }
    }

    private static UpgradeUiConfig.ButtonConfig findByUpgradeId(
            Map<String, UpgradeUiConfig.ButtonConfig> buttons,
            String upgradeId
    ) {
        for (UpgradeUiConfig.ButtonConfig button : buttons.values()) {
            if (button != null && upgradeId.equals(button.upgradeId)) {
                return button;
            }
        }
        return null;
    }

    private static UpgradeUiConfig.ButtonConfig createMissingDroneMicrophoneButton() {
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
        button.icon.count = 1;
        button.icon.foil = false;

        button.name = UpgradeUiConfig.LocalizedText.singleLanguage("en_us", "Microphone Module");
        button.name.values.put("ru_ru", "Микрофонный модуль");
        button.name.values.put("uk_ua", "Мікрофонний модуль");
        button.name.values.put("ja_jp", "マイクモジュール");
        button.name.values.put("rpr", "Микрофонный модульъ");

        button.lore = new UpgradeUiConfig.LocalizedLines();
        button.lore.values.put("en_us", List.of("Adds a microphone to the drone."));
        button.lore.values.put("ru_ru", List.of("Добавляет микрофон на дрон."));
        button.lore.values.put("uk_ua", List.of("Додає мікрофон до дрона."));
        button.lore.values.put("ja_jp", List.of("ドローンにマイクを追加します。"));
        button.lore.values.put("rpr", List.of("Прибавляетъ микрофонъ къ дрону."));
        return button;
    }
}
