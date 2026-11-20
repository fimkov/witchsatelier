package com.witchcraft.block;

import com.mojang.serialization.MapCodec;
import com.witchcraft.entity.SealchairEntity;
import com.witchcraft.registry.ModBlocks;
import com.witchcraft.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A Sealchair at rest: a chair standing on four carved hoofed legs, the glyph under its seat asleep. It is placed
 * and broken like any wooden thing. Sit in it and the glyph wakes: the chair gets up and walks where you look
 * (see {@link SealchairEntity}); get up, and it settles down again where it stopped.
 */
public class SealchairBlock extends BaseEntityBlock {
    public static final MapCodec<SealchairBlock> CODEC = simpleCodec(SealchairBlock::new);
    public static final net.minecraft.world.level.block.state.properties.DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    // seat and legs, and the carved back on the side away from where it faces
    private static final VoxelShape SEAT = Shapes.or(Block.box(1.5, 0, 1.5, 14.5, 13, 14.5));
    private static final VoxelShape[] SHAPES = new VoxelShape[4];

    static {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            // the back stands behind the seat: on the side opposite the way the chair faces
            Direction b = d.getOpposite();
            double x0 = 2, z0 = 2, x1 = 14, z1 = 14;
            switch (b) {
                case NORTH -> { z0 = 1.5; z1 = 4.5; }
                case SOUTH -> { z0 = 11.5; z1 = 14.5; }
                case WEST -> { x0 = 1.5; x1 = 4.5; }
                default -> { x0 = 11.5; x1 = 14.5; }
            }
            SHAPES[d.get2DDataValue()] = Shapes.or(SEAT, Block.box(x0, 13, z0, x1, 24, z1));
        }
    }

    public SealchairBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        // it faces whoever set it down, ready to be sat in
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPES[state.getValue(FACING).get2DDataValue()];
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new Entity(pos, state);
    }

    /** Sitting down wakes it: the chair gets up off its spot and carries you. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player.isSecondaryUseActive() || player.isPassenger()) return InteractionResult.PASS;
        if (level instanceof ServerLevel sl) {
            SealchairEntity chair = ModEntities.SEALCHAIR.get().create(sl);
            if (chair == null) return InteractionResult.FAIL;
            float yaw = state.getValue(FACING).toYRot();
            chair.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, 0);
            chair.setYBodyRot(yaw);
            chair.setYHeadRot(yaw);
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            sl.addFreshEntity(chair);
            player.startRiding(chair);
            sl.playSound(null, pos, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 0.8f, 0.8f);
            sl.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.6f, 1.5f);
            sl.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 0.2, pos.getZ() + 0.5, 8, 0.3, 0.05, 0.3, 0.01);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Settles a walking chair back into a block where it stopped, facing the way it walked; false if there is no room. */
    public static boolean settle(Level level, BlockPos near, float yaw) {
        BlockState state = ModBlocks.SEALCHAIR.get().defaultBlockState().setValue(FACING, Direction.fromYRot(yaw));
        BlockPos[] tries = {near, near.above(), near.below(), near.north(), near.south(), near.east(), near.west()};
        for (BlockPos p : tries) {
            BlockState here = level.getBlockState(p);
            if (!here.canBeReplaced() || !here.getFluidState().isEmpty()) continue;
            if (!level.getBlockState(p.below()).isFaceSturdy(level, p.below(), Direction.UP)) continue;
            level.setBlock(p, state, Block.UPDATE_ALL);
            level.playSound(null, p, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 0.9f, 0.9f);
            return true;
        }
        return false;
    }

    /** Only there so the chair can be drawn with its carved model. */
    public static class Entity extends BlockEntity {
        public Entity(BlockPos pos, BlockState state) {
            super(ModBlocks.SEALCHAIR_BE.get(), pos, state);
        }
    }
}
