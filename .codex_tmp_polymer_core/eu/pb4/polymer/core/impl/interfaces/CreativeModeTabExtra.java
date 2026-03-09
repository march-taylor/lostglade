package eu.pb4.polymer.core.impl.interfaces;

import eu.pb4.polymer.core.api.item.PolymerItemGroupUtils;
import net.minecraft.class_2960;
import net.minecraft.class_7225;
import net.minecraft.class_7699;

public interface CreativeModeTabExtra {
    PolymerItemGroupUtils.Contents polymer$getContentsWith(class_2960 id, class_7699 enabledFeatures, boolean operatorEnabled, class_7225.class_7874 lookup);
    default boolean polymer$isSyncable() {
        return true;
    }
}
