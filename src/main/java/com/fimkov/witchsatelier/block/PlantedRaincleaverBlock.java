package com.fimkov.witchsatelier.block;

import com.fimkov.witchsatelier.registry.ModBlocks;
import com.fimkov.witchsatelier.util.TimedBlocks;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A Raincleaver planted point-down (sneak and use it on the ground): its glyphs keep cutting, so no water or lava
 * comes within a few blocks of it, in a dry bubble round about, for as long as it stands. The blade wears slowly
 * while it does. Use it with an empty hand to pull it out; the water closes in again behind it.
 */
public class PlantedRaincleaverBlock extends BaseEntityBlock {
    public static final MapCodec<PlantedRaincleaverBlock> CODEC = simpleCodec(PlantedRaincleaverBlock::new);
    /** How far the dry bubble reaches (blocks from the hilt). */
    public static final double REACH = 4.5;
    private static final VoxelShape SHAPE = box(6, 0, 6, 10, 16, 10);

    public PlantedRaincleaverBlock(Properties props) {
        super(props);
    }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) { return SHAPE; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new Entity(pos, state);
    }

    /** Plants {@code sword} at {@code pos} (air or a liquid). */
    public static boolean plant(Level level, BlockPos pos, ItemStack sword) {
        BlockState there = level.getBlockState(pos);
        if (!there.isAir() && !(there.getBlock() instanceof LiquidBlock) && !there.canBeReplaced()) return false;
        if (level.isClientSide) return true;
        level.setBlock(pos, ModBlocks.PLANTED_RAINCLEAVER.get().defaultBlockState(), 3);
        if (level.getBlockEntity(pos) instanceof Entity be) {
            be.sword = sword;
            be.changed();
        }
        level.playSound(null, pos, SoundEvents.TRIDENT_HIT_GROUND, SoundSource.BLOCKS, 1f, 0.9f);
        cut((ServerLevel) level, pos);
        return true;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof Entity be)) return InteractionResult.PASS;
        if (!level.isClientSide) {
            ItemStack out = be.sword;
            be.sword = ItemStack.EMPTY;
            level.removeBlock(pos, false);
            if (!out.isEmpty() && !player.getInventory().add(out)) player.drop(out, false);
            level.playSound(null, pos, SoundEvents.TRIDENT_RETURN, SoundSource.BLOCKS, 1f, 1f);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof Entity be && !be.sword.isEmpty()) {
            net.minecraft.world.Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, be.sword);
            be.sword = ItemStack.EMPTY;
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, ModBlocks.PLANTED_RAINCLEAVER_BE.get(), PlantedRaincleaverBlock::serverTick);
    }

    private static void serverTick(Level level, BlockPos pos, BlockState state, Entity be) {
        if (!(level instanceof ServerLevel sl)) return;
        long t = level.getGameTime();
        if (t % 5 == 0) cut(sl, pos);
        if (t % 300 == 0 && !be.sword.isEmpty()) {
            // the glyphs wear as they work
            be.sword.setDamageValue(be.sword.getDamageValue() + 1);
            if (be.sword.getDamageValue() >= be.sword.getMaxDamage()) {
                be.sword = ItemStack.EMPTY;
                level.playSound(null, pos, SoundEvents.ITEM_BREAK, SoundSource.BLOCKS, 1f, 1f);
                level.removeBlock(pos, false);
                return;
            }
            be.setChanged();
        }
    }

    /** Pushes the liquid back from round the blade, and keeps back what it already pushed. */
    private static void cut(ServerLevel level, BlockPos pos) {
        int r = (int) Math.ceil(REACH);
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-r, -r, -r), pos.offset(r, r, r))) {
            if (p.distToCenterSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > REACH * REACH) continue;
            if (TimedBlocks.extend(level, p, 20)) continue;
            BlockState s = level.getBlockState(p);
            if (!(s.getBlock() instanceof LiquidBlock)) continue;
            TimedBlocks.set(level, p, Blocks.AIR.defaultBlockState(), 20);
            if (level.random.nextInt(6) == 0) level.sendParticles(ParticleTypes.SPLASH, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 3, 0.3, 0.3, 0.3, 0.05);
        }
    }

    public static class Entity extends BlockEntity {
        ItemStack sword = ItemStack.EMPTY;

        public Entity(BlockPos pos, BlockState state) {
            super(ModBlocks.PLANTED_RAINCLEAVER_BE.get(), pos, state);
        }

        public ItemStack sword() {
            return sword;
        }

        void changed() {
            setChanged();
            if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }

        @Override
        protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.saveAdditional(tag, registries);
            if (!sword.isEmpty()) tag.put("sword", sword.save(registries));
        }

        @Override
        protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.loadAdditional(tag, registries);
            sword = tag.contains("sword") ? ItemStack.parse(registries, tag.getCompound("sword")).orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
        }

        @Override
        public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
            return saveWithoutMetadata(registries);
        }

        @Override
        public Packet<ClientGamePacketListener> getUpdatePacket() {
            return ClientboundBlockEntityDataPacket.create(this);
        }
    }
}
