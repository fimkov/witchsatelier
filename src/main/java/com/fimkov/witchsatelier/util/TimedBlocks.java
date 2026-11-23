package com.fimkov.witchsatelier.util;

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

public final class TimedBlocks {
    private record Entry(BlockPos pos, BlockState placed, BlockState original, long until) {}

    private static final Map<ResourceKey<Level>, List<Entry>> ALL = new HashMap<>();

    private TimedBlocks() {}

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

    public static boolean extend(ServerLevel level, BlockPos pos, int ticks) {
        return extend(level, pos, ticks, false);
    }

    public static boolean extend(ServerLevel level, BlockPos pos, int ticks, boolean onlyCleared) {
        List<Entry> list = ALL.get(level.dimension());
        if (list == null) return false;
        for (int i = 0; i < list.size(); i++) {
            Entry e = list.get(i);
            if (!e.pos.equals(pos)) continue;
            if (onlyCleared && !e.placed.isAir()) return false;
            if (level.getBlockState(pos) != e.placed) return false;
            list.set(i, new Entry(e.pos, e.placed, e.original, Math.max(e.until, level.getGameTime() + ticks)));
            return true;
        }
        return false;
    }

    public static void tick(ServerLevel level) {
        List<Entry> list = ALL.get(level.dimension());
        if (list == null || list.isEmpty()) return;
        long now = level.getGameTime();
        for (Iterator<Entry> it = list.iterator(); it.hasNext(); ) {
            Entry e = it.next();
            if (e.until > now) continue;
            if (!level.isLoaded(e.pos)) continue;
            if (level.getBlockState(e.pos) == e.placed) level.setBlock(e.pos, e.original, 3);
            it.remove();
        }
    }

    public static void restoreAll(ServerLevel level) {
        List<Entry> list = ALL.remove(level.dimension());
        if (list == null) return;
        for (Entry e : list) if (level.getBlockState(e.pos) == e.placed) level.setBlock(e.pos, e.original, 2);
    }
}
