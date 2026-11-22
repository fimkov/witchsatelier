package com.fimkov.witchsatelier.entity;

import com.fimkov.witchsatelier.registry.ModItems;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A Sealchair awake: the chair block got up when someone sat in it and now walks wherever its rider looks, steady
 * on rough ground and up a step. It exists only while carrying someone; let it go and it settles back into a block.
 */
public class SealchairEntity extends PathfinderMob {
    public SealchairEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder attributes() {
        return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, 20).add(Attributes.MOVEMENT_SPEED, 0.28)
                .add(Attributes.STEP_HEIGHT, 1.1).add(Attributes.KNOCKBACK_RESISTANCE, 0.8);
    }

    /** Not a creature to be struck or led about: you only ever see it walking with someone in it. */
    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.hurt(source, amount);
        return false;
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    /** You get up beside it, not on top of it: it is about to be a chair again where it stands. */
    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        Vec3 fwd = Vec3.directionFromRotation(0, getYRot());
        Vec3 side = new Vec3(-fwd.z, 0, fwd.x);
        for (Vec3 off : new Vec3[]{fwd, side, side.reverse(), fwd.reverse()}) {
            Vec3 at = position().add(off.scale(1.0));
            net.minecraft.core.BlockPos bp = net.minecraft.core.BlockPos.containing(at);
            for (int dy = 0; dy <= 1; dy++) {
                Vec3 spot = net.minecraft.world.entity.vehicle.DismountHelper.findSafeDismountLocation(passenger.getType(), level(), bp.above(dy), false);
                if (spot != null) return spot;
            }
        }
        return super.getDismountLocationForPassenger(passenger);
    }

    /** With nobody in it, it settles down where it is and is a chair again (or, with no room, folds up). */
    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && getPassengers().isEmpty() && tickCount > 2 && isAlive()) {
            net.minecraft.core.BlockPos at = net.minecraft.core.BlockPos.containing(getX(), getY() + 0.2, getZ());
            if (!com.fimkov.witchsatelier.block.SealchairBlock.settle(level(), at, getYRot())) spawnAtLocation(new ItemStack(ModItems.SEALCHAIR.get()));
            if (level() instanceof net.minecraft.server.level.ServerLevel sl)
                sl.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD, getX(), getY() + 0.3, getZ(), 6, 0.3, 0.05, 0.3, 0.01);
            discard();
        }
    }

    @Override
    public LivingEntity getControllingPassenger() {
        return getFirstPassenger() instanceof Player p ? p : null;
    }

    @Override
    protected void tickRidden(Player rider, Vec3 input) {
        super.tickRidden(rider, input);
        setRot(rider.getYRot(), rider.getXRot() * 0.5f);
        yRotO = yBodyRot = yHeadRot = getYRot();
    }

    @Override
    protected Vec3 getRiddenInput(Player rider, Vec3 input) {
        float forward = rider.zza <= 0 ? rider.zza * 0.35f : rider.zza;
        return new Vec3(rider.xxa * 0.5f, 0, forward);
    }

    @Override
    protected float getRiddenSpeed(Player rider) {
        return (float) getAttributeValue(Attributes.MOVEMENT_SPEED) * (rider.isSprinting() ? 1.5f : 1f);
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(net.minecraft.world.entity.Entity passenger, net.minecraft.world.entity.EntityDimensions dims, float scale) {
        return new Vec3(0, dims.height() * 0.62, 0);
    }

    @Override
    public boolean removeWhenFarAway(double d) {
        return false;
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.WOOD_HIT;
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getDeathSound() {
        return SoundEvents.WOOD_BREAK;
    }

    @Override
    protected void playStepSound(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        playSound(SoundEvents.WOOD_STEP, 0.4f, 1.4f);
    }
}
