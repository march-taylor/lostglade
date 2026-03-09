package eu.pb4.polymer.core.api.other;

import eu.pb4.polymer.core.api.utils.PolymerSyncedObject;
import net.minecraft.class_1842;
import net.minecraft.class_6880;
import net.minecraft.class_7923;
import org.jetbrains.annotations.Nullable;
import xyz.nucleoid.packettweaker.PacketContext;

public interface PolymerPotion extends PolymerSyncedObject<class_1842> {
    @Override
    @Nullable
    default class_1842 getPolymerReplacement(class_1842 potion, PacketContext context) {
        return null;
    }

    static void registerOverlay(class_6880<class_1842> entry, PolymerPotion overlay) {
        PolymerSyncedObject.setSyncedObject(class_7923.field_41179, entry.comp_349(), overlay);
    }

    static void registerOverlay(class_1842 entry, PolymerPotion overlay) {
        PolymerSyncedObject.setSyncedObject(class_7923.field_41179, entry, overlay);
    }
}
