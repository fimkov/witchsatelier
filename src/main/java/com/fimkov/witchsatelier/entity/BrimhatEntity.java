package com.fimkov.witchsatelier.entity;

import com.fimkov.witchsatelier.glyph.GlyphService;
import com.fimkov.witchsatelier.glyph.model.Sigil;
import com.fimkov.witchsatelier.glyph.surface.SurfacePlane;
import com.fimkov.witchsatelier.registry.ModItems;
import com.fimkov.witchsatelier.spell.ElementLook;
import com.fimkov.witchsatelier.spell.SpellProgram;
import com.fimkov.witchsatelier.spell.SpellProgram.Form;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * A Brimhat: a witch of the forbidden path, face hidden behind a mask, brim pulled low. They draw glyphs in the
 * open air with a quick hand: a volley of fire, threads that bind you where you stand, a step through shadow.
 * They keep their distance and never fight fair. Anyone wearing a Brimhat mask is taken for one of their own.
 */
public class BrimhatEntity extends Monster {
    private static final EntityDataAccessor<Integer> CASTING = SynchedEntityData.defineId(BrimhatEntity.class, EntityDataSerializers.INT);

    public BrimhatEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        xpReward = 12;
    }

    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 30).add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FOLLOW_RANGE, 32).add(Attributes.ARMOR, 2).add(Attributes.ATTACK_DAMAGE, 3);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(CASTING, 0);
    }

    /** Ticks left of the drawing gesture (0 when not casting); for the arm pose. */
    public int casting() {
        return entityData.get(CASTING);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new CastGoal(this));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 12));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, p -> !wearsMask(p)));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, KnightMoralisEntity.class, true));
    }

    /** Brimhats leave their own alone: a mask is enough to pass. */
    public static boolean wearsMask(LivingEntity e) {
        return e.getItemBySlot(EquipmentSlot.HEAD).is(ModItems.BRIMHAT_MASK.get());
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType type, SpawnGroupData data) {
        setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.INK_PEN.get()));
        setDropChance(EquipmentSlot.MAINHAND, 0.08f);
        return super.finalizeSpawn(level, difficulty, type, data);
    }

    @Override
    public void tick() {
        super.tick();
        int c = casting();
        if (c > 0 && !level().isClientSide) entityData.set(CASTING, c - 1);
        if (level().isClientSide && c > 0 && random.nextInt(2) == 0) {
            // the glyph being drawn in the air glimmers in front of the hand
            Vec3 at = getEyePosition().add(getViewVector(1).scale(0.9)).add(0, -0.3, 0);
            level().addParticle(ParticleTypes.ENCHANTED_HIT, at.x + (random.nextDouble() - 0.5) * 0.6, at.y + (random.nextDouble() - 0.5) * 0.6,
                    at.z + (random.nextDouble() - 0.5) * 0.6, 0, 0, 0);
        }
    }

    // ------------------------------------------------------------------ spells

    public enum Spell { BOLTS, BIND, STEP, CURSE }

    public void cast(Spell spell, LivingEntity target) {
        if (!(level() instanceof ServerLevel sl)) return;
        entityData.set(CASTING, 18);
        Vec3 eye = getEyePosition();
        Vec3 to = target.getBoundingBox().getCenter().subtract(eye).normalize();
        switch (spell) {
            case BOLTS -> {
                Vec3 o = eye.add(to.scale(0.9)).subtract(0, 0.25, 0);
                int bolts = 2 + random.nextInt(3);
                SpellProgram p = new SpellProgram(Sigil.FIRE, null, Form.BOLTS, false, o, to, to, 0.5, 4, bolts * 4,
                        false, false, random.nextInt(3) == 0, 0, bolts, 0, false, 0);
                GlyphService.startSpell(sl, null, p, getUUID(), true);
                sl.playSound(null, getX(), getY(), getZ(), com.fimkov.witchsatelier.registry.ModSounds.BRIMHAT_CAST.get(), SoundSource.HOSTILE, 1f, 1.1f);
            }
            case BIND -> {
                // a ring of red threads opens under the target's feet
                BlockPos below = target.blockPosition().below();
                SurfacePlane plane = SurfacePlane.of(below, Direction.UP);
                Vec3 o = new Vec3(target.getX(), below.getY() + 1.02, target.getZ());
                SpellProgram p = new SpellProgram(Sigil.CRYSTAL, null, Form.MANIFEST, false, o, new Vec3(0, 1, 0), new Vec3(0, 1, 0),
                        1.3, 3, 70, false, true, false, 0, 0, 0, false, 0).withMods(SpellProgram.BIND);
                GlyphService.startSpell(sl, plane, p, getUUID(), false);
                sl.playSound(null, target.getX(), target.getY(), target.getZ(), com.fimkov.witchsatelier.registry.ModSounds.BRIMHAT_CAST.get(), SoundSource.HOSTILE, 1f, 0.8f);
            }
            case STEP -> {
                // a step through shadow, to somewhere beside the target
                for (int i = 0; i < 8; i++) {
                    double a = random.nextDouble() * Math.PI * 2, d = 5 + random.nextDouble() * 5;
                    double x = target.getX() + Math.cos(a) * d, z = target.getZ() + Math.sin(a) * d;
                    sl.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1, getZ(), 15, 0.3, 0.6, 0.3, 0.02);
                    if (randomTeleport(x, target.getY(), z, true)) {
                        sl.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1, getZ(), 15, 0.3, 0.6, 0.3, 0.02);
                        sl.playSound(null, getX(), getY(), getZ(), SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.HOSTILE, 1f, 0.8f);
                        break;
                    }
                }
            }
            case CURSE -> {
                // forbidden magic, drawn straight onto the victim's shadow
                target.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 100, 0));
                target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 140, 0));
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
                Vec3 c = target.position();
                sl.sendParticles(ElementLook.rune(Sigil.CRYSTAL, null), c.x, c.y + 1, c.z, 20, 0.5, 0.8, 0.5, 0.02);
                sl.playSound(null, c.x, c.y, c.z, com.fimkov.witchsatelier.registry.ModSounds.BRIMHAT_CAST.get(), SoundSource.HOSTILE, 1f, 0.6f);
            }
        }
    }

    /** Keeps a wary distance, closes in when far, and casts every few seconds when it has a clear line. */
    static class CastGoal extends Goal {
        private final BrimhatEntity mob;
        private int cooldown = 30;
        private int strafe;

        CastGoal(BrimhatEntity mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity t = mob.getTarget();
            return t != null && t.isAlive();
        }

        @Override
        public void tick() {
            LivingEntity t = mob.getTarget();
            if (t == null) return;
            mob.getLookControl().setLookAt(t, 30, 30);
            double d = mob.distanceTo(t);
            boolean sees = mob.getSensing().hasLineOfSight(t);
            if (d > 14 || !sees) mob.getNavigation().moveTo(t, 1.0);
            else if (d < 6) {
                Vec3 away = mob.position().subtract(t.position()).normalize().scale(6);
                mob.getNavigation().moveTo(mob.getX() + away.x, mob.getY(), mob.getZ() + away.z, 1.15);
            } else {
                mob.getNavigation().stop();
                if (--strafe <= 0) strafe = 20 + mob.random.nextInt(30);
                mob.getMoveControl().strafe(0, strafe > 25 ? 0.4f : -0.4f);
            }
            if (--cooldown > 0 || !sees || d > 22) return;
            RandomSource r = mob.random;
            Spell s;
            if (d < 5 && r.nextInt(2) == 0) s = Spell.STEP;
            else {
                int roll = r.nextInt(10);
                s = roll < 5 ? Spell.BOLTS : roll < 8 ? Spell.BIND : roll < 9 ? Spell.CURSE : Spell.STEP;
            }
            mob.cast(s, t);
            cooldown = 50 + r.nextInt(40);
        }
    }

    /** What a Brimhat leaves behind: ink, sometimes blood ink, a forbidden page, rarely the mask itself. */
    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        if (random.nextFloat() < 0.3f) spawnAtLocation(new ItemStack(ModItems.CONJURING_INK.get()));
        if (random.nextFloat() < 0.12f) spawnAtLocation(new ItemStack(ModItems.BLOOD_INK.get()));
        if (recentlyHit && random.nextFloat() < 0.07f) spawnAtLocation(new ItemStack(ModItems.BRIMHAT_MASK.get()));
        if (recentlyHit && random.nextFloat() < 0.04f) spawnAtLocation(new ItemStack(ModItems.BRIMHAT_MANTLE.get()));
        if (random.nextFloat() < 0.15f) spawnAtLocation(com.fimkov.witchsatelier.glyph.page.GlyphLibrary.forbiddenPageItem(random));
        if (recentlyHit && random.nextFloat() < 0.03f)
            spawnAtLocation(com.fimkov.witchsatelier.glyph.page.GlyphLibrary.volumeBook(com.fimkov.witchsatelier.glyph.page.GlyphLibrary.FORBIDDEN_BOOK, random));
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.WITCH_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.WITCH_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.WITCH_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return 0.7f + random.nextFloat() * 0.1f;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
    }

    /** Steps come with the swish of the long robe. */
    @Override
    protected void playStepSound(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        super.playStepSound(pos, state);
        if (random.nextInt(2) == 0) playSound(com.fimkov.witchsatelier.registry.ModSounds.CLOTH_RUSTLE.get(), 0.35f, 0.85f + random.nextFloat() * 0.3f);
    }
}
