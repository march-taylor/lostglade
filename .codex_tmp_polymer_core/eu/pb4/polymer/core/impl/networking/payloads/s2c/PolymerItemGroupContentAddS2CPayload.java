package eu.pb4.polymer.core.impl.networking.payloads.s2c;

import eu.pb4.polymer.core.api.item.PolymerItemGroupUtils;
import eu.pb4.polymer.core.api.item.PolymerItemUtils;
import eu.pb4.polymer.core.impl.PolymerImplUtils;
import eu.pb4.polymer.core.impl.networking.S2CPackets;
import eu.pb4.polymer.networking.api.ContextByteBuf;
import xyz.nucleoid.packettweaker.PacketContext;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.class_1761;
import net.minecraft.class_1799;
import net.minecraft.class_2960;
import net.minecraft.class_3244;
import net.minecraft.class_7923;
import net.minecraft.class_8710;
import net.minecraft.class_9135;
import net.minecraft.class_9139;

public record PolymerItemGroupContentAddS2CPayload(class_2960 groupId, List<Entry> stacksMain, List<Entry> stacksSearch) implements class_8710 {
    public static final class_8710.class_9154<PolymerItemGroupContentAddS2CPayload> ID = new class_8710.class_9154<>(S2CPackets.SYNC_ITEM_GROUP_CONTENTS_ADD);
    public static final class_9139<ContextByteBuf, PolymerItemGroupContentAddS2CPayload> CODEC = class_9139.method_56438(PolymerItemGroupContentAddS2CPayload::write, PolymerItemGroupContentAddS2CPayload::read);
    public static PolymerItemGroupContentAddS2CPayload of(int version, class_1761 group, class_3244 handler) {
        List<Entry> entryMain;
        List<Entry> entrySearch;

        var contents = PolymerItemGroupUtils.getContentsFor(handler.field_14140, group);

        if (PolymerItemGroupUtils.isPolymerItemGroup(group)) {
            entryMain = List.of(new Entry(Mode.INSERT_END, class_1799.field_8037, List.copyOf(contents.main())));
            entrySearch = List.of(new Entry(Mode.INSERT_END, class_1799.field_8037, List.copyOf(contents.search())));
        } else if (version == 9) {
            var ctx = PacketContext.create(handler);
            var stackMain = new ArrayList<class_1799>();
            var stackSearch = new ArrayList<class_1799>();

            entryMain = List.of(new Entry(Mode.INSERT_END, class_1799.field_8037, stackMain));
            entrySearch = List.of(new Entry(Mode.INSERT_END, class_1799.field_8037, stackSearch));

            for (var item : contents.main()) {
                if (PolymerItemUtils.isPolymerServerItem(item, ctx) || PolymerImplUtils.isServerSideSyncableEntry(class_7923.field_41178, item.method_7909())) {
                    stackMain.add(item);
                }
            }

            for (var item : contents.search()) {
                if (PolymerItemUtils.isPolymerServerItem(item, ctx) || PolymerImplUtils.isServerSideSyncableEntry(class_7923.field_41178, item.method_7909())) {
                    stackSearch.add(item);
                }
            }
        } else {
            var ctx = PacketContext.create(handler);
            entryMain = new ArrayList<>();
            entrySearch = new ArrayList<>();

            groupEntries(entryMain, contents.main(), ctx);
            groupEntries(entrySearch, contents.search(), ctx);
        }

        return new PolymerItemGroupContentAddS2CPayload(PolymerItemGroupUtils.getId(group), entryMain, entrySearch);
    }

    private static void groupEntries(List<Entry> entry, Collection<class_1799> main, PacketContext.NotNullWithPlayer ctx) {
        var stacks = new ArrayList<class_1799>();

        class_1799 previous = class_1799.field_8037;
        for (var item : main) {
            if (PolymerItemUtils.isPolymerServerItem(item, ctx) || PolymerImplUtils.isServerSideSyncableEntry(class_7923.field_41178, item.method_7909())) {
                stacks.add(item);
            } else {
                if (!stacks.isEmpty()) {
                    var mode = previous.method_7960() ? Mode.INSERT_BEGINNING : Mode.RELATIVE;
                    entry.add(new Entry(mode, previous, stacks));
                    stacks = new ArrayList<>();
                }

                previous = item;
            }
        }
        if (!stacks.isEmpty()) {
            var mode = previous.method_7960() ? Mode.INSERT_END : Mode.RELATIVE;
            entry.add(new Entry(mode, previous, stacks));
        }
    }

    public void write(ContextByteBuf buf) {
        buf.method_10812(this.groupId);

        if (buf.version() == 9) {
            class_1799.field_49269.encode(buf, this.stacksMain.isEmpty() ? List.of() : this.stacksMain.getFirst().stacks());
            class_1799.field_49269.encode(buf, this.stacksSearch.isEmpty() ? List.of() : this.stacksSearch.getFirst().stacks());
            return;
        }

        Entry.LIST_PACKET_CODEC.encode(buf, this.stacksMain);
        Entry.LIST_PACKET_CODEC.encode(buf, this.stacksSearch);
    }

    public boolean isNonEmpty() {
        return !this.stacksMain.isEmpty() || !this.stacksSearch.isEmpty();
    }

    public static PolymerItemGroupContentAddS2CPayload read(ContextByteBuf buf) {
        if (buf.version() == 9) {
            return new PolymerItemGroupContentAddS2CPayload(buf.method_10810(),
                    List.of(new Entry(Mode.INSERT_END, class_1799.field_8037, class_1799.field_49269.decode(buf))),
                    List.of(new Entry(Mode.INSERT_END, class_1799.field_8037, class_1799.field_49269.decode(buf)))
            );
        }
        return new PolymerItemGroupContentAddS2CPayload(buf.method_10810(),
                Entry.LIST_PACKET_CODEC.decode(buf),
                Entry.LIST_PACKET_CODEC.decode(buf)
        );
    }

    @Override
    public class_9154<? extends class_8710> method_56479() {
        return ID;
    }

    public record Entry(Mode mode, class_1799 relative, List<class_1799> stacks) {
        public static final class_9139<ContextByteBuf, Entry> PACKET_CODEC = class_9139.method_56437(Entry::write, Entry::read);

        private static Entry read(ContextByteBuf byteBuf) {
            var mode = Mode.values()[byteBuf.method_10816()];
            var stack = class_1799.field_8037;
            if (mode == Mode.RELATIVE) {
                stack = class_1799.field_48349.decode(byteBuf);
            }
            var list = class_1799.field_49269.decode(byteBuf);
            return new Entry(mode, stack, list);
        }

        private static void write(ContextByteBuf byteBuf, Entry entry) {
            byteBuf.method_10804(entry.mode.ordinal());
            if (entry.mode == Mode.RELATIVE) {
                class_1799.field_48349.encode(byteBuf, entry.relative);
            }
            class_1799.field_49269.encode(byteBuf, entry.stacks);
        }

        public static final class_9139<ContextByteBuf, List<Entry>> LIST_PACKET_CODEC = PACKET_CODEC.method_56433(class_9135.method_56363());
    }

    public enum Mode {
        RELATIVE,
        INSERT_BEGINNING,
        INSERT_END,
    }
}
