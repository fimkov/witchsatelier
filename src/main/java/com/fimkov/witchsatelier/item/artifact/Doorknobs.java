package com.fimkov.witchsatelier.item.artifact;

import com.fimkov.witchsatelier.entity.DoorknobEntity;
import com.fimkov.witchsatelier.item.artifact.DoorknobLinks.Knob;
import com.fimkov.witchsatelier.registry.ModEntities;
import com.fimkov.witchsatelier.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Doorknob-Determined Destination Doorways. A knob from a bonded pair clicks onto a door; walking through that door from the
 * knob's side brings you out of the door that holds the partner knob, on that knob's side, facing away from it.
 */
public final class Doorknobs {
    private Doorknobs() {}

    public static final int COOLDOWN_TICKS = 20;

    private record Track(Vec3 prev, Vec3 pos, long tick) {}

    private static final Map<UUID, Track> TRACKS = new HashMap<>();
    private static final Map<UUID, Long> COOLDOWN = new HashMap<>();

    public static boolean isDoor(BlockState s) {
        return s.getBlock() instanceof DoorBlock && s.hasProperty(DoorBlock.HALF);
    }

    public static boolean isLowerDoor(BlockState s) {
        return isDoor(s) && s.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER;
    }

    public static BlockPos lowerHalf(Level level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        if (!isDoor(s)) return null;
        return s.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
    }

    /** The side of the doorway a point is on: the door's facing or its opposite. */
    public static Direction sideOf(BlockState door, BlockPos lower, Vec3 at) {
        Direction f = door.getValue(DoorBlock.FACING);
        Vec3 d = at.subtract(Vec3.atBottomCenterOf(lower));
        return d.x * f.getStepX() + d.z * f.getStepZ() >= 0 ? f : f.getOpposite();
    }

    private static Vec3 step(Direction d, double k) {
        return new Vec3(d.getStepX() * k, 0, d.getStepZ() * k);
    }

    /** Where the knob sits on the door leaf, near the free edge, on the given side; it follows the leaf when the door swings. */
    public static Vec3 knobPoint(BlockState door, BlockPos lower, Direction side) {
        Direction f = door.getValue(DoorBlock.FACING);
        Direction hinge = door.getValue(DoorBlock.HINGE) == DoorHingeSide.RIGHT ? f.getClockWise() : f.getCounterClockWise();
        if (side.getAxis() != f.getAxis()) side = f.getOpposite();
        double out = 3 / 32.0 + 0.05;
        Vec3 rel;
        if (!door.getValue(DoorBlock.OPEN)) {
            rel = step(f, -13 / 32.0).add(step(side, out)).add(step(hinge, -5 / 16.0));
        } else {
            Direction turned = side == f ? hinge : hinge.getOpposite();
            rel = step(f, 5 / 16.0).add(step(hinge, 13 / 32.0)).add(step(turned, out));
        }
        return Vec3.atBottomCenterOf(lower).add(rel).add(0, 0.92, 0);
    }

