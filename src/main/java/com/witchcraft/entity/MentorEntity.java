package com.witchcraft.entity;

import com.witchcraft.glyph.Lessons;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Mistress Wren, who keeps an atelier in the meadows and teaches whoever knocks: a lesson at a time, a gift for
 * each one learnt. She stays near her cottage and never fights.
 */
public class MentorEntity extends PathfinderMob {
    public MentorEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder attributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 30).add(Attributes.MOVEMENT_SPEED, 0.22);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new LookAtPlayerGoal(this, Player.class, 8f, 0.6f));
        goalSelector.addGoal(3, new WaterAvoidingRandomStrollGoal(this, 0.5) {
            @Override
            public boolean canUse() {
                // she keeps to her atelier
                return super.canUse() && (!hasRestriction() || isWithinRestriction());
            }
        });
        goalSelector.addGoal(4, new RandomLookAroundGoal(this));
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (player instanceof ServerPlayer sp) {
            getLookControl().setLookAt(player);
            Lessons.talk(sp, this);
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override
    public void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (hasRestriction()) {
            tag.putLong("Home", getRestrictCenter().asLong());
            tag.putFloat("HomeRadius", getRestrictRadius());
        }
    }

    @Override
    public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Home")) restrictTo(net.minecraft.core.BlockPos.of(tag.getLong("Home")), (int) tag.getFloat("HomeRadius"));
    }

    @Override
    public boolean removeWhenFarAway(double dist) {
        return false;
    }

    @Override
    protected void playStepSound(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        super.playStepSound(pos, state);
        if (random.nextInt(2) == 0) playSound(com.witchcraft.registry.ModSounds.CLOTH_RUSTLE.get(), 0.35f, 0.9f + random.nextFloat() * 0.3f);
    }
}
