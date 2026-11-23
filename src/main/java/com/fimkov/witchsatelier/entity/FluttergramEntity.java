package com.fimkov.witchsatelier.entity;

import com.fimkov.witchsatelier.registry.ModEntities;
import com.fimkov.witchsatelier.registry.ModItems;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Only the look of a fluttergram in flight: it flutters up and away from the sender, or flies in to land on the addressee.
 * The letter itself never rides on it (see the mailroom), so nothing is lost if it is unloaded or shot down.
 */
public class FluttergramEntity extends Entity implements ItemSupplier {
    private static final EntityDataAccessor<Integer> TARGET = SynchedEntityData.defineId(FluttergramEntity.class, EntityDataSerializers.INT);
    private static ItemStack look;

    public FluttergramEntity(EntityType<? extends FluttergramEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public static void depart(ServerLevel level, Player from, Vec3 toward) {
        FluttergramEntity e = new FluttergramEntity(ModEntities.FLUTTERGRAM.get(), level);
        Vec3 look = from.getLookAngle();
        Vec3 dir = toward != null ? toward.subtract(from.position()) : look;
        dir = new Vec3(dir.x, 0, dir.z);
        dir = dir.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : dir.normalize();
        e.moveTo(from.getEyePosition().add(look.scale(0.6)).subtract(0, 0.2, 0));
        e.setDeltaMovement(dir.scale(0.12).add(0, 0.1, 0));
        level.addFreshEntity(e);
    }

    public static void arrive(ServerLevel level, ServerPlayer to) {
        FluttergramEntity e = new FluttergramEntity(ModEntities.FLUTTERGRAM.get(), level);
        double a = level.random.nextDouble() * Math.PI * 2;
        Vec3 start = to.position().add(Math.cos(a) * 8, 5, Math.sin(a) * 8);
        e.moveTo(start);
        e.entityData.set(TARGET, to.getId());
        e.setDeltaMovement(to.getEyePosition().subtract(start).normalize().scale(0.3));
        level.addFreshEntity(e);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        b.define(TARGET, -1);
    }

    @Override
    public void tick() {
        Vec3 v = getDeltaMovement();
        int id = entityData.get(TARGET);
        Entity target = id < 0 ? null : level().getEntity(id);
        if (target != null) {
            Vec3 d = target.position().add(0, target.getBbHeight() * 0.6, 0).subtract(position());
            if (d.length() < 0.7) {
                land();
                return;
            }
            v = v.lerp(d.normalize().scale(0.32), 0.18);
        } else {
            v = v.add(0, 0.012, 0).scale(1.035);
            if (v.length() > 0.7) v = v.normalize().scale(0.7);
        }
        setDeltaMovement(v);
        setPos(position().add(v));
        if (level().isClientSide && tickCount % 4 == 0)
            level().addParticle(ParticleTypes.END_ROD, getX(), getY() + 0.1, getZ(), 0, -0.02, 0);
        if (!level().isClientSide && tickCount > (target != null || id >= 0 ? 120 : 70)) land();
    }

    private void land() {
        if (level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.POOF, getX(), getY() + 0.1, getZ(), 4, 0.1, 0.1, 0.1, 0.01);
            discard();
        }
    }

    @Override
    public ItemStack getItem() {
        if (look == null) look = new ItemStack(ModItems.FLUTTERGRAM.get());
        return look;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}
}
