package com.fimkov.witchsatelier.entity;

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
 * How badly someone has broken the Pact: forbidden magic (blood ink, petrification), magic made where villagers see it,
 * adds to it, time slowly forgives it. Past {@link #HUNTED} the Knights Moralis come looking.
 */
public final class Taboo {
    public static final String KEY = "witchsatelier_taboo";
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

    /** The hunt on a player: which knights are after them (0: none), and how long none of them has been near. */
    public static final String HUNT = "witchsatelier_hunt", LOST = "witchsatelier_hunt_lost";
    /** How long a hunted player may be out of the knights' reach before others are sent: 30 s. */
    private static final int LOST_CHECKS = 6;

    public static long huntOf(Player p) {
        return p.getPersistentData().getLong(HUNT);
    }

    /** The Pact is answered: the hunt is called off, and the knights on it go their way. */
    public static void endHunt(Player p) {
        p.getPersistentData().remove(HUNT);
        p.getPersistentData().remove(LOST);
    }

    /** Forgiveness, and the knights' visits. */
    public static void onPlayerTick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !p.isAlive()) return;
        if (p.tickCount % 1200 == 0) {
            int t = of(p);
            if (t > 0) p.getPersistentData().putInt(KEY, t - 1);
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
            // not hunted yet: now and then the order notices
            if (p.tickCount % 1200 != 0 || level.random.nextFloat() > 0.12f + (t - HUNTED) * 0.004f) return;
            hunt = level.random.nextLong() | 1;
            p.getPersistentData().putLong(HUNT, hunt);
            send(level, p, knights, hunt);
            return;
        }
        // hunted: whoever runs, hides or leaves the knights behind in unloaded country is found again before long
        long id = hunt;
        boolean near = !level.getEntitiesOfClass(KnightMoralisEntity.class, new AABB(p.blockPosition()).inflate(48), k -> k.huntId() == id).isEmpty();
        int lost = near ? 0 : p.getPersistentData().getInt(LOST) + 1;
        if (lost >= LOST_CHECKS) {
            send(level, p, knights, hunt);
            lost = 0;
        }
        p.getPersistentData().putInt(LOST, lost);
    }

    /** Knights step out of thin air round the player, a little way off. */
    public static int send(ServerLevel level, ServerPlayer p, int knights, long hunt) {
        int sent = 0;
        for (int k = 0; k < knights; k++) {
            for (int attempt = 0; attempt < 12; attempt++) {
                double a = level.random.nextDouble() * Math.PI * 2, d = 12 + level.random.nextDouble() * 8;
                BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        BlockPos.containing(p.getX() + Math.cos(a) * d, 0, p.getZ() + Math.sin(a) * d));
                // under a roof or in a cave the ground is not the top: try the player's own height first
                BlockPos at = BlockPos.containing(p.getX() + Math.cos(a) * d, p.getY(), p.getZ() + Math.sin(a) * d);
                BlockPos spot = KnightMoralisEntity.standable(level, at) ? at : Math.abs(top.getY() - p.getY()) <= 12 && KnightMoralisEntity.standable(level, top) ? top : null;
                if (spot == null) continue;
                KnightMoralisEntity kn = com.fimkov.witchsatelier.registry.ModEntities.KNIGHT_MORALIS.get().spawn(level, spot, MobSpawnType.EVENT);
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

    /** Dying ends it: the knights have done what they came for and leave, and the Pact counts it as paid. */
    public static void onDeath(net.neoforged.neoforge.event.entity.living.LivingDeathEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        boolean byKnight = e.getSource().getEntity() instanceof KnightMoralisEntity;
        if (huntOf(p) == 0 && !byKnight) return;
        p.getPersistentData().putInt(KEY, 0);
        endHunt(p);
        for (KnightMoralisEntity k : p.serverLevel().getEntitiesOfClass(KnightMoralisEntity.class, new AABB(p.blockPosition()).inflate(96)))
            if (k.isHunting(p) || k.getTarget() == p) k.leave();
    }
}
