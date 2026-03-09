package eu.pb4.polymer.core.impl.networking.entry;

import eu.pb4.polymer.core.impl.PolymerImplUtils;
import eu.pb4.polymer.core.impl.interfaces.RegistryExtension;
import eu.pb4.polymer.networking.api.ContextByteBuf;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.class_2378;
import net.minecraft.class_2540;
import net.minecraft.class_2960;
import net.minecraft.class_3244;
import net.minecraft.class_9139;

public record PolymerTagEntry(class_2960 registry, List<TagData> tags) {

    public static final class_9139<ContextByteBuf, PolymerTagEntry> CODEC = class_9139.method_56438(PolymerTagEntry::write, PolymerTagEntry::read);

    public static PolymerTagEntry of(class_2378<Object> registry, class_3244 handler, int version) {
        if (registry instanceof RegistryExtension && !((RegistryExtension<Object>) registry).polymer$getEntries().isEmpty()) {
            var registryExtension = (RegistryExtension<Object>) registry;

            var out = new ArrayList<TagData>();
            for (var entry : registryExtension.polymer$getTagsInternal().values()){
                var ids = new IntArrayList();

                for (var obj : entry) {
                    if (PolymerImplUtils.isServerSideSyncableEntry(registry, obj.comp_349())) {
                        ids.add(registry.method_10206(obj.comp_349()));
                    }
                }

                if (!ids.isEmpty()) {
                    out.add(new TagData(entry.method_40251().comp_327(), ids));
                }
            }

            return out.isEmpty() ? null : new PolymerTagEntry(registry.method_46765().method_29177(), out);
        }
        return null;
    }

    public static PolymerTagEntry read(class_2540 buf) {
        var registry = buf.method_10810();
        var size = buf.method_10816();

        var tags = new ArrayList<TagData>();
        for (int i = 0; i < size; i++) {
            var tagId = buf.method_10810();
            var sizeIds = buf.method_10816();
            var idList = new IntArrayList(sizeIds);
            for (int a = 0; a < sizeIds; a++) {
                idList.add(buf.method_10816());
            }
            tags.add(new TagData(tagId, idList));
        }

        return new PolymerTagEntry(registry, tags);
    }

    public void write(class_2540 buf) {
        buf.method_10812(this.registry);
        buf.method_10804(this.tags.size());

        for (var tag : this.tags) {
            buf.method_10812(tag.id);
            buf.method_10804(tag.ids.size());
            for (var id : tag.ids) {
                buf.method_10804(id);
            }
        }
    }


    public record TagData(class_2960 id, IntList ids) {}
}
