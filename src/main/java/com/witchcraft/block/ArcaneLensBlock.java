package com.witchcraft.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Arcane Lense: a great lens from before the Pact. A glyph drawn on it, or set right behind or before it, is
 * amplified: its spell comes out far stronger than its ink and size would give.
 */
public class ArcaneLensBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<ArcaneLensBlock> CODEC = simpleCodec(ArcaneLensBlock::new);
    public static final double AMPLIFY = 1.8;
    private static final VoxelShape NS = box(0, 0, 6, 16, 16, 10), EW = box(6, 0, 0, 10, 16, 16);

    public ArcaneLensBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return state.getValue(FACING).getAxis() == Direction.Axis.Z ? NS : EW;
    }

    /** True if a lens sits within reach of a glyph centred at {@code at}. */
    public static boolean near(Level level, Vec3 at) {
        BlockPos c = BlockPos.containing(at);
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-1, -1, -1), c.offset(1, 1, 1))) {
            if (level.getBlockState(p).getBlock() instanceof ArcaneLensBlock && Vec3.atCenterOf(p).distanceTo(at) < 1.6) return true;
        }
        return false;
    }
}
