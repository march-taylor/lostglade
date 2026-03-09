package eu.pb4.polymer.core.api.other;

import eu.pb4.polymer.core.api.utils.PolymerSyncedObject;
import net.minecraft.class_1293;
import net.minecraft.class_1842;
import org.jetbrains.annotations.Nullable;

public class SimplePolymerPotion extends class_1842 implements PolymerPotion {
    public SimplePolymerPotion(class_1293... effects) {
        super((String)null, effects);
    }

    public SimplePolymerPotion(@Nullable String baseName, class_1293... effects) {
        super(baseName, effects);
    }
}
