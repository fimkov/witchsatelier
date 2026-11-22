package com.fimkov.witchsatelier.client;

import com.fimkov.witchsatelier.WitchsAtelier;
import com.fimkov.witchsatelier.block.InkwellBlock;
import com.fimkov.witchsatelier.client.dev.SmokeClient;
import com.fimkov.witchsatelier.client.dev.VisualCheck;
import com.fimkov.witchsatelier.client.fx.GlowParticle;
import com.fimkov.witchsatelier.client.fx.SpellFxClient;
import com.fimkov.witchsatelier.client.fx.WaterParticle;
import com.fimkov.witchsatelier.client.model.ClothMotion;
import com.fimkov.witchsatelier.client.model.WitchModels;
import com.fimkov.witchsatelier.client.portal.GapRenderer;
import com.fimkov.witchsatelier.client.portal.WindowwayClient;
import com.fimkov.witchsatelier.entity.GlyphArrow;
import com.fimkov.witchsatelier.glyph.ink.InkType;
import com.fimkov.witchsatelier.item.InkPenItem;
import com.fimkov.witchsatelier.item.InkwellItem;
import com.fimkov.witchsatelier.item.PalmQuireItem;
import com.fimkov.witchsatelier.item.SearneedleItem;
import com.fimkov.witchsatelier.item.WoodcruorWandItem;
import com.fimkov.witchsatelier.item.artifact.Awakening;
import com.fimkov.witchsatelier.network.ModNetwork;
import com.fimkov.witchsatelier.registry.ModBlocks;
import com.fimkov.witchsatelier.registry.ModEntities;
import com.fimkov.witchsatelier.registry.ModItems;
import com.fimkov.witchsatelier.registry.ModParticles;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

public final class ClientSetup {
    private ClientSetup() {}

    private static IClientItemExtensions armorModel(ModelLayerLocation loc) {
        return new IClientItemExtensions() {
            private HumanoidModel<?> model;

            @Override
            public HumanoidModel<?> getHumanoidArmorModel(LivingEntity entity,
                    ItemStack stack, EquipmentSlot slot, HumanoidModel<?> original) {
                if (model == null) model = WitchModels.bake(loc);
                return model;
            }

            @Override
            public void setupModelAnimations(LivingEntity entity, ItemStack stack,
                    EquipmentSlot slot, Model m, float limbSwing, float limbSwingAmount,
                    float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
                float flare = entity.isFallFlying() ? 1 : 0;
                if (m instanceof HumanoidModel<?> hm) ClothMotion.apply(hm, entity, limbSwing, limbSwingAmount, ageInTicks, flare);
            }
        };
    }

