package com.fimkov.witchsatelier.spell;

import com.fimkov.witchsatelier.glyph.GlyphService;
import com.fimkov.witchsatelier.glyph.recognize.Pt;
import com.fimkov.witchsatelier.glyph.surface.SurfacePlane;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Wall Breaker (earth + Crush) pulverises the surface it is drawn on; Integration (earth + reversed Crush)
 * puts pulverised blocks back together. Pulverised blocks are remembered for a while so they can be restored.
 */
public final class Earthworks {
    private static final long MEMORY_TICKS = 20 * 60 * 10;

    private record Rubble(BlockState state, long time) {}

    private static final Map<ResourceKey<Level>, Map<BlockPos, Rubble>> RUBBLE = new HashMap<>();

    private Earthworks() {}

    static void crush(ServerLevel level, ActiveSpell spell) {
        SpellProgram p = spell.program;
        SurfacePlane plane = spell.plane;
        Pt c = plane.toUV(p.origin());
        double r = p.radius();
        int depth = 1 + (int) (p.power() / 8);
        double maxHardness = 1.5 + p.power() * 0.3;
        Map<BlockPos, Rubble> memory = RUBBLE.computeIfAbsent(level.dimension(), k -> new HashMap<>());
        List<BlockPos> removed = new ArrayList<>();
        for (int u = (int) Math.floor(c.x() - r); u <= (int) Math.ceil(c.x() + r); u++) {
            for (int v = (int) Math.floor(c.y() - r); v <= (int) Math.ceil(c.y() + r); v++) {
                double du = u + 0.5 - c.x(), dv = v + 0.5 - c.y();
                if (du * du + dv * dv > (r + 0.5) * (r + 0.5)) continue;
                BlockPos face = plane.blockAt(u + 0.5, v + 0.5);
                for (int k = 0; k < depth; k++) {
                    BlockPos pos = face.relative(plane.face().getOpposite(), k);
                    BlockState s = level.getBlockState(pos);
                    float hardness = s.getDestroySpeed(level, pos);
                    if (s.isAir() || hardness < 0 || hardness > maxHardness || s.hasBlockEntity()) break;
                    level.levelEvent(2001, pos, Block.getId(s));
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                    memory.put(pos.immutable(), new Rubble(s, level.getGameTime()));
                    removed.add(pos.immutable());
                }
            }
        }
        Vec3 o = p.origin();
        level.playSound(null, o.x, o.y, o.z, SoundEvents.GRAVEL_BREAK, SoundSource.BLOCKS, 1.5f, 0.6f);
        com.mojang.logging.LogUtils.getLogger().debug("[earth] crush at {} r={} depth={}: {} blocks", p.origin(), r, depth, removed.size());
        GlyphService.onBlocksRemoved(level, removed);
    }

    static void restore(ServerLevel level, Vec3 origin, double range) {
        Map<BlockPos, Rubble> memory = RUBBLE.get(level.dimension());
        if (memory == null) return;
        long now = level.getGameTime();
        int count = 0;
        for (Iterator<Map.Entry<BlockPos, Rubble>> it = memory.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<BlockPos, Rubble> e = it.next();
            if (now - e.getValue().time() > MEMORY_TICKS) { it.remove(); continue; }
            BlockPos pos = e.getKey();
            if (pos.distToCenterSqr(origin) > range * range) continue;
            if (!level.getBlockState(pos).canBeReplaced()) continue;
            level.setBlock(pos, e.getValue().state(), 3);
            level.levelEvent(2001, pos, Block.getId(e.getValue().state()));
            it.remove();
            count++;
        }
        com.mojang.logging.LogUtils.getLogger().debug("[earth] restore at {} range {}: {} restored, {} remembered", origin, range, count, memory.size());
        if (count > 0) level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.STONE_PLACE, SoundSource.BLOCKS, 1.5f, 0.8f);
    }
}
