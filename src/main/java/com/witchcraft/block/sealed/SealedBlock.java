package com.witchcraft.block.sealed;

import com.witchcraft.glyph.ink.InkSupply;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
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

/**
 * A block carrying a seal that has to be kept inked. Use a pen with ink on it to restore the seal: each
 * restoring costs {@link #inkCost()} units of ink and gives {@link #chargePerInking()} ticks, up to
 * {@link #maxCharge()}. While the seal holds the block is LIT.
 */
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
        if (!(stack.getItem() instanceof com.witchcraft.item.InkPenItem)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (level.isClientSide) return ItemInteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof SealedBlockEntity be) || !(player instanceof ServerPlayer sp)) return ItemInteractionResult.FAIL;
        InkSupply ink = InkSupply.of(sp);
        if (be.charge() >= maxCharge() - chargePerInking() / 4) {
            return ItemInteractionResult.CONSUME; // nothing to restore
        }
        if (ink == null || ink.available() < inkCost()) {
            sp.displayClientMessage(Component.translatable("message.witchcraft.pen_dry"), true);
            return ItemInteractionResult.FAIL;
        }
        ink.consume(inkCost());
        double quality = Math.max(0.4, ink.ink.duration * ink.ink.potency);
        be.addCharge((int) (chargePerInking() * Math.min(2, quality)), maxCharge());
        level.setBlock(pos, state.setValue(LIT, true), 3);
        level.playSound(null, pos, com.witchcraft.registry.ModSounds.SEAL_RESTORE.get(), SoundSource.BLOCKS, 1f, 1f);
        level.playSound(null, pos, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 0.6f, 1.5f);
        ((ServerLevel) level).sendParticles(com.witchcraft.spell.ElementLook.rune(null, com.witchcraft.glyph.model.SignType.REPETITION),
                pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, 12, 0.3, 0.3, 0.3, 0.02);
        return ItemInteractionResult.SUCCESS;
    }

    /** Called by subclasses' tickers: drains the seal and keeps LIT in step. @return true while it holds */
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
