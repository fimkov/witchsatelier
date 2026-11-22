package com.fimkov.witchsatelier;

import com.fimkov.witchsatelier.event.ServerEvents;
import com.fimkov.witchsatelier.network.ModNetwork;
import com.fimkov.witchsatelier.registry.ModComponents;
import com.fimkov.witchsatelier.registry.ModItems;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;

@Mod(WitchsAtelier.MODID)
public final class WitchsAtelier {
    public static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    public static final String MODID = "witchsatelier";

    public WitchsAtelier(IEventBus modBus) {
        com.fimkov.witchsatelier.registry.ModBlocks.BLOCKS.register(modBus);
        com.fimkov.witchsatelier.registry.ModBlocks.BLOCK_ENTITIES.register(modBus);
        com.fimkov.witchsatelier.item.attire.WitchAttire.MATERIALS.register(modBus);
        com.fimkov.witchsatelier.registry.ModEntities.ENTITIES.register(modBus);
        com.fimkov.witchsatelier.registry.ModFeatures.FEATURES.register(modBus);
        com.fimkov.witchsatelier.registry.ModStructures.TYPES.register(modBus);
        com.fimkov.witchsatelier.registry.ModStructures.PIECES.register(modBus);
        com.fimkov.witchsatelier.advancement.MagicTrigger.TRIGGERS.register(modBus);
        com.fimkov.witchsatelier.registry.ModSounds.SOUNDS.register(modBus);
        modBus.addListener(com.fimkov.witchsatelier.registry.ModEntities::attributes);
        modBus.addListener(com.fimkov.witchsatelier.registry.ModEntities::spawnPlacements);
        NeoForge.EVENT_BUS.addListener(com.fimkov.witchsatelier.entity.Taboo::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(com.fimkov.witchsatelier.spell.Airborne::onPlayerTick);
        // development only: the multiplayer smoke test (the release jar leaves dev/ out)
        if (System.getProperty("witchsatelier.smoke") != null && net.neoforged.fml.loading.FMLEnvironment.dist.isDedicatedServer())
            com.fimkov.witchsatelier.dev.SmokeServer.init();
        NeoForge.EVENT_BUS.addListener(com.fimkov.witchsatelier.entity.Taboo::onDeath);
        ModItems.ITEMS.register(modBus);
        com.fimkov.witchsatelier.loot.SpellLootModifier.SERIALIZERS.register(modBus);
        ModItems.TABS.register(modBus);
        ModComponents.COMPONENTS.register(modBus);
        com.fimkov.witchsatelier.registry.ModAttachments.TYPES.register(modBus);
        com.fimkov.witchsatelier.registry.ModParticles.TYPES.register(modBus);
        modBus.addListener(ModNetwork::register);
        ServerEvents.register(NeoForge.EVENT_BUS);
        NeoForge.EVENT_BUS.addListener(com.fimkov.witchsatelier.entity.StrangerSpawner::onLevelTick);
        NeoForge.EVENT_BUS.addListener(com.fimkov.witchsatelier.item.artifact.DormantEvents::onUseItem);
        NeoForge.EVENT_BUS.addListener(com.fimkov.witchsatelier.block.Starfall::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(com.fimkov.witchsatelier.glyph.page.PageService::washBoots);
        NeoForge.EVENT_BUS.addListener(com.fimkov.witchsatelier.item.artifact.DormantEvents::onUseOnBlock);
        NeoForge.EVENT_BUS.addListener(com.fimkov.witchsatelier.block.sealed.BeastWardingTowerBlock::onSpawnCheck);
        NeoForge.EVENT_BUS.addListener(com.fimkov.witchsatelier.item.attire.WitchAttireItem::onChangeTarget);
        NeoForge.EVENT_BUS.addListener(com.fimkov.witchsatelier.registry.ModBrewing::register);
        if (net.neoforged.fml.ModList.get().isLoaded("sable")) {
            // in its own class, so nothing of Sable is touched when it is not installed
            try {
                com.fimkov.witchsatelier.compat.SableCompat.init();
                LOGGER.info("Sable found: glyph magic moves physics ships too");
            } catch (Throwable t) {
                LOGGER.warn("Sable is installed but its API did not match; ships will not feel glyph magic", t);
            }
        }
        if (FMLEnvironment.dist == Dist.CLIENT) {
            com.fimkov.witchsatelier.client.ClientSetup.init(modBus);
        }
    }
}
