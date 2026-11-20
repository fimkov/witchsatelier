package com.witchcraft.entity;

import com.witchcraft.glyph.GlyphService;
import com.witchcraft.glyph.ink.InkType;
import com.witchcraft.glyph.model.Sigil;
import com.witchcraft.glyph.recognize.Pt;
import com.witchcraft.glyph.storage.GlyphLevelData;
import com.witchcraft.glyph.surface.SurfacePlane;
import com.witchcraft.registry.ModEntities;
import com.witchcraft.registry.ModItems;
import com.witchcraft.registry.ModSounds;
import com.witchcraft.spell.SpellProgram;
import com.witchcraft.spell.SpellProgram.Form;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * The Brimhat Elder: the one the others answer to. Three blocks and more of tattered robe floating over the ground,
 * a hat as wide as a cart, a porcelain mask with red light behind its eyes, a brush as tall as a witch, and a halo of
 * its own grimoire's pages turning at its back.
 *
 * <p>It wakes when it sees you, and fights in three stages, each crossed with a roar that throws you back:
 * <ul>
 *   <li>first it draws glyphs in the air and looses fire from them, and opens petrifying rings under your feet;</li>
 *   <li>then it calls its followers, and draws a <b>great ring</b> round you on the floor in blood ink: when that ring
 *   closes, all inside it burns. Get out, or break the ring before it closes (rub it out, break a block under it, wash
 *   it away): the spell turns on its maker and leaves it reeling;</li>
 *   <li>last it fights in a fury, warding itself when pressed.</li>
 * </ul>
 */
public class BrimhatElderEntity extends BrimhatEntity {
    /** What it is doing, for the client's animation. */
    public enum Anim { IDLE, DRAW_AIR, INSCRIBE, SUMMON, PHASE, STAGGER, DYING }

