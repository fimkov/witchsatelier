package com.witchcraft.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Void Basin (after the Toilet of the Void): a basin whose depths open onto a starry sky. Whatever falls in is
 * gone, without a sound but a faint chime. Its inventor vanished the night they finished it.
 */
public class VoidBasinBlock extends Block {
    public static final MapCodec<VoidBasinBlock> CODEC = simpleCodec(VoidBasinBlock::new);
    private static final VoxelShape SHAPE = Shapes.join(Shapes.block(), box(2, 4, 2, 14, 16, 14), BooleanOp.ONLY_FIRST);

    public VoidBasinBlock(Properties props) {
        super(props);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (level.isClientSide || !(entity instanceof ItemEntity item) || item.getY() > pos.getY() + 0.95) return;
        ((ServerLevel) level).sendParticles(ParticleTypes.REVERSE_PORTAL, item.getX(), item.getY() + 0.1, item.getZ(), 10, 0.15, 0.1, 0.15, 0.02);
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.4f, 0.5f);
        item.discard();
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource rnd) {
        if (rnd.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.REVERSE_PORTAL, pos.getX() + 0.2 + rnd.nextDouble() * 0.6, pos.getY() + 0.3, pos.getZ() + 0.2 + rnd.nextDouble() * 0.6, 0, 0.02, 0);
        }
    }
}
