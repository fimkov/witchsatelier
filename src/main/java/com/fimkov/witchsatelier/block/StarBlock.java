package com.fimkov.witchsatelier.block;

import com.fimkov.witchsatelier.registry.ModBlocks;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.TheEndPortalBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class StarBlock extends BaseEntityBlock {
    public static final MapCodec<StarBlock> CODEC = simpleCodec(StarBlock::new);

    public StarBlock(Properties props) {
        super(props);
    }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new Entity(pos, state);
    }

    public static class Entity extends TheEndPortalBlockEntity {
        public Entity(BlockPos pos, BlockState state) {
            super(ModBlocks.STAR_BLOCK_BE.get(), pos, state);
        }

        @Override
        public boolean shouldRenderFace(Direction face) {
            return level == null || Block.shouldRenderFace(getBlockState(), level, worldPosition, face, worldPosition.relative(face));
        }
    }
}
