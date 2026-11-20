package com.witchcraft.worldgen;

import com.mojang.serialization.Codec;
import com.witchcraft.WitchCraft;
import com.witchcraft.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * A witch's atelier: a small half-timbered cottage of silverwood and pale plaster under a steep roof, a
 * chimney with a fire still going. Inside: shelves of books, a desk with an inkwell, a washing barrel, a
 * chest of a witch's things, and glyphs on the walls, some of them left ready with their rings open.
 * A warding tower stands by the path.
 */
public class AtelierFeature extends Feature<NoneFeatureConfiguration> {
    public static final ResourceKey<LootTable> LOOT = ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.fromNamespaceAndPath(WitchCraft.MODID, "chests/atelier"));
    private static final int W = 9, D = 11, WALL = 4;

    public AtelierFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        return build(ctx, true);
    }

    /** Builds the place; {@code checkSite} false when the site was already judged fit (a structure chose it). */
    public boolean build(FeaturePlaceContext<NoneFeatureConfiguration> ctx, boolean checkSite) {
        WorldGenLevel level = ctx.level();
        RandomSource rnd = ctx.random();
        BlockPos origin = ctx.origin();
        int x0 = origin.getX() - W / 2, z0 = origin.getZ() - D / 2;
        // the ground must be fairly level and dry
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        for (int x = 0; x < W; x += 2) for (int z = 0; z < D; z += 2) {
            int h = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x0 + x, z0 + z);
            min = Math.min(min, h);
            max = Math.max(max, h);
            if (checkSite && !level.getFluidState(new BlockPos(x0 + x, h - 1, z0 + z)).isEmpty()) return false;
        }
        if (checkSite && max - min > 3) return false;
        int y0 = min;
        BlockPos base = new BlockPos(x0, y0, z0);

        BlockState plaster = Blocks.CALCITE.defaultBlockState(), frame = ModBlocks.SILVERWOOD_LOG.get().defaultBlockState();
        BlockState floor = Blocks.SPRUCE_PLANKS.defaultBlockState(), stone = Blocks.COBBLESTONE.defaultBlockState();
        // foundation, down to the ground, and clear space above
        for (int x = -1; x <= W; x++) for (int z = -1; z <= D; z++) {
            boolean pad = x == -1 || z == -1 || x == W || z == D;
            for (int y = -1; y >= -6; y--) {
                BlockPos p = base.offset(x, y, z);
                if (!level.getBlockState(p).canBeReplaced() && !level.getBlockState(p).is(net.minecraft.tags.BlockTags.LEAVES)) break;
                set(level, p, pad ? Blocks.DIRT.defaultBlockState() : stone);
            }
            for (int y = 0; y <= 10; y++) set(level, base.offset(x, y, z), Blocks.AIR.defaultBlockState());
            if (pad) set(level, base.offset(x, -1, z), Blocks.GRASS_BLOCK.defaultBlockState());
        }
        for (int x = 0; x < W; x++) for (int z = 0; z < D; z++) {
            boolean edge = x == 0 || z == 0 || x == W - 1 || z == D - 1;
            set(level, base.offset(x, 0, z), edge ? stone : floor);
            if (!edge) continue;
            boolean post = (x == 0 || x == W - 1) && (z == 0 || z == D - 1 || z == D / 2) || (z == 0 || z == D - 1) && (x == W / 2);
            for (int y = 1; y <= WALL; y++) {
                BlockState st = post || y == WALL ? frame : plaster;
                if (y == WALL && !post) st = frame.setValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS, x == 0 || x == W - 1 ? Direction.Axis.Z : Direction.Axis.X);
                set(level, base.offset(x, y, z), st);
            }
        }
        // windows
        for (int z : new int[]{2, 3, D - 4, D - 3}) for (int y = 2; y <= 3; y++) {
            set(level, base.offset(0, y, z), Blocks.GLASS_PANE.defaultBlockState().setValue(net.minecraft.world.level.block.IronBarsBlock.NORTH, true).setValue(net.minecraft.world.level.block.IronBarsBlock.SOUTH, true));
            set(level, base.offset(W - 1, y, z), Blocks.GLASS_PANE.defaultBlockState().setValue(net.minecraft.world.level.block.IronBarsBlock.NORTH, true).setValue(net.minecraft.world.level.block.IronBarsBlock.SOUTH, true));
        }
        for (int x : new int[]{2, W - 3}) for (int y = 2; y <= 3; y++) {
            set(level, base.offset(x, y, D - 1), Blocks.GLASS_PANE.defaultBlockState().setValue(net.minecraft.world.level.block.IronBarsBlock.EAST, true).setValue(net.minecraft.world.level.block.IronBarsBlock.WEST, true));
        }
        // door
        BlockState door = Blocks.SPRUCE_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.SOUTH);
        set(level, base.offset(W / 2, 1, 0), door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        set(level, base.offset(W / 2, 2, 0), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        set(level, base.offset(W / 2, 3, 0), plaster);
        // the steep gable roof, ridge along z
        BlockState stair = Blocks.DARK_OAK_STAIRS.defaultBlockState();
        for (int step = 0; step <= W / 2 + 1; step++) {
            int y = WALL + 1 + step;
            for (int z = -1; z <= D; z++) {
                int xl = -1 + step, xr = W - step;
                if (xl < xr) {
                    set(level, base.offset(xl, y, z), stair.setValue(StairBlock.FACING, Direction.EAST));
                    set(level, base.offset(xr, y, z), stair.setValue(StairBlock.FACING, Direction.WEST));
                    // the gable ends
                    if (z == 0 || z == D - 1) for (int x = xl + 1; x < xr; x++) set(level, base.offset(x, y, z), plaster);
                    // underside of the eaves
                    if (step > 0 && (z == 0 || z == D - 1)) {
                        set(level, base.offset(xl, y - 1, z), stair.setValue(StairBlock.FACING, Direction.WEST).setValue(StairBlock.HALF, Half.TOP));
                        set(level, base.offset(xr, y - 1, z), stair.setValue(StairBlock.FACING, Direction.EAST).setValue(StairBlock.HALF, Half.TOP));
                    }
                } else if (xl == xr) {
                    set(level, base.offset(xl, y, z), Blocks.DARK_OAK_SLAB.defaultBlockState());
                }
            }
        }
        // a round window high in the front gable
        set(level, base.offset(W / 2, WALL + 2, 0), Blocks.GLASS_PANE.defaultBlockState().setValue(net.minecraft.world.level.block.IronBarsBlock.EAST, true).setValue(net.minecraft.world.level.block.IronBarsBlock.WEST, true));
        // chimney with a fire still going
        for (int y = 1; y <= WALL + 6; y++) set(level, base.offset(W - 2, y, D - 2), Blocks.BRICKS.defaultBlockState());
        set(level, base.offset(W - 2, WALL + 7, D - 2), Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.SIGNAL_FIRE, false));
        set(level, base.offset(W - 3, 1, D - 2), Blocks.FURNACE.defaultBlockState().setValue(net.minecraft.world.level.block.FurnaceBlock.FACING, Direction.WEST));

        // ---- inside
        for (int x = 1; x < W - 1; x++) {
            if (x == W - 2) continue;
            set(level, base.offset(x, 1, D - 2), Blocks.BOOKSHELF.defaultBlockState());
            set(level, base.offset(x, 2, D - 2), rnd.nextInt(3) == 0 ? Blocks.CHISELED_BOOKSHELF.defaultBlockState() : Blocks.BOOKSHELF.defaultBlockState());
        }
        // the desk: slabs on the west wall, the inkwell on it, a quill-and-paper corner
        for (int z = 3; z <= 5; z++) set(level, base.offset(1, 1, z), Blocks.SPRUCE_SLAB.defaultBlockState().setValue(net.minecraft.world.level.block.SlabBlock.TYPE, net.minecraft.world.level.block.state.properties.SlabType.TOP));
        set(level, base.offset(1, 2, 4), ModBlocks.INKWELL.get().defaultBlockState());
        set(level, base.offset(2, 1, 4), Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST));
        set(level, base.offset(1, 2, 5), Blocks.POTTED_ALLIUM.defaultBlockState());
        // the chest
        set(level, base.offset(W - 2, 1, 2), Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.WEST));
        net.minecraft.world.RandomizableContainer.setBlockEntityLootTable(level, rnd, base.offset(W - 2, 1, 2), LOOT);
        set(level, base.offset(W - 2, 1, 4), ModBlocks.WASHING_BARREL.get().defaultBlockState());
        set(level, base.offset(W - 2, 1, 5), Blocks.BREWING_STAND.defaultBlockState());
        set(level, base.offset(W - 2, 1, 6), Blocks.CAULDRON.defaultBlockState());
        set(level, base.offset(2, 1, D - 3), Blocks.LECTERN.defaultBlockState().setValue(net.minecraft.world.level.block.LecternBlock.FACING, Direction.SOUTH));
        // rugs and light
        for (int x = 3; x <= 5; x++) for (int z = 3; z <= 6; z++) set(level, base.offset(x, 1, z), Blocks.PURPLE_CARPET.defaultBlockState());
        set(level, base.offset(W / 2, WALL, D / 2), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        set(level, base.offset(W / 2, WALL + 1, D / 2), frame.setValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS, Direction.Axis.Z));
        if (rnd.nextInt(3) == 0) set(level, base.offset(W / 2, 1, D - 3), ModBlocks.ARCANE_LENS.get().defaultBlockState());

        // ---- the face of the house: shutters by the windows, flower boxes under them, a porch roof, lanterns, ivy
        BlockState shutter = Blocks.DARK_OAK_TRAPDOOR.defaultBlockState().setValue(net.minecraft.world.level.block.TrapDoorBlock.OPEN, true);
        for (int[] w : new int[][]{{0, 2}, {0, D - 4}, {W - 1, 2}, {W - 1, D - 4}}) {
            int x = w[0], z = w[1];
            Direction out = x == 0 ? Direction.WEST : Direction.EAST;
            BlockPos box = base.offset(x, 1, z).relative(out);
            set(level, box, Blocks.SPRUCE_TRAPDOOR.defaultBlockState().setValue(net.minecraft.world.level.block.TrapDoorBlock.HALF, Half.TOP)
                    .setValue(net.minecraft.world.level.block.TrapDoorBlock.FACING, out));
            for (int k = 0; k < 2; k++) {
                BlockPos flower = base.offset(x, 2, z + k).relative(out);
                set(level, flower, Blocks.POTTED_ALLIUM.defaultBlockState());
            }
            set(level, base.offset(x, 2, z - 1).relative(out), shutter.setValue(net.minecraft.world.level.block.TrapDoorBlock.FACING, Direction.SOUTH).setValue(net.minecraft.world.level.block.TrapDoorBlock.HALF, Half.BOTTOM));
            set(level, base.offset(x, 3, z - 1).relative(out), shutter.setValue(net.minecraft.world.level.block.TrapDoorBlock.FACING, Direction.SOUTH).setValue(net.minecraft.world.level.block.TrapDoorBlock.HALF, Half.TOP));
            set(level, base.offset(x, 2, z + 2).relative(out), shutter.setValue(net.minecraft.world.level.block.TrapDoorBlock.FACING, Direction.NORTH).setValue(net.minecraft.world.level.block.TrapDoorBlock.HALF, Half.BOTTOM));
            set(level, base.offset(x, 3, z + 2).relative(out), shutter.setValue(net.minecraft.world.level.block.TrapDoorBlock.FACING, Direction.NORTH).setValue(net.minecraft.world.level.block.TrapDoorBlock.HALF, Half.TOP));
        }
        // a little porch roof over the door and two lanterns either side of it
        for (int dx = -1; dx <= 1; dx++) set(level, base.offset(W / 2 + dx, 4, -1), Blocks.DARK_OAK_SLAB.defaultBlockState());
        set(level, base.offset(W / 2 - 1, 3, -1), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        set(level, base.offset(W / 2 + 1, 3, -1), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        // ivy creeping up the corners
        for (int[] cxz : new int[][]{{-1, 0, 4}, {W, D - 1, 8}}) {
            Direction face = cxz[0] < 0 ? Direction.EAST : Direction.WEST;
            for (int y = 1; y <= 2 + rnd.nextInt(3); y++) {
                BlockPos v = base.offset(cxz[0], y, cxz[1]);
                set(level, v, Blocks.VINE.defaultBlockState().setValue(net.minecraft.world.level.block.VineBlock.getPropertyForFace(face), true));
            }
        }

        // ---- outside: the path, a little garden, a warding tower
        for (int z = -1; z >= -4; z--) {
            BlockPos p = new BlockPos(x0 + W / 2, level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x0 + W / 2, z0 + z) - 1, z0 + z);
            if (level.getBlockState(p).is(Blocks.GRASS_BLOCK) || level.getBlockState(p).is(Blocks.DIRT)) set(level, p, Blocks.DIRT_PATH.defaultBlockState());
        }
        BlockPos tower = base.offset(W / 2 + 2, 0, -2);
        set(level, tower, ModBlocks.WARDING_TOWER.get().defaultBlockState().setValue(com.witchcraft.block.sealed.SealedBlock.LIT, true));
        if (level.getBlockEntity(tower) instanceof com.witchcraft.block.sealed.SealedBlockEntity be) be.addCharge(24000 * 4, 24000 * 8);
        Block[] flowers = {Blocks.ALLIUM, Blocks.LILY_OF_THE_VALLEY, Blocks.CORNFLOWER, Blocks.OXEYE_DAISY};
        for (int i = 0; i < 6; i++) {
            BlockPos p = base.offset(-1, 0, 1 + rnd.nextInt(D - 2));
            if (level.getBlockState(p.below()).is(Blocks.GRASS_BLOCK)) set(level, p, flowers[rnd.nextInt(flowers.length)].defaultBlockState());
        }

        // ---- the glyphs: a lantern ready to be lit on the wall by the desk, a lifting glyph on the floor
        var dim = level.getLevel().dimension();
        WorldGlyphs.draw(dim, base.offset(0, 3, 4), Direction.EAST, "lantern", 0.45, true, rnd);
        WorldGlyphs.draw(dim, base.offset(4, 0, 7), Direction.UP, rnd.nextBoolean() ? "wind_underfoot" : "birds_of_light", 0.9, true, rnd);

        // every other atelier is lived in: its witch teaches whoever knocks
        if (rnd.nextInt(2) == 0) {
            var mentor = com.witchcraft.registry.ModEntities.MENTOR.get().create(level.getLevel());
            if (mentor != null) {
                BlockPos home = base.offset(W / 2, 1, D / 2);
                mentor.moveTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, rnd.nextFloat() * 360, 0);
                mentor.restrictTo(home, 7);
                mentor.finalizeSpawn(level, level.getCurrentDifficultyAt(home), net.minecraft.world.entity.MobSpawnType.STRUCTURE, null);
                level.addFreshEntityWithPassengers(mentor);
            }
        }
        return true;
    }

    private static void set(WorldGenLevel level, BlockPos p, BlockState s) {
        level.setBlock(p, s, 2);
    }
}
