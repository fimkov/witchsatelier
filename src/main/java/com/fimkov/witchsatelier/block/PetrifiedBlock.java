package com.fimkov.witchsatelier.block;

import com.fimkov.witchsatelier.registry.ModBlocks;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Whatever petrification swept over, turned to glowing crystal, which still remembers what it was. Integration
 * drawn nearby gives it back; broken, it is only dust.
 */
public class PetrifiedBlock extends BaseEntityBlock {
    public static final MapCodec<PetrifiedBlock> CODEC = simpleCodec(PetrifiedBlock::new);
    /** setBlock flags: tell the clients, but leave the neighbours be (a door half or a torch is not knocked off). */
    private static final int QUIET = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    public PetrifiedBlock(Properties props) {
        super(props);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    /** Crystal against crystal shows no face between them: the mass reads as one. */
    @Override
    protected boolean skipRendering(BlockState state, BlockState adjacent, net.minecraft.core.Direction side) {
        return adjacent.is(this) || super.skipRendering(state, adjacent, side);
    }

    @Override
    protected float getShadeBrightness(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos) {
        return 1.0f;
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new Entity(pos, state);
    }

    /** Could petrification take {@code pos}? The air too: the crystal fills all of it. Not liquid, nothing that holds things, nothing unbreakable. */
    public static boolean takes(ServerLevel level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        if (!s.getFluidState().isEmpty() || s.hasBlockEntity() || s.getDestroySpeed(level, pos) < 0) return false;
        return !s.is(ModBlocks.STAR_BLOCK.get()) && !s.is(ModBlocks.PETRIFIED.get()) && !s.is(ModBlocks.PETRIFIED_CLUSTER.get());
    }

    /** Turns the block at {@code pos} to petrified stone. @return true if it was taken */
    public static boolean petrify(ServerLevel level, BlockPos pos) {
        if (!takes(level, pos)) return false;
        BlockState was = level.getBlockState(pos);
        level.setBlock(pos, ModBlocks.PETRIFIED.get().defaultBlockState(), QUIET);
        if (level.getBlockEntity(pos) instanceof Entity be) {
            be.was = was;
            be.setChanged();
        }
        return true;
    }

    /** A long crystal shard on the outside of the mass, pointing {@code out}. */
    public static void spike(ServerLevel level, BlockPos pos, net.minecraft.core.Direction out) {
        if (!level.getBlockState(pos).isAir()) return;
        level.setBlock(pos, ModBlocks.PETRIFIED_CLUSTER.get().defaultBlockState().setValue(PetrifiedClusterBlock.FACING, out), QUIET);
    }

    /** Integration: petrified blocks within range become what they were. @return how many */
    public static int restore(ServerLevel level, Vec3 center, double range) {
        int n = 0, r = (int) Math.ceil(range);
        BlockPos c = BlockPos.containing(center);
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-r, -r, -r), c.offset(r, r, r))) {
            if (p.distToCenterSqr(center) > range * range) continue;
            if (level.getBlockState(p).is(ModBlocks.PETRIFIED_CLUSTER.get())) {
                level.setBlock(p, Blocks.AIR.defaultBlockState(), QUIET);
                continue;
            }
            if (!(level.getBlockEntity(p) instanceof Entity be)) continue;
            BlockState was = be.was == null ? Blocks.STONE.defaultBlockState() : be.was;
            BlockPos at = p.immutable();
            level.setBlock(at, was, QUIET);
            if (n % 6 == 0) level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ModBlocks.PETRIFIED.get().defaultBlockState()),
                    at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 4, 0.3, 0.3, 0.3, 0.05);
            n++;
        }
        if (n > 0) level.playSound(null, c, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.5f, 0.7f);
        return n;
    }

    public static class Entity extends BlockEntity {
        BlockState was;

        public Entity(BlockPos pos, BlockState state) {
            super(ModBlocks.PETRIFIED_BE.get(), pos, state);
        }

        public BlockState was() {
            return was;
        }

        @Override
        protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.saveAdditional(tag, registries);
            if (was != null) tag.put("was", NbtUtils.writeBlockState(was));
        }

        @Override
        protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.loadAdditional(tag, registries);
            was = tag.contains("was") ? NbtUtils.readBlockState(registries.lookupOrThrow(Registries.BLOCK), tag.getCompound("was")) : null;
        }
    }
}
