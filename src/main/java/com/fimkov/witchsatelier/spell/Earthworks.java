package com.fimkov.witchsatelier.spell;

import com.fimkov.witchsatelier.glyph.GlyphService;
import com.fimkov.witchsatelier.glyph.recognize.Pt;
import com.fimkov.witchsatelier.glyph.surface.SurfacePlane;
import com.mojang.logging.LogUtils;
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

public final class Earthworks {
    private static final long MEMORY_TICKS = 20 * 60 * 10;

    private record Rubble(BlockState state, long time) {}

    private static final Map<ResourceKey<Level>, Map<BlockPos, Rubble>> RUBBLE = new HashMap<>();

    private Earthworks() {}

    public static void clearLevel(ResourceKey<Level> dim) {
        RUBBLE.remove(dim);
    }

    public static BlockState conjured(BlockState found, BlockState fallback) {
        if (found.getBlock() instanceof net.minecraft.world.level.block.FallingBlock) {
            return found.is(net.minecraft.tags.BlockTags.SAND) ? Blocks.SANDSTONE.defaultBlockState() : fallback;
        }
        boolean plain = found.is(net.minecraft.tags.BlockTags.DIRT) || found.is(net.minecraft.tags.BlockTags.BASE_STONE_OVERWORLD)
                || found.is(net.minecraft.tags.BlockTags.BASE_STONE_NETHER) || found.is(net.minecraft.tags.BlockTags.TERRACOTTA)
                || found.is(Blocks.COBBLESTONE) || found.is(Blocks.SANDSTONE) || found.is(Blocks.RED_SANDSTONE) || found.is(Blocks.MUD)
                || found.is(Blocks.PACKED_MUD) || found.is(Blocks.CLAY) || found.is(Blocks.SNOW_BLOCK) || found.is(Blocks.END_STONE);
        return plain ? found.getBlock().defaultBlockState() : fallback;
    }

    public static boolean mayChange(ServerLevel level, BlockPos pos, java.util.UUID caster, boolean breaking) {
        if (caster == null) return true;
        if (level.getPlayerByUUID(caster) instanceof net.minecraft.server.level.ServerPlayer sp) {
            if (!sp.mayBuild() || !level.mayInteract(sp, pos)) return false;
            BlockState state = level.getBlockState(pos);
            if (breaking) return !net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.level.BlockEvent.BreakEvent(level, pos, state, sp)).isCanceled();
            var snap = net.neoforged.neoforge.common.util.BlockSnapshot.create(level.dimension(), level, pos);
            return !net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent(snap, state, sp)).isCanceled();
        }
        if (level.getEntity(caster) instanceof net.minecraft.world.entity.Mob) return level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING);
        return true;
    }

    static void crush(ServerLevel level, ActiveSpell spell) {
        SpellProgram p = spell.program;
        SurfacePlane plane = spell.plane;
        Pt c = plane.toUV(p.origin());
        double r = p.radius();
        int depth = 1 + (int) (p.power() / 8);
        double maxHardness = 1.5 + p.power() * 0.3;
        Map<BlockPos, Rubble> memory = RUBBLE.computeIfAbsent(level.dimension(), k -> new HashMap<>());
        long now = level.getGameTime();
        memory.values().removeIf(old -> now - old.time() < 0 || now - old.time() > MEMORY_TICKS);
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
                    if (!mayChange(level, pos, spell.caster, true)) break;
                    level.levelEvent(2001, pos, Block.getId(s));
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                    memory.put(pos.immutable(), new Rubble(s, level.getGameTime()));
                    removed.add(pos.immutable());
                }
            }
        }
        Vec3 o = p.origin();
        level.playSound(null, o.x, o.y, o.z, SoundEvents.GRAVEL_BREAK, SoundSource.BLOCKS, 1.5f, 0.6f);
        LogUtils.getLogger().debug("[earth] crush at {} r={} depth={}: {} blocks", p.origin(), r, depth, removed.size());
        GlyphService.onBlocksRemoved(level, removed);
    }

    static void restore(ServerLevel level, Vec3 origin, double range) {
        Map<BlockPos, Rubble> memory = RUBBLE.get(level.dimension());
        if (memory == null) return;
        long now = level.getGameTime();
        int count = 0;
        for (Iterator<Map.Entry<BlockPos, Rubble>> it = memory.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<BlockPos, Rubble> e = it.next();
            long age = now - e.getValue().time();
            if (age < 0 || age > MEMORY_TICKS) { it.remove(); continue; }
            BlockPos pos = e.getKey();
            if (pos.distToCenterSqr(origin) > range * range) continue;
            if (!level.getBlockState(pos).canBeReplaced()) continue;
            level.setBlock(pos, e.getValue().state(), 3);
            level.levelEvent(2001, pos, Block.getId(e.getValue().state()));
            it.remove();
            count++;
        }
        LogUtils.getLogger().debug("[earth] restore at {} range {}: {} restored, {} remembered", origin, range, count, memory.size());
        if (count > 0) level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.STONE_PLACE, SoundSource.BLOCKS, 1.5f, 0.8f);
    }
}
