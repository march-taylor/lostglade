package eu.pb4.polymer.core.impl.other;

import eu.pb4.polymer.core.mixin.other.IdMapperAccessor;
import net.minecraft.class_2361;

public class FixedIdList<T> extends class_2361<T> {

    @Override
    public int method_10204() {
        return ((IdMapperAccessor) (Object) this).getIdToT().size();
    }

    public int mapSize() {
        return super.method_10204();
    }
}
