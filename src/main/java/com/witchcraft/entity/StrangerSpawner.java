package com.witchcraft.entity;

import com.witchcraft.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Brings the stranger by, very rarely: once a minute, for a player standing in a village, a small chance (and
 * never while another stranger is anywhere near). They stay about a day.
 */
public final class StrangerSpawner {
    private static final int PERIOD = 1200;
    public static float chance = 0.012f;

    private StrangerSpawner() {}

    public static void onLevelTick(LevelTickEvent.Post e) {
        if (!(e.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return;
        if (level.getGameTime() % PERIOD != 0 || !level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_DO_TRADER_SPAWNING)) return;
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator() || !level.isCloseToVillage(p.blockPosition(), 2) || level.random.nextFloat() >= chance) continue;
            if (!level.getEntities(ModEntities.STRANGER.get(), new AABB(p.blockPosition()).inflate(256), x -> true).isEmpty()) continue;
            trySpawn(level, p);
        }
    }

    public static StrangerEntity trySpawn(ServerLevel level, ServerPlayer near) {
        for (int attempt = 0; attempt < 10; attempt++) {
            double a = level.random.nextDouble() * Math.PI * 2, d = 12 + level.random.nextDouble() * 14;
            int x = (int) Math.floor(near.getX() + Math.cos(a) * d), z = (int) Math.floor(near.getZ() + Math.sin(a) * d);
            BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
            if (!level.getBlockState(top.below()).isFaceSturdy(level, top.below(), net.minecraft.core.Direction.UP)) continue;
            if (!level.getBlockState(top).isAir() || !level.getBlockState(top.above()).isAir()) continue;
            StrangerEntity s = ModEntities.STRANGER.get().spawn(level, top, MobSpawnType.EVENT);
            if (s == null) continue;
            s.setDespawnDelay(24000);
            s.setWanderTarget(near.blockPosition());
            s.restrictTo(near.blockPosition(), 16);
            return s;
        }
        return null;
    }
}
