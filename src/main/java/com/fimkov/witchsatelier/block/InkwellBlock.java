package com.fimkov.witchsatelier.block;

import com.mojang.serialization.MapCodec;
import com.fimkov.witchsatelier.glyph.ink.InkType;
import com.fimkov.witchsatelier.item.InkBottleItem;
import com.fimkov.witchsatelier.item.InkPenItem;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class InkwellBlock extends BaseEntityBlock {
    public static final MapCodec<InkwellBlock> CODEC = simpleCodec(InkwellBlock::new);
    public static final IntegerProperty FILL = IntegerProperty.create("fill", 0, 3);
    public static final EnumProperty<InkType> INK = EnumProperty.create("ink", InkType.class);
    private static final VoxelShape SHAPE = Block.box(5, 0, 5, 11, 6, 11);

    public InkwellBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(FILL, 0).setValue(INK, InkType.CONJURING));
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @org.jetbrains.annotations.Nullable net.minecraft.world.entity.LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof InkwellBlockEntity well) well.refreshState();
    }

    public static int fillFor(int amount) {
        if (amount <= 0) return 0;
        return amount < InkwellBlockEntity.CAPACITY / 3 ? 1 : amount < InkwellBlockEntity.CAPACITY * 2 / 3 ? 2 : 3;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FILL, INK);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new InkwellBlockEntity(pos, state);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof InkwellBlockEntity well)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (stack.getItem() instanceof InkBottleItem bottle) {
            if (level.isClientSide) return ItemInteractionResult.SUCCESS;
            if (!well.pour(bottle.ink, InkBottleItem.UNITS)) return ItemInteractionResult.CONSUME;
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
                ItemStack glass = new ItemStack(Items.GLASS_BOTTLE);
                if (!player.getInventory().add(glass)) player.drop(glass, false);
            }
            level.playSound(null, pos, SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 1f, 0.8f);
            return ItemInteractionResult.SUCCESS;
        }
        if (stack.getItem() instanceof InkPenItem pen) {
            if (pen.selfInked()) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            if (level.isClientSide) return ItemInteractionResult.SUCCESS;
            if (well.amount() <= 0) return ItemInteractionResult.CONSUME;
            var nib = InkPenItem.loaded(stack);
            int on = nib != null && nib.type() == well.ink() ? nib.amount() : 0;
            int got = well.take(InkPenItem.NIB_CAPACITY - on);
            InkPenItem.setLoaded(stack, well.ink(), on + got);
            level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 0.6f, 1.6f);
            return ItemInteractionResult.SUCCESS;
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }
}
