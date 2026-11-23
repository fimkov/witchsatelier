package com.fimkov.witchsatelier.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.List;
import java.util.UUID;

/**
 * Villagers who see magic being made. A witness takes fright at once (and the Pact counts the sighting), then
 * becomes a talebearer: it runs for the village bell, and if it rings it the whole village hears and the debt
 * rises again. A Seal of Obliviation closed at it before then takes the sighting back (see {@link #forget}).
 */
public final class Witnesses {
    static final String SEEN_AT = "witchsatelier_seen_at";

    /** The tale a talebearer carries, kept on the villager. */
    static final String TALE = "witchsatelier_tale";
    private static final String BY = "by", ACT = "act", DUE = "due", BELL_AT = "bell", GOSSIPED = "gossip";

    public static final double SIGHT = 16;

    private static final int COOLDOWN = 200;
    public static final int TABOO = 4, GOSSIP = 25;

    /** Debt added when a talebearer rings the bell, and for killing one to keep it quiet. */
    public static final int BELL = 10, SILENCED = 12;

    /** How long a talebearer keeps running for the bell before the tale goes stale. */
    public static final int TALE_TICKS = 1200;

    private static final int BELL_SEARCH = 48;
    private static final double VILLAGE = 48, AT_BELL = 2.5, ACT_REACH = 96;
    private static final float RUN = 0.75f;

    private Witnesses() {}

    public static int saw(ServerPlayer p) {
        if (p.isCreative() || p.isSpectator()) return 0;
        ServerLevel level = p.serverLevel();
        Vec3 eye = p.getEyePosition();
        List<AbstractVillager> who = level.getEntitiesOfClass(AbstractVillager.class, new AABB(eye, eye).inflate(SIGHT),
                v -> v.isAlive() && !v.isSleeping() && !v.isBaby() && sees(v, p));
        if (who.isEmpty()) return 0;
        com.fimkov.witchsatelier.advancement.Feats.seen(p);
        long now = level.getGameTime();
        boolean counts = !p.getPersistentData().contains(SEEN_AT) || now - p.getPersistentData().getLong(SEEN_AT) >= COOLDOWN;
        long act = counts ? now : p.getPersistentData().getLong(SEEN_AT);
        for (AbstractVillager v : who) {
            level.sendParticles(ParticleTypes.ANGRY_VILLAGER, v.getX(), v.getEyeY() + 0.4, v.getZ(), 2, 0.25, 0.1, 0.25, 0);
            if (v instanceof Villager villager) {
                villager.getBrain().setMemoryWithExpiry(MemoryModuleType.HURT_BY, level.damageSources().magic(), 160);
                villager.getBrain().setMemoryWithExpiry(MemoryModuleType.HURT_BY_ENTITY, p, 160);
                if (counts) villager.getGossips().add(p.getUUID(), GossipType.MINOR_NEGATIVE, GOSSIP);
                carry(villager, p.getUUID(), act, now, counts ? GOSSIP : 0);
            }
        }
        if (counts) {
            AbstractVillager first = who.get(0);
            level.playSound(null, first.getX(), first.getY(), first.getZ(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 1f, 0.9f);
            p.getPersistentData().putLong(SEEN_AT, now);
            Taboo.add(p, TABOO);
        }
        return who.size();
    }

    private static boolean sees(AbstractVillager v, ServerPlayer p) {
        Vec3 to = p.getEyePosition().subtract(v.getEyePosition());
        if (to.lengthSqr() > SIGHT * SIGHT) return false;
        if (to.lengthSqr() > 4 && v.getViewVector(1f).dot(to.normalize()) < -0.2) return false;
        return v.hasLineOfSight(p);
    }

    private static void carry(Villager v, UUID by, long act, long now, int gossiped) {
        CompoundTag old = tale(v);
        if (old != null && by.equals(old.getUUID(BY))) {
            if (gossiped > 0) old.putInt(GOSSIPED, old.getInt(GOSSIPED) + gossiped);
            return;
        }
        CompoundTag t = new CompoundTag();
        t.putUUID(BY, by);
        t.putLong(ACT, act);
        t.putLong(DUE, now + TALE_TICKS);
        t.putInt(GOSSIPED, gossiped);
        v.getPersistentData().put(TALE, t);
    }

    static CompoundTag tale(Villager v) {
        CompoundTag data = v.getPersistentData();
        return data.contains(TALE, Tag.TAG_COMPOUND) ? data.getCompound(TALE) : null;
    }

    /** Whom this villager is running to tell on, or null. */
    public static UUID talebearerAbout(Villager v) {
        CompoundTag t = tale(v);
        return t == null || !t.hasUUID(BY) ? null : t.getUUID(BY);
    }

    private static boolean sameAct(Villager v, UUID by, long act) {
        CompoundTag t = tale(v);
        return t != null && t.hasUUID(BY) && by.equals(t.getUUID(BY)) && t.getLong(ACT) == act;
    }

    public static void onTick(EntityTickEvent.Post e) {
        if (!(e.getEntity() instanceof Villager v) || v.tickCount % 10 != 0 || !(v.level() instanceof ServerLevel level)) return;
        CompoundTag t = tale(v);
        if (t == null || !v.isAlive()) return;
        if (!t.hasUUID(BY) || level.getGameTime() >= t.getLong(DUE)) {
            v.getPersistentData().remove(TALE);
            return;
        }
        if (v.tickCount % 20 == 0) level.sendParticles(ParticleTypes.ANGRY_VILLAGER, v.getX(), v.getEyeY() + 0.5, v.getZ(), 1, 0.15, 0.05, 0.15, 0);
        BlockPos bell = bellFor(level, v, t);
        if (bell == null) return;
        if (v.position().distanceTo(Vec3.atBottomCenterOf(bell)) <= AT_BELL) {
            ring(level, v, bell, t);
            return;
        }
        v.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(bell, RUN, 1));
    }

