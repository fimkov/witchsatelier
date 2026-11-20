package com.witchcraft.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Vector3f;

/**
 * Glowstone Path (Olruggio's): a paving stone in two pieces, the top one hovering on float keystones. Step on it
 * and your weight presses the halves together, the ring closes and the stone glows; step off and it fades.
 */
public class GlowstonePathBlock extends Block {
    public static final MapCodec<GlowstonePathBlock> CODEC = simpleCodec(GlowstonePathBlock::new);
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    private static final VoxelShape UP = box(0, 0, 0, 16, 14, 16), DOWN = box(0, 0, 0, 16, 13, 16);

    public GlowstonePathBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(LIT, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(LIT);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return state.getValue(LIT) ? DOWN : UP;
    }

    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        if (!level.isClientSide && entity instanceof LivingEntity && !state.getValue(LIT)) {
            level.setBlock(pos, state.setValue(LIT, true), 3);
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_STEP, SoundSource.BLOCKS, 0.6f, 1.6f);
            level.scheduleTick(pos, this, 10);
        }
        super.stepOn(level, pos, state, entity);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource rnd) {
        if (!state.getValue(LIT)) return;
        boolean someone = !level.getEntitiesOfClass(LivingEntity.class, new AABB(pos.above()).inflate(0, 0.3, 0).move(0, -0.3, 0)).isEmpty();
        if (someone) level.scheduleTick(pos, this, 10);
        else level.setBlock(pos, state.setValue(LIT, false), 3);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource rnd) {
        if (!state.getValue(LIT) || rnd.nextInt(2) != 0) return;
        level.addParticle(new DustParticleOptions(new Vector3f(1f, 0.92f, 0.6f), 0.6f), pos.getX() + rnd.nextDouble(), pos.getY() + 0.9, pos.getZ() + rnd.nextDouble(), 0, 0.02, 0);
    }
}