    static int inkTint(InkType t) {
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

    static int nibTint(InkType t) {
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

        if (System.getProperty("witchsatelier.visualcheck") != null) VisualCheck.init();
        if (System.getProperty("witchsatelier.smoke") != null) SmokeClient.init();
        PalmQuireItem.clientOpener = hand -> Minecraft.getInstance().setScreen(new PageScreen(hand));
        IEventBus game = NeoForge.EVENT_BUS;
        game.addListener(ClientDrawing::onInteract);
        game.addListener(ClientDrawing::onClientTick);
        game.addListener(ClientDrawing::onScroll);
        game.addListener(TraceGuide::onScroll);
        game.addListener(GlyphRenderer::onRenderLevel);
        game.addListener(PennantRibbons::onRenderLevel);
        game.addListener(SpellFxClient::render);
        game.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> ClientGlyphs.INSTANCE.reset());
        game.addListener(ClientKeys::onClientTick);
        game.addListener(WindowwayClient::onClientTick);
        game.addListener(WindowwayClient::onFramePre);
        game.addListener(WindowwayClient::onRenderLevel);
        modBus.addListener(WindowwayClient::registerShaders);
        modBus.addListener(SpellFxClient::registerShaders);
        game.addListener(HandsRenderer::onClientTick);
        game.addListener(HandsRenderer::onRenderHand);
        modBus.addListener((RegisterColorHandlersEvent.Block e) -> e.register(
                (state, level, pos, tint) -> tint == 0 ? inkTint(state.getValue(InkwellBlock.INK)) : -1,
                ModBlocks.INKWELL.get()));
        modBus.addListener((RegisterColorHandlersEvent.Item e) -> e.register((stack, tint) -> {
            if (tint != 0) return -1;
            var c = InkwellItem.contents(stack);
            return inkTint(c == null ? InkType.CONJURING : c.type());
        }, ModItems.INKWELL.get()));

        modBus.addListener((RegisterColorHandlersEvent.Item e) -> e.register((stack, tint) -> {
            if (tint != 1) return -1;
            if (stack.getItem() instanceof WoodcruorWandItem) return 0xFF6A1E24;
            var nib = InkPenItem.loaded(stack);
            if (nib == null) return stack.getItem() instanceof SearneedleItem ? 0xFFFF7A2A : 0xFFC8CED8;
            return nibTint(stack.getItem() instanceof SearneedleItem ? InkType.SEARED : nib.type());
        }, ModItems.INK_PEN.get(), ModItems.WOODCRUOR_WAND.get(), ModItems.SEARNEEDLE.get()));
        modBus.addListener(ClientKeys::register);
        modBus.addListener((RegisterParticleProvidersEvent e) -> {
            e.registerSpriteSet(ModParticles.DROPLET.get(), s -> new WaterParticle.Provider(s, WaterParticle.Kind.DROPLET));
            e.registerSpriteSet(ModParticles.MIST.get(), s -> new WaterParticle.Provider(s, WaterParticle.Kind.MIST));
            e.registerSpriteSet(ModParticles.MOTE.get(), s -> new GlowParticle.Provider(s, GlowParticle.Kind.MOTE));
            e.registerSpriteSet(ModParticles.SPARK.get(), s -> new GlowParticle.Provider(s, GlowParticle.Kind.SPARK));
            e.registerSpriteSet(ModParticles.RUNE.get(), s -> new GlowParticle.Provider(s, GlowParticle.Kind.RUNE));
        });
        modBus.addListener((RegisterClientExtensionsEvent e) -> {
            e.registerItem(new PenAnimation(), ModItems.INK_PEN.get(), ModItems.WOODCRUOR_WAND.get(), ModItems.SEARNEEDLE.get());
            e.registerItem(new IClientItemExtensions() {
                private PennantItemRenderer renderer;

                @Override
                public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                    if (renderer == null) renderer = new PennantItemRenderer();
                    return renderer;
                }
            }, ModItems.CAPTURE_PENNANT.get());
            e.registerItem(armorModel(WitchModels.HAT), ModItems.WITCH_HAT.get(), ModItems.APPRENTICE_HAT.get());
            e.registerItem(armorModel(WitchModels.CAPELET), ModItems.WITCH_ROBE.get());
            e.registerItem(armorModel(WitchModels.MANTLE), ModItems.WITCH_MANTLE.get(), ModItems.MIRROR_MANTLE.get(), ModItems.BRIMHAT_MANTLE.get(), ModItems.WINGCLOAK.get());
        });
        NeoForge.EVENT_BUS.addListener(SoaringClient::onClientTick);
        NeoForge.EVENT_BUS.addListener((ItemTooltipEvent e) -> {
            var id = BuiltInRegistries.ITEM.getKey(e.getItemStack().getItem());
            String desc = "item." + id.getNamespace() + "." + id.getPath() + ".desc";
            if (id.getNamespace().equals(WitchsAtelier.MODID) && e.getItemStack().getItem() instanceof BlockItem
                    && I18n.exists(desc))
                e.getToolTip().add(Component.translatable(desc).withStyle(ChatFormatting.GRAY));
            if (Awakening.dormant(e.getItemStack()))
                e.getToolTip().add(Math.min(1, e.getToolTip().size()), Component.translatable("tooltip.witchsatelier.dormant")
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC));
        });
        modBus.addListener((EntityRenderersEvent.RegisterRenderers e) ->
                {
                    e.registerEntityRenderer(ModEntities.STRANGER.get(), StrangerRenderer::new);
                    e.registerEntityRenderer(ModEntities.BRIMHAT.get(), BrimhatRenderer::new);
                    e.registerBlockEntityRenderer(ModBlocks.STATUE_BE.get(), StatueRenderer::new);
                    e.registerBlockEntityRenderer(ModBlocks.WASHING_BARREL_BE.get(), WashingBarrelRenderer::new);
                    e.registerBlockEntityRenderer(ModBlocks.PLANTED_RAINCLEAVER_BE.get(), PlantedRaincleaverRenderer::new);
                    e.registerBlockEntityRenderer(ModBlocks.STAR_BLOCK_BE.get(), StarBlockRenderer::new);
                    e.registerBlockEntityRenderer(ModBlocks.SEALCHAIR_BE.get(), SealchairRenderer.AtRest::new);
                    e.registerBlockEntityRenderer(ModBlocks.WINDOWWAY_GAP_BE.get(), GapRenderer::new);
                    e.registerEntityRenderer(ModEntities.SEALCHAIR.get(), SealchairRenderer::new);
                    e.registerEntityRenderer(ModEntities.GLYPH_ARROW.get(), ctx -> new ArrowRenderer<GlyphArrow>(ctx) {
                        @Override
                        public ResourceLocation getTextureLocation(GlyphArrow a) {
                            return ResourceLocation.withDefaultNamespace("textures/entity/projectiles/arrow.png");
                        }
                    });
                    e.registerEntityRenderer(ModEntities.KNIGHT_MORALIS.get(), KnightMoralisRenderer::new);
                });
        modBus.addListener((EntityRenderersEvent.RegisterLayerDefinitions e) -> {
            e.registerLayerDefinition(WitchModels.HAT, WitchModels::pointedHat);
            e.registerLayerDefinition(StrangerRenderer.LAYER, StrangerRenderer::layer);
            e.registerLayerDefinition(BrimhatRenderer.LAYER, BrimhatRenderer::layer);
            e.registerLayerDefinition(BrimhatRenderer.HAT, BrimhatRenderer::hat);
            e.registerLayerDefinition(KnightMoralisRenderer.LAYER, KnightMoralisRenderer::layer);
            e.registerLayerDefinition(KnightMoralisRenderer.GEAR, KnightMoralisRenderer::gear);
            e.registerLayerDefinition(SealchairRenderer.LAYER, SealchairRenderer::layer);
            e.registerLayerDefinition(WitchModels.CAPELET, WitchModels::capelet);
            e.registerLayerDefinition(WitchModels.MANTLE, WitchModels::mantle);
        });
    }
}
