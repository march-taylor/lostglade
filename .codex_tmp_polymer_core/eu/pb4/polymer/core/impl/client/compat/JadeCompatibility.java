package eu.pb4.polymer.core.impl.client.compat;

import eu.pb4.polymer.core.api.client.ClientPolymerBlock;
import eu.pb4.polymer.core.api.client.PolymerClientUtils;
import eu.pb4.polymer.core.impl.PolymerImpl;
import eu.pb4.polymer.core.impl.PolymerImplUtils;
import eu.pb4.polymer.core.impl.client.InternalClientRegistry;
import net.minecraft.class_124;
import net.minecraft.class_1297;
import net.minecraft.class_2248;
import net.minecraft.class_2561;
import net.minecraft.class_2680;
import net.minecraft.class_2960;
import net.minecraft.class_310;
import net.minecraft.class_5250;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;
import snownee.jade.addon.core.ModNameProvider;
import snownee.jade.addon.debug.RegistryNameProvider;
import snownee.jade.api.*;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.config.IWailaConfig;
import snownee.jade.api.theme.IThemeHelper;
import snownee.jade.api.ui.BoxStyle;
import snownee.jade.api.ui.Element;
import snownee.jade.api.ui.JadeUI;
import snownee.jade.impl.ui.ItemStackElement;
import snownee.jade.util.ModIdentification;

@ApiStatus.Internal
@SuppressWarnings("UnstableApiUsage")
public class JadeCompatibility implements IWailaPlugin {

    @Override
    public void registerClient(IWailaClientRegistration registrar) {
        if (PolymerImpl.IS_CLIENT) {
            registrar.registerBlockComponent(BlockOverride.INSTANCE, class_2248.class);
            registrar.registerEntityComponent(EntityOverride.INSTANCE, class_1297.class);

            registrar.addItemModNameCallback(PolymerImplUtils::getModName);
        }
    }

    private static class BlockOverride implements IBlockComponentProvider {
        public static final BlockOverride INSTANCE = new BlockOverride();

        private static final class_2960 ID = class_2960.method_12829("polymer:blockstate");


        @Override
        public @Nullable Element getIcon(BlockAccessor accessor, IPluginConfig config, Element currentIcon) {
            try {
                var block = InternalClientRegistry.getBlockAt(accessor.getPosition());
                if (block != ClientPolymerBlock.NONE_STATE) {
                    class_2680 state = accessor.getLevel().method_8320(accessor.getPosition());

                    var itemStack = block.block().displayStack();
                    if (itemStack.method_7960()) {
                        itemStack = state.method_65171(accessor.getLevel(), accessor.getPosition(), false);
                        if (!itemStack.method_7960() && state.method_31709()) {
                            var blockEntity = accessor.getLevel().method_8321(accessor.getPosition());

                            if (blockEntity != null) {
                                itemStack.method_57365(blockEntity.method_58693());
                            }
                        }
                    }

                    return ItemStackElement.of(itemStack);
                }
            } catch (Throwable e) {

            }
            return null;
        }

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            try {

                var block = InternalClientRegistry.getBlockAt(accessor.getPosition());
            if (block != ClientPolymerBlock.NONE_STATE) {
                var formatting = IWailaConfig.get().formatting();
                tooltip.clear();
                try {
                    tooltip.add(IThemeHelper.get().title(block.block().name().getString()), JadeIds.CORE_OBJECT_NAME);
                } catch (Throwable e) {
                }
                try {

                    RegistryNameProvider.Mode mode = config.getEnum(JadeIds.DEBUG_REGISTRY_NAME);

                    if (mode != RegistryNameProvider.Mode.OFF) {
                        if (mode != RegistryNameProvider.Mode.ADVANCED_TOOLTIPS || class_310.method_1551().field_1690.field_1827) {
                            tooltip.add(formatting.registryName(block.block().identifier().toString()));
                        }
                    }
                } catch (Throwable e) {
                }

                try {

                    if (config.get(JadeIds.DEBUG_BLOCK_STATES)) {
                        ITooltip box = JadeUI.tooltip();
                        block.states().entrySet().forEach((p) -> {
                            class_5250 valueText = class_2561.method_43470(" " + p.getValue()).method_27695();
                            if (p.getValue().equals("true") || p.getValue().equals("false")) {
                                valueText = valueText.method_27692(p.getValue().equals("true") ? class_124.field_1060 : class_124.field_1061);
                            }

                            box.add(class_2561.method_43470(p.getKey() + ":").method_10852(valueText));
                        });
                        tooltip.add(JadeUI.box(box, BoxStyle.nestedBox()));
                    }
                } catch (Throwable e) {
                }
                try {

                    if (config.getEnum(JadeIds.CORE_MOD_NAME) == ModNameProvider.Mode.ON) {
                        String modName = ModIdentification.getModName(block.block().identifier());

                        if (modName == null || modName.isEmpty() || modName.equals("Minecraft")) {
                            modName = "Server";
                        }
                        tooltip.add(IThemeHelper.get().modName(modName));
                    }
                } catch (Throwable e) {
                }

            }
            } catch (Throwable e) {

            }
        }

        @Override
        public class_2960 getUid() {
            return ID;
        }

        @Override
        public int getDefaultPriority() {
            return 99999;
        }

        @Override
        public boolean isRequired() {
            return true;
        }
    }

    private static final class EntityOverride implements IEntityComponentProvider {
        public static final EntityOverride INSTANCE = new EntityOverride();
        private static final class_2960 ID = class_2960.method_12829("polymer:entities");


        @Override
        public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
            try {

                var entity = accessor.getEntity();

            var type = PolymerClientUtils.getEntityType(entity);

            if (type != null) {
                tooltip.clear();
                if (type != null) {
                    try {

                        tooltip.add(IThemeHelper.get().title(entity.method_5476().getString()), JadeIds.CORE_OBJECT_NAME);
                    } catch (Throwable e) {
                    }

                    var formatting = IWailaConfig.get().formatting();

                    var mode = config.getEnum(JadeIds.DEBUG_REGISTRY_NAME);
                    try {

                        if (mode != RegistryNameProvider.Mode.OFF) {
                            if (mode != RegistryNameProvider.Mode.ADVANCED_TOOLTIPS || class_310.method_1551().field_1690.field_1827) {
                                tooltip.add(formatting.registryName(type.identifier().toString()));
                            }
                        }
                    } catch (Throwable e) {
                    }
                    try {
                        if (config.getEnum(JadeIds.CORE_MOD_NAME) == ModNameProvider.Mode.ON) {
                            String modName = ModIdentification.getModName(type.identifier());

                            if (modName == null || modName.isEmpty() || modName.equals("Minecraft")) {
                                modName = "Server";
                            }
                            tooltip.add(IThemeHelper.get().modName(modName));
                        }
                    } catch (Throwable e) {
                    }

                }
            }
            } catch (Throwable e) {

            }
        }

        @Override
        public class_2960 getUid() {
            return ID;
        }

        @Override
        public int getDefaultPriority() {
            return 999999;
        }

        @Override
        public boolean isRequired() {
            return true;
        }
    }
}