    private static BlockPos bellFor(ServerLevel level, Villager v, CompoundTag t) {
        if (t.contains(BELL_AT)) {
            BlockPos at = BlockPos.of(t.getLong(BELL_AT));
            if (level.getBlockState(at).getBlock() instanceof BellBlock) return at;
            t.remove(BELL_AT);
        }
        BlockPos found = level.getPoiManager().findClosest(type -> type.is(PoiTypes.MEETING), v.blockPosition(), BELL_SEARCH,
                PoiManager.Occupancy.ANY).orElse(null);
        if (found != null) t.putLong(BELL_AT, found.asLong());
        return found;
    }

    private static void ring(ServerLevel level, Villager v, BlockPos bell, CompoundTag t) {
        UUID by = t.getUUID(BY);
        long act = t.getLong(ACT);
        if (level.getBlockState(bell).getBlock() instanceof BellBlock b) b.attemptToRing(v, level, bell, null);
        v.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        for (Villager o : level.getEntitiesOfClass(Villager.class, new AABB(bell).inflate(VILLAGE), o -> o.isAlive() && o != v)) {
            o.getGossips().add(by, GossipType.MINOR_NEGATIVE, GOSSIP);
        }
        for (Villager o : level.getEntitiesOfClass(Villager.class, v.getBoundingBox().inflate(ACT_REACH), o -> sameAct(o, by, act))) {
            o.getPersistentData().remove(TALE);
        }
        v.getPersistentData().remove(TALE);
        ServerPlayer p = level.getServer().getPlayerList().getPlayer(by);
        if (p != null) Taboo.add(p, BELL);
    }

    /**
     * The Seal of Obliviation's work on a villager: the last minute is gone. A tale not yet told is dropped, the bad
     * word it took against the witch is taken back, and if no other witness still carries the same sighting the
     * Pact forgets it too. Returns whether there was a tale to forget.
     */
    public static boolean forget(ServerLevel level, Villager v) {
        v.getBrain().eraseMemory(MemoryModuleType.HURT_BY);
        v.getBrain().eraseMemory(MemoryModuleType.HURT_BY_ENTITY);
        v.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        CompoundTag t = tale(v);
        if (t == null) return false;
        v.getPersistentData().remove(TALE);
        if (!t.hasUUID(BY)) return true;
        UUID by = t.getUUID(BY);
        long act = t.getLong(ACT);
        int gossiped = t.getInt(GOSSIPED);
        if (gossiped > 0) v.getGossips().remove(by, GossipType.MINOR_NEGATIVE, gossiped);
        boolean others = !level.getEntitiesOfClass(Villager.class, v.getBoundingBox().inflate(ACT_REACH), o -> o != v && sameAct(o, by, act)).isEmpty();
        ServerPlayer p = level.getServer().getPlayerList().getPlayer(by);
        if (!others && p != null) Taboo.add(p, -TABOO);
        return true;
    }

    public static void onDeath(LivingDeathEvent e) {
        if (!(e.getEntity() instanceof Villager v) || !(e.getSource().getEntity() instanceof ServerPlayer p)) return;
        if (p.getUUID().equals(talebearerAbout(v))) Taboo.add(p, SILENCED);
    }
}
