package com.fimkov.witchsatelier.registry;

import com.fimkov.witchsatelier.WitchsAtelier;
import com.fimkov.witchsatelier.block.InkwellBlock;
import com.fimkov.witchsatelier.block.InkwellBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Optional;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(WitchsAtelier.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, WitchsAtelier.MODID);

    /** Silverwood: the root of all magic. Its dead branches turn black; boiled, they give woodcruor. */
    public static final TreeGrower SILVERWOOD_GROWER = new TreeGrower(WitchsAtelier.MODID + "_silverwood", Optional.empty(),
            Optional.of(ResourceKey.create(Registries.CONFIGURED_FEATURE, ResourceLocation.fromNamespaceAndPath(WitchsAtelier.MODID, "silverwood"))),
            Optional.empty());

    public static final DeferredBlock<RotatedPillarBlock> SILVERWOOD_LOG = BLOCKS.registerBlock("silverwood_log", RotatedPillarBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.BIRCH_LOG).mapColor(MapColor.SNOW));
    public static final DeferredBlock<LeavesBlock> SILVERWOOD_LEAVES = BLOCKS.registerBlock("silverwood_leaves", LeavesBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.BIRCH_LEAVES).mapColor(MapColor.QUARTZ));
    public static final DeferredBlock<SaplingBlock> SILVERWOOD_SAPLING = BLOCKS.registerBlock("silverwood_sapling",
            p -> new SaplingBlock(SILVERWOOD_GROWER, p), BlockBehaviour.Properties.ofFullCopy(Blocks.BIRCH_SAPLING));

    public static final DeferredBlock<InkwellBlock> INKWELL = BLOCKS.registerBlock("inkwell", InkwellBlock::new,
            BlockBehaviour.Properties.of().strength(0.3f).sound(SoundType.GLASS).noOcclusion().pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<InkwellBlockEntity>> INKWELL_BE = BLOCK_ENTITIES.register("inkwell",
            () -> BlockEntityType.Builder.of(InkwellBlockEntity::new, INKWELL.get()).build(null));

    public static final DeferredBlock<com.fimkov.witchsatelier.block.sealed.BeastWardingTowerBlock> WARDING_TOWER = BLOCKS.registerBlock("beast_warding_tower",
            com.fimkov.witchsatelier.block.sealed.BeastWardingTowerBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.BRICKS).noOcclusion().lightLevel(st -> st.getValue(com.fimkov.witchsatelier.block.sealed.SealedBlock.LIT) ? 7 : 0));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.fimkov.witchsatelier.block.sealed.BeastWardingTowerBlock.Entity>> WARDING_TOWER_BE =
            BLOCK_ENTITIES.register("beast_warding_tower", () -> BlockEntityType.Builder.of(com.fimkov.witchsatelier.block.sealed.BeastWardingTowerBlock.Entity::new, WARDING_TOWER.get()).build(null));

    public static final DeferredBlock<com.fimkov.witchsatelier.block.sealed.WashingBarrelBlock> WASHING_BARREL = BLOCKS.registerBlock("washing_barrel",
            com.fimkov.witchsatelier.block.sealed.WashingBarrelBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.BARREL));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.fimkov.witchsatelier.block.sealed.WashingBarrelBlock.Entity>> WASHING_BARREL_BE =
            BLOCK_ENTITIES.register("washing_barrel", () -> BlockEntityType.Builder.of(com.fimkov.witchsatelier.block.sealed.WashingBarrelBlock.Entity::new, WASHING_BARREL.get()).build(null));

    public static final DeferredBlock<com.fimkov.witchsatelier.block.SealchairBlock> SEALCHAIR = BLOCKS.registerBlock("sealchair",
            com.fimkov.witchsatelier.block.SealchairBlock::new, BlockBehaviour.Properties.of().mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                    .strength(2f).sound(SoundType.WOOD).noOcclusion().ignitedByLava()
                    .instrument(net.minecraft.world.level.block.state.properties.NoteBlockInstrument.BASS));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.fimkov.witchsatelier.block.SealchairBlock.Entity>> SEALCHAIR_BE =
            BLOCK_ENTITIES.register("sealchair", () -> BlockEntityType.Builder.of(com.fimkov.witchsatelier.block.SealchairBlock.Entity::new, SEALCHAIR.get()).build(null));

    public static final DeferredBlock<com.fimkov.witchsatelier.block.ArcaneLensBlock> ARCANE_LENS = BLOCKS.registerBlock("arcane_lens",
            com.fimkov.witchsatelier.block.ArcaneLensBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.GLASS).strength(2f).sound(SoundType.AMETHYST).noOcclusion().lightLevel(s -> 4));
    public static final DeferredBlock<com.fimkov.witchsatelier.block.VoidBasinBlock> VOID_BASIN = BLOCKS.registerBlock("void_basin",
            com.fimkov.witchsatelier.block.VoidBasinBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.CAULDRON).lightLevel(s -> 3));

    public static final DeferredBlock<com.fimkov.witchsatelier.block.GlowstonePathBlock> GLOWSTONE_PATH = BLOCKS.registerBlock("glowstone_path",
            com.fimkov.witchsatelier.block.GlowstonePathBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.SMOOTH_STONE)
                    // two hovering halves, lower than a block: it must not hide its neighbours' faces
                    .noOcclusion()
                    .lightLevel(st -> st.getValue(com.fimkov.witchsatelier.block.GlowstonePathBlock.LIT) ? 12 : 0));
    public static final DeferredBlock<com.fimkov.witchsatelier.block.DragonTeacupBlock> DRAGON_TEACUP = BLOCKS.registerBlock("dragon_teacup",
            com.fimkov.witchsatelier.block.DragonTeacupBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.FLOWER_POT).sound(SoundType.GLASS));

    /** A creature turned to stone by petrification. */
    public static final DeferredBlock<com.fimkov.witchsatelier.block.StatueBlock> STATUE = BLOCKS.registerBlock("statue",
            com.fimkov.witchsatelier.block.StatueBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.STONE).noOcclusion().strength(1.5f, 6f));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.fimkov.witchsatelier.block.StatueBlock.Entity>> STATUE_BE =
            BLOCK_ENTITIES.register("statue", () -> BlockEntityType.Builder.of(com.fimkov.witchsatelier.block.StatueBlock.Entity::new, STATUE.get()).build(null));

    /** The ground of the starfall beyond the Void Basin: stars to stand on. */
    public static final DeferredBlock<com.fimkov.witchsatelier.block.StarBlock> STAR_BLOCK = BLOCKS.registerBlock("star_block",
            com.fimkov.witchsatelier.block.StarBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.BEDROCK).lightLevel(s -> 6).sound(net.minecraft.world.level.block.SoundType.AMETHYST));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.fimkov.witchsatelier.block.StarBlock.Entity>> STAR_BLOCK_BE =
            BLOCK_ENTITIES.register("star_block", () -> BlockEntityType.Builder.of(com.fimkov.witchsatelier.block.StarBlock.Entity::new, STAR_BLOCK.get()).build(null));

    /** A Raincleaver planted point-down: no water or lava comes near it. */
    public static final DeferredBlock<com.fimkov.witchsatelier.block.PlantedRaincleaverBlock> PLANTED_RAINCLEAVER = BLOCKS.registerBlock("planted_raincleaver",
            com.fimkov.witchsatelier.block.PlantedRaincleaverBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BARS).noOcclusion().noCollission().strength(0.6f).pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.fimkov.witchsatelier.block.PlantedRaincleaverBlock.Entity>> PLANTED_RAINCLEAVER_BE =
            BLOCK_ENTITIES.register("planted_raincleaver", () -> BlockEntityType.Builder.of(com.fimkov.witchsatelier.block.PlantedRaincleaverBlock.Entity::new, PLANTED_RAINCLEAVER.get()).build(null));

    /** The opening an active windowway makes in its wall. */
    public static final DeferredBlock<com.fimkov.witchsatelier.windowway.WindowwayGapBlock> WINDOWWAY_GAP = BLOCKS.registerBlock("windowway_gap",
            com.fimkov.witchsatelier.windowway.WindowwayGapBlock::new,
            BlockBehaviour.Properties.of().noCollission().noOcclusion().strength(-1f, 3600000f).noLootTable()
                    .isViewBlocking((st, l, p) -> false).isSuffocating((st, l, p) -> false).pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.fimkov.witchsatelier.windowway.WindowwayGapBlockEntity>> WINDOWWAY_GAP_BE =
            BLOCK_ENTITIES.register("windowway_gap", () -> BlockEntityType.Builder.of(com.fimkov.witchsatelier.windowway.WindowwayGapBlockEntity::new, WINDOWWAY_GAP.get()).build(null));

    private ModBlocks() {}
}
