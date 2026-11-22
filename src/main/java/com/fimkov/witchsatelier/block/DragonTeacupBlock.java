package com.fimkov.witchsatelier.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Palm Dragon Teacup (Qifrey's): a glyph on the cup's base, a hidden rune on the saucer. Set the cup down (use it)
 * and the two meet: the tea rises out of the cup as a little dragon that winds about and settles again.
 */
public class DragonTeacupBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<DragonTeacupBlock> CODEC = simpleCodec(DragonTeacupBlock::new);
    public static final BooleanProperty DANCING = BooleanProperty.create("dancing");
    private static final VoxelShape SHAPE = box(3, 0, 3, 13, 6, 13);

    public DragonTeacupBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(DANCING, false).setValue(FACING, net.minecraft.core.Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(DANCING, FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (state.getValue(DANCING)) return InteractionResult.PASS;
        if (!level.isClientSide) {
            level.setBlock(pos, state.setValue(DANCING, true), 3);
            level.scheduleTick(pos, this, 120);
            level.playSound(null, pos, SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 0.7f, 1.3f);
            level.playSound(null, pos, com.fimkov.witchsatelier.registry.ModSounds.BIRD_CALL.get(), SoundSource.BLOCKS, 0.5f, 0.6f);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource rnd) {
        if (state.getValue(DANCING)) level.setBlock(pos, state.setValue(DANCING, false), 3);
    }

    /** The dragon: a string of drops winding up out of the cup, a head of brighter spray leading it. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource rnd) {
        if (!state.getValue(DANCING)) return;
        double t = level.getGameTime() * 0.25;
        for (int i = 0; i < 14; i++) {
            double s = t - i * 0.22;
            double r = 0.35 + 0.1 * Math.sin(s * 0.7);
            double x = pos.getX() + 0.5 + Math.cos(s) * r, y = pos.getY() + 0.45 + 0.45 + 0.3 * Math.sin(s * 0.5) + i * 0.02, z = pos.getZ() + 0.5 + Math.sin(s) * r;
            level.addParticle(i == 0 ? com.fimkov.witchsatelier.spell.ElementLook.spray() : com.fimkov.witchsatelier.spell.ElementLook.droplet(), x, y, z, 0, 0.0, 0);
        }
        if (rnd.nextInt(6) == 0) level.addParticle(ParticleTypes.SPLASH, pos.getX() + 0.5, pos.getY() + 0.4, pos.getZ() + 0.5, 0, 0, 0);
    }
}
