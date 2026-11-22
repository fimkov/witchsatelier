package com.fimkov.witchsatelier.block;

import com.fimkov.witchsatelier.WitchsAtelier;
import com.fimkov.witchsatelier.glyph.GlyphService;
import com.fimkov.witchsatelier.glyph.ink.InkType;
import com.fimkov.witchsatelier.item.InkPenItem;
import com.fimkov.witchsatelier.registry.ModBlocks;
import com.fimkov.witchsatelier.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class Starfall {
    private Starfall() {}

    public static final ResourceKey<Level> LEVEL = ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath(WitchsAtelier.MODID, "starfall"));
    private static final String RETURN = "witchsatelier_starfall_return";

    private static final String LEAVING = "witchsatelier_starfall_leaving";

    public static final int RETURN_TICKS = 40;
    private static final Map<UUID, Collection<Long>> GLYPHS = new HashMap<>();
    private static final BlockPos ISLAND = new BlockPos(0, 100, 0);

    private static final int DOME = 10;

    public static boolean isStarfall(Level level) {
        return level.dimension().equals(LEVEL);
    }

    public static boolean among(Level level, Vec3 at) {
        return isStarfall(level);
    }

    public static void fallIn(ServerPlayer p, BlockPos basin) {
        ServerLevel stars = p.server.getLevel(LEVEL);
        if (stars == null || among(p.level(), p.position())) return;
        Vec3 back = beside(p, basin);
        CompoundTag tag = new CompoundTag();
        tag.putString("dim", p.level().dimension().location().toString());
        tag.putDouble("x", back.x);
        tag.putDouble("y", back.y);
        tag.putDouble("z", back.z);
        p.getPersistentData().put(RETURN, tag);
        chamber(stars);
        ((ServerLevel) p.level()).sendParticles(ParticleTypes.REVERSE_PORTAL, p.getX(), p.getY() + 0.5, p.getZ(), 60, 0.3, 0.6, 0.3, 0.1);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8f, 0.5f);
        p.teleportTo(stars, ISLAND.getX() + 0.5, ISLAND.getY() + 1, ISLAND.getZ() + 0.5, p.getYRot(), p.getXRot());
        p.resetFallDistance();
        p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 40, 0, false, false));
        stars.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1, p.getZ(), 80, 1.2, 1.2, 1.2, 0.08);
        stars.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PORTAL_TRAVEL, SoundSource.PLAYERS, 0.25f, 1.6f);
        stars.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1f, 0.6f);

        boolean pen = false;
        for (ItemStack st : p.getInventory().items) if (st.getItem() instanceof InkPenItem) pen = true;
        if (!pen) {
            ItemStack lent = new ItemStack(ModItems.INK_PEN.get());
            InkPenItem.setLoaded(lent, InkType.CONJURING, InkPenItem.NIB_CAPACITY);
            if (!p.getInventory().add(lent)) p.drop(lent, false);
        }
    }

    private static Vec3 beside(ServerPlayer p, BlockPos basin) {
        Level level = p.level();
        Direction from = p.getDirection().getOpposite();
        List<Direction> order = new ArrayList<>(List.of(from));
        for (var d : Direction.Plane.HORIZONTAL) if (d != from) order.add(d);
        for (var d : order) {
            BlockPos at = basin.relative(d);
            for (int up = 0; up <= 1; up++) {
                BlockPos feet = at.above(up);
                if (level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                        && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()
                        && !level.getBlockState(feet.below()).getCollisionShape(level, feet.below()).isEmpty())
                    return new Vec3(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5);
            }
        }
        return new Vec3(basin.getX() + 0.5, basin.getY() + 1.1, basin.getZ() + 1.5);
    }

    public static void goHome(ServerPlayer p) {
        CompoundTag back = p.getPersistentData().getCompound(RETURN);
        ServerLevel to = null;
        if (back.contains("dim")) {
            ResourceLocation id = ResourceLocation.tryParse(back.getString("dim"));
            if (id != null) to = p.server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
        }
        Vec3 at;
        if (to == null) {
            to = p.server.overworld();
            BlockPos s = to.getSharedSpawnPos();
            at = new Vec3(s.getX() + 0.5, s.getY() + 1, s.getZ() + 0.5);
        } else {
            at = new Vec3(back.getDouble("x"), back.getDouble("y"), back.getDouble("z"));
        }
        p.getPersistentData().remove(RETURN);
        p.getPersistentData().remove(LEAVING);
        p.teleportTo(to, at.x, at.y, at.z, p.getYRot(), p.getXRot());
        p.resetFallDistance();
        p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 60, 0, false, false));

        to.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 1, at.z, 70, 0.6, 1.0, 0.6, 0.12);
        to.sendParticles(ParticleTypes.REVERSE_PORTAL, at.x, at.y + 0.2, at.z, 120, 1.4, 0.1, 1.4, 0.05);
        to.sendParticles(ParticleTypes.FLASH, at.x, at.y + 1, at.z, 1, 0, 0, 0, 0);
        to.playSound(null, at.x, at.y, at.z, SoundEvents.ENDER_EYE_DEATH, SoundSource.PLAYERS, 1f, 0.8f);
        to.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1f, 1.4f);
    }

    private static void chamber(ServerLevel stars) {
        BlockPos top = ISLAND.above(DOME);
        if (stars.getBlockState(top).is(ModBlocks.STAR_BLOCK.get())) return;
        var star = ModBlocks.STAR_BLOCK.get().defaultBlockState();
        var air = Blocks.AIR.defaultBlockState();
        int r = DOME + 1;
        for (BlockPos q : BlockPos.betweenClosed(ISLAND.offset(-r, -1, -r), ISLAND.offset(r, r, r))) {
            int dx = q.getX() - ISLAND.getX(), dy = q.getY() - ISLAND.getY(), dz = q.getZ() - ISLAND.getZ();
            double d = Math.sqrt(dx * dx + dy * dy + dz * dz), flat = Math.sqrt(dx * dx + dz * dz);
            boolean floor = dy <= 0 && flat <= DOME + 0.5;
            boolean shell = dy > 0 && d <= DOME + 0.5 && d > DOME - 0.6;
            stars.setBlock(q, floor || shell ? star : air, 2);
        }
        var rnd = stars.random;
        for (int i = 0; i < 7; i++) {
            double a = rnd.nextDouble() * Math.PI * 2, dist = 4 + rnd.nextDouble() * 4;
            BlockPos c = ISLAND.offset((int) (Math.cos(a) * dist), 3 + rnd.nextInt(4), (int) (Math.sin(a) * dist));
            stars.setBlock(c, star, 2);
            if (rnd.nextBoolean()) stars.setBlock(c.relative(Direction.getRandom(rnd)), star, 2);
        }
    }

    public static void beginReturn(ServerPlayer p, Collection<Long> glyph) {
        if (p.getPersistentData().contains(LEAVING)) return;
        p.getPersistentData().putLong(LEAVING, p.level().getGameTime() + RETURN_TICKS);
        GLYPHS.put(p.getUUID(), List.copyOf(glyph));
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1f, 1.5f);
    }

    private static void whirl(ServerPlayer p, long left) {
        ServerLevel l = (ServerLevel) p.level();
        double t = (RETURN_TICKS - left) / (double) RETURN_TICKS;
        for (int i = 0; i < 4; i++) {
            double a = l.getGameTime() * 0.35 + i * Math.PI / 2, r = 1.6 - t * 1.1, y = p.getY() + t * 2.2 + i * 0.15;
            l.sendParticles(ParticleTypes.END_ROD, p.getX() + Math.cos(a) * r, y, p.getZ() + Math.sin(a) * r, 1, 0, 0, 0, 0);
        }
        if (left % 6 == 0) l.sendParticles(ParticleTypes.REVERSE_PORTAL, p.getX(), p.getY() + 1, p.getZ(), 12, 0.5, 0.8, 0.5, 0.06);
        if (left == 10) l.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 1f, 1.8f);
    }

    public static void onPlayerTick(PlayerTickEvent.Post e) {
        if (e.getEntity() instanceof ServerPlayer p && p.getPersistentData().contains(LEAVING)) {
            long left = p.getPersistentData().getLong(LEAVING) - p.level().getGameTime();
            if (!isStarfall(p.level()) || left > RETURN_TICKS) {
                p.getPersistentData().remove(LEAVING);
            } else if (left > 0) {
                whirl(p, left);
            } else {
                Collection<Long> glyph = GLYPHS.remove(p.getUUID());
                if (glyph != null) GlyphService.eraseStrokes((ServerLevel) p.level(), glyph);
                ((ServerLevel) p.level()).sendParticles(ParticleTypes.FLASH, p.getX(), p.getY() + 1, p.getZ(), 1, 0, 0, 0, 0);
                goHome(p);
                return;
            }
        }
        if (e.getEntity() instanceof ServerPlayer p && p.getY() < 20 && among(p.level(), p.position())
                && p.getPersistentData().contains(RETURN)) goHome(p);
    }
}
