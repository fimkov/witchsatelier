package com.witchcraft.block.sealed;

import com.mojang.serialization.MapCodec;
import com.witchcraft.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Beast Warding Tower: a little brick tower with a Beast Repellent seal on hide behind its hatch. While the seal
 * holds, no hostile creature spawns near it, and those that wander close are driven back. The seal fades
 * after a few days and must be restored with a pen, the way witches tend the towers along the roads.
 */
public class BeastWardingTowerBlock extends SealedBlock {
    public static final MapCodec<BeastWardingTowerBlock> CODEC = simpleCodec(BeastWardingTowerBlock::new);
    public static final int RADIUS = 24;
    private static final VoxelShape SHAPE = box(3, 0, 3, 13, 16, 13);
    private static final Map<ResourceKey<Level>, Set<BlockPos>> ACTIVE = new HashMap<>();

    public BeastWardingTowerBlock(Properties props) {
        super(props);
    }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override protected int inkCost() { return 128; }
    @Override protected int chargePerInking() { return 24000 * 2; }
    @Override protected int maxCharge() { return 24000 * 8; }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new Entity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, ModBlocks.WARDING_TOWER_BE.get(), BeastWardingTowerBlock::serverTick);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        Set<BlockPos> set = ACTIVE.get(level.dimension());
        if (set != null) set.remove(pos);
        super.onRemove(state, level, pos, newState, moved);
    }

    private static void serverTick(Level level, BlockPos pos, BlockState state, Entity be) {
        boolean on = hold(level, pos, state, be);
        Set<BlockPos> set = ACTIVE.computeIfAbsent(level.dimension(), k -> new HashSet<>());
        if (!on) { set.remove(pos); return; }
        set.add(pos.immutable());
        if (level.getGameTime() % 10 != 0) return;
        Vec3 c = Vec3.atCenterOf(pos);
        for (Mob m : level.getEntitiesOfClass(Mob.class, new AABB(pos).inflate(RADIUS * 0.6, 8, RADIUS * 0.6), m -> m instanceof Enemy)) {
            Vec3 away = m.position().subtract(c);
            Vec3 flat = new Vec3(away.x, 0, away.z);
            if (flat.lengthSqr() < 1e-4) flat = new Vec3(1, 0, 0);
            m.setDeltaMovement(m.getDeltaMovement().add(flat.normalize().scale(0.35)).add(0, 0.05, 0));
            m.hurtMarked = true;
            if (m.getTarget() != null && m.getTarget().distanceToSqr(c) < RADIUS * RADIUS * 0.36) m.setTarget(null);
            m.getNavigation().stop();
        }
        if (level instanceof ServerLevel sl && level.getGameTime() % 40 == 0) {
            sl.sendParticles(ParticleTypes.WHITE_SMOKE, c.x, c.y + 0.6, c.z, 2, 0.05, 0.05, 0.05, 0.01);
        }
    }

    /** Hostile creatures do not spawn near a tower whose seal holds. */
    public static void onSpawnCheck(MobSpawnEvent.PositionCheck e) {
        if (!(e.getEntity() instanceof Enemy) || e.getSpawnType() == MobSpawnType.SPAWNER || e.getSpawnType() == MobSpawnType.SPAWN_EGG
                || e.getSpawnType() == MobSpawnType.COMMAND || e.getSpawnType() == MobSpawnType.TRIAL_SPAWNER) return;
        if (!(e.getLevel() instanceof ServerLevel level)) return;
        Set<BlockPos> set = ACTIVE.get(level.dimension());
        if (set == null || set.isEmpty()) return;
        for (BlockPos p : set) {
            if (p.distToCenterSqr(e.getX(), e.getY(), e.getZ()) < RADIUS * RADIUS) {
                e.setResult(MobSpawnEvent.PositionCheck.Result.FAIL);
                return;
            }
        }
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource rnd) {
        if (!state.getValue(LIT) || rnd.nextInt(3) != 0) return;
        level.addParticle(ParticleTypes.END_ROD, pos.getX() + 0.5 + (rnd.nextDouble() - 0.5) * 0.4, pos.getY() + 0.55, pos.getZ() + 0.5 + (rnd.nextDouble() - 0.5) * 0.4,
                0, 0.01, 0);
    }

    public static class Entity extends SealedBlockEntity {
        public Entity(BlockPos pos, BlockState state) {
            super(ModBlocks.WARDING_TOWER_BE.get(), pos, state);
        }
    }
}
