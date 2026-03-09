package eu.pb4.polymer.virtualentity.api;

import eu.pb4.polymer.virtualentity.api.elements.VirtualElement;
import eu.pb4.polymer.virtualentity.api.attachment.HolderAttachment;
import eu.pb4.polymer.virtualentity.impl.HolderHolder;
import eu.pb4.polymer.virtualentity.impl.SafeBundler;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.class_1923;
import net.minecraft.class_2338;
import net.minecraft.class_243;
import net.minecraft.class_2596;
import net.minecraft.class_2602;
import net.minecraft.class_2716;
import net.minecraft.class_3222;
import net.minecraft.class_3244;

public class ElementHolder {
    private final Consumer<class_2596<class_2602>> EMPTY_PACKET_CONSUMER = (p) -> {};

    private HolderAttachment attachment;
    private final List<VirtualElement> elements = new ObjectArrayList<>();
    private final List<class_3244> players = new ArrayList<>();
    protected class_243 currentPos = class_243.field_1353;
    private class_1923 currentChunkPos = null;

    private final IntList entityIds = new IntArrayList();
    private final IntList attachedPassengerEntityIds = new IntArrayList();

    public boolean isPartOf(int entityId) {
        return this.entityIds.contains(entityId);
    }

    public IntList getEntityIds() {
        return this.entityIds;
    }

    public <T extends VirtualElement> T addElement(T element) {
        if (this.addElementWithoutUpdates(element)) {
            for (var player : this.players) {
                var x = new SafeBundler(player::method_14364);
                element.startWatching(player.method_32311(), x);
                x.finish();
            }
        }
        return element;
    }

    public boolean addElementWithoutUpdates(VirtualElement element) {
        if (!this.elements.contains(element)) {
            this.elements.add(element);
            this.entityIds.addAll(element.getEntityIds());
            element.setHolder(this);
            return true;
        }
        return false;
    }

    public void removeElement(VirtualElement element) {
        if (this.removeElementWithoutUpdates(element)) {
            var packet = new class_2716(element.getEntityIds());
            for (var player : this.players) {
                for (var e : this.elements) {
                    e.stopWatching(player.method_32311(), player::method_14364);
                }
                player.method_14364(packet);
            }
        }
    }

    public boolean removeElementWithoutUpdates(VirtualElement element) {
        if (this.elements.contains(element)) {
            this.elements.remove(element);
            this.entityIds.removeAll(element.getEntityIds());
            element.setHolder(null);
            return true;
        }
        return false;
    }

    public List<VirtualElement> getElements() {
        return Collections.unmodifiableList(this.elements);
    }

    public boolean startWatching(class_3244 player) {
        if (this.players.contains(player)) {
            return false;
        }
        this.players.add(player);
        ((HolderHolder) player).polymer$addHolder(this);
        var packets = new SafeBundler(player::method_14364);

        for (var e : this.elements) {
            e.startWatching(player.method_32311(), packets);
        }

        this.startWatchingExtraPackets(player, packets);

        if (this.attachment != null) {
            this.attachment.startWatchingExtraPackets(player, packets);
        }

        packets.finish();

        return true;
    }

    protected void startWatchingExtraPackets(class_3244 player, Consumer<class_2596<class_2602>> packetConsumer) {
    }

    public final boolean startWatching(class_3222 player) {
        return startWatching(player.field_13987);
    }

    public boolean stopWatching(class_3244 player) {
        if (!this.players.contains(player)) {
            return false;
        }
        this.players.remove(player);
        ((HolderHolder) player).polymer$removeHolder(this);

        Consumer<class_2596<class_2602>> packetConsumer = player.method_48106() ? player::method_14364 : EMPTY_PACKET_CONSUMER;

        for (var e : this.elements) {
            e.stopWatching(player.method_32311(), packetConsumer);
        }
        packetConsumer.accept(new class_2716(this.entityIds));

        return true;
    }

    public final boolean stopWatching(class_3222 player) {
        return stopWatching(player.field_13987);
    }

