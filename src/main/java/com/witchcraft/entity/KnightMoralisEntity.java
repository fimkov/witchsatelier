package com.witchcraft.entity;

import com.witchcraft.glyph.GlyphService;
import com.witchcraft.glyph.model.Sigil;
import com.witchcraft.glyph.surface.SurfacePlane;
import com.witchcraft.spell.SpellProgram;
import com.witchcraft.spell.SpellProgram.Form;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.DifficultyInstance;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * A knight of the Knights Moralis, the order that enforces the Pact. Silver-white, unhurried, polite to the
 * innocent. They hunt Brimhats, and anyone who has broken the Pact (see {@link Taboo}), or wears a Brimhat
 * mask: first a ring of binding light under the culprit's feet, then the knight's blade.
 */
public class KnightMoralisEntity extends PathfinderMob {
    private int bindCooldown = 40;

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
        goalSelector.addGoal(1, new BindGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.1, true));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.7));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 10));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, BrimhatEntity.class, true));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false,
                p -> p instanceof Player pl && (Taboo.of(pl) >= Taboo.HUNTED || BrimhatEntity.wearsMask(pl))));
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType type, SpawnGroupData data) {
        setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        setDropChance(EquipmentSlot.MAINHAND, 0.02f);
        // the pennant in the off hand (its own rare drop is below)
        setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(com.witchcraft.registry.ModItems.CAPTURE_PENNANT.get()));
        setDropChance(EquipmentSlot.OFFHAND, 0f);
        return super.finalizeSpawn(level, difficulty, type, data);
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, net.minecraft.world.damagesource.DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        if (recentlyHit && random.nextFloat() < 0.06f) spawnAtLocation(new ItemStack(com.witchcraft.registry.ModItems.CAPTURE_PENNANT.get()));
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return distance > 128 * 128;
    }

    /** Before closing in, the knight pins the culprit with a ring of binding light. */
    /** Entity event: the knight just cast its binding. */
    public static final byte BIND_EVENT = 60;
    /** Client only: ticks left of the binding pose. */
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
            sl.playSound(null, t.getX(), t.getY(), t.getZ(), com.witchcraft.registry.ModSounds.KNIGHT_BIND.get(), SoundSource.HOSTILE, 1f, 1f);
            knight.bindCooldown = 160 + knight.random.nextInt(80);
            sl.broadcastEntityEvent(knight, BIND_EVENT); // the client shows the knight levelling the pennant
            com.witchcraft.network.Net.toNear(sl, knight.position(), 96, new com.witchcraft.network.Payloads.PennantFx(knight.getId(), t.getId(),
                    (float) t.getX(), (float) t.getY(), (float) t.getZ(), 60));
        }

        @Override
        public boolean canContinueToUse() {
            return false;
        }
    }

    /** Steps come with the swish of the long robe. */
    @Override
    protected void playStepSound(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        super.playStepSound(pos, state);
        if (random.nextInt(2) == 0) playSound(com.witchcraft.registry.ModSounds.CLOTH_RUSTLE.get(), 0.35f, 0.85f + random.nextFloat() * 0.3f);
    }
}
