package eu.pb4.polymer.virtualentity.api.elements;

import com.mojang.datafixers.util.Pair;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.mixin.LivingEntityAccessor;
import eu.pb4.polymer.virtualentity.mixin.accessors.ServerEntityAccessor;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.class_1297;
import net.minecraft.class_1299;
import net.minecraft.class_1304;
import net.minecraft.class_1309;
import net.minecraft.class_1799;
import net.minecraft.class_243;
import net.minecraft.class_2596;
import net.minecraft.class_2602;
import net.minecraft.class_2716;
import net.minecraft.class_2744;
import net.minecraft.class_3218;
import net.minecraft.class_3222;
import net.minecraft.class_3231;
import net.minecraft.class_3730;

public class EntityElement<T extends class_1297> extends AbstractElement {
    private final T entity;
    private final class_3231 entry;

    public EntityElement(T entity, class_3218 world) {
        this(entity, world, InteractionHandler.EMPTY);
    }

    public EntityElement(T entity, class_3218 world, InteractionHandler handler) {
        this.entity = entity;
        this.entry = new class_3231(world, this.entity, 1, false, new class_3231.class_12004() {
            @Override
            public void method_18730(class_2596<? super class_2602> packet) {
                sendPacket((class_2596<class_2602>) packet);
            }

            @Override
            public void method_18734(class_2596<? super class_2602> packet) {
                method_18730(packet);
            }

            @Override
            public void method_74531(class_2596<? super class_2602> packet, Predicate<class_3222> predicate) {
                sendPacket((class_2596<class_2602>) packet, predicate);
            }
        });
        this.setInteractionHandler(handler);
    }

    public EntityElement(class_1299<T> entityType, class_3218 world) {
        this(entityType.method_5883(world, class_3730.field_52444), world);
    }

    public EntityElement(class_1299<T> entityType, class_3218 world, InteractionHandler handler) {
        this(entityType.method_5883(world, class_3730.field_52444), world);
    }

    public T entity() {
        return this.entity;
    }

    @Override
    public IntList getEntityIds() {
        return IntList.of(this.entity.method_5628());
    }

    @Override
    public void setHolder(@Nullable ElementHolder holder) {
        super.setHolder(holder);
        if (holder != null) {
            var pos = this.getCurrentPos();
            this.entity.method_23327(pos.field_1352, pos.field_1351, pos.field_1350);
        }
    }

    @Override
    public void setOffset(class_243 vec3d) {
        super.setOffset(vec3d);
        if (this.getOverridePos() == null && this.getHolder() != null) {
            var pos = this.getHolder().getPos().method_1019(vec3d);
            this.entity.method_23327(pos.field_1352, pos.field_1351, pos.field_1350);
        }
    }

    @Override
    public void setOverridePos(class_243 vec3d) {
        super.setOverridePos(vec3d);
        if (this.getHolder() != null) {
            this.entity.method_23327(vec3d.field_1352, vec3d.field_1351, vec3d.field_1350);
        }
    }

    @Override
    public void startWatching(class_3222 player, Consumer<class_2596<class_2602>> packetConsumer) {
        if (!this.elementVisiblityPredicate.test(player)) {
            return;
        }
        this.entry.method_18757(player, packetConsumer);
    }

    @Override
    public void stopWatching(class_3222 player, Consumer<class_2596<class_2602>> packetConsumer) {
        if (!this.elementVisiblityPredicate.test(player)) {
            return;
        }
       packetConsumer.accept(new class_2716(this.entity.method_5628()));
    }

    @Override
    public void notifyMove(class_243 oldPos, class_243 currentPos, class_243 delta) {
        if (this.getOverridePos() == null && this.getHolder() != null) {
            var pos = currentPos.method_1019(this.getOffset());
            this.entity.method_23327(pos.field_1352, pos.field_1351, pos.field_1350);
        }
    }

    @Override
    public void setInitialPosition(class_243 pos) {
        if (this.getOverridePos() == null) {
            pos = pos.method_1019(this.getOffset());
            this.entity.method_33574(pos);
            ((ServerEntityAccessor) this.entry).getPositionCodec().method_43494(pos);
        } else {
            ((ServerEntityAccessor) this.entry).getPositionCodec().method_43494(this.getOverridePos());
        }
    }

    @Override
    public class_243 getLastSyncedPos() {
        return this.entry.method_60942();
    }

    @Override
    public void tick() {
        this.entry.method_18756();
        if (this.entity instanceof class_1309 livingEntity) {
            this.sendEquipmentChanges(livingEntity);
        }
    }

    private void sendEquipmentChanges(class_1309 livingEntity) {
        var ac = ((LivingEntityAccessor) livingEntity);
        var equipmentChanges = ac.callCollectEquipmentChanges();
        if (equipmentChanges != null && !equipmentChanges.isEmpty()) {
            List<Pair<class_1304, class_1799>> list = new ArrayList<>(equipmentChanges.size());
            equipmentChanges.forEach((slot, stack) -> {
                class_1799 itemStack = stack.method_7972();
                list.add(Pair.of(slot, itemStack));
                //ac.callSetSyncedArmorStack(slot, itemStack);
            });

            if (this.getHolder() != null) {
                sendPacket(new class_2744(livingEntity.method_5628(), list));
            }
        }
    }
}
