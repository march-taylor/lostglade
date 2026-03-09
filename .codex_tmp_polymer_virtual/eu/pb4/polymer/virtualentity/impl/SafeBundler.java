package eu.pb4.polymer.virtualentity.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.class_2596;
import net.minecraft.class_2602;
import net.minecraft.class_8039;
import net.minecraft.class_8042;

public class SafeBundler implements Consumer<class_2596<class_2602>> {
    private List<class_2596<? super class_2602>> packets = new ArrayList<>();
    private final Consumer<class_2596<class_2602>> consumer;

    public SafeBundler(Consumer<class_2596<class_2602>> consumer) {
        this.consumer = consumer;
    }

    @Override
    public void accept(class_2596<class_2602> packet) {
        this.packets.add(packet);
        if (this.packets.size() == class_8039.field_41878) {
            this.consumer.accept(new class_8042(this.packets));
            this.packets = new ArrayList<>();
        }
    }

    public void finish() {
        if (!this.packets.isEmpty()) {
            this.consumer.accept(new class_8042(this.packets));
            this.packets = null;
        }
    }
}
