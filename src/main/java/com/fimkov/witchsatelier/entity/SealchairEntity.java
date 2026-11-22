package com.fimkov.witchsatelier.entity;

import com.fimkov.witchsatelier.block.SealchairBlock;
import com.fimkov.witchsatelier.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class SealchairEntity extends PathfinderMob {
    public SealchairEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder attributes() {
        return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, 20).add(Attributes.MOVEMENT_SPEED, 0.28)
                .add(Attributes.STEP_HEIGHT, 1.1).add(Attributes.KNOCKBACK_RESISTANCE, 0.8);
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.hurt(source, amount);
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

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        Vec3 fwd = Vec3.directionFromRotation(0, getYRot());
        Vec3 side = new Vec3(-fwd.z, 0, fwd.x);
        for (Vec3 off : new Vec3[]{fwd, side, side.reverse(), fwd.reverse()}) {
            Vec3 at = position().add(off.scale(1.0));
            BlockPos bp = BlockPos.containing(at);
            for (int dy = 0; dy <= 1; dy++) {
                Vec3 spot = DismountHelper.findSafeDismountLocation(passenger.getType(), level(), bp.above(dy), false);
                if (spot != null) return spot;
            }
        }
        return super.getDismountLocationForPassenger(passenger);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && getPassengers().isEmpty() && tickCount > 2 && isAlive()) {
            BlockPos at = BlockPos.containing(getX(), getY() + 0.2, getZ());
            if (!SealchairBlock.settle(level(), at, getYRot())) spawnAtLocation(new ItemStack(ModItems.SEALCHAIR.get()));
            if (level() instanceof ServerLevel sl)
                sl.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 0.3, getZ(), 6, 0.3, 0.05, 0.3, 0.01);
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
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dims, float scale) {
        return new Vec3(0, dims.height() * 0.62, 0);
    }

    @Override
    public boolean removeWhenFarAway(double d) {
        return false;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.WOOD_HIT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.WOOD_BREAK;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(SoundEvents.WOOD_STEP, 0.4f, 1.4f);
    }
}
