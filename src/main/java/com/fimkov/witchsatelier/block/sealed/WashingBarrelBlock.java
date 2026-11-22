package com.fimkov.witchsatelier.block.sealed;

import com.fimkov.witchsatelier.registry.ModBlocks;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class WashingBarrelBlock extends SealedBlock {
    public static final MapCodec<WashingBarrelBlock> CODEC = simpleCodec(WashingBarrelBlock::new);

    public WashingBarrelBlock(Properties props) {
        super(props);
    }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override protected int inkCost() { return 96; }
    @Override protected int chargePerInking() { return 24000; }
    @Override protected int maxCharge() { return 24000 * 4; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new Entity(pos, state);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        ItemInteractionResult inked = super.useItemOn(stack, state, level, pos, player, hand, hit);
        if (inked != ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION) return inked;
        if (!(level.getBlockEntity(pos) instanceof Entity be) || !be.held.isEmpty()) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!stack.isDamageableItem() || !stack.isDamaged()) {
            if (!level.isClientSide) player.displayClientMessage(Component.translatable("message.witchsatelier.barrel.not_worn"), true);
            return ItemInteractionResult.CONSUME;
        }
        if (!level.isClientSide) {
            be.held = stack.split(1);
            be.changed();
            if (be.charge() <= 0) player.displayClientMessage(Component.translatable("message.witchsatelier.barrel.faded"), true);
            level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 0.6f, 1.3f);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof Entity be)) return InteractionResult.PASS;
        if (be.held.isEmpty()) {
            if (!level.isClientSide) player.displayClientMessage(Component.translatable(be.charge() > 0
                    ? "message.witchsatelier.barrel.empty" : "message.witchsatelier.barrel.faded"), true);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!level.isClientSide) {
            ItemStack out = be.held;
            be.held = ItemStack.EMPTY;
            be.changed();
            if (!player.getInventory().add(out)) player.drop(out, false);
            level.playSound(null, pos, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 0.6f, 1.3f);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof Entity be && !be.held.isEmpty()) {
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, be.held);
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, ModBlocks.WASHING_BARREL_BE.get(), WashingBarrelBlock::serverTick);
    }

    private static void serverTick(Level level, BlockPos pos, BlockState state, Entity be) {
        if (be.held.isEmpty() || !be.held.isDamaged()) {
            if (be.charge() > 0 != state.getValue(LIT)) level.setBlock(pos, state.setValue(LIT, be.charge() > 0), 3);
            return;
        }
        if (!hold(level, pos, state, be) || level.getGameTime() % 20 != 0) return;
        ItemStack st = be.held;
        int step = Math.max(1, st.getMaxDamage() / 100);
        st.setDamageValue(Math.max(0, st.getDamageValue() - step));
        be.changed();
        if (level instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.BUBBLE_POP, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 3, 0.25, 0.05, 0.25, 0.01);
            if (!st.isDamaged()) sl.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.8f, 1.6f);
        }
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource rnd) {
        if (state.getValue(LIT) && rnd.nextInt(4) == 0)
            level.addParticle(ParticleTypes.SPLASH, pos.getX() + 0.3 + rnd.nextDouble() * 0.4, pos.getY() + 1.0, pos.getZ() + 0.3 + rnd.nextDouble() * 0.4, 0, 0, 0);
    }

    public static class Entity extends SealedBlockEntity {
        ItemStack held = ItemStack.EMPTY;

        public Entity(BlockPos pos, BlockState state) {
            super(ModBlocks.WASHING_BARREL_BE.get(), pos, state);
        }

        public ItemStack held() {
            return held;
        }

        void changed() {
            setChanged();
            if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }

        @Override
        protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.saveAdditional(tag, registries);
            if (!held.isEmpty()) tag.put("held", held.save(registries));
        }

        @Override
        protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.loadAdditional(tag, registries);
            held = tag.contains("held") ? ItemStack.parse(registries, tag.getCompound("held")).orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
        }
    }
}
