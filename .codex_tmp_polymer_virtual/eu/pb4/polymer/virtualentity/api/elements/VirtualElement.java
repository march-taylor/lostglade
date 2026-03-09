package eu.pb4.polymer.virtualentity.api.elements;

import eu.pb4.polymer.virtualentity.api.ElementHolder;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;
import net.minecraft.class_10371;
import net.minecraft.class_1268;
import net.minecraft.class_1297;
import net.minecraft.class_243;
import net.minecraft.class_2596;
import net.minecraft.class_2602;
import net.minecraft.class_2824;
import net.minecraft.class_3222;

public interface VirtualElement {
    IntList getEntityIds();

    @ApiStatus.OverrideOnly
    void setHolder(@Nullable ElementHolder holder);

    @Nullable
    ElementHolder getHolder();

    class_243 getOffset();
    void setOffset(class_243 vec3d);
    @Nullable
    default class_243 getOverridePos() {
        return null;
    }

    @Nullable
    default void setOverridePos(class_243 vec3d) {};

    default class_243 getCurrentPos() {
        var pos = this.getOverridePos();
        return pos != null ? pos : (this.getHolder() != null ? this.getHolder().getPos().method_1019(this.getOffset()) : class_243.field_1353);
    }


    @Nullable
    default class_243 getLastSyncedPos() {
        return this.getCurrentPos();
    }

    void startWatching(class_3222 player, Consumer<class_2596<class_2602>> packetConsumer);
    void stopWatching(class_3222 player, Consumer<class_2596<class_2602>> packetConsumer);
    void notifyMove(class_243 oldPos, class_243 currentPos, class_243 delta);
    void tick();

    InteractionHandler getInteractionHandler(class_3222 player);

    default void setInitialPosition(class_243 newPos) {
    }

    interface InteractionHandler {
        InteractionHandler EMPTY = new InteractionHandler() {};

        static InteractionHandler redirect(class_1297 redirectedEntity) {
            return new InteractionHandler() {
                @Override
                public void interact(class_3222 player, class_1268 hand) {
                    player.field_13987.method_12062(class_2824.method_34207(redirectedEntity, player.method_5715(), hand));
                }

                @Override
                public void interactAt(class_3222 player, class_1268 hand, class_243 pos) {
                    player.field_13987.method_12062(class_2824.method_34208(redirectedEntity, player.method_5715(), hand, pos));
                }

                @Override
                public void attack(class_3222 player) {
                    player.field_13987.method_12062(class_2824.method_34206(redirectedEntity, player.method_5715()));
                }

                @Override
                public void pickItem(class_3222 player, boolean includeData) {
                    player.field_13987.method_12084(new class_10371(redirectedEntity.method_5628(), includeData));
                }
            };
        }

        default void interact(class_3222 player, class_1268 hand) {};
        default void interactAt(class_3222 player, class_1268 hand, class_243 pos) {};
        default void attack(class_3222 player) {};
        default void pickItem(class_3222 player, boolean includeData) {};
    }
}
