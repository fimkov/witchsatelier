package com.fimkov.witchsatelier.block.sealed;

import com.fimkov.witchsatelier.glyph.ink.InkSupply;
import com.fimkov.witchsatelier.glyph.model.SignType;
import com.fimkov.witchsatelier.item.InkPenItem;
import com.fimkov.witchsatelier.registry.ModSounds;
import com.fimkov.witchsatelier.spell.ElementLook;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

public abstract class SealedBlock extends BaseEntityBlock {
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    protected SealedBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(LIT, false));
    }

    protected abstract int inkCost();
    protected abstract int chargePerInking();
    protected abstract int maxCharge();

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(LIT);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(stack.getItem() instanceof InkPenItem)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (level.isClientSide) return ItemInteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof SealedBlockEntity be) || !(player instanceof ServerPlayer sp)) return ItemInteractionResult.FAIL;
        InkSupply ink = InkSupply.of(sp);
        if (be.charge() >= maxCharge() - chargePerInking() / 4) {
            return ItemInteractionResult.CONSUME;
        }
        if (ink == null || ink.available() < inkCost()) {
            sp.displayClientMessage(Component.translatable("message.witchsatelier.pen_dry"), true);
            return ItemInteractionResult.FAIL;
        }
        ink.consume(inkCost());
        double quality = Math.max(0.4, ink.ink.duration * ink.ink.potency);
        be.addCharge((int) (chargePerInking() * Math.min(2, quality)), maxCharge());
        level.setBlock(pos, state.setValue(LIT, true), 3);
        level.playSound(null, pos, ModSounds.SEAL_RESTORE.get(), SoundSource.BLOCKS, 1f, 1f);
        level.playSound(null, pos, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 0.6f, 1.5f);
        ((ServerLevel) level).sendParticles(ElementLook.rune(null, SignType.REPETITION),
                pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, 12, 0.3, 0.3, 0.3, 0.02);
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @org.jetbrains.annotations.Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof SealedBlockEntity be && be.charge() == 0) {
            be.addCharge(chargePerInking(), maxCharge());
            level.setBlock(pos, state.setValue(LIT, true), 3);
        }
    }

    protected static boolean hold(Level level, BlockPos pos, BlockState state, SealedBlockEntity be) {
        boolean on = be.drain();
        if (on != state.getValue(LIT)) {
            level.setBlock(pos, state.setValue(LIT, on), 3);
            if (!on) {
                level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.BLOCKS, 0.6f, 0.6f);
                if (level instanceof ServerLevel sl) sl.sendParticles(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, 8, 0.2, 0.2, 0.2, 0.01);
            }
        }
        return on;
    }
}
