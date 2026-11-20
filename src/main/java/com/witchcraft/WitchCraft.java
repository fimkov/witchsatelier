package com.witchcraft;

import com.witchcraft.event.ServerEvents;
import com.witchcraft.network.ModNetwork;
import com.witchcraft.registry.ModComponents;
import com.witchcraft.registry.ModItems;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;

@Mod(WitchCraft.MODID)
public final class WitchCraft {
    public static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    public static final String MODID = "witchcraft";

    public WitchCraft(IEventBus modBus) {
        com.witchcraft.registry.ModBlocks.BLOCKS.register(modBus);
        com.witchcraft.registry.ModBlocks.BLOCK_ENTITIES.register(modBus);
        com.witchcraft.item.attire.WitchAttire.MATERIALS.register(modBus);
        com.witchcraft.registry.ModEntities.ENTITIES.register(modBus);
        com.witchcraft.registry.ModFeatures.FEATURES.register(modBus);
        com.witchcraft.registry.ModStructures.TYPES.register(modBus);
        com.witchcraft.registry.ModStructures.PIECES.register(modBus);
        com.witchcraft.advancement.MagicTrigger.TRIGGERS.register(modBus);
        com.witchcraft.registry.ModSounds.SOUNDS.register(modBus);
        modBus.addListener(com.witchcraft.registry.ModEntities::attributes);
        modBus.addListener(com.witchcraft.registry.ModEntities::spawnPlacements);
        NeoForge.EVENT_BUS.addListener(com.witchcraft.entity.Taboo::onPlayerTick);
        ModItems.ITEMS.register(modBus);
        com.witchcraft.loot.SpellLootModifier.SERIALIZERS.register(modBus);
        ModItems.TABS.register(modBus);
        ModComponents.COMPONENTS.register(modBus);
        com.witchcraft.registry.ModAttachments.TYPES.register(modBus);
        com.witchcraft.registry.ModParticles.TYPES.register(modBus);
        modBus.addListener(ModNetwork::register);
        ServerEvents.register(NeoForge.EVENT_BUS);
        NeoForge.EVENT_BUS.addListener(com.witchcraft.entity.StrangerSpawner::onLevelTick);
        NeoForge.EVENT_BUS.addListener(com.witchcraft.block.sealed.BeastWardingTowerBlock::onSpawnCheck);
        NeoForge.EVENT_BUS.addListener(com.witchcraft.item.attire.WitchAttireItem::onChangeTarget);
        NeoForge.EVENT_BUS.addListener(com.witchcraft.registry.ModBrewing::register);
        if (net.neoforged.fml.ModList.get().isLoaded("sable")) {
            // in its own class, so nothing of Sable is touched when it is not installed
            try {
                com.witchcraft.compat.SableCompat.init();
                LOGGER.info("Sable found: glyph magic moves physics ships too");
            } catch (Throwable t) {
                LOGGER.warn("Sable is installed but its API did not match; ships will not feel glyph magic", t);
            }
        }
        if (FMLEnvironment.dist == Dist.CLIENT) {
            com.witchcraft.client.ClientSetup.init(modBus);
        }
    }
}
