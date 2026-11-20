package com.witchcraft.client;

import com.witchcraft.network.ModNetwork;
import com.witchcraft.registry.ModItems;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Client-only wiring; only ever loaded on the physical client. */
public final class ClientSetup {
    private ClientSetup() {}

    /** Armour drawn with one of our own 3D models (baked lazily, once models are loaded). */
    private static net.neoforged.neoforge.client.extensions.common.IClientItemExtensions armorModel(net.minecraft.client.model.geom.ModelLayerLocation loc) {
        return new net.neoforged.neoforge.client.extensions.common.IClientItemExtensions() {
            private net.minecraft.client.model.HumanoidModel<?> model;

            @Override
            public net.minecraft.client.model.HumanoidModel<?> getHumanoidArmorModel(net.minecraft.world.entity.LivingEntity entity,
                    net.minecraft.world.item.ItemStack stack, net.minecraft.world.entity.EquipmentSlot slot, net.minecraft.client.model.HumanoidModel<?> original) {
                if (model == null) model = com.witchcraft.client.model.WitchModels.bake(loc);
                return model;
            }

            @Override
            public void setupModelAnimations(net.minecraft.world.entity.LivingEntity entity, net.minecraft.world.item.ItemStack stack,
                    net.minecraft.world.entity.EquipmentSlot slot, net.minecraft.client.model.Model m, float limbSwing, float limbSwingAmount,
                    float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
                // the garment's own cloth moves with the wearer (gliding in a wingcloak streams it out behind)
                float flare = entity.isFallFlying() ? 1 : 0;
                if (m instanceof net.minecraft.client.model.HumanoidModel<?> hm) com.witchcraft.client.model.ClothMotion.apply(hm, entity, limbSwing, limbSwingAmount, ageInTicks, flare);
            }
        };
    }

    /** Colour of the ink seen through the inkwell's glass. */
    static int inkTint(com.witchcraft.glyph.ink.InkType t) {
        return switch (t) {
            case CONJURING -> 0xFF2A1858;
            case WATERPROOF -> 0xFF0E5A66;
            case INVISIBLE -> 0xFFB9B0E6;
            case LASTING -> 0xFF7A4612;
            case BLOOD -> 0xFF7A0A14;
            case CHALK -> 0xFFB9A3C8;
            case SEARED -> 0xFF2A1408;
        };
    }

    /** Wet ink on a nib catches the light: brighter than the ink seen in the well. */
    static int nibTint(com.witchcraft.glyph.ink.InkType t) {
        return switch (t) {
            case CONJURING -> 0xFF6A45E0;
            case WATERPROOF -> 0xFF22B3C4;
            case INVISIBLE -> 0xFFE2DCFF;
            case LASTING -> 0xFFC07A2C;
            case BLOOD -> 0xFFC0142A;
            case CHALK -> 0xFFD8C8E0;
            case SEARED -> 0xFFFF6A1A;
        };
    }