    private static final EntityDataAccessor<Integer> ANIM = SynchedEntityData.defineId(BrimhatElderEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> STAGE = SynchedEntityData.defineId(BrimhatElderEntity.class, EntityDataSerializers.INT);

    static final int DRAW_AIR_TICKS = 26, INSCRIBE_TICKS = 96, SUMMON_TICKS = 24, PHASE_TICKS = 44, STAGGER_TICKS = 70, DEATH_TICKS = 70;
    /** The great ring: radius in blocks, pieces it is drawn in, when each goes down, when it closes. */
    static final double RING_RADIUS = 4.5;
    static final int RING_PIECES = 6, RING_START = 12, RING_STEP = 10, RING_CLOSE = 84;

    private final ServerBossEvent bar = new ServerBossEvent(Component.translatable("entity.witchcraft.brimhat_elder"), BossEvent.BossBarColor.RED,
            BossEvent.BossBarOverlay.NOTCHED_10);

    private int animTick, animLength;
    private int attackCooldown = 40, stepCooldown = 0;
    private boolean awake;
    /** Client: tick the current animation began, as this side saw it. */
    private int animStartClient;
    private float recentDamage;
    private int recentDamageAge;

    // the great ring being drawn
    private SurfacePlane ringPlane;
    private Pt ringCenter;
    private double ringStartAngle;
    private final List<Long> ringStrokes = new ArrayList<>();

    public BrimhatElderEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        xpReward = 120;
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder attributes() {
        return BrimhatEntity.attributes().add(Attributes.MAX_HEALTH, 220).add(Attributes.ARMOR, 8).add(Attributes.KNOCKBACK_RESISTANCE, 0.85)
                .add(Attributes.MOVEMENT_SPEED, 0.26).add(Attributes.FOLLOW_RANGE, 40).add(Attributes.ATTACK_DAMAGE, 6);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(ANIM, 0);
        b.define(STAGE, 1);
    }

    public Anim anim() {
        int i = entityData.get(ANIM);
        return i >= 0 && i < Anim.values().length ? Anim.values()[i] : Anim.IDLE;
    }

    /** Ticks into the current animation (client: as seen here, smoothed by {@code partialTick}). */
    public float animAge(float partialTick) {
        return level().isClientSide ? tickCount - animStartClient + partialTick : animTick;
    }

    /** 1, 2 or 3: how far into the fight. */
    public int stage() {
        return entityData.get(STAGE);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (ANIM.equals(key)) animStartClient = tickCount;
    }

    private void startAnim(Anim a, int length) {
        entityData.set(ANIM, a.ordinal());
        animTick = 0;
        animLength = length;
    }

    private boolean busy() {
        return anim() != Anim.IDLE;
    }

    // ------------------------------------------------------------------ boss bar

    @Override
    public void startSeenByPlayer(ServerPlayer p) {
        super.startSeenByPlayer(p);
        bar.addPlayer(p);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer p) {
        super.stopSeenByPlayer(p);
        bar.removePlayer(p);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new FightGoal(this));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 16));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, p -> !wearsMask(p)));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, KnightMoralisEntity.class, true));
    }

    // ------------------------------------------------------------------ the fight

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        bar.setProgress(getHealth() / getMaxHealth());
        bar.setDarkenScreen(stage() >= 3);
        if (recentDamageAge > 0 && --recentDamageAge == 0) recentDamage = 0;
        if (!(level() instanceof ServerLevel sl)) return;
        LivingEntity t = getTarget();
        if (!awake && t != null) {
            // it rises to meet whoever found it
            awake = true;
            startAnim(Anim.PHASE, PHASE_TICKS);
            sl.playSound(null, getX(), getY(), getZ(), SoundEvents.WITHER_AMBIENT, SoundSource.HOSTILE, 1.6f, 0.55f);
        }
        if (busy()) {
            animTick++;
            timeline(sl, t);
            if (animTick >= animLength && anim() != Anim.DYING) startAnim(Anim.IDLE, 0);
        }
    }

    /** What happens at each moment of the current move. */
    private void timeline(ServerLevel sl, LivingEntity t) {
        switch (anim()) {
            case DRAW_AIR -> {
                if (animTick == DRAW_AIR_TICKS - 4 && t != null) airVolley(sl, t);
            }
            case SUMMON -> {
                if (animTick == 14) {
                    if (t != null && stage() == 1) petrifyRing(sl, t);
                    else summon(sl);
                }
            }
            case INSCRIBE -> greatRing(sl);
            case PHASE -> {
                if (animTick == 22) shockwave(sl);
            }
            default -> {}
        }
    }

    /** The glyph drawn in the air goes off: a volley of fire at the target. */
    private void airVolley(ServerLevel sl, LivingEntity t) {
        Vec3 eye = getEyePosition();
        Vec3 fwd = Vec3.directionFromRotation(0, yBodyRot);
        Vec3 o = eye.add(fwd.scale(1.6)).add(0, -0.6, 0);
        Vec3 to = t.getBoundingBox().getCenter().subtract(o).normalize();
        int bolts = stage() == 1 ? 3 : stage() == 2 ? 5 : 7;
        SpellProgram p = new SpellProgram(Sigil.FIRE, null, Form.BOLTS, false, o, to, to, 0.6, 5 + stage(), bolts * 4,
                false, false, stage() >= 2, 0, bolts, 0, false, 0);
        GlyphService.startSpell(sl, null, p, getUUID(), true);
        sl.playSound(null, o.x, o.y, o.z, ModSounds.BRIMHAT_CAST.get(), SoundSource.HOSTILE, 1.4f, 0.9f);
        sl.sendParticles(ParticleTypes.FLAME, o.x, o.y, o.z, 30, 0.5, 0.5, 0.5, 0.05);
    }

    /** A grey ring opens under the target; it takes a moment to close its stare. */
    private void petrifyRing(ServerLevel sl, LivingEntity t) {
        BlockPos below = t.blockPosition().below();
        Vec3 o = new Vec3(t.getX(), below.getY() + 1.02, t.getZ());
        SpellProgram p = new SpellProgram(Sigil.EARTH, null, Form.PETRIFY, false, o, new Vec3(0, 1, 0), new Vec3(0, 1, 0),
                1.6, 6, 70, false, false, false, 0, 0, 0, false, 0);
        GlyphService.startSpell(sl, SurfacePlane.of(below, Direction.UP), p, getUUID(), false);
        sl.playSound(null, t.getX(), t.getY(), t.getZ(), ModSounds.PETRIFY.get(), SoundSource.HOSTILE, 1f, 0.7f);
    }

    private void summon(ServerLevel sl) {
        int around = sl.getEntitiesOfClass(BrimhatEntity.class, getBoundingBox().inflate(16), b -> !(b instanceof BrimhatElderEntity)).size();
        for (int i = around; i < 4 && i < around + 2; i++) {
            Vec3 at = position().add((random.nextDouble() - 0.5) * 8, 0, (random.nextDouble() - 0.5) * 8);
            BrimhatEntity b = ModEntities.BRIMHAT.get().spawn(sl, BlockPos.containing(at), MobSpawnType.MOB_SUMMONED);
            if (b != null) {
                b.setTarget(getTarget());
                sl.sendParticles(ParticleTypes.LARGE_SMOKE, b.getX(), b.getY() + 1, b.getZ(), 25, 0.4, 0.8, 0.4, 0.02);
            }
        }
        sl.playSound(null, getX(), getY(), getZ(), ModSounds.BRIMHAT_CAST.get(), SoundSource.HOSTILE, 1.4f, 0.5f);
    }

    /** A crystal shell around itself for a while: arrows turn back. */
    private void ward(ServerLevel sl) {
        BlockPos below = blockPosition().below();
        Vec3 o = new Vec3(getX(), below.getY() + 1.02, getZ());
        SpellProgram p = new SpellProgram(null, com.witchcraft.glyph.model.SignType.DIAMOND, Form.CENTER_SIGN, false, o, new Vec3(0, 1, 0), new Vec3(0, 1, 0),
                1.6, 4, 120, false, false, false, 0, 0, 0, false, 0).withMods(SpellProgram.WARD);
        GlyphService.startSpell(sl, SurfacePlane.of(below, Direction.UP), p, getUUID(), false);
        sl.playSound(null, getX(), getY(), getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.HOSTILE, 1.5f, 0.6f);
    }

    /** The roar between stages: everyone near is thrown back. */
    private void shockwave(ServerLevel sl) {
        sl.playSound(null, getX(), getY(), getZ(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 1.4f, 1.5f);
        for (int k = 0; k < 48; k++) {
            double a = k * Math.PI * 2 / 48;
            sl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, getX() + Math.cos(a) * 1.5, getY() + 0.3, getZ() + Math.sin(a) * 1.5, 0,
                    Math.cos(a), 0.05, Math.sin(a), 0.5);
        }
        sl.sendParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + 1.5, getZ(), 1, 0, 0, 0, 0);
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(7), e -> e != this && !(e instanceof BrimhatEntity))) {
            Vec3 away = e.position().subtract(position()).multiply(1, 0, 1).normalize();
            e.push(away.x * 1.4, 0.6, away.z * 1.4);
            e.hurtMarked = true;
            e.hurt(damageSources().mobAttack(this), 4);
        }
    }

    // ------------------------------------------------------------------ the great ring

    /** Starts drawing the great ring round {@code t}, if the floor there will take ink. */
    public boolean beginGreatRing(ServerLevel sl, LivingEntity t) {
        BlockPos floor = t.blockPosition().below();
        SurfacePlane plane = SurfacePlane.of(floor, Direction.UP);
        Pt c = plane.toUV(new Vec3(t.getX(), floor.getY() + 1, t.getZ()));
        int good = 0;
        for (int k = 0; k < 24; k++) {
            double a = k * Math.PI * 2 / 24;
            if (GlyphService.isDrawable(sl, plane, c.x() + Math.cos(a) * RING_RADIUS, c.y() + Math.sin(a) * RING_RADIUS)) good++;
        }
        if (good < 22) return false;
        ringPlane = plane;
        ringCenter = c;
        ringStartAngle = random.nextDouble() * Math.PI * 2;
        ringStrokes.clear();
        startAnim(Anim.INSCRIBE, INSCRIBE_TICKS);
        sl.playSound(null, getX(), getY(), getZ(), SoundEvents.EVOKER_PREPARE_ATTACK, SoundSource.HOSTILE, 1.5f, 0.6f);
        return true;
    }

    private void greatRing(ServerLevel sl) {
        if (ringPlane == null) return;
        // the ring goes down piece by piece, the brush sweeping round, a gap left where it will close
        for (int k = 0; k < RING_PIECES; k++) {
            if (animTick == RING_START + k * RING_STEP) inkArc(sl, k);
        }
        // fire wells up along the line, more and more as it nears closing
        if (animTick > RING_START && animTick % 3 == 0) {
            double done = Math.min(1, (animTick - RING_START) / (double) (RING_PIECES * RING_STEP));
            int n = 4 + (int) (animTick / 8);
            for (int i = 0; i < n; i++) {
                double a = ringStartAngle + random.nextDouble() * Math.PI * 2 * done;
                Vec3 w = ringPlane.toWorld(ringCenter.x() + Math.cos(a) * RING_RADIUS, ringCenter.y() + Math.sin(a) * RING_RADIUS, 0.05);
                sl.sendParticles(animTick > RING_CLOSE - 20 ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME, w.x, w.y, w.z, 1, 0.05, 0.05, 0.05, 0.01);
            }
        }
        if (animTick == RING_CLOSE - 16) sl.playSound(null, getX(), getY(), getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 1.2f, 0.5f);
        if (animTick == RING_CLOSE) closeGreatRing(sl);
    }

    private void inkArc(ServerLevel sl, int k) {
        // the last piece runs on past the start: that is the closing
        double span = Math.PI * 2 / RING_PIECES;
        double a0 = ringStartAngle + k * span - (k > 0 ? 0.05 : 0), a1 = ringStartAngle + (k + 1) * span + (k == RING_PIECES - 1 ? 0.12 : 0);
        if (k == RING_PIECES - 1) a1 -= 0.6; // the gap, closed only at the end
        int n = (int) Math.ceil((a1 - a0) * RING_RADIUS / 0.06);
        double[] uv = new double[(n + 1) * 2];
        for (int i = 0; i <= n; i++) {
            double a = a0 + (a1 - a0) * i / n;
            double r = RING_RADIUS * (1 + 0.012 * Math.sin(a * 5));
            uv[i * 2] = ringCenter.x() + Math.cos(a) * r;
            uv[i * 2 + 1] = ringCenter.y() + Math.sin(a) * r;
        }
        for (var s : GlyphService.inkWithoutReading(sl, ringPlane, List.of(uv), InkType.BLOOD, getUUID())) ringStrokes.add(s.id());
        Vec3 w = ringPlane.toWorld(uv[uv.length - 2], uv[uv.length - 1], 0.1);
        sl.playSound(null, w.x, w.y, w.z, ModSounds.QUILL_SCRATCH.get(), SoundSource.HOSTILE, 1.2f, 0.6f);
    }

    /** The ring closes: if none of it was broken, everything inside burns; if it was, the spell turns on the Elder. */
    private void closeGreatRing(ServerLevel sl) {
        GlyphLevelData data = GlyphLevelData.get(sl);
        boolean whole = ringStrokes.size() == RING_PIECES && ringStrokes.stream().allMatch(id -> data.get(id) != null);
        Vec3 c = ringPlane.toWorld(ringCenter, 0);
        if (whole) {
            sl.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 2f, 0.7f);
            sl.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y + 0.5, c.z, 1, 0, 0, 0, 0);
            for (int k = 0; k < 120; k++) {
                double a = random.nextDouble() * Math.PI * 2, d = Math.sqrt(random.nextDouble()) * RING_RADIUS;
                sl.sendParticles(ParticleTypes.FLAME, c.x + Math.cos(a) * d, c.y + 0.2, c.z + Math.sin(a) * d, 0, 0, 0.6 + random.nextDouble() * 0.5, 0, 0.6);
            }
            for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(RING_RADIUS, 3, RING_RADIUS),
                    e -> e != this && !(e instanceof BrimhatEntity) && Math.hypot(e.getX() - c.x, e.getZ() - c.z) < RING_RADIUS)) {
                e.hurt(damageSources().indirectMagic(this, this), 14);
                e.igniteForSeconds(4);
                e.push(0, 0.9, 0);
                e.hurtMarked = true;
            }
        } else {
            // broken: the spell finds no way out but back up the brush
            sl.playSound(null, getX(), getY(), getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.HOSTILE, 2f, 0.5f);
            sl.playSound(null, getX(), getY(), getZ(), SoundEvents.WITHER_HURT, SoundSource.HOSTILE, 1.5f, 0.5f);
            sl.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 2, getZ(), 40, 0.6, 1, 0.6, 0.03);
            hurt(damageSources().magic(), 10);
            startAnim(Anim.STAGGER, STAGGER_TICKS);
        }
        GlyphService.eraseStrokes(sl, ringStrokes);
        ringStrokes.clear();
        ringPlane = null;
    }

    // ------------------------------------------------------------------ taking hits

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (anim() == Anim.PHASE && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            if (level() instanceof ServerLevel sl) sl.sendParticles(ParticleTypes.ENCHANTED_HIT, getX(), getY() + 2, getZ(), 10, 0.5, 0.8, 0.5, 0.1);
            return false;
        }
        if (anim() == Anim.STAGGER) amount *= 1.5f; // reeling, it cannot guard
        float before = getHealth() / getMaxHealth();
        boolean hit = super.hurt(source, amount);
        if (!hit || !(level() instanceof ServerLevel sl)) return hit;
        recentDamage += amount;
        recentDamageAge = 60;
        float after = getHealth() / getMaxHealth();
        int stage = after > 0.66f ? 1 : after > 0.33f ? 2 : 3;
        if (stage > stage() && isAlive()) {
            entityData.set(STAGE, stage);
            // the next stage: whatever it was doing, it breaks off, rises and roars
            if (ringPlane != null) { GlyphService.eraseStrokes(sl, ringStrokes); ringStrokes.clear(); ringPlane = null; }
            startAnim(Anim.PHASE, PHASE_TICKS);
        } else if (stage() >= 3 && recentDamage > 24 && !busy()) {
            recentDamage = 0;
            ward(sl);
        }
        return hit;
    }

    // ------------------------------------------------------------------ dying

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (!level().isClientSide) {
            if (ringPlane != null && level() instanceof ServerLevel sl) { GlyphService.eraseStrokes(sl, ringStrokes); ringStrokes.clear(); ringPlane = null; }
            startAnim(Anim.DYING, DEATH_TICKS);
            playSound(SoundEvents.WITHER_DEATH, 1.2f, 0.6f);
        }
    }

    /** It takes its time dying: sinks, its pages fall, its hat comes off, then it comes apart into ink. */
    @Override
    protected void tickDeath() {
        deathTime++;
        if (level() instanceof ServerLevel sl) {
            if (deathTime % 3 == 0) sl.sendParticles(ParticleTypes.SQUID_INK, getX(), getY() + 1 + random.nextDouble() * 2, getZ(), 4, 0.5, 0.5, 0.5, 0.02);
            if (deathTime == DEATH_TICKS - 10) sl.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1.5, getZ(), 60, 0.6, 1, 0.6, 0.05);
            if (deathTime >= DEATH_TICKS && !isRemoved()) {
                sl.broadcastEntityEvent(this, (byte) 60);
                remove(RemovalReason.KILLED);
            }
        }
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        spawnAtLocation(new ItemStack(ModItems.BRIMHAT_MASK.get()));
        spawnAtLocation(com.witchcraft.glyph.page.GlyphLibrary.forbiddenPageItem(random));
        spawnAtLocation(com.witchcraft.glyph.page.GlyphLibrary.grimoireBook(random, 6, "petrification", "crystal_ward", "binding_threads"));
        spawnAtLocation(new ItemStack(ModItems.BLOOD_INK.get(), 2));
    }

    // ------------------------------------------------------------------ presence

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide && isAlive()) {
            // embers rise off it, more as the fight goes on; ink drips from the ragged hem
            if (random.nextInt(4 - Math.min(3, stage())) == 0) {
                level().addParticle(stage() >= 3 ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.SMALL_FLAME, getX() + (random.nextDouble() - 0.5) * 1.4,
                        getY() + 0.6 + random.nextDouble() * 2.6, getZ() + (random.nextDouble() - 0.5) * 1.4, 0, 0.02, 0);
            }
            if (random.nextInt(5) == 0) level().addParticle(new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(0.35f, 0.03f, 0.06f), 1.2f),
                    getX() + (random.nextDouble() - 0.5) * 1.3, getY() + 0.3 + random.nextDouble() * 0.4, getZ() + (random.nextDouble() - 0.5) * 1.3, 0, -0.05, 0);
        }
    }

    /** It floats: no footsteps, no fall. */
    @Override
    protected void playStepSound(BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        if (random.nextInt(3) == 0) playSound(ModSounds.CLOTH_RUSTLE.get(), 0.5f, 0.6f);
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public float getVoicePitch() {
        return 0.5f;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Awake", awake);
        tag.putInt("Stage", stage());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        awake = tag.getBoolean("Awake");
        if (tag.contains("Stage")) entityData.set(STAGE, Math.max(1, Math.min(3, tag.getInt("Stage"))));
    }

    /** For tests and screenshots: play a move as if chosen. */
    public void debugAnim(Anim a) {
        awake = true;
        startAnim(a, switch (a) {
            case DRAW_AIR -> DRAW_AIR_TICKS;
            case INSCRIBE -> INSCRIBE_TICKS;
            case SUMMON -> SUMMON_TICKS;
            case PHASE -> PHASE_TICKS;
            case STAGGER -> STAGGER_TICKS;
            case DYING -> DEATH_TICKS;
            default -> 0;
        });
    }

    /** For screenshots: the great ring part drawn round {@code at}, without the fight running. */
    public void debugRing(ServerLevel sl, Vec3 at, int pieces) {
        BlockPos floor = BlockPos.containing(at).below();
        ringPlane = SurfacePlane.of(floor, Direction.UP);
        ringCenter = ringPlane.toUV(new Vec3(at.x, floor.getY() + 1, at.z));
        ringStartAngle = 0.3;
        for (int k = 0; k < pieces; k++) inkArc(sl, k);
    }

    public void debugStage(int stage) {
        entityData.set(STAGE, stage);
    }

    /**
     * Keeps its distance and floats round its target; between moves it chooses what to do by how the fight stands.
     * While a move plays out it stands still and faces its target.
     */
    static class FightGoal extends Goal {
        private final BrimhatElderEntity elder;
        private int strafe;

        FightGoal(BrimhatElderEntity elder) {
            this.elder = elder;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity t = elder.getTarget();
            return t != null && t.isAlive();
        }

        @Override
        public void tick() {
            LivingEntity t = elder.getTarget();
            if (t == null || !(elder.level() instanceof ServerLevel sl)) return;
            elder.getLookControl().setLookAt(t, 30, 30);
            if (elder.busy()) {
                elder.getNavigation().stop();
                return;
            }
            double d = elder.distanceTo(t);
            boolean sees = elder.getSensing().hasLineOfSight(t);
            if (d > 13 || !sees) elder.getNavigation().moveTo(t, 1.0);
            else if (d < 6) {
                Vec3 away = elder.position().subtract(t.position()).normalize().scale(6);
                elder.getNavigation().moveTo(elder.getX() + away.x, elder.getY(), elder.getZ() + away.z, 1.1);
            } else {
                elder.getNavigation().stop();
                if (--strafe <= 0) strafe = 30 + elder.random.nextInt(30);
                elder.getMoveControl().strafe(0, strafe > 30 ? 0.35f : -0.35f);
            }
            // pressed close, it steps away through shadow
            if (d < 4 && --elder.stepCooldown <= 0) {
                elder.cast(Spell.STEP, t);
                elder.stepCooldown = 80;
            }
            if (--elder.attackCooldown > 0 || !sees || d > 24) return;
            int stage = elder.stage();
            int roll = elder.random.nextInt(10);
            if (stage >= 2 && roll < 3 && elder.beginGreatRing(sl, t)) {
                // drawing the great ring
            } else if (roll < (stage == 1 ? 7 : 6)) {
                elder.startAnim(Anim.DRAW_AIR, DRAW_AIR_TICKS);
            } else {
                elder.startAnim(Anim.SUMMON, SUMMON_TICKS);
            }
            elder.attackCooldown = (stage == 3 ? 22 : stage == 2 ? 34 : 46) + elder.random.nextInt(20);
        }
    }

    /** True while it is breaking off whatever it does to roar into a new stage (it takes no harm then). */
    public boolean roaring() {
        return anim() == Anim.PHASE;
    }

    @Override
    public boolean isPushable() {
        return !busy();
    }

    @Override
    protected void doPush(Entity e) {
        if (!busy()) super.doPush(e);
    }
}