    public static InteractionResult attach(ServerLevel level, Player player, BlockPos clicked, ItemStack stack) {
        UUID bond = DoorknobItem.bond(stack);
        BlockPos lower = lowerHalf(level, clicked);
        if (bond == null || lower == null) return InteractionResult.PASS;
        if (!level.mayInteract(player, lower) || !player.mayBuild()) return InteractionResult.FAIL;
        BlockState door = level.getBlockState(lower);
        GlobalPos at = GlobalPos.of(level.dimension(), lower);
        DoorknobLinks links = DoorknobLinks.get(level.getServer());
        Knob old = links.remove(at);
        if (old != null) {
            discardEntity(level, old);
            give(player, old.item());
        }
        Knob k = new Knob(UUID.randomUUID(), bond, at, sideOf(door, lower, player.position()), stack.copyWithCount(1),
                level.getServer().overworld().getGameTime());
        stack.shrink(1);
        links.put(k);
        Vec3 p = knobPoint(door, lower, k.side());
        DoorknobEntity knob = new DoorknobEntity(ModEntities.DOORKNOB.get(), level);
        knob.setup(k.self(), lower, k.side());
        knob.moveTo(p);
        level.addFreshEntity(knob);
        level.playSound(null, lower, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.8f, 1.6f);
        level.playSound(null, lower, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.6f, 1.4f);
        level.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 6, 0.05, 0.05, 0.05, 0.02);
        return InteractionResult.SUCCESS;
    }

    /** Takes the knob off a door and gives it back; the far side is shut again. */
    public static boolean takeOff(ServerLevel level, Player player, BlockPos clicked) {
        BlockPos lower = lowerHalf(level, clicked);
        if (lower == null || !level.mayInteract(player, lower)) return false;
        Knob k = DoorknobLinks.get(level.getServer()).remove(GlobalPos.of(level.dimension(), lower));
        if (k == null) return false;
        discardEntity(level, k);
        give(player, k.item());
        level.playSound(null, lower, SoundEvents.CHAIN_BREAK, SoundSource.BLOCKS, 0.8f, 1.5f);
        return true;
    }

    private static void give(Player player, ItemStack item) {
        if (item.isEmpty()) return;
        ItemStack copy = item.copy();
        if (!player.getInventory().add(copy)) player.drop(copy, false);
    }

    private static void drop(ServerLevel level, Knob k) {
        discardEntity(level, k);
        if (k.item().isEmpty()) return;
        Vec3 c = Vec3.atCenterOf(k.door().pos());
        ItemEntity item = new ItemEntity(level, c.x, c.y, c.z, k.item().copy());
        item.setDefaultPickUpDelay();
        level.addFreshEntity(item);
    }

    private static void discardEntity(ServerLevel level, Knob k) {
        for (DoorknobEntity e : level.getEntitiesOfClass(DoorknobEntity.class, new AABB(k.door().pos()).inflate(2),
                e -> k.self().equals(e.self()))) e.discard();
    }

    public static void forget() {
        TRACKS.clear();
        COOLDOWN.clear();
    }

    public static void tick(ServerLevel level) {
        long now = level.getGameTime();
        if (now % 200 == 0) {
            TRACKS.values().removeIf(t -> now - t.tick() > 40);
            COOLDOWN.values().removeIf(t -> now - t > COOLDOWN_TICKS);
        }
        DoorknobLinks links = DoorknobLinks.get(level.getServer());
        for (Knob k : links.in(level.dimension())) {
            BlockPos p = k.door().pos();
            if (!level.isLoaded(p)) continue;
            BlockState st = level.getBlockState(p);
            if (!isLowerDoor(st)) {
                if (links.remove(k)) drop(level, k);
                continue;
            }
            if (st.getValue(DoorBlock.OPEN)) watch(level, links, k, now);
        }
    }

    private static boolean canPass(Entity e) {
        return (e instanceof LivingEntity || e instanceof ItemEntity) && e.isAlive() && !e.isSpectator()
                && !e.isPassenger() && !e.isVehicle() && e.canUsePortal(false);
    }

    private static void watch(ServerLevel level, DoorknobLinks links, Knob k, long now) {
        BlockPos p = k.door().pos();
        Vec3 c = Vec3.atBottomCenterOf(p);
        Direction s = k.side(), across = s.getClockWise();
        for (Entity e : level.getEntities((Entity) null, new AABB(p).expandTowards(0, 1, 0).inflate(1.5, 0.5, 1.5), Doorknobs::canPass)) {
            Vec3 cur = e.position();
            Track t = TRACKS.get(e.getUUID());
            if (t == null || t.tick() != now) {
                t = new Track(t != null && t.tick() == now - 1 ? t.pos() : null, cur, now);
                TRACKS.put(e.getUUID(), t);
            }
            if (t.prev() == null) continue;
            double before = along(t.prev(), c, s), after = along(cur, c, s);
            if (before <= 0 || after > 0) continue;
            if (Math.abs(along(cur, c, across)) > 0.5 || cur.y < p.getY() - 0.5 || cur.y > p.getY() + 1.2) continue;
            Long cd = COOLDOWN.get(e.getUUID());
            if (cd != null && now - cd < COOLDOWN_TICKS) continue;
            Knob to = links.partnerOf(k);
            if (to == null) return;
            carry(level, links, e, k, to, now);
        }
    }

    private static double along(Vec3 v, Vec3 c, Direction d) {
        return (v.x - c.x) * d.getStepX() + (v.z - c.z) * d.getStepZ();
    }

    private static void carry(ServerLevel from, DoorknobLinks links, Entity e, Knob a, Knob b, long now) {
        ServerLevel to = from.getServer().getLevel(b.door().dimension());
        if (to == null) return;
        BlockPos bp = b.door().pos();
        to.getChunk(bp);
        BlockState door = to.getBlockState(bp);
        if (!isLowerDoor(door)) {
            if (links.remove(b)) drop(to, b);
            return;
        }
        Vec3 exit = exitFor(to, e, bp, b.side());
        if (exit == null) return;
        if (!door.getValue(DoorBlock.OPEN) && door.getBlock() instanceof DoorBlock db) db.setOpen(null, to, door, bp, true);
        float turn = b.side().toYRot() - a.side().getOpposite().toYRot();
        float yaw = Mth.wrapDegrees(e.getYRot() + turn);
        Vec3 vel = e.getDeltaMovement().yRot((float) Math.toRadians(-turn));
        to.getChunkSource().addRegionTicket(TicketType.POST_TELEPORT, new ChunkPos(bp), 1, e.getId());
        COOLDOWN.put(e.getUUID(), now);
        TRACKS.put(e.getUUID(), new Track(null, exit, now));
        if (e instanceof ServerPlayer sp) {
            sp.teleportTo(to, exit.x, exit.y, exit.z, yaw, sp.getXRot());
            sp.resetFallDistance();
        } else {
            e.teleportTo(to, exit.x, exit.y, exit.z, Set.of(), yaw, e.getXRot());
            if (to == from) {
                e.setDeltaMovement(vel);
                e.hurtMarked = true;
            }
        }
        from.playSound(null, a.door().pos(), ModSounds.WINDOWWAY.get(), SoundSource.BLOCKS, 0.5f, 1.3f);
        to.playSound(null, bp, ModSounds.WINDOWWAY.get(), SoundSource.BLOCKS, 0.5f, 1.3f);
    }

    /** Just outside the partner door on its knob side, or in the open doorway itself if that side is walled up. */
    private static Vec3 exitFor(ServerLevel to, Entity e, BlockPos door, Direction side) {
        Vec3 c = Vec3.atBottomCenterOf(door);
        double off = 0.55 + e.getBbWidth() / 2;
        for (Vec3 at : List.of(c.add(step(side, off)), c)) {
            if (to.noCollision(e, e.getDimensions(e.getPose()).makeBoundingBox(at))) return at;
        }
        return null;
    }
}
