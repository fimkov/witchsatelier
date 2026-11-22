package com.fimkov.witchsatelier.registry;

import com.fimkov.witchsatelier.WitchsAtelier;
import com.fimkov.witchsatelier.block.ArcaneLensBlock;
import com.fimkov.witchsatelier.block.DragonTeacupBlock;
import com.fimkov.witchsatelier.block.GlowstonePathBlock;
import com.fimkov.witchsatelier.block.InkwellBlock;
import com.fimkov.witchsatelier.block.InkwellBlockEntity;
import com.fimkov.witchsatelier.block.PetrifiedBlock;
import com.fimkov.witchsatelier.block.PetrifiedClusterBlock;
import com.fimkov.witchsatelier.block.PlantedRaincleaverBlock;
import com.fimkov.witchsatelier.block.SealchairBlock;
import com.fimkov.witchsatelier.block.StarBlock;
import com.fimkov.witchsatelier.block.StatueBlock;
import com.fimkov.witchsatelier.block.VoidBasinBlock;
import com.fimkov.witchsatelier.block.sealed.BeastWardingTowerBlock;
import com.fimkov.witchsatelier.block.sealed.SealedBlock;
import com.fimkov.witchsatelier.block.sealed.WashingBarrelBlock;
import com.fimkov.witchsatelier.windowway.WindowwayGapBlock;
import com.fimkov.witchsatelier.windowway.WindowwayGapBlockEntity;
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
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Optional;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(WitchsAtelier.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, WitchsAtelier.MODID);

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
            BlockBehaviour.Properties.of().strength(0.3f).sound(SoundType.GLASS).noOcclusion().pushReaction(PushReaction.DESTROY));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<InkwellBlockEntity>> INKWELL_BE = BLOCK_ENTITIES.register("inkwell",
            () -> BlockEntityType.Builder.of(InkwellBlockEntity::new, INKWELL.get()).build(null));

    public static final DeferredBlock<BeastWardingTowerBlock> WARDING_TOWER = BLOCKS.registerBlock("beast_warding_tower",
            BeastWardingTowerBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.BRICKS).noOcclusion().lightLevel(st -> st.getValue(SealedBlock.LIT) ? 7 : 0));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BeastWardingTowerBlock.Entity>> WARDING_TOWER_BE =
            BLOCK_ENTITIES.register("beast_warding_tower", () -> BlockEntityType.Builder.of(BeastWardingTowerBlock.Entity::new, WARDING_TOWER.get()).build(null));

    public static final DeferredBlock<WashingBarrelBlock> WASHING_BARREL = BLOCKS.registerBlock("washing_barrel",
            WashingBarrelBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.BARREL));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WashingBarrelBlock.Entity>> WASHING_BARREL_BE =
            BLOCK_ENTITIES.register("washing_barrel", () -> BlockEntityType.Builder.of(WashingBarrelBlock.Entity::new, WASHING_BARREL.get()).build(null));

    public static final DeferredBlock<SealchairBlock> SEALCHAIR = BLOCKS.registerBlock("sealchair",
            SealchairBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.WOOD)
                    .strength(2f).sound(SoundType.WOOD).noOcclusion().ignitedByLava()
                    .instrument(NoteBlockInstrument.BASS));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SealchairBlock.Entity>> SEALCHAIR_BE =
            BLOCK_ENTITIES.register("sealchair", () -> BlockEntityType.Builder.of(SealchairBlock.Entity::new, SEALCHAIR.get()).build(null));

    public static final DeferredBlock<ArcaneLensBlock> ARCANE_LENS = BLOCKS.registerBlock("arcane_lens",
            ArcaneLensBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.GLASS).strength(2f).sound(SoundType.AMETHYST).noOcclusion().lightLevel(s -> 4));
    public static final DeferredBlock<VoidBasinBlock> VOID_BASIN = BLOCKS.registerBlock("void_basin",
            VoidBasinBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.CAULDRON).lightLevel(s -> 3));

    public static final DeferredBlock<GlowstonePathBlock> GLOWSTONE_PATH = BLOCKS.registerBlock("glowstone_path",
            GlowstonePathBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.SMOOTH_STONE)

                    .noOcclusion()
                    .lightLevel(st -> st.getValue(GlowstonePathBlock.LIT) ? 12 : 0));
    public static final DeferredBlock<DragonTeacupBlock> DRAGON_TEACUP = BLOCKS.registerBlock("dragon_teacup",
            DragonTeacupBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.FLOWER_POT).sound(SoundType.GLASS));

    public static final DeferredBlock<StatueBlock> STATUE = BLOCKS.registerBlock("statue",
            StatueBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.STONE).noOcclusion().strength(1.5f, 6f));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<StatueBlock.Entity>> STATUE_BE =
            BLOCK_ENTITIES.register("statue", () -> BlockEntityType.Builder.of(StatueBlock.Entity::new, STATUE.get()).build(null));

    public static final DeferredBlock<PetrifiedBlock> PETRIFIED = BLOCKS.registerBlock("petrified_stone",
            PetrifiedBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.CALCITE).strength(1.2f, 6f).sound(SoundType.AMETHYST).lightLevel(s -> 7)
                    .noOcclusion().isViewBlocking((s, l, p) -> false).isSuffocating((s, l, p) -> false).isRedstoneConductor((s, l, p) -> false));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PetrifiedBlock.Entity>> PETRIFIED_BE =
            BLOCK_ENTITIES.register("petrified_stone", () -> BlockEntityType.Builder.of(PetrifiedBlock.Entity::new, PETRIFIED.get()).build(null));

    public static final DeferredBlock<PetrifiedClusterBlock> PETRIFIED_CLUSTER = BLOCKS.registerBlock("petrified_cluster",
            PetrifiedClusterBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.SMALL_AMETHYST_BUD).noCollission().noOcclusion().strength(0.3f).lightLevel(s -> 9));

    public static final DeferredBlock<StarBlock> STAR_BLOCK = BLOCKS.registerBlock("star_block",
            StarBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.BEDROCK).lightLevel(s -> 6).sound(SoundType.AMETHYST));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<StarBlock.Entity>> STAR_BLOCK_BE =
            BLOCK_ENTITIES.register("star_block", () -> BlockEntityType.Builder.of(StarBlock.Entity::new, STAR_BLOCK.get()).build(null));

    public static final DeferredBlock<PlantedRaincleaverBlock> PLANTED_RAINCLEAVER = BLOCKS.registerBlock("planted_raincleaver",
            PlantedRaincleaverBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BARS).noOcclusion().noCollission().strength(0.6f).pushReaction(PushReaction.DESTROY));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PlantedRaincleaverBlock.Entity>> PLANTED_RAINCLEAVER_BE =
            BLOCK_ENTITIES.register("planted_raincleaver", () -> BlockEntityType.Builder.of(PlantedRaincleaverBlock.Entity::new, PLANTED_RAINCLEAVER.get()).build(null));

    public static final DeferredBlock<WindowwayGapBlock> WINDOWWAY_GAP = BLOCKS.registerBlock("windowway_gap",
            WindowwayGapBlock::new,
            BlockBehaviour.Properties.of().noCollission().noOcclusion().strength(-1f, 3600000f).noLootTable()
                    .isViewBlocking((st, l, p) -> false).isSuffocating((st, l, p) -> false).pushReaction(PushReaction.BLOCK));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WindowwayGapBlockEntity>> WINDOWWAY_GAP_BE =
            BLOCK_ENTITIES.register("windowway_gap", () -> BlockEntityType.Builder.of(WindowwayGapBlockEntity::new, WINDOWWAY_GAP.get()).build(null));

    private ModBlocks() {}
}
