package com.witchcraft.windowway;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Holds the block the gap replaced, so it can always come back (even after a restart). */
public class WindowwayGapBlockEntity extends BlockEntity {
    private BlockState original = Blocks.STONE.defaultBlockState();

    public WindowwayGapBlockEntity(BlockPos pos, BlockState state) {
        super(com.witchcraft.registry.ModBlocks.WINDOWWAY_GAP_BE.get(), pos, state);
    }

    public void setOriginal(BlockState state) {
        original = state;
        setChanged();
    }

    public BlockState original() {
        return original;
    }

    static void serverTick(Level level, BlockPos pos, BlockState state, WindowwayGapBlockEntity be) {
        if (level.getGameTime() % 20 != 0) return;
        if (level instanceof ServerLevel sl && !WindowwayService.ownsGap(sl, pos)) {
            level.setBlock(pos, be.original, 2 | 16);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("original", NbtUtils.writeBlockState(original));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("original")) original = NbtUtils.readBlockState(registries.lookupOrThrow(Registries.BLOCK), tag.getCompound("original"));
    }
}
