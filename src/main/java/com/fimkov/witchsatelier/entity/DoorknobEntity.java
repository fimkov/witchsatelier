package com.fimkov.witchsatelier.entity;

import com.fimkov.witchsatelier.item.artifact.DoorknobLinks;
import com.fimkov.witchsatelier.item.artifact.Doorknobs;
import com.fimkov.witchsatelier.registry.ModItems;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * Only the look of a doorknob on a door: the link itself lives in {@link DoorknobLinks}. It follows the door leaf as the door
 * swings, and goes away when its knob is taken off or the door is gone.
 */
public class DoorknobEntity extends Entity implements ItemSupplier {
    private static final EntityDataAccessor<BlockPos> DOOR = SynchedEntityData.defineId(DoorknobEntity.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Direction> SIDE = SynchedEntityData.defineId(DoorknobEntity.class, EntityDataSerializers.DIRECTION);
    private static ItemStack look;

    private UUID self = Util.NIL_UUID;

    public DoorknobEntity(EntityType<? extends DoorknobEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public void setup(UUID self, BlockPos door, Direction side) {
        this.self = self;
        entityData.set(DOOR, door);
        entityData.set(SIDE, side);
    }

    public UUID self() {
        return self;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        b.define(DOOR, BlockPos.ZERO);
        b.define(SIDE, Direction.NORTH);
    }

    @Override
    public void tick() {
        BlockPos door = entityData.get(DOOR);
        BlockState st = level().getBlockState(door);
        if (Doorknobs.isLowerDoor(st)) {
            Vec3 at = Doorknobs.knobPoint(st, door, entityData.get(SIDE));
            if (at.distanceToSqr(position()) > 1e-6) setPos(at);
        }
        if (level() instanceof ServerLevel sl && tickCount % 20 == 0) {
            var k = DoorknobLinks.get(sl.getServer()).bySelf(self);
            if (k == null || !k.door().pos().equals(door) || !k.door().dimension().equals(sl.dimension())) discard();
        }
    }

    @Override
    public ItemStack getItem() {
        if (look == null) look = new ItemStack(ModItems.DOORKNOB.get());
        return look;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public PushReaction getPistonPushReaction() {
        return PushReaction.IGNORE;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) {
        return d < 48 * 48;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("Knob")) self = tag.getUUID("Knob");
        entityData.set(DOOR, BlockPos.of(tag.getLong("Door")));
        Direction side = Direction.from3DDataValue(tag.getInt("Side"));
        entityData.set(SIDE, side.getAxis().isHorizontal() ? side : Direction.NORTH);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putUUID("Knob", self);
        tag.putLong("Door", entityData.get(DOOR).asLong());
        tag.putInt("Side", entityData.get(SIDE).get3DDataValue());
    }
}
