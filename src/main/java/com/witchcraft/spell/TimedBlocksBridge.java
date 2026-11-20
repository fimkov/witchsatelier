package com.witchcraft.spell;

import com.witchcraft.util.TimedBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

/** Short-lived blocks a spell leaves behind. */
final class TimedBlocksBridge {
    private TimedBlocksBridge() {}

    static void water(ServerLevel level, BlockPos at) {
        TimedBlocks.set(level, at, Blocks.WATER.defaultBlockState(), 200);
    }
}
