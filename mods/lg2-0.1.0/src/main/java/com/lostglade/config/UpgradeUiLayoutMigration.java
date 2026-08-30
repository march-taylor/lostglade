package com.lostglade.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Applies the fixed visual layout and concise material hints for the drone tuning screen. */
public final class UpgradeUiLayoutMigration {
    private static final String SCREEN = "it_drones";
    private static final String[] IDS = {
            "it_drone_kamikaze", "it_drone_combat", "it_drone_auto_aim",
            "it_drone_night_vision", "it_drone_paint", "it_drone_microphone"
    };
    // The first 27 menu slots belong to the 3-row chest. These anchors point
    // at the visible 3×9 player-inventory area below it.
    private static final int[] SLOTS = {30, 32, 46, 48, 50, 52};

    private static final String[] RU = {
            "Установка: \uE100 TNT",
            "Установка: \uE101 арбалет",
            "Установка: \uE102 скалк-сенсор",
            "Установка: \uE103 салатовое стекло",
            "Установка: \uE104 краситель",
            "Установка: \uE105 микрофон"
    };
    private static final String[] EN = {
            "Installation: \uE100 TNT",
            "Installation: \uE101 crossbow",
            "Installation: \uE102 sculk sensor",
            "Installation: \uE103 lime stained glass",
            "Installation: \uE104 dye",
            "Installation: \uE105 microphone"
    };

    private UpgradeUiLayoutMigration() {}

    public static void apply() {
        UpgradeUiConfig.ConfigData config = UpgradeUiConfig.get();
        if (config.screens == null) {
            config.screens = new LinkedHashMap<>();
        }

        UpgradeUiConfig.ScreenConfig screen = config.screens.get(SCREEN);
        boolean changed = false;
        if (screen == null) {
            screen = new UpgradeUiConfig.ScreenConfig();
            screen.enabled = true;
            screen.rows = 3;
            screen.theme = "default";
            screen.title = UpgradeUiConfig.LocalizedText.of("Drone tuning", "Настройка дрона");
            screen.buttons = new LinkedHashMap<>();
            config.screens.put(SCREEN, screen);
            changed = true;
        } else if (screen.buttons == null) {
            screen.buttons = new LinkedHashMap<>();
            changed = true;
        }

        for (int i = 0; i < IDS.length; i++) {
            UpgradeUiConfig.ButtonConfig button = find(screen.buttons, IDS[i]);
            if (button == null) {
                button = createDefaultButton(IDS[i], i);
                screen.buttons.put(IDS[i], button);
                changed = true;
            }
            if (!button.enabled) { button.enabled = true; changed = true; }
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

        if (changed) {
            UpgradeUiConfig.save();
        }
    }

    private static UpgradeUiConfig.ButtonConfig find(Map<String, UpgradeUiConfig.ButtonConfig> buttons, String id) {
        for (UpgradeUiConfig.ButtonConfig button : buttons.values()) {
            if (button != null && id.equals(button.upgradeId)) return button;
        }
        return null;
    }

    private static UpgradeUiConfig.ButtonConfig createDefaultButton(String id, int index) {
        UpgradeUiConfig.ButtonConfig button = new UpgradeUiConfig.ButtonConfig();
        button.enabled = true;
        button.slot = SLOTS[index];
        button.hitboxWidth = 1;
        button.hitboxHeight = 1;
        button.type = UpgradeUiConfig.ButtonType.PURCHASE_UPGRADE.id;
        button.upgradeId = id;
        button.pricesBitcoins = new ArrayList<>(List.of(defaultPrice(index)));
        button.icon = UpgradeUiConfig.IconConfig.upgrade();
        button.name = UpgradeUiConfig.LocalizedText.of(defaultNameEn(index), defaultNameRu(index));
        button.lore = new UpgradeUiConfig.LocalizedLines();
        button.lore.values.put("en_us", List.of(EN[index]));
        button.lore.values.put("ru_ru", List.of(RU[index]));
        return button;
    }

    private static int defaultPrice(int index) {
        return switch (index) {
            case 0 -> 1200;
            case 1 -> 1500;
            case 2 -> 700;
            case 3 -> 800;
            case 4 -> 600;
            case 5 -> 900;
            default -> 900;
        };
    }

    private static String defaultNameRu(int index) {
        return switch (index) {
            case 0 -> "Камикадзе";
            case 1 -> "Пушка";
            case 2 -> "Автонаведение";
            case 3 -> "Ночное зрение";
            case 4 -> "Перекраска";
            case 5 -> "Микрофонный модуль";
            default -> "Модуль";
        };
    }

    private static String defaultNameEn(int index) {
        return switch (index) {
            case 0 -> "Kamikaze";
            case 1 -> "Cannon";
            case 2 -> "Auto-aim";
            case 3 -> "Night vision";
            case 4 -> "Repainting";
            case 5 -> "Microphone module";
            default -> "Module";
        };
    }
}
