package com.fimkov.witchsatelier.spell;

import com.fimkov.witchsatelier.util.TimedBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

final class TimedBlocksBridge {
    private TimedBlocksBridge() {}

    static void water(ServerLevel level, BlockPos at) {
        TimedBlocks.set(level, at, Blocks.WATER.defaultBlockState(), 200);
    }
}
