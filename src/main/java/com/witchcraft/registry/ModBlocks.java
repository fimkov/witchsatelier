package com.witchcraft.registry;

import com.witchcraft.WitchCraft;
import com.witchcraft.block.InkwellBlock;
import com.witchcraft.block.InkwellBlockEntity;
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
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(WitchCraft.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, WitchCraft.MODID);

    /** Silverwood: the root of all magic. Its dead branches turn black; boiled, they give woodcruor. */
    public static final TreeGrower SILVERWOOD_GROWER = new TreeGrower(WitchCraft.MODID + "_silverwood", Optional.empty(),
            Optional.of(ResourceKey.create(Registries.CONFIGURED_FEATURE, ResourceLocation.fromNamespaceAndPath(WitchCraft.MODID, "silverwood"))),
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

    public static final DeferredBlock<com.witchcraft.block.sealed.BeastWardingTowerBlock> WARDING_TOWER = BLOCKS.registerBlock("beast_warding_tower",
            com.witchcraft.block.sealed.BeastWardingTowerBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.BRICKS).noOcclusion().lightLevel(st -> st.getValue(com.witchcraft.block.sealed.SealedBlock.LIT) ? 7 : 0));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.witchcraft.block.sealed.BeastWardingTowerBlock.Entity>> WARDING_TOWER_BE =
            BLOCK_ENTITIES.register("beast_warding_tower", () -> BlockEntityType.Builder.of(com.witchcraft.block.sealed.BeastWardingTowerBlock.Entity::new, WARDING_TOWER.get()).build(null));

    public static final DeferredBlock<com.witchcraft.block.sealed.WashingBarrelBlock> WASHING_BARREL = BLOCKS.registerBlock("washing_barrel",
            com.witchcraft.block.sealed.WashingBarrelBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.BARREL));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.witchcraft.block.sealed.WashingBarrelBlock.Entity>> WASHING_BARREL_BE =
            BLOCK_ENTITIES.register("washing_barrel", () -> BlockEntityType.Builder.of(com.witchcraft.block.sealed.WashingBarrelBlock.Entity::new, WASHING_BARREL.get()).build(null));

    public static final DeferredBlock<com.witchcraft.block.SealchairBlock> SEALCHAIR = BLOCKS.registerBlock("sealchair",
            com.witchcraft.block.SealchairBlock::new, BlockBehaviour.Properties.of().mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                    .strength(2f).sound(SoundType.WOOD).noOcclusion().ignitedByLava()
                    .instrument(net.minecraft.world.level.block.state.properties.NoteBlockInstrument.BASS));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.witchcraft.block.SealchairBlock.Entity>> SEALCHAIR_BE =
            BLOCK_ENTITIES.register("sealchair", () -> BlockEntityType.Builder.of(com.witchcraft.block.SealchairBlock.Entity::new, SEALCHAIR.get()).build(null));

    public static final DeferredBlock<com.witchcraft.block.ArcaneLensBlock> ARCANE_LENS = BLOCKS.registerBlock("arcane_lens",
            com.witchcraft.block.ArcaneLensBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.GLASS).strength(2f).sound(SoundType.AMETHYST).noOcclusion().lightLevel(s -> 4));
    public static final DeferredBlock<com.witchcraft.block.VoidBasinBlock> VOID_BASIN = BLOCKS.registerBlock("void_basin",
            com.witchcraft.block.VoidBasinBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.CAULDRON).lightLevel(s -> 3));

    public static final DeferredBlock<com.witchcraft.block.GlowstonePathBlock> GLOWSTONE_PATH = BLOCKS.registerBlock("glowstone_path",
            com.witchcraft.block.GlowstonePathBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.SMOOTH_STONE)
                    .lightLevel(st -> st.getValue(com.witchcraft.block.GlowstonePathBlock.LIT) ? 12 : 0));
    public static final DeferredBlock<com.witchcraft.block.DragonTeacupBlock> DRAGON_TEACUP = BLOCKS.registerBlock("dragon_teacup",
            com.witchcraft.block.DragonTeacupBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.FLOWER_POT).sound(SoundType.GLASS));

    /** A creature turned to stone by petrification. */
    public static final DeferredBlock<com.witchcraft.block.StatueBlock> STATUE = BLOCKS.registerBlock("statue",
            com.witchcraft.block.StatueBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.STONE).noOcclusion().strength(1.5f, 6f));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.witchcraft.block.StatueBlock.Entity>> STATUE_BE =
            BLOCK_ENTITIES.register("statue", () -> BlockEntityType.Builder.of(com.witchcraft.block.StatueBlock.Entity::new, STATUE.get()).build(null));

    /** The opening an active windowway makes in its wall. */
    public static final DeferredBlock<com.witchcraft.windowway.WindowwayGapBlock> WINDOWWAY_GAP = BLOCKS.registerBlock("windowway_gap",
            com.witchcraft.windowway.WindowwayGapBlock::new,
            BlockBehaviour.Properties.of().noCollission().noOcclusion().strength(-1f, 3600000f).noLootTable()
                    .isViewBlocking((st, l, p) -> false).isSuffocating((st, l, p) -> false).pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.witchcraft.windowway.WindowwayGapBlockEntity>> WINDOWWAY_GAP_BE =
            BLOCK_ENTITIES.register("windowway_gap", () -> BlockEntityType.Builder.of(com.witchcraft.windowway.WindowwayGapBlockEntity::new, WINDOWWAY_GAP.get()).build(null));

    private ModBlocks() {}
}
