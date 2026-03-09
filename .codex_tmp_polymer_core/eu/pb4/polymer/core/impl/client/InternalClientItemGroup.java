package eu.pb4.polymer.core.impl.client;

import eu.pb4.polymer.core.api.item.PolymerItemGroupUtils;
import eu.pb4.polymer.core.api.utils.PolymerObject;
import eu.pb4.polymer.core.impl.PolymerImplUtils;
import eu.pb4.polymer.core.impl.interfaces.CreativeModeTabExtra;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.class_1761;
import net.minecraft.class_1799;
import net.minecraft.class_2561;
import net.minecraft.class_2960;
import net.minecraft.class_5321;
import net.minecraft.class_7225;
import net.minecraft.class_7699;
import net.minecraft.class_7924;
import org.jetbrains.annotations.ApiStatus;

@Environment(EnvType.CLIENT)
@ApiStatus.Internal
public class InternalClientItemGroup extends class_1761 implements PolymerObject, CreativeModeTabExtra {
    private final class_2960 identifier;

    public InternalClientItemGroup(class_7915 row, int column, class_2960 identifier, class_2561 name, class_1799 stack) {
        super(row, column, class_7916.field_41052, name, stack::method_7972, (a, c) -> {});
        this.identifier = identifier;
    }

    public class_2960 getIdentifier() {
        return this.identifier;
    }

    public class_2960 getId() {
        return PolymerImplUtils.id( "group/" + this.identifier.method_12836() + "/" + this.identifier.method_12832());
    }


    @Override
    public PolymerItemGroupUtils.Contents polymer$getContentsWith(class_2960 id, class_7699 enabledFeatures, boolean operatorEnabled, class_7225.class_7874 lookup) {
        return null;
    }

    @Override
    public boolean polymer$isSyncable() {
        return false;
    }

    public class_5321<class_1761> getKey() {
        return class_5321.method_29179(class_7924.field_44688, this.identifier);
    }
}
