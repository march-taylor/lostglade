package eu.pb4.polymer.core.impl.client.compat;

import eu.pb4.polymer.common.impl.entity.InternalEntityHelpers;
import eu.pb4.polymer.core.api.client.ClientPolymerBlock;
import eu.pb4.polymer.core.api.client.PolymerClientUtils;
import eu.pb4.polymer.core.api.item.PolymerItemUtils;
import eu.pb4.polymer.core.impl.PolymerImplUtils;
import eu.pb4.polymer.core.impl.client.InternalClientRegistry;
import mcp.mobius.waila.api.*;
import mcp.mobius.waila.api.component.ItemComponent;
import mcp.mobius.waila.api.component.PairComponent;
import net.minecraft.class_124;
import net.minecraft.class_1297;
import net.minecraft.class_1542;
import net.minecraft.class_1799;
import net.minecraft.class_1802;
import net.minecraft.class_2248;
import net.minecraft.class_2561;
import net.minecraft.class_2583;
import net.minecraft.class_2680;
import net.minecraft.class_2960;
import net.minecraft.class_7923;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public class WthitCompatibility implements IWailaClientPlugin {
    private static final class_2960 BLOCK_STATES = class_2960.method_12829("attribute.block_state");

    @Override
    public void register(IClientRegistrar registrar) {
        registrar.redirect(BlockOverride.INSTANCE, class_2248.class, 400);
        registrar.head(BlockOverride.INSTANCE, class_2248.class, 100000);
        registrar.body(BlockOverride.INSTANCE, class_2248.class, 100000);
        registrar.tail(BlockOverride.INSTANCE, class_2248.class, 100000);
        registrar.icon(BlockOverride.INSTANCE, class_2248.class, 500);

        registrar.head(ItemEntityOverride.INSTANCE, class_1542.class, 100000);
        registrar.tail(ItemEntityOverride.INSTANCE, class_1542.class, 100000);

        registrar.head(EntityOverride.INSTANCE, class_1297.class, 100000);
        registrar.tail(EntityOverride.INSTANCE, class_1297.class, 100000);

        registrar.eventListener(OtherOverrides.INSTANCE);
    }

    private static class OtherOverrides implements IEventListener {
        public static final OtherOverrides INSTANCE = new OtherOverrides();

        @Override
        public @Nullable String getHoveredItemModName(class_1799 stack, IPluginConfig config) {
            return PolymerImplUtils.getModName(stack);
        }
    }

    private static class BlockOverride implements IBlockComponentProvider {
        public static final BlockOverride INSTANCE = new BlockOverride();

        @Override
        public @Nullable ITargetRedirector.Result redirect(ITargetRedirector redirect, IBlockAccessor accessor, IPluginConfig config) {
            if (InternalClientRegistry.getBlockAt(accessor.getPosition()) != ClientPolymerBlock.NONE_STATE)
                return redirect.toSelf();
            return null;
        }

        @Override
        public @Nullable ITooltipComponent getIcon(IBlockAccessor accessor, IPluginConfig config) {
            var block = InternalClientRegistry.getBlockAt(accessor.getPosition());
            if (block != ClientPolymerBlock.NONE_STATE) {
                class_2680 state = accessor.getWorld().method_8320(accessor.getPosition());

                var itemStack = block.block().displayStack();
                if (itemStack.method_7960()) {
                    itemStack = state.method_65171(accessor.getWorld(), accessor.getPosition(), false);
                    if (!itemStack.method_7960() && state.method_31709()) {
                        var blockEntity = accessor.getWorld().method_8321(accessor.getPosition());

                        if (blockEntity != null) {
                            itemStack.method_57365(blockEntity.method_58693());
                        }
                    }
                }

                return new ItemComponent(itemStack);
            }
            return null;
        }

        @Override
        public void appendHead(ITooltip tooltip, IBlockAccessor accessor, IPluginConfig config) {
            var block = InternalClientRegistry.getBlockAt(accessor.getPosition());
            if (block != ClientPolymerBlock.NONE_STATE) {
                var formatting = IWailaConfig.get().getFormatter();
                tooltip.setLine(WailaConstants.OBJECT_NAME_TAG, formatting.blockName(block.block().name().getString()));
                if (config.getBoolean(WailaConstants.CONFIG_SHOW_REGISTRY)) {
                    tooltip.setLine(WailaConstants.REGISTRY_NAME_TAG, formatting.registryName(block.block().identifier().toString()));
                }
            }
        }

        @Override
        public void appendBody(ITooltip tooltip, IBlockAccessor accessor, IPluginConfig config) {
            if (config.getBoolean(BLOCK_STATES)) {
                var block = InternalClientRegistry.getBlockAt(accessor.getPosition());
                if (block != ClientPolymerBlock.NONE_STATE) {
                    for (var state : block.states().entrySet()) {
                        var value = state.getValue();
                        var valueText = class_2561.method_43470(value).method_10862(class_2583.field_24360.method_10977(value.equals("true") ? class_124.field_1060 : value.equals("false") ? class_124.field_1061 : class_124.field_1070));
                        tooltip.addLine(new PairComponent(class_2561.method_43470(state.getKey()), valueText));
                    }
                }
            }
        }

        @Override
        public void appendTail(ITooltip tooltip, IBlockAccessor accessor, IPluginConfig config) {
            if (config.getBoolean(WailaConstants.CONFIG_SHOW_MOD_NAME)) {
                var block = InternalClientRegistry.getBlockAt(accessor.getPosition());
                if (block != ClientPolymerBlock.NONE_STATE) {
                    String modName = IModInfo.get(block.block().identifier()).getName();

                    if (modName == null || modName.isEmpty() || modName.equals("Minecraft")) {
                        modName = PolymerImplUtils.getModName(block.block().identifier());
                    }

                    tooltip.setLine(WailaConstants.MOD_NAME_TAG, IWailaConfig.get().getFormatter().modName(modName));
                }
            }
        }
    }

    private static final class ItemEntityOverride implements IEntityComponentProvider {
        public static final ItemEntityOverride INSTANCE = new ItemEntityOverride();

        @Override
        public void appendHead(ITooltip tooltip, IEntityAccessor accessor, IPluginConfig config) {
            if (config.getBoolean(WailaConstants.CONFIG_SHOW_REGISTRY)) {

                var stack = accessor.<class_1542>getEntity().method_6983();
                var id = PolymerItemUtils.getServerIdentifier(stack);

                if (id != null) {
                    var formatting = IWailaConfig.get().getFormatter();
                    tooltip.setLine(WailaConstants.REGISTRY_NAME_TAG, formatting.registryName(id));
                }
            }
        }


        @Override
        public void appendTail(ITooltip tooltip, IEntityAccessor accessor, IPluginConfig config) {
            if (config.getBoolean(WailaConstants.CONFIG_SHOW_MOD_NAME)) {
                var stack = accessor.<class_1542>getEntity().method_6983();
                var id = PolymerItemUtils.getServerIdentifier(stack);
                if (id != null) {
                    String modName = null;
                    var regBlock = class_7923.field_41178.method_63535(id);
                    if (regBlock != null && regBlock != class_1802.field_8162) {
                        modName = IModInfo.get(regBlock).getName();
                    }

                    if (modName == null || modName.isEmpty() || (modName.equals("Minecraft") && !id.method_12836().equals("minecraft"))) {
                        modName = PolymerImplUtils.getModName(id);
                    }

                    tooltip.setLine(WailaConstants.MOD_NAME_TAG, IWailaConfig.get().getFormatter().modName(modName));
                }
            }
        }
    }


    private static final class EntityOverride implements IEntityComponentProvider {
        public static final EntityOverride INSTANCE = new EntityOverride();

        @Override
        public @Nullable ITargetRedirector.Result redirect(ITargetRedirector redirect, IEntityAccessor accessor, IPluginConfig config) {
            if (PolymerClientUtils.getEntityType(accessor.getEntity()) != null) return redirect.toSelf();
            return null;
        }

        @Override
        public void appendHead(ITooltip tooltip, IEntityAccessor accessor, IPluginConfig config) {
            if (config.getBoolean(WailaConstants.CONFIG_SHOW_REGISTRY)) {

                var entity = accessor.getEntity();
                var type = PolymerClientUtils.getEntityType(entity);
                if (type != null) {
                    var formatting = IWailaConfig.get().getFormatter();
                    tooltip.setLine(WailaConstants.REGISTRY_NAME_TAG, formatting.registryName(type.identifier()));
                }
            }
        }

        @Override
        public void appendTail(ITooltip tooltip, IEntityAccessor accessor, IPluginConfig config) {
            if (config.getBoolean(WailaConstants.CONFIG_SHOW_MOD_NAME)) {
                var type = PolymerClientUtils.getEntityType(accessor.<class_1542>getEntity());
                if (type != null) {
                    String modName = null;
                    var regBlock = class_7923.field_41177.method_63535(type.identifier());
                    if (regBlock != null) {
                        modName = IModInfo.get(InternalEntityHelpers.getEntity(regBlock)).getName();
                    }

                    if (modName == null || modName.isEmpty() || (modName.equals("Minecraft") && !type.identifier().method_12836().equals("minecraft"))) {
                        modName = PolymerImplUtils.getModName(type.identifier());
                    }

                    tooltip.setLine(WailaConstants.MOD_NAME_TAG, IWailaConfig.get().getFormatter().modName(modName));
                }
            }
        }
    }
}
