package com.fimkov.witchsatelier;

import com.fimkov.witchsatelier.advancement.MagicTrigger;
import com.fimkov.witchsatelier.block.Starfall;
import com.fimkov.witchsatelier.block.sealed.BeastWardingTowerBlock;
import com.fimkov.witchsatelier.client.ClientSetup;
import com.fimkov.witchsatelier.compat.SableCompat;
import com.fimkov.witchsatelier.dev.SmokeServer;
import com.fimkov.witchsatelier.entity.StrangerSpawner;
import com.fimkov.witchsatelier.entity.Taboo;
import com.fimkov.witchsatelier.entity.Witnesses;
import com.fimkov.witchsatelier.event.ServerEvents;
import com.fimkov.witchsatelier.glyph.page.PageDispensing;
import com.fimkov.witchsatelier.glyph.page.PageService;
import com.fimkov.witchsatelier.item.artifact.DormantEvents;
import com.fimkov.witchsatelier.item.attire.WitchAttire;
import com.fimkov.witchsatelier.item.attire.WitchAttireItem;
import com.fimkov.witchsatelier.loot.SpellLootModifier;
import com.fimkov.witchsatelier.network.ModNetwork;
import com.fimkov.witchsatelier.recipe.ModRecipes;
import com.fimkov.witchsatelier.registry.ModAttachments;
import com.fimkov.witchsatelier.registry.ModBlocks;
import com.fimkov.witchsatelier.registry.ModBrewing;
import com.fimkov.witchsatelier.registry.ModComponents;
import com.fimkov.witchsatelier.registry.ModEntities;
import com.fimkov.witchsatelier.registry.ModFeatures;
import com.fimkov.witchsatelier.registry.ModItems;
import com.fimkov.witchsatelier.registry.ModParticles;
import com.fimkov.witchsatelier.registry.ModSounds;
import com.fimkov.witchsatelier.registry.ModStructures;
import com.fimkov.witchsatelier.spell.Airborne;
import com.mojang.logging.LogUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(WitchsAtelier.MODID)
public final class WitchsAtelier {
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final String MODID = "witchsatelier";

    public WitchsAtelier(IEventBus modBus) {
        ModBlocks.BLOCKS.register(modBus);
        ModBlocks.BLOCK_ENTITIES.register(modBus);
        WitchAttire.MATERIALS.register(modBus);
        ModEntities.ENTITIES.register(modBus);
        ModFeatures.FEATURES.register(modBus);
        ModStructures.TYPES.register(modBus);
        ModStructures.PIECES.register(modBus);
        MagicTrigger.TRIGGERS.register(modBus);
        ModSounds.SOUNDS.register(modBus);
        modBus.addListener(ModEntities::attributes);
        modBus.addListener((FMLCommonSetupEvent e) -> e.enqueueWork(PageDispensing::register));
        modBus.addListener(ModEntities::spawnPlacements);
        NeoForge.EVENT_BUS.addListener(Taboo::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(Airborne::onPlayerTick);

        if (System.getProperty("witchsatelier.smoke") != null && FMLEnvironment.dist.isDedicatedServer())
            SmokeServer.init();
        NeoForge.EVENT_BUS.addListener(Taboo::onDeath);
        NeoForge.EVENT_BUS.addListener(Taboo::onClone);
        NeoForge.EVENT_BUS.addListener(Witnesses::onTick);
        NeoForge.EVENT_BUS.addListener(Witnesses::onDeath);
        ModItems.ITEMS.register(modBus);
        SpellLootModifier.SERIALIZERS.register(modBus);
        ModRecipes.SERIALIZERS.register(modBus);
        ModItems.TABS.register(modBus);
        ModComponents.COMPONENTS.register(modBus);
        ModAttachments.TYPES.register(modBus);
        ModParticles.TYPES.register(modBus);
        modBus.addListener(ModNetwork::register);
        ServerEvents.register(NeoForge.EVENT_BUS);
        NeoForge.EVENT_BUS.addListener(StrangerSpawner::onLevelTick);
        com.fimkov.witchsatelier.world.PassageStars.register(modBus, NeoForge.EVENT_BUS);
        com.fimkov.witchsatelier.windowway.WindowwayService.register(modBus);
        com.fimkov.witchsatelier.advancement.Feats.register(NeoForge.EVENT_BUS);
        NeoForge.EVENT_BUS.addListener(DormantEvents::onUseItem);
        NeoForge.EVENT_BUS.addListener(Starfall::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(PageService::washBoots);
        NeoForge.EVENT_BUS.addListener(DormantEvents::onUseOnBlock);
        NeoForge.EVENT_BUS.addListener(BeastWardingTowerBlock::onSpawnCheck);
        NeoForge.EVENT_BUS.addListener(WitchAttireItem::onChangeTarget);
        NeoForge.EVENT_BUS.addListener(ModBrewing::register);
        if (ModList.get().isLoaded("sable")) {
            try {
                SableCompat.init();
                LOGGER.info("Sable found: glyph magic moves physics ships too");
            } catch (Throwable t) {
                LOGGER.warn("Sable is installed but its API did not match; ships will not feel glyph magic", t);
            }
        }
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientSetup.init(modBus);
        }
    }
}
