package com.fimkov.witchsatelier.block;

import com.fimkov.witchsatelier.registry.ModBlocks;
import com.fimkov.witchsatelier.registry.ModSounds;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Optional;

public class StatueBlock extends BaseEntityBlock {
    public static final MapCodec<StatueBlock> CODEC = simpleCodec(StatueBlock::new);
    private static final VoxelShape SHAPE = box(3, 0, 3, 13, 16, 13);

    public StatueBlock(Properties props) {
        super(props);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

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
        return level.isClientSide ? null : createTickerHelper(type, ModBlocks.STATUE_BE.get(), StatueBlock::stirring);
    }

    public static boolean petrify(ServerLevel level, Mob mob) {
        BlockPos at = mob.blockPosition();
        if (!level.getBlockState(at).canBeReplaced()) return false;
        CompoundTag data = new CompoundTag();
        if (!mob.saveAsPassenger(data)) return false;
        level.setBlock(at, ModBlocks.STATUE.get().defaultBlockState(), 3);
        if (level.getBlockEntity(at) instanceof Entity be) {
            be.data = data;
            be.yaw = mob.getYRot();
            be.setChanged();
            level.sendBlockUpdated(at, be.getBlockState(), be.getBlockState(), 3);
        }
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ModBlocks.PETRIFIED.get().defaultBlockState()), mob.getX(), mob.getY() + mob.getBbHeight() / 2,
                mob.getZ(), 30, 0.3, mob.getBbHeight() / 3, 0.3, 0.1);
        level.sendParticles(ParticleTypes.END_ROD, mob.getX(), mob.getY() + mob.getBbHeight() / 2, mob.getZ(), 12, 0.3, mob.getBbHeight() / 3, 0.3, 0.03);
        level.playSound(null, at, ModSounds.PETRIFY.get(), SoundSource.HOSTILE, 1.2f, 1f);
        level.playSound(null, at, SoundEvents.BASALT_BREAK, SoundSource.HOSTILE, 1f, 0.5f);
        mob.discard();
        return true;
    }

    public static int revive(ServerLevel level, Vec3 center, double range) {
        int n = 0;
        BlockPos c = BlockPos.containing(center);
        int r = (int) Math.ceil(range);
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-r, -r, -r), c.offset(r, r, r))) {
            if (p.distToCenterSqr(center) > range * range || !(level.getBlockEntity(p) instanceof Entity be) || be.data == null) continue;
            BlockPos at = p.immutable();
            CompoundTag data = be.data;
            level.removeBlock(at, false);
            Optional<net.minecraft.world.entity.Entity> made = EntityType.create(data, level);
            if (made.isPresent()) {
                net.minecraft.world.entity.Entity e = made.get();
                e.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, be.yaw, 0);
                level.addFreshEntity(e);
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()), e.getX(), e.getY() + 1, e.getZ(), 25, 0.3, 0.6, 0.3, 0.1);
                level.playSound(null, at, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.NEUTRAL, 1f, 1.2f);
                n++;
            }
        }
        return n;
    }

    /**
     * Integration drawn in any ink but blood: the body cannot be given back, only the mind is woken. The statue stays
     * stone and does not move, but its eyes glint and now and then it makes its sounds.
     * Returns how many statues were reached.
     */
    public static int stir(ServerLevel level, Vec3 center, double range) {
        int n = 0;
        BlockPos c = BlockPos.containing(center);
        int r = (int) Math.ceil(range);
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-r, -r, -r), c.offset(r, r, r))) {
            if (p.distToCenterSqr(center) > range * range || !(level.getBlockEntity(p) instanceof Entity be) || be.data == null) continue;
            if (!be.stirred) {
                be.stirred = true;
                be.setChanged();
                level.sendBlockUpdated(p, be.getBlockState(), be.getBlockState(), 3);
            }
            glint(level, be);
            be.speak(level);
            level.playSound(null, p, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 1f, 0.6f);
            n++;
        }
        return n;
    }

    private static void stirring(Level level, BlockPos pos, BlockState state, Entity be) {
        if (!be.stirred || be.data == null || !(level instanceof ServerLevel sl) || (sl.getGameTime() + pos.asLong()) % 20 != 0) return;
        if (sl.random.nextInt(3) == 0) glint(sl, be);
        if (sl.random.nextInt(8) == 0) be.speak(sl);
    }

    private static void glint(ServerLevel level, Entity be) {
        BlockPos p = be.getBlockPos();
        double eye = be.eyeHeight(level);
        level.sendParticles(ParticleTypes.END_ROD, p.getX() + 0.5, p.getY() + eye, p.getZ() + 0.5, 2, 0.12, 0.04, 0.12, 0);
    }

    public static class Entity extends BlockEntity {
        CompoundTag data;
        float yaw;
        boolean stirred;
        private Mob voice;

        public net.minecraft.world.entity.Entity shown;

        public Entity(BlockPos pos, BlockState state) {
            super(ModBlocks.STATUE_BE.get(), pos, state);
        }

        public void set(CompoundTag data, float yaw) {
            this.data = data;
            this.yaw = yaw;
            setChanged();
        }

        public CompoundTag data() {
            return data;
        }

        public float yaw() {
            return yaw;
        }

        /** Woken by Integration in ordinary ink: still stone, but awake inside. */
        public boolean stirred() {
            return stirred;
        }

        private Mob voice(ServerLevel level) {
            if (voice == null && data != null && EntityType.create(data, level).orElse(null) instanceof Mob m) {
                m.moveTo(worldPosition.getX() + 0.5, worldPosition.getY(), worldPosition.getZ() + 0.5, yaw, 0);
                voice = m;
            }
            return voice;
        }

        double eyeHeight(ServerLevel level) {
            Mob m = voice(level);
            return m == null ? 1 : m.getEyeHeight();
        }

        void speak(ServerLevel level) {
            Mob m = voice(level);
            if (m != null) m.playAmbientSound();
        }

        @Override
        protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.saveAdditional(tag, registries);
            if (data != null) tag.put("creature", data);
            tag.putFloat("yaw", yaw);
            if (stirred) tag.putBoolean("stirred", true);
        }

        @Override
        protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.loadAdditional(tag, registries);
            data = tag.contains("creature") ? tag.getCompound("creature") : null;
            yaw = tag.getFloat("yaw");
            stirred = tag.getBoolean("stirred");
            voice = null;
            shown = null;
        }

        @Override
        public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
            return saveWithoutMetadata(registries);
        }

        @Override
        public Packet<ClientGamePacketListener> getUpdatePacket() {
            return ClientboundBlockEntityDataPacket.create(this);
        }
    }
}
