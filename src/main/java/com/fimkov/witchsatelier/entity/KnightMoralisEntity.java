package com.fimkov.witchsatelier.entity;

import com.fimkov.witchsatelier.glyph.GlyphService;
import com.fimkov.witchsatelier.glyph.model.Sigil;
import com.fimkov.witchsatelier.glyph.surface.SurfacePlane;
import com.fimkov.witchsatelier.network.Net;
import com.fimkov.witchsatelier.network.Payloads;
import com.fimkov.witchsatelier.registry.ModItems;
import com.fimkov.witchsatelier.registry.ModSounds;
import com.fimkov.witchsatelier.spell.SpellProgram.Form;
import com.fimkov.witchsatelier.spell.SpellProgram;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.UUID;

public class KnightMoralisEntity extends PathfinderMob {
    private int bindCooldown = 40;

    private UUID quarry;
    private long huntId;

    private int leaving;

    private int stuck;
    private double lastGap = Double.MAX_VALUE;

    public KnightMoralisEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        xpReward = 8;
    }

    public static AttributeSupplier.Builder attributes() {
        return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, 44).add(Attributes.MOVEMENT_SPEED, 0.32)
                .add(Attributes.FOLLOW_RANGE, 40).add(Attributes.ARMOR, 10).add(Attributes.ATTACK_DAMAGE, 7)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.5);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new TalonsGoal(this));
        goalSelector.addGoal(1, new BindGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.1, true));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.7));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 10));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this) {
            @Override
            public boolean canUse() {
                return leaving == 0 && super.canUse();
            }
        });
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, BrimhatEntity.class, true));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false,
                p -> leaving == 0 && p instanceof Player pl && (Taboo.of(pl) >= Taboo.HUNTED || BrimhatEntity.wearsMask(pl))));
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType type, SpawnGroupData data) {
        setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        setDropChance(EquipmentSlot.MAINHAND, 0.02f);

        setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(ModItems.CAPTURE_PENNANT.get()));
        setDropChance(EquipmentSlot.OFFHAND, 0f);
        return super.finalizeSpawn(level, difficulty, type, data);
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        if (recentlyHit && random.nextFloat() < 0.06f) spawnAtLocation(new ItemStack(ModItems.CAPTURE_PENNANT.get()));
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return quarry == null && distance > 128 * 128;
    }

    public void hunt(Player p, long hunt) {
        quarry = p.getUUID();
        huntId = hunt;
        setTarget(p);
        setPersistenceRequired();
    }

    public long huntId() {
        return leaving > 0 ? 0 : huntId;
    }

    public boolean isHunting(Player p) {
        return quarry != null && quarry.equals(p.getUUID());
    }

    public void leave() {
        if (leaving > 0) return;
        leaving = 80;
        quarry = null;
        setTarget(null);
        setLastHurtByMob(null);
        setAggressive(false);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (quarry != null) {
            tag.putUUID("Quarry", quarry);
            tag.putLong("Hunt", huntId);
        }
        if (leaving > 0) tag.putInt("Leaving", leaving);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        quarry = tag.hasUUID("Quarry") ? tag.getUUID("Quarry") : null;
        huntId = tag.getLong("Hunt");
        leaving = tag.getInt("Leaving");
        blackPennant(false);
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (!(level() instanceof ServerLevel sl)) return;
        if (talonsCooldown > 0) talonsCooldown--;
        if (leaving > 0) {
            setTarget(null);
            if (--leaving <= 0) vanish(sl);
            else if (leaving % 20 == 0 && getNavigation().isDone()) {
                Vec3 away = DefaultRandomPos.getPos(this, 12, 4);
                if (away != null) getNavigation().moveTo(away.x, away.y, away.z, 0.8);
            }
            return;
        }
        if (quarry == null || tickCount % 10 != 0) return;
        Player p = sl.getPlayerByUUID(quarry);

        if (p == null || !p.isAlive() || Taboo.huntOf(p) != huntId) {
            leave();
            return;
        }
        double gap = distanceTo(p);
        if (gap > 96) {
            vanish(sl);
            return;
        }
        if (!p.isCreative() && !p.isSpectator() && getTarget() != p) setTarget(p);

        if (gap > 4 && gap > lastGap - 0.5) stuck += 10;
        else stuck = 0;
        lastGap = gap;
        if (stuck >= 100) {
            stuck = 0;
            lastGap = Double.MAX_VALUE;
            stepTo(sl, p);
        }
    }

    private void vanish(ServerLevel sl) {
        sl.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 1, getZ(), 30, 0.3, 0.8, 0.3, 0.05);
        sl.playSound(null, getX(), getY(), getZ(), SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.HOSTILE, 0.8f, 1.2f);
        discard();
    }

    static void arrive(ServerLevel sl, Vec3 at) {
        sl.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 1, at.z, 30, 0.3, 0.8, 0.3, 0.05);
        sl.playSound(null, at.x, at.y, at.z, SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.HOSTILE, 0.8f, 0.8f);
    }

    private void stepTo(ServerLevel sl, Player p) {
        for (int i = 0; i < 16; i++) {
            double a = random.nextDouble() * Math.PI * 2, d = 2 + random.nextDouble() * 2;
            BlockPos at = BlockPos.containing(p.getX() + Math.cos(a) * d, p.getY() + random.nextInt(3) - 1, p.getZ() + Math.sin(a) * d);
            if (!standable(sl, at)) continue;
            arrive(sl, position());
            moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, getYRot(), getXRot());
            getNavigation().stop();
            arrive(sl, position());
            return;
        }
    }

    static boolean standable(ServerLevel sl, BlockPos at) {
        return sl.getBlockState(at.below()).isFaceSturdy(sl, at.below(), Direction.UP)
                && sl.getBlockState(at).getCollisionShape(sl, at).isEmpty() && sl.getBlockState(at.above()).getCollisionShape(sl, at.above()).isEmpty()
                && sl.getFluidState(at).isEmpty();
    }

    public static final byte BIND_EVENT = 60;

    public int bindPose;

    @Override
    public void handleEntityEvent(byte id) {
        if (id == BIND_EVENT) bindPose = 24;
        else super.handleEntityEvent(id);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide && bindPose > 0) bindPose--;
    }

    static class BindGoal extends Goal {
        private final KnightMoralisEntity knight;

        BindGoal(KnightMoralisEntity knight) {
            this.knight = knight;
            setFlags(EnumSet.noneOf(Flag.class));
        }

        @Override
        public boolean canUse() {
            return knight.getTarget() != null && --knight.bindCooldown <= 0 && knight.distanceTo(knight.getTarget()) < 16
                    && knight.getSensing().hasLineOfSight(knight.getTarget());
        }

        @Override
        public void start() {
            LivingEntity t = knight.getTarget();
            if (t == null || !(knight.level() instanceof ServerLevel sl)) return;
            BlockPos below = t.blockPosition().below();
            Vec3 o = new Vec3(t.getX(), below.getY() + 1.02, t.getZ());
            SpellProgram p = new SpellProgram(Sigil.LIGHT, null, Form.MANIFEST, false, o, new Vec3(0, 1, 0), new Vec3(0, 1, 0),
                    1.4, 3, 60, false, true, false, 0, 0, 0, false, 0).withMods(SpellProgram.BIND);
            GlyphService.startSpell(sl, SurfacePlane.of(below, Direction.UP), p, knight.getUUID(), false);
            sl.playSound(null, t.getX(), t.getY(), t.getZ(), ModSounds.KNIGHT_BIND.get(), SoundSource.HOSTILE, 1f, 1f);
            knight.bindCooldown = 160 + knight.random.nextInt(80);
            sl.broadcastEntityEvent(knight, BIND_EVENT);
            Net.toNear(sl, knight.position(), 96, new Payloads.PennantFx(knight.getId(), t.getId(),
                    (float) t.getX(), (float) t.getY(), (float) t.getZ(), 60));
        }

        @Override
        public boolean canContinueToUse() {
            return false;
        }
    }

    private int talonsCooldown;

    /** The black of the pennant when it carries Sinsinger's Talons. */
    public static final int TALONS_BLACK = 0x1D1D21;

    /** Whether this knight may take the player alive: its own quarry, held by the binding light, and worn down. */
    public boolean mayTakeAlive(Player p) {
        if (leaving > 0 || quarry == null || !isHunting(p) || Taboo.huntOf(p) != huntId || p.isCreative() || p.isSpectator() || !p.isAlive()) return false;
        MobEffectInstance held = p.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
        return held != null && held.getAmplifier() >= 4 && p.getHealth() <= p.getMaxHealth() * 0.5f;
    }

    private void blackPennant(boolean black) {
        ItemStack pennant = getOffhandItem();
        if (!pennant.is(ModItems.CAPTURE_PENNANT.get())) return;
        if (black) pennant.set(DataComponents.DYED_COLOR, new DyedItemColor(TALONS_BLACK, false));
        else pennant.remove(DataComponents.DYED_COLOR);
    }

    /**
     * Sinsinger's Talons: the seal on the black pennant takes hold of the head and erases. The hunt is over and most
     * of the debt with it; the player wakes where they stood, blind for a moment, slowed and dizzy, with everything
     * they carried.
     */
    public static void takeAlive(ServerLevel sl, KnightMoralisEntity knight, ServerPlayer p) {
        Net.toNear(sl, knight.position(), 96, new Payloads.PennantFx(knight.getId(), p.getId(),
                (float) p.getX(), (float) p.getEyeY(), (float) p.getZ(), 40));
        Taboo.settle(p, Math.min(Taboo.of(p) / 4, Taboo.HUNTED / 2));
        p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0));
        p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 160, 0));
        p.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 200, 0));
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200, 1));
        double eye = p.getEyeY();
        sl.sendParticles(ParticleTypes.SQUID_INK, p.getX(), eye, p.getZ(), 30, 0.3, 0.3, 0.3, 0.05);
        for (int i = 0; i < 16; i++) {
            double a = i * Math.PI / 8;
            sl.sendParticles(ParticleTypes.END_ROD, p.getX() + Math.cos(a) * 0.6, eye, p.getZ() + Math.sin(a) * 0.6, 0,
                    -Math.cos(a), 0, -Math.sin(a), 0.04);
        }
        sl.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ILLUSIONER_PREPARE_BLINDNESS, SoundSource.HOSTILE, 1.2f, 0.6f);
        sl.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BELL_RESONATE, SoundSource.HOSTILE, 1f, 0.5f);
    }

    static class TalonsGoal extends Goal {
        static final int WIND_UP = 20, COOLDOWN = 100;
        static final double REACH = 4.5, LUNGE = 5.5;

        private final KnightMoralisEntity knight;
        private ServerPlayer quarry;
        private int windUp;

        TalonsGoal(KnightMoralisEntity knight) {
            this.knight = knight;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return knight.talonsCooldown <= 0 && knight.getTarget() instanceof ServerPlayer p && knight.mayTakeAlive(p)
                    && knight.distanceTo(p) < REACH && knight.getSensing().hasLineOfSight(p);
        }

        @Override
        public void start() {
            quarry = (ServerPlayer) knight.getTarget();
            windUp = WIND_UP;
            knight.blackPennant(true);
            knight.getNavigation().stop();
            knight.level().broadcastEntityEvent(knight, BIND_EVENT);
            knight.playSound(ModSounds.KNIGHT_BIND.get(), 1f, 0.5f);
            knight.playSound(SoundEvents.SCULK_CLICKING, 1f, 0.6f);
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            if (quarry == null || !(knight.level() instanceof ServerLevel sl)) return;
            knight.getLookControl().setLookAt(quarry, 30, 30);
            if (windUp % 4 == 0) {
                Vec3 hand = knight.position().add(knight.getLookAngle().scale(0.4)).add(0, knight.getBbHeight() + 0.3, 0);
                sl.sendParticles(ParticleTypes.SQUID_INK, hand.x, hand.y, hand.z, 3, 0.1, 0.25, 0.1, 0.01);
            }
            if (--windUp > 0) return;
            if (knight.mayTakeAlive(quarry) && knight.distanceTo(quarry) <= LUNGE && knight.getSensing().hasLineOfSight(quarry)) {
                takeAlive(sl, knight, quarry);
            } else {
                knight.talonsCooldown = COOLDOWN;
            }
        }

        @Override
        public boolean canContinueToUse() {
            return windUp > 0 && quarry != null && quarry.isAlive() && knight.leaving == 0;
        }

        @Override
        public void stop() {
            knight.blackPennant(false);
            quarry = null;
            windUp = 0;
        }
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        super.playStepSound(pos, state);
        if (random.nextInt(2) == 0) playSound(ModSounds.CLOTH_RUSTLE.get(), 0.35f, 0.85f + random.nextFloat() * 0.3f);
    }
}