    public void tick() {
        if (this.attachment == null) {
            return;
        }

        this.onTick();

        this.updatePosition();

        for (var e : this.elements) {
            e.tick();
        }
    }

    protected void onTick() {
    }

    protected void updatePosition() {
        if (this.attachment == null || !this.attachment.canUpdatePosition()) {
            return;
        }

        var newPos = this.attachment.getPos();

        if (!this.currentPos.equals(newPos)) {
            var delta = newPos.method_1020(this.currentPos);
            this.notifyElementsOfPositionUpdate(newPos, delta);
            this.currentPos = newPos;
            this.currentChunkPos = null;
        }
    }

    protected void updateInitialPosition() {
        var newPos = this.attachment.getPos();

        for (var e : this.elements) {
            e.setInitialPosition(newPos);
        }

        this.currentPos = newPos;
        this.currentChunkPos = null;
    }

    protected void invalidateCaches() {
        this.currentChunkPos = null;
    }

    public class_1923 getChunkPos() {
        if (this.currentChunkPos == null) {
            this.currentChunkPos = new class_1923(class_2338.method_49638(this.currentPos));
        }
        return this.currentChunkPos;
    }

    protected void notifyElementsOfPositionUpdate(class_243 newPos, class_243 delta) {
        for (var e : this.elements) {
            e.notifyMove(this.currentPos, newPos, delta);
        }
    }

    public void sendPacket(class_2596<? extends class_2602> packet) {
        for (var player : players) {
            player.method_14364(packet);
        }
    }

    public void sendPacket(class_2596<? extends class_2602> packet, Predicate<class_3222> predicate) {
        for (var player : players) {
            if (predicate.test(player.method_32311())) {
                player.method_14364(packet);
            }
        }
    }

    @Nullable
    public HolderAttachment getAttachment() {
        return this.attachment;
    }

    public void setAttachment(@Nullable HolderAttachment attachment) {
        var oldAttachment = this.attachment;
        this.attachment = attachment;
        if (attachment != null) {
            if (this.currentPos == class_243.field_1353 && attachment.canUpdatePosition()) {
                this.updateInitialPosition();
            }
            attachment.updateCurrentlyTracking(new ArrayList<>(this.players));
            this.onAttachmentSet(attachment, oldAttachment);
        } else if (oldAttachment != null) {
            this.onAttachmentRemoved(oldAttachment);
        }
    }

    protected void onAttachmentSet(HolderAttachment attachment, @Nullable HolderAttachment oldAttachment) {
    }

    protected void onAttachmentRemoved(HolderAttachment oldAttachment) {
    }

    public class_243 getPos() {
        if (this.currentPos == class_243.field_1353 && attachment != null && attachment.canUpdatePosition()) {
            this.currentPos = attachment.getPos();
        }

        return this.currentPos;
    }

    public VirtualElement.InteractionHandler getInteraction(int id, class_3222 player) {
        for (var x : this.elements) {
            if (x.getEntityIds().contains(id)) {
                return x.getInteractionHandler(player);
            }
        }
        return VirtualElement.InteractionHandler.EMPTY;
    }

    public void destroy() {
        for (var x : new ArrayList<>(this.players)) {
            this.stopWatching(x);
        }

        if (this.attachment != null) {
            this.attachment.destroy();
        }
    }

    public Collection<class_3244> getWatchingPlayers() {
        return this.players;
    }

    @Override
    public boolean equals(Object o) {
        return this == o;
    }

    @Override
    public int hashCode() {
        return 31;
    }

    public void notifyUpdate(HolderAttachment.UpdateType updateType) {
    }

    public IntList getAttachedPassengerEntityIds() {
        return this.attachedPassengerEntityIds;
    }

    public <T extends VirtualElement> T addPassengerElement(T element) {
        this.addElement(element);
        attachedPassengerEntityIds.addAll(element.getEntityIds());
        return element;
    }

    public void addPassengerId(int i) {
        this.attachedPassengerEntityIds.add(i);
    }

    public void removePassengerId(int i) {
        this.attachedPassengerEntityIds.removeInt(i);
    }
}
