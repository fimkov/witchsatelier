package com.fimkov.witchsatelier.worldgen;

import com.mojang.serialization.Codec;
import com.fimkov.witchsatelier.WitchsAtelier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * A ruined tower of tomes: a round tower of old stone, three floors of shelves climbing its walls, reading
 * desks, ladders between the floors, the top broken open to the sky. Copies of magic books end up in places
 * like this; so do grimoires, and glyphs left on the floor for whoever comes next.
 */
public class LibraryRuinFeature extends Feature<NoneFeatureConfiguration> {
    public static final ResourceKey<LootTable> LOOT = ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.fromNamespaceAndPath(WitchsAtelier.MODID, "chests/library"));
    private static final int R = 5, FLOOR = 5, FLOORS = 3;

    public LibraryRuinFeature(Codec<NoneFeatureConfiguration> codec) {
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
        BlockPos o = ctx.origin();
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        for (int dx = -R; dx <= R; dx += R) for (int dz = -R; dz <= R; dz += R) {
            int h = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, o.getX() + dx, o.getZ() + dz);
            min = Math.min(min, h);
            max = Math.max(max, h);
        }
        if (checkSite && (max - min > 4 || !level.getFluidState(new BlockPos(o.getX(), min - 1, o.getZ())).isEmpty())) return false;
        BlockPos c = new BlockPos(o.getX(), min, o.getZ());
        BlockState[] walls = {Blocks.STONE_BRICKS.defaultBlockState(), Blocks.MOSSY_STONE_BRICKS.defaultBlockState(), Blocks.CRACKED_STONE_BRICKS.defaultBlockState()};
        int top = FLOOR * FLOORS + 2;
        for (int dx = -R - 1; dx <= R + 1; dx++) for (int dz = -R - 1; dz <= R + 1; dz++) {
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d > R + 0.5) continue;
            // foundation
            for (int y = -1; y >= -5; y--) {
                BlockPos p = c.offset(dx, y, dz);
                if (!level.getBlockState(p).canBeReplaced()) break;
                set(level, p, Blocks.COBBLESTONE.defaultBlockState());
            }
            boolean wall = d > R - 0.5;
            // the ruin: the wall crumbles away towards the top, more on one side
            int wallTop = top - (int) Math.max(0, (dx + R) * 0.6) - rnd.nextInt(3);
            for (int y = 0; y <= top; y++) {
                BlockPos p = c.offset(dx, y, dz);
                if (wall) {
                    if (y <= wallTop) set(level, p, walls[rnd.nextInt(walls.length)]);
                    else set(level, p, Blocks.AIR.defaultBlockState());
                } else {
                    boolean floor = y % FLOOR == 0 && y < FLOOR * FLOORS;
                    boolean hole = floor && y > 0 && dx == R - 2 && dz == 0;    // where the ladder comes up
                    set(level, p, floor && !hole ? (y == 0 ? Blocks.POLISHED_ANDESITE.defaultBlockState() : Blocks.SPRUCE_PLANKS.defaultBlockState())
                            : Blocks.AIR.defaultBlockState());
                }
            }
        }
        // windows
        for (int f = 0; f < FLOORS; f++) for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos w = c.relative(dir, R).above(f * FLOOR + 2);
            if (!level.getBlockState(w).isAir()) {
                set(level, w, Blocks.AIR.defaultBlockState());
                set(level, w.above(), Blocks.AIR.defaultBlockState());
            }
        }
        // the door
        set(level, c.relative(Direction.SOUTH, R).above(1), Blocks.AIR.defaultBlockState());
        set(level, c.relative(Direction.SOUTH, R).above(2), Blocks.AIR.defaultBlockState());
        // shelves round the inside of every floor, a ladder up the east side, desks and lanterns
        for (int f = 0; f < FLOORS; f++) {
            int y0 = f * FLOOR + 1;
            for (int dx = -R; dx <= R; dx++) for (int dz = -R; dz <= R; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d <= R - 1.5 || d > R - 0.5) continue;
                if (dz == R - 1 && Math.abs(dx) <= 1 && f == 0) continue;         // keep the doorway clear
                if (dx >= R - 2 && Math.abs(dz) <= 1) continue;                    // and the ladder
                for (int y = 0; y < 3; y++) {
                    if (rnd.nextInt(7) == 0) continue;                             // gaps where books were taken
                    set(level, c.offset(dx, y0 + y, dz), rnd.nextInt(4) == 0 ? Blocks.CHISELED_BOOKSHELF.defaultBlockState() : Blocks.BOOKSHELF.defaultBlockState());
                }
            }
            for (int y = 0; y < FLOOR; y++) {
                if (f == FLOORS - 1 && y > 1) break;
                set(level, c.offset(R - 1, y0 + y, 0), Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.WEST));
            }
            set(level, c.offset(0, y0, -1), Blocks.LECTERN.defaultBlockState().setValue(net.minecraft.world.level.block.LecternBlock.FACING, Direction.SOUTH));
            set(level, c.offset(0, y0 + 3, 0), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
            if (f < FLOORS - 1) set(level, c.offset(0, y0 + 4, 0), Blocks.SPRUCE_PLANKS.defaultBlockState());
            if (rnd.nextInt(3) > 0 || f == 1) {
                BlockPos chest = c.offset(-R + 2, y0, 0);
                set(level, chest, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.EAST));
                net.minecraft.world.RandomizableContainer.setBlockEntityLootTable(level, rnd, chest, LOOT);
            }
            if (f == 0) set(level, c.offset(-1, y0, 1), com.fimkov.witchsatelier.registry.ModBlocks.INKWELL.get().defaultBlockState());
        }
        // a glyph left ready on the ground floor
        String[] glyphs = {"lantern", "birds_of_light", "gathering_breeze", "crystal_ward"};
        WorldGlyphs.draw(level.getLevel().dimension(), c.offset(0, 0, 2), Direction.UP, glyphs[rnd.nextInt(glyphs.length)], 1.1, true, rnd);
        return true;
    }

    private static void set(WorldGenLevel level, BlockPos p, BlockState s) {
        level.setBlock(p, s, 2);
    }
}
