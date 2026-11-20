package com.witchcraft.worldgen;

import com.mojang.serialization.Codec;
import com.witchcraft.WitchCraft;
import com.witchcraft.registry.ModBlocks;
import com.witchcraft.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * A Brimhat hideout: a broken ring of black stone in the gloom, soul lanterns, a chest of forbidden things,
 * the cage of a spawner where they gather, and a great forbidden glyph on the floor, ring left open. Around it
 * stand statues of those who came too close. An Integration glyph would give them back.
 */
public class HideoutFeature extends Feature<NoneFeatureConfiguration> {
    public static final ResourceKey<LootTable> LOOT = ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.fromNamespaceAndPath(WitchCraft.MODID, "chests/hideout"));
    private static final String[] VICTIMS = {"minecraft:villager", "minecraft:sheep", "minecraft:wandering_trader", "minecraft:fox", "minecraft:pillager", "minecraft:villager"};

    public HideoutFeature(Codec<NoneFeatureConfiguration> codec) {
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
        int y = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, o.getX(), o.getZ());
        if (checkSite && !level.getFluidState(new BlockPos(o.getX(), y - 1, o.getZ())).isEmpty()) return false;
        BlockPos c = new BlockPos(o.getX(), y, o.getZ());
        int R = 6;
        BlockState[] floor = {Blocks.POLISHED_BLACKSTONE.defaultBlockState(), Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(),
                Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), Blocks.BLACKSTONE.defaultBlockState()};
        for (int dx = -R - 1; dx <= R + 1; dx++) for (int dz = -R - 1; dz <= R + 1; dz++) {
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d > R + 0.6) continue;
            BlockPos p = c.offset(dx, -1, dz);
            for (int k = 0; k < 4; k++) if (level.getBlockState(p.below(k)).canBeReplaced()) level.setBlock(p.below(k), Blocks.BLACKSTONE.defaultBlockState(), 2);
            level.setBlock(p, d < R - 0.5 ? floor[rnd.nextInt(floor.length)] : Blocks.MOSSY_COBBLESTONE.defaultBlockState(), 2);
            for (int h = 0; h <= 4; h++) level.setBlock(c.offset(dx, h, dz), Blocks.AIR.defaultBlockState(), 2);
            // the broken ring wall
            if (d > R - 0.6 && rnd.nextInt(10) < 7) {
                int hgt = 1 + rnd.nextInt(3);
                for (int h = 0; h < hgt; h++) {
                    BlockState w = rnd.nextInt(4) == 0 ? Blocks.CRACKED_DEEPSLATE_BRICKS.defaultBlockState() : Blocks.DEEPSLATE_BRICKS.defaultBlockState();
                    level.setBlock(c.offset(dx, h, dz), w, 2);
                }
            }
        }
        // soul lanterns on posts at the four quarters
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos post = c.relative(dir, R - 2);
            level.setBlock(post, Blocks.POLISHED_BLACKSTONE_WALL.defaultBlockState(), 2);
            level.setBlock(post.above(), Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false), 2);
        }
        // the spawner, caged, and the chest
        BlockPos sp = c.offset(-3, 0, 3);
        level.setBlock(sp, Blocks.SPAWNER.defaultBlockState(), 2);
        if (level.getBlockEntity(sp) instanceof SpawnerBlockEntity be) be.setEntityId(ModEntities.BRIMHAT.get(), rnd);
        BlockPos chest = c.offset(3, 0, 3);
        level.setBlock(chest, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.WEST), 2);
        net.minecraft.world.RandomizableContainer.setBlockEntityLootTable(level, rnd, chest, LOOT);
        // one hideout in three is where their Elder keeps its court
        if (rnd.nextInt(3) == 0) {
            var elder = ModEntities.BRIMHAT_ELDER.get().create(level.getLevel());
            if (elder != null) {
                elder.moveTo(c.getX() + 0.5, c.getY(), c.getZ() - 2.5, rnd.nextFloat() * 360, 0);
                elder.setPersistenceRequired();
                elder.finalizeSpawn(level, level.getCurrentDifficultyAt(c), MobSpawnType.STRUCTURE, null);
                level.addFreshEntityWithPassengers(elder);
            }
        }
        // statues of those who came too close
        int statues = 2 + rnd.nextInt(3);
        for (int i = 0; i < statues; i++) {
            double a = rnd.nextDouble() * Math.PI * 2;
            BlockPos p = c.offset((int) Math.round(Math.cos(a) * (R - 1.5)), 0, (int) Math.round(Math.sin(a) * (R - 1.5)));
            if (!level.getBlockState(p).isAir()) continue;
            level.setBlock(p, ModBlocks.STATUE.get().defaultBlockState(), 2);
            if (level.getBlockEntity(p) instanceof com.witchcraft.block.StatueBlock.Entity be) {
                CompoundTag tag = new CompoundTag();
                tag.putString("id", VICTIMS[rnd.nextInt(VICTIMS.length)]);
                be.set(tag, (float) Math.toDegrees(Math.atan2(-(c.getX() - p.getX()), c.getZ() - p.getZ())));
            }
        }
        // the forbidden glyph, drawn large in blood-dark ink on the floor, its ring left open
        WorldGlyphs.draw(level.getLevel().dimension(), c.below(), Direction.UP, "petrification", 2.2, true, rnd);
        return true;
    }
}
