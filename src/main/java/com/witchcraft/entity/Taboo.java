package com.witchcraft.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * How badly someone has broken the Pact: forbidden magic (blood ink, petrification, glyphs on living things)
 * adds to it, time slowly forgives it. Past {@link #HUNTED} the Knights Moralis come looking.
 */
public final class Taboo {
    public static final String KEY = "witchcraft_taboo";
    public static final int HUNTED = 30, MAX = 200;

    private Taboo() {}

    public static int of(Player p) {
        return p.getPersistentData().getInt(KEY);
    }

    public static void add(Player p, int amount) {
        int before = of(p);
        int now = Math.min(MAX, before + amount);
        p.getPersistentData().putInt(KEY, now);
        if (before < HUNTED && now >= HUNTED && p instanceof ServerPlayer sp) {
            // no explanation, only a feeling of being watched
            sp.level().playSound(null, sp.getX(), sp.getY(), sp.getZ(), net.minecraft.sounds.SoundEvents.BELL_RESONATE,
                    net.minecraft.sounds.SoundSource.AMBIENT, 0.6f, 0.5f);
        }
    }

    /** Forgiveness, and the knights' visits. */
    public static void onPlayerTick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || p.tickCount % 1200 != 0) return;
        int t = of(p);
        if (t > 0) p.getPersistentData().putInt(KEY, t - 1);
        if (t < HUNTED || p.isCreative() || p.isSpectator()) return;
        ServerLevel level = p.serverLevel();
        if (level.random.nextFloat() > 0.12f + (t - HUNTED) * 0.004f) return;
        if (!level.getEntitiesOfClass(KnightMoralisEntity.class, new AABB(p.blockPosition()).inflate(64)).isEmpty()) return;
        int knights = t >= HUNTED * 3 ? 3 : t >= HUNTED * 2 ? 2 : 1;
        for (int k = 0; k < knights; k++) {
            for (int attempt = 0; attempt < 10; attempt++) {
                double a = level.random.nextDouble() * Math.PI * 2, d = 16 + level.random.nextDouble() * 10;
                BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        BlockPos.containing(p.getX() + Math.cos(a) * d, 0, p.getZ() + Math.sin(a) * d));
                if (Math.abs(top.getY() - p.getY()) > 12 || !level.getBlockState(top).isAir()) continue;
                KnightMoralisEntity kn = com.witchcraft.registry.ModEntities.KNIGHT_MORALIS.get().spawn(level, top, MobSpawnType.EVENT);
                if (kn != null) { kn.setTarget(p); break; }
            }
        }
    }
}
