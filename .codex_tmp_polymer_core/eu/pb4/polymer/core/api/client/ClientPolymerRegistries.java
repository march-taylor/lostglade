package eu.pb4.polymer.core.api.client;

import eu.pb4.polymer.core.impl.client.InternalClientRegistry;
import eu.pb4.polymer.core.api.utils.PolymerRegistry;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.class_1291;
import net.minecraft.class_2361;
import net.minecraft.class_2591;
import net.minecraft.class_3611;
import net.minecraft.class_3852;
import net.minecraft.class_3917;
import net.minecraft.class_9331;

@Environment(EnvType.CLIENT)
public interface ClientPolymerRegistries {
    PolymerRegistry<ClientPolymerBlock> BLOCKS = InternalClientRegistry.BLOCKS;
    class_2361<ClientPolymerBlock.State> BLOCK_STATES = InternalClientRegistry.BLOCK_STATES;
    PolymerRegistry<ClientPolymerItem> ITEMS = InternalClientRegistry.ITEMS;
    PolymerRegistry<ClientPolymerEntityType> ENTITY_TYPES = InternalClientRegistry.ENTITY_TYPES;
    PolymerRegistry<ClientPolymerEntry<class_3852>> VILLAGER_PROFESSIONS = InternalClientRegistry.VILLAGER_PROFESSIONS;
    PolymerRegistry<ClientPolymerEntry<class_2591<?>>> BLOCK_ENTITY = InternalClientRegistry.BLOCK_ENTITY;
    PolymerRegistry<ClientPolymerEntry<class_1291>> STATUS_EFFECT = InternalClientRegistry.STATUS_EFFECT;
    PolymerRegistry<ClientPolymerEntry<class_3611>> FLUID = InternalClientRegistry.FLUID;
    PolymerRegistry<ClientPolymerEntry<class_3917<?>>> SCREEN_HANDLER = InternalClientRegistry.SCREEN_HANDLER;
    PolymerRegistry<ClientPolymerEntry<class_9331<?>>> DATA_COMPONENT_TYPE = InternalClientRegistry.DATA_COMPONENT_TYPE;
    PolymerRegistry<ClientPolymerEntry<class_9331<?>>> ENCHANTMENT_COMPONENT_TYPE = InternalClientRegistry.ENCHANTMENT_COMPONENT_TYPE;
}
