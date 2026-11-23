package com.fimkov.witchsatelier.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

public class WaterHorseEntity extends AbstractHorse {
    public WaterHorseEntity(EntityType<? extends AbstractHorse> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder attributes() {
        return createBaseHorseAttributes().add(Attributes.MAX_HEALTH, 20).add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.JUMP_STRENGTH, 0.8).add(Attributes.STEP_HEIGHT, 1.0);
    }

    public void conjure(Player owner) {
        setTamed(true);
        if (owner != null) setOwnerUUID(owner.getUUID());
        equipSaddle(new ItemStack(Items.SADDLE), null);
    }

    public void splash() {
        if (level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.SPLASH, getX(), getY() + 0.8, getZ(), 40, 0.6, 0.5, 0.6, 0.2);
            sl.sendParticles(ParticleTypes.FALLING_WATER, getX(), getY() + 1.0, getZ(), 30, 0.6, 0.4, 0.6, 0);
            sl.playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_SPLASH, SoundSource.NEUTRAL, 1f, 0.8f);
        }
        discard();
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel sl)) return;
        if (tickCount % 3 == 0) sl.sendParticles(ParticleTypes.DRIPPING_WATER, getX(), getY() + 0.9, getZ(), 2, 0.4, 0.4, 0.4, 0);
        clearFire();
        var feet = blockPosition();
        if (sl.getBlockState(feet).is(net.minecraft.tags.BlockTags.FIRE)) sl.removeBlock(feet, false);
        if (isInLava() || sl.getBlockState(feet).is(Blocks.MAGMA_BLOCK)) {
            sl.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 0.5, getZ(), 6, 0.4, 0.3, 0.4, 0.02);
            if (tickCount % 10 == 0) hurt(damageSources().onFire(), 2f);
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypes.DROWN) || source.is(DamageTypes.FALL)) return false;
        if (source.is(net.minecraft.tags.DamageTypeTags.IS_FIRE)) amount *= 2;
        return super.hurt(source, amount);
    }

    @Override
    public boolean canMate(Animal other) {
        return false;
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    protected void dropEquipment() {
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean hit) {
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return false;
    }

    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob other) {
        return null;
    }

    @Override
    protected boolean shouldDropLoot() {
        return false;
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel sl) sl.sendParticles(ParticleTypes.SPLASH, getX(), getY() + 0.8, getZ(), 40, 0.6, 0.5, 0.6, 0.2);
    }
}
