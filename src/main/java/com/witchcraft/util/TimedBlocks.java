package com.witchcraft.util;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** Blocks a contraption changes for a while (a cleft in a river, a bubble of water); put back when time is up. */
public final class TimedBlocks {
    private record Entry(BlockPos pos, BlockState placed, BlockState original, long until) {}

    private static final Map<ResourceKey<Level>, List<Entry>> ALL = new HashMap<>();

    private TimedBlocks() {}

    /** Sets {@code state} at {@code pos} for {@code ticks}, then restores what was there (if nobody changed it since). */
    public static void set(ServerLevel level, BlockPos pos, BlockState state, int ticks) {
        BlockState was = level.getBlockState(pos);
        for (Entry e : ALL.getOrDefault(level.dimension(), List.of())) {
            if (e.pos.equals(pos)) { was = e.original; break; }
        }
        BlockState original = was;
        ALL.computeIfAbsent(level.dimension(), k -> new ArrayList<>()).removeIf(e -> e.pos.equals(pos));
        level.setBlock(pos, state, 2);
        ALL.get(level.dimension()).add(new Entry(pos.immutable(), state, original, level.getGameTime() + ticks));
    }

    public static void tick(ServerLevel level) {
        List<Entry> list = ALL.get(level.dimension());
        if (list == null || list.isEmpty()) return;
        long now = level.getGameTime();
        for (Iterator<Entry> it = list.iterator(); it.hasNext(); ) {
            Entry e = it.next();
            if (e.until > now) continue;
            if (level.isLoaded(e.pos) && level.getBlockState(e.pos) == e.placed) level.setBlock(e.pos, e.original, 3);
            it.remove();
        }
    }

    /** The world is closing: put everything back now. */
    public static void restoreAll(ServerLevel level) {
        List<Entry> list = ALL.remove(level.dimension());
        if (list == null) return;
        for (Entry e : list) if (level.getBlockState(e.pos) == e.placed) level.setBlock(e.pos, e.original, 2);
    }
}
