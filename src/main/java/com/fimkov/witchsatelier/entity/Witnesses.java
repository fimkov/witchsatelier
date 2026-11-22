package com.fimkov.witchsatelier.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class Witnesses {
    private static final String SEEN_AT = "witchsatelier_seen_at";

    public static final double SIGHT = 16;

    private static final int COOLDOWN = 200;
    public static final int TABOO = 4, GOSSIP = 25;

    private Witnesses() {}

    public static int saw(ServerPlayer p) {
        if (p.isCreative() || p.isSpectator()) return 0;
        ServerLevel level = p.serverLevel();
        Vec3 eye = p.getEyePosition();
        List<AbstractVillager> who = level.getEntitiesOfClass(AbstractVillager.class, new AABB(eye, eye).inflate(SIGHT),
                v -> v.isAlive() && !v.isSleeping() && !v.isBaby() && sees(v, p));
        if (who.isEmpty()) return 0;
        long now = level.getGameTime();
        boolean counts = !p.getPersistentData().contains(SEEN_AT) || now - p.getPersistentData().getLong(SEEN_AT) >= COOLDOWN;
        for (AbstractVillager v : who) {
            level.sendParticles(ParticleTypes.ANGRY_VILLAGER, v.getX(), v.getEyeY() + 0.4, v.getZ(), 2, 0.25, 0.1, 0.25, 0);
            if (v instanceof Villager villager) {
                villager.getBrain().setMemoryWithExpiry(MemoryModuleType.HURT_BY, level.damageSources().magic(), 160);
                villager.getBrain().setMemoryWithExpiry(MemoryModuleType.HURT_BY_ENTITY, p, 160);
                if (counts) villager.getGossips().add(p.getUUID(), GossipType.MINOR_NEGATIVE, GOSSIP);
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
}