    public static void init(IEventBus modBus) {
        ModNetwork.client = ClientGlyphs.INSTANCE;
        com.witchcraft.client.dev.VisualCheck.init();
        com.witchcraft.item.PalmQuireItem.clientOpener = hand -> net.minecraft.client.Minecraft.getInstance().setScreen(new PageScreen(hand));
        IEventBus game = NeoForge.EVENT_BUS;
        game.addListener(ClientDrawing::onInteract);
        game.addListener(ClientDrawing::onClientTick);
        game.addListener(ClientDrawing::onScroll);
        game.addListener(TraceGuide::onScroll);
        game.addListener(GlyphRenderer::onRenderLevel);
        game.addListener(PennantRibbons::onRenderLevel);
        game.addListener(com.witchcraft.client.fx.SpellFxClient::render);
        game.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> ClientGlyphs.INSTANCE.reset());
        game.addListener(ClientKeys::onClientTick);
        game.addListener(com.witchcraft.client.portal.WindowwayClient::onClientTick);
        game.addListener(com.witchcraft.client.portal.WindowwayClient::onFramePre);
        game.addListener(com.witchcraft.client.portal.WindowwayClient::onRenderLevel);
        modBus.addListener(com.witchcraft.client.portal.WindowwayClient::registerShaders);
        game.addListener(HandsRenderer::onClientTick);
        game.addListener(HandsRenderer::onRenderHand);
        modBus.addListener((net.neoforged.neoforge.client.event.RegisterColorHandlersEvent.Block e) -> e.register(
                (state, level, pos, tint) -> tint == 0 ? inkTint(state.getValue(com.witchcraft.block.InkwellBlock.INK)) : -1,
                com.witchcraft.registry.ModBlocks.INKWELL.get()));
        modBus.addListener((net.neoforged.neoforge.client.event.RegisterColorHandlersEvent.Item e) -> e.register((stack, tint) -> {
            if (tint != 0) return -1;
            var c = com.witchcraft.item.InkwellItem.contents(stack);
            return inkTint(c == null ? com.witchcraft.glyph.ink.InkType.CONJURING : c.type());
        }, com.witchcraft.registry.ModItems.INKWELL.get()));
        // the pen's nib shows the ink it was dipped in; a dry nib is bare steel
        modBus.addListener((net.neoforged.neoforge.client.event.RegisterColorHandlersEvent.Item e) -> e.register((stack, tint) -> {
            if (tint != 1) return -1;
            if (stack.getItem() instanceof com.witchcraft.item.WoodcruorWandItem) return 0xFF6A1E24;
            var nib = com.witchcraft.item.InkPenItem.loaded(stack);
            if (nib == null) return stack.getItem() instanceof com.witchcraft.item.SearneedleItem ? 0xFFFF7A2A : 0xFFC8CED8;
            return nibTint(stack.getItem() instanceof com.witchcraft.item.SearneedleItem ? com.witchcraft.glyph.ink.InkType.SEARED : nib.type());
        }, ModItems.INK_PEN.get(), ModItems.WOODCRUOR_WAND.get(), ModItems.SEARNEEDLE.get()));
        modBus.addListener(ClientKeys::register);
        modBus.addListener((net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent e) -> {
            e.registerSpriteSet(com.witchcraft.registry.ModParticles.DROPLET.get(), s -> new com.witchcraft.client.fx.WaterParticle.Provider(s, com.witchcraft.client.fx.WaterParticle.Kind.DROPLET));
            e.registerSpriteSet(com.witchcraft.registry.ModParticles.MIST.get(), s -> new com.witchcraft.client.fx.WaterParticle.Provider(s, com.witchcraft.client.fx.WaterParticle.Kind.MIST));
            e.registerSpriteSet(com.witchcraft.registry.ModParticles.MOTE.get(), s -> new com.witchcraft.client.fx.GlowParticle.Provider(s, com.witchcraft.client.fx.GlowParticle.Kind.MOTE));
            e.registerSpriteSet(com.witchcraft.registry.ModParticles.SPARK.get(), s -> new com.witchcraft.client.fx.GlowParticle.Provider(s, com.witchcraft.client.fx.GlowParticle.Kind.SPARK));
            e.registerSpriteSet(com.witchcraft.registry.ModParticles.RUNE.get(), s -> new com.witchcraft.client.fx.GlowParticle.Provider(s, com.witchcraft.client.fx.GlowParticle.Kind.RUNE));
        });
        modBus.addListener((RegisterClientExtensionsEvent e) -> {
            e.registerItem(new PenAnimation(), ModItems.INK_PEN.get(), ModItems.WOODCRUOR_WAND.get(), ModItems.SEARNEEDLE.get());
            e.registerItem(new net.neoforged.neoforge.client.extensions.common.IClientItemExtensions() {
                private PennantItemRenderer renderer;

                @Override
                public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
                    if (renderer == null) renderer = new PennantItemRenderer();
                    return renderer;
                }
            }, ModItems.CAPTURE_PENNANT.get());
            e.registerItem(armorModel(com.witchcraft.client.model.WitchModels.HAT), ModItems.WITCH_HAT.get(), ModItems.APPRENTICE_HAT.get());
            e.registerItem(armorModel(com.witchcraft.client.model.WitchModels.CAPELET), ModItems.WITCH_ROBE.get());
            e.registerItem(armorModel(com.witchcraft.client.model.WitchModels.SKIRT), ModItems.WITCH_STOCKINGS.get());
            e.registerItem(armorModel(com.witchcraft.client.model.WitchModels.MANTLE), ModItems.WITCH_MANTLE.get(), ModItems.MIRROR_MANTLE.get(), ModItems.BRIMHAT_MANTLE.get(), ModItems.WINGCLOAK.get());
        });
        modBus.addListener((net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers e) ->
                {
                    e.registerEntityRenderer(com.witchcraft.registry.ModEntities.STRANGER.get(), StrangerRenderer::new);
                    e.registerEntityRenderer(com.witchcraft.registry.ModEntities.MENTOR.get(), MentorRenderer::new);
                    e.registerEntityRenderer(com.witchcraft.registry.ModEntities.BRIMHAT.get(), BrimhatRenderer::new);
                    e.registerEntityRenderer(com.witchcraft.registry.ModEntities.BRIMHAT_ELDER.get(), ElderRenderer::new);
                    e.registerBlockEntityRenderer(com.witchcraft.registry.ModBlocks.STATUE_BE.get(), StatueRenderer::new);
                    e.registerBlockEntityRenderer(com.witchcraft.registry.ModBlocks.SEALCHAIR_BE.get(), SealchairRenderer.AtRest::new);
                    e.registerBlockEntityRenderer(com.witchcraft.registry.ModBlocks.WINDOWWAY_GAP_BE.get(), com.witchcraft.client.portal.GapRenderer::new);
                    e.registerEntityRenderer(com.witchcraft.registry.ModEntities.SEALCHAIR.get(), SealchairRenderer::new);
                    e.registerEntityRenderer(com.witchcraft.registry.ModEntities.KNIGHT_MORALIS.get(), KnightMoralisRenderer::new);
                });
        modBus.addListener((net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterLayerDefinitions e) -> {
            e.registerLayerDefinition(com.witchcraft.client.model.WitchModels.HAT, com.witchcraft.client.model.WitchModels::pointedHat);
            e.registerLayerDefinition(StrangerRenderer.LAYER, StrangerRenderer::layer);
            e.registerLayerDefinition(MentorRenderer.LAYER, MentorRenderer::layer);
            e.registerLayerDefinition(BrimhatRenderer.LAYER, BrimhatRenderer::layer);
            e.registerLayerDefinition(BrimhatRenderer.HAT, BrimhatRenderer::hat);
            e.registerLayerDefinition(ElderRenderer.LAYER, ElderRenderer::layer);
            e.registerLayerDefinition(KnightMoralisRenderer.LAYER, KnightMoralisRenderer::layer);
            e.registerLayerDefinition(KnightMoralisRenderer.GEAR, KnightMoralisRenderer::gear);
            e.registerLayerDefinition(SealchairRenderer.LAYER, SealchairRenderer::layer);
            e.registerLayerDefinition(com.witchcraft.client.model.WitchModels.CAPELET, com.witchcraft.client.model.WitchModels::capelet);
            e.registerLayerDefinition(com.witchcraft.client.model.WitchModels.SKIRT, com.witchcraft.client.model.WitchModels::skirt);
            e.registerLayerDefinition(com.witchcraft.client.model.WitchModels.MANTLE, com.witchcraft.client.model.WitchModels::mantle);
        });
    }
}
