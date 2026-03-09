package eu.pb4.polymer.core.impl.other;

import java.util.Collection;
import java.util.Set;
import net.minecraft.class_1761;
import net.minecraft.class_1799;
import net.minecraft.class_7699;
import net.minecraft.class_7708;

public class ItemGroupEntriesImpl implements class_1761.class_7704 {
        public final Collection<class_1799> parentTabStacks = class_7708.method_47572();
        public final Set<class_1799> searchTabStacks = class_7708.method_47572();
        private final class_1761 group;
        private final class_7699 enabledFeatures;

        public ItemGroupEntriesImpl(class_1761 group, class_7699 enabledFeatures) {
            this.group = group;
            this.enabledFeatures = enabledFeatures;
        }

        public void method_45417(class_1799 stack, class_1761.class_7705 visibility) {
            if (stack.method_7947() != 1) {
                throw new IllegalArgumentException("Stack size must be exactly 1");
            } else {
                boolean bl = this.parentTabStacks.contains(stack) && visibility != class_1761.class_7705.field_40193;
                if (bl) {
                    String var10002 = stack.method_7954().getString();
                    throw new IllegalStateException("Accidentally adding the same item stack twice " + var10002 + " to a Creative Mode Tab: " + this.group.method_7737().getString());
                } else {
                    if (stack.method_7909().method_45382(this.enabledFeatures)) {
                        switch (visibility.ordinal()) {
                            case 0:
                                this.parentTabStacks.add(stack);
                                this.searchTabStacks.add(stack);
                                break;
                            case 1:
                                this.parentTabStacks.add(stack);
                                break;
                            case 2:
                                this.searchTabStacks.add(stack);
                        }
                    }

                }
            }
        }
    }