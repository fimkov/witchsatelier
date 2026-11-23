package com.fimkov.witchsatelier.spell;

import com.fimkov.witchsatelier.entity.Witnesses;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The Seal of Obliviation (Vision ringed with four Pulls): the one spell on a body the Pact allows, and only to keep
 * the secret. Drawn on a surface it erases whatever creature stands in the ring; closed in the palm it erases the
 * creature under the crosshair. It never touches players.
 */
public final class Obliviation {
    public static final double PALM_REACH = 6;
    public static final int DOZE = 60;

    private Obliviation() {}

    static void run(ServerLevel level, ActiveSpell spell) {
        if (spell.handheld) {
            if (spell.age() == 1) {
                LivingEntity target = inSight(level, spell);
                if (target == null || !erase(level, target)) {
                    Vec3 o = spell.program.origin();
                    level.sendParticles(ParticleTypes.WHITE_ASH, o.x, o.y, o.z, 10, 0.2, 0.2, 0.2, 0.01);
                }
            }
            spell.ticksLeft = Math.min(spell.ticksLeft, 1);
            return;
        }
        SpellProgram p = spell.program;
        Vec3 o = p.origin(), n = p.normal();
        double r = Math.max(0.8, p.radius());
        if (spell.age() % 8 == 0) {
            double a = level.random.nextDouble() * Math.PI * 2;
            Vec3 at = o.add(spell.frame.planeVector(Math.cos(a), Math.sin(a)).scale(r)).add(n.scale(0.1));
            level.sendParticles(ParticleTypes.WHITE_ASH, at.x, at.y, at.z, 3, 0.05, 0.05, 0.05, 0.01);
        }
        if (spell.age() % 10 != 1) return;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(o, o.add(n.scale(1.5))).inflate(r),
                e -> e.isAlive() && !e.isSpectator() && !(e instanceof Player))) {
            Vec3 rel = e.position().subtract(o);
            double along = rel.dot(n);
            if (along < -0.6 || along > 1.8 || rel.subtract(n.scale(along)).length() > r) continue;
            erase(level, e);
        }
    }

    private static LivingEntity inSight(ServerLevel level, ActiveSpell spell) {
        if (spell.caster == null || !(level.getEntity(spell.caster) instanceof LivingEntity c)) return null;
        Vec3 eye = c.getEyePosition(), end = eye.add(c.getLookAngle().scale(PALM_REACH));
        HitResult wall = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, c));
        if (wall.getType() != HitResult.Type.MISS) end = wall.getLocation();
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(level, c, eye, end, new AABB(eye, end).inflate(1),
                e -> e instanceof LivingEntity && e != c && e.isAlive() && !e.isSpectator(), 0.3f);
        return hit != null ? (LivingEntity) hit.getEntity() : null;
    }

    /** Takes the last minute from one creature. Returns whether there was anything to take. */
    public static boolean erase(ServerLevel level, LivingEntity e) {
        if (e instanceof Player || !e.isAlive()) return false;
        boolean any = false;
        if (e instanceof Villager v) any = Witnesses.forget(level, v);
        if (e instanceof NeutralMob angry && angry.isAngry()) {
            angry.stopBeingAngry();
            any = true;
        }
        if (e instanceof Mob m) {
            if (m.getTarget() != null) {
                m.setTarget(null);
                any = true;
            }
            if (m.getBrain().checkMemory(MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_PRESENT)) {
                m.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
                any = true;
            }
            if (any) m.getNavigation().stop();
        }
        if (!any) return false;
        e.setLastHurtByMob(null);
        e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, DOZE, 6, false, false));
        double y = e.getEyeY() + 0.2;
        level.sendParticles(ParticleTypes.END_ROD, e.getX(), y, e.getZ(), 10, 0.3, 0.2, 0.3, 0.01);
        level.sendParticles(ParticleTypes.WHITE_ASH, e.getX(), y + 0.3, e.getZ(), 16, 0.3, 0.3, 0.3, 0.02);
        level.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.ILLUSIONER_PREPARE_BLINDNESS, SoundSource.PLAYERS, 0.6f, 1.6f);
        level.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1f, 0.5f);
        return true;
    }
}
