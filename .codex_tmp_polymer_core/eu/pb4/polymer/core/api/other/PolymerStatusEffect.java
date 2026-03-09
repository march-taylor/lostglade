package eu.pb4.polymer.core.api.other;

import eu.pb4.polymer.core.api.utils.PolymerSyncedObject;
import org.jetbrains.annotations.Nullable;
import xyz.nucleoid.packettweaker.PacketContext;

import java.util.List;
import java.util.Optional;
import net.minecraft.class_1291;
import net.minecraft.class_1799;
import net.minecraft.class_1802;
import net.minecraft.class_1844;
import net.minecraft.class_3222;
import net.minecraft.class_7923;
import net.minecraft.class_9334;

public interface PolymerStatusEffect extends PolymerSyncedObject<class_1291> {
    static void registerOverlay(class_1291 effect) {
        registerOverlay(effect, (e, c) -> null);
    }

    static void registerOverlay(class_1291 effect, PolymerSyncedObject<class_1291> overlay) {
        PolymerSyncedObject.setSyncedObject(class_7923.field_41174, effect, overlay);
    }

    @Nullable
    default class_1799 getPolymerIcon(class_1291 effect, class_3222 player) {
        var icon = class_1802.field_8574.method_7854();
        icon.method_57379(class_9334.field_49651, new class_1844(Optional.empty(),
                Optional.of(((class_1291) this).method_5556()), List.of(), Optional.empty()));
        return icon;
    }

    @Override
    @Nullable
    default class_1291 getPolymerReplacement(class_1291 potion, PacketContext context) {
        return null;
    }
}
