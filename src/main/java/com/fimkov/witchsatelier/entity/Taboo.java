package com.fimkov.witchsatelier.entity;

import com.fimkov.witchsatelier.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

public final class Taboo {
    public static final String KEY = "witchsatelier_taboo";
    public static final int HUNTED = 30, MAX = 200;

    private Taboo() {}

    public static int of(Player p) {
        return p.getPersistentData().getInt(KEY);
    }

    public static void add(Player p, int amount) {
        int before = of(p);
        int now = Math.max(0, Math.min(MAX, before + amount));
        p.getPersistentData().putInt(KEY, now);
        if (before < HUNTED && now >= HUNTED && p instanceof ServerPlayer sp) {
            sp.level().playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.BELL_RESONATE,
                    SoundSource.AMBIENT, 0.6f, 0.5f);
            com.fimkov.witchsatelier.advancement.Feats.hunted(sp);
        }
    }

    public static final String HUNT = "witchsatelier_hunt", LOST = "witchsatelier_hunt_lost";

    private static final int LOST_CHECKS = 6;

    public static long huntOf(Player p) {
        return p.getPersistentData().getLong(HUNT);
    }

    public static void endHunt(Player p) {
        p.getPersistentData().remove(HUNT);
        p.getPersistentData().remove(LOST);
    }

    public static void onPlayerTick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !p.isAlive()) return;
        if (p.tickCount % 1200 == 0) {
            int t = of(p);
            if (t > 0) p.getPersistentData().putInt(KEY, t - 1);
            if (t == 1) com.fimkov.witchsatelier.advancement.Feats.forgiven(p);
        }
        if (p.tickCount % 100 != 0) return;
        int t = of(p);
        long hunt = huntOf(p);
        if (t < HUNTED || p.isCreative() || p.isSpectator()) {
            if (hunt != 0) endHunt(p);
            return;
        }
        ServerLevel level = p.serverLevel();
        int knights = t >= HUNTED * 3 ? 3 : t >= HUNTED * 2 ? 2 : 1;
        if (hunt == 0) {
            if (p.tickCount % 1200 != 0 || level.random.nextFloat() > 0.12f + (t - HUNTED) * 0.004f) return;
            hunt = level.random.nextLong() | 1;
            p.getPersistentData().putLong(HUNT, hunt);
            send(level, p, knights, hunt);
            return;
        }

        long id = hunt;
        boolean near = !level.getEntitiesOfClass(KnightMoralisEntity.class, new AABB(p.blockPosition()).inflate(48), k -> k.huntId() == id).isEmpty();
        int lost = near ? 0 : p.getPersistentData().getInt(LOST) + 1;
        if (lost >= LOST_CHECKS) {
            send(level, p, knights, hunt);
            lost = 0;
        }
        p.getPersistentData().putInt(LOST, lost);
    }

    public static int send(ServerLevel level, ServerPlayer p, int knights, long hunt) {
        int sent = 0;
        for (int k = 0; k < knights; k++) {
            for (int attempt = 0; attempt < 12; attempt++) {
                double a = level.random.nextDouble() * Math.PI * 2, d = 12 + level.random.nextDouble() * 8;
                BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        BlockPos.containing(p.getX() + Math.cos(a) * d, 0, p.getZ() + Math.sin(a) * d));

                BlockPos at = BlockPos.containing(p.getX() + Math.cos(a) * d, p.getY(), p.getZ() + Math.sin(a) * d);
                BlockPos spot = KnightMoralisEntity.standable(level, at) ? at : Math.abs(top.getY() - p.getY()) <= 12 && KnightMoralisEntity.standable(level, top) ? top : null;
                if (spot == null) continue;
                KnightMoralisEntity kn = ModEntities.KNIGHT_MORALIS.get().spawn(level, spot, MobSpawnType.EVENT);
                if (kn != null) {
                    kn.hunt(p, hunt);
                    KnightMoralisEntity.arrive(level, kn.position());
                    sent++;
                    break;
                }
            }
        }
        return sent;
    }

    public static void onClone(net.neoforged.neoforge.event.entity.player.PlayerEvent.Clone e) {
        var from = e.getOriginal().getPersistentData();
        var to = e.getEntity().getPersistentData();
        for (String k : new String[]{KEY, HUNT, LOST, Witnesses.SEEN_AT, StrangerEntity.GIFT_TAG}) {
            if (from.contains(k)) to.put(k, from.get(k).copy());
        }
    }

    public static void onDeath(LivingDeathEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        boolean byKnight = e.getSource().getEntity() instanceof KnightMoralisEntity;
        if (huntOf(p) == 0 && !byKnight) return;
        settle(p, 0);
    }

    /** Ends the hunt for this player, leaves at most {@code keep} of the debt, and sends the knights away. */
    public static void settle(ServerPlayer p, int keep) {
        p.getPersistentData().putInt(KEY, Math.max(0, Math.min(of(p), keep)));
        endHunt(p);
        for (KnightMoralisEntity k : p.serverLevel().getEntitiesOfClass(KnightMoralisEntity.class, new AABB(p.blockPosition()).inflate(96)))
            if (k.isHunting(p) || k.getTarget() == p) k.leave();
    }
}
