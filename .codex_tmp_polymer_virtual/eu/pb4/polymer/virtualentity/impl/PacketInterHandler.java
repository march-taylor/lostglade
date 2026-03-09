package eu.pb4.polymer.virtualentity.impl;

import eu.pb4.polymer.virtualentity.api.elements.DisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.VirtualElement;
import net.minecraft.class_1268;
import net.minecraft.class_243;
import net.minecraft.class_2824;
import net.minecraft.class_3222;

public record PacketInterHandler(class_3222 player, VirtualElement.InteractionHandler interactionHandler) implements class_2824.class_5908 {
    @Override
    public void method_34219(class_1268 hand) {
        interactionHandler.interact(player, hand);
    }

    @Override
    public void method_34220(class_1268 hand, class_243 pos) {
        interactionHandler.interactAt(player, hand, pos);
    }

    @Override
    public void method_34218() {
        interactionHandler.attack(player);
    }
}
