package com.fimkov.witchsatelier.windowway;

import com.fimkov.witchsatelier.advancement.MagicTrigger;
import com.fimkov.witchsatelier.glyph.surface.SurfacePlane;
import com.fimkov.witchsatelier.network.Net;
import com.fimkov.witchsatelier.network.Payloads;
import com.fimkov.witchsatelier.registry.ModBlocks;
import com.fimkov.witchsatelier.registry.ModSounds;
import com.fimkov.witchsatelier.spell.ActiveSpell;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class WindowwayService {
    private WindowwayService() {}

    private static final class Open {
        final ActiveSpell spell;
        PortalFrame frame;
        final UUID owner;
        final Set<BlockPos> gaps = new HashSet<>();

        Open(ActiveSpell spell, PortalFrame frame) {
            this.spell = spell;
            this.frame = frame;
            this.owner = spell.caster;
        }
    }

    private static final Map<ResourceKey<Level>, Map<Long, Open>> OPEN = new HashMap<>();
    private static final Map<UUID, Long> COOLDOWN = new HashMap<>();
    private static final Map<UUID, Vec3> LAST = new HashMap<>();

    private static Map<Long, Open> of(ServerLevel level) {
        return OPEN.computeIfAbsent(level.dimension(), k -> new LinkedHashMap<>());
    }

    public static List<PortalFrame> frames(ServerLevel level) {
        List<PortalFrame> out = new ArrayList<>();
        for (Open o : of(level).values()) out.add(o.frame);
        return out;
    }

    public static PortalFrame frame(ServerLevel level, long id) {
        Open o = of(level).get(id);
        return o == null ? null : o.frame;
    }

    public static void open(ServerLevel level, ActiveSpell spell) {
        if (spell.plane == null || of(level).containsKey(spell.id)) return;
        SurfacePlane plane = spell.plane;
        Vec3 n = plane.normal();
        if (com.fimkov.witchsatelier.compat.Ships.onShip(level, BlockPos.containing(spell.program.origin().subtract(n.scale(0.2))))) return;
        Vec3 center = spell.program.origin().subtract(n.scale(0.05));
        Vec3 up;
        if (Math.abs(n.y) < 0.5) {
            up = new Vec3(0, 1, 0);
        } else {
            Player p = spell.caster == null ? null : level.getPlayerByUUID(spell.caster);
            Vec3 look = p == null ? new Vec3(0, 0, -1) : p.getLookAngle();
            Direction d = Direction.getNearest(look.x, 0, look.z);
            up = new Vec3(d.getStepX(), 0, d.getStepZ());
        }
        Open me = new Open(spell, PortalFrame.of(spell.id, center, up, n, spell.program.radius(), -1));
        of(level).put(spell.id, me);
        pair(level, me);
        sync(level);
    }

    private static void pair(ServerLevel level, Open me) {
        Open partner = null;
        for (Open other : of(level).values()) {
            if (other != me && other.frame.link() < 0 && Objects.equals(other.owner, me.owner)) partner = other;
        }
        if (partner != null) link(level, me, partner);
    }

    private static void link(ServerLevel level, Open a, Open b) {
        a.frame = a.frame.withLink(b.frame.id());
        b.frame = b.frame.withLink(a.frame.id());
        openGaps(level, a);
        openGaps(level, b);
        for (Open o : List.of(a, b)) {
            Vec3 c = o.frame.center();
            level.playSound(null, c.x, c.y, c.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.2f, 0.5f);
            level.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.4f, 1.8f);
        }
    }

    public static void close(ServerLevel level, long spellId) {
        Open me = of(level).remove(spellId);
        if (me == null) return;
        closeGaps(level, me);
        Open partner = me.frame.link() >= 0 ? of(level).get(me.frame.link()) : null;
        if (partner != null) {
            partner.frame = partner.frame.withLink(-1);
            closeGaps(level, partner);
            pair(level, partner);
        }
        sync(level);
    }

    public static boolean ownsGap(ServerLevel level, BlockPos pos) {
        for (Open o : of(level).values()) if (o.gaps.contains(pos)) return true;
        return false;
    }

    private static void openGaps(ServerLevel level, Open o) {
        PortalFrame f = o.frame;
        BlockPos c = BlockPos.containing(f.center().subtract(f.n().scale(0.5)));
        int R = (int) Math.ceil(f.radius()) + 1;
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-R, -R, -R), c.offset(R, R, R))) {
            if (!f.opens(Vec3.atCenterOf(p).add(f.n().scale(0.5)))) continue;
            BlockState st = level.getBlockState(p);
            if (st.isAir() || st.is(ModBlocks.WINDOWWAY_GAP.get()) || st.hasBlockEntity() || st.getDestroySpeed(level, p) < 0) continue;
            BlockPos at = p.immutable();
            level.setBlock(at, ModBlocks.WINDOWWAY_GAP.get().defaultBlockState(), 2 | 16);
            if (level.getBlockEntity(at) instanceof WindowwayGapBlockEntity be) {
                be.setOriginal(st);
                level.sendBlockUpdated(at, st, level.getBlockState(at), 2);
            }
            o.gaps.add(at);
        }
    }

    private static void closeGaps(ServerLevel level, Open o) {
        for (BlockPos p : o.gaps) {
            if (level.getBlockEntity(p) instanceof WindowwayGapBlockEntity be) {
                BlockState back = be.original();
                level.setBlock(p, back, 2 | 16);
            }
        }
        o.gaps.clear();
    }

    public static void tick(ServerLevel level) {
        Map<Long, Open> open = of(level);
        if (open.isEmpty()) return;
        long now = level.getGameTime();
        for (Open o : List.copyOf(open.values())) {
            Open to = o.frame.link() >= 0 ? open.get(o.frame.link()) : null;
            if (to == null) continue;
            PortalFrame f = o.frame;
            AABB box = new AABB(f.center(), f.center()).inflate(f.radius() + 2);
            for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, box, p -> p.isAlive() && !p.isPassenger() && !p.isSpectator())) {
                Vec3 cur = p.position().add(0, p.getBbHeight() / 2, 0);
                Vec3 prev = LAST.put(p.getUUID(), cur);
                Long cd = COOLDOWN.get(p.getUUID());
                if (prev == null || (cd != null && now - cd < 6) || prev.distanceTo(cur) > 2 || !crossed(f, prev, cur)) continue;
                cross(level, p, o, to);
            }
            for (Entity e : level.getEntities((Entity) null, box, e -> !(e instanceof Player) && e.isAlive() && !e.isPassenger())) {
                Long cd = COOLDOWN.get(e.getUUID());
                if (cd != null && now - cd < 4) continue;
                Vec3 prev = new Vec3(e.xo, e.yo + e.getBbHeight() / 2, e.zo);
                Vec3 cur = e.position().add(0, e.getBbHeight() / 2, 0);
                if (!crossed(f, prev, cur)) continue;
                carry(level, e, f, to.frame);
                COOLDOWN.put(e.getUUID(), now);
            }
        }
        if (now % 200 == 0) {
            COOLDOWN.values().removeIf(t -> now - t > 100);
            LAST.keySet().removeIf(id -> !(level.getPlayerByUUID(id) instanceof ServerPlayer));
        }
    }

    public static boolean crossed(PortalFrame f, Vec3 prev, Vec3 cur) {
        double a = f.side(prev), b = f.side(cur);
        if (!(a >= 0 && b < 0)) return false;
        double t = a / (a - b);
        Vec3 hit = prev.add(cur.subtract(prev).scale(t));
        return f.inOpening(hit);
    }

    private static void carry(ServerLevel level, Entity e, PortalFrame from, PortalFrame to) {
        double h = e.getBbHeight() / 2;
        Vec3 c = PortalFrame.mapPoint(from, to, e.position().add(0, h, 0));
        float[] yp = PortalFrame.yawPitch(PortalFrame.mapDir(from, to, e.getLookAngle()));
        Vec3 vel = PortalFrame.mapDir(from, to, e.getDeltaMovement());
        e.teleportTo(c.x, c.y - h, c.z);
        e.setYRot(yp[0]);
        e.setXRot(yp[1]);
        e.setYHeadRot(yp[0]);
        e.setDeltaMovement(vel);
        e.hurtMarked = true;
        e.setOldPosAndRot();
        whoosh(level, from, to);
    }

    public static void handleCross(ServerPlayer player, long id) {
        ServerLevel level = player.serverLevel();
        Open o = of(level).get(id);
        Open to = o == null || o.frame.link() < 0 ? null : of(level).get(o.frame.link());
        if (to == null) return;
        Long cd = COOLDOWN.get(player.getUUID());
        if (cd != null && level.getGameTime() - cd < 6) return;
        cross(level, player, o, to);
    }

    private static void cross(ServerLevel level, ServerPlayer player, Open o, Open to) {
        PortalFrame f = o.frame;
        Vec3 c = player.position().add(0, player.getBbHeight() / 2, 0);
        double side = f.side(c);
        if (Math.abs(side) > 2.5 || f.inPlane(c) > f.radius() + 1.5) return;
        Vec3 onPlane = c.subtract(f.n().scale(side));
        if (!f.inOpening(onPlane)) return;
        if (side > -0.05) c = onPlane.subtract(f.n().scale(0.05));
        COOLDOWN.put(player.getUUID(), level.getGameTime());
        double h = player.getBbHeight() / 2;
        Vec3 at = PortalFrame.mapPoint(f, to.frame, c);
        float[] yp = PortalFrame.yawPitch(PortalFrame.mapDir(f, to.frame, player.getLookAngle()));
        Vec3 vel = PortalFrame.mapDir(f, to.frame, player.getDeltaMovement());
        level.getChunkSource().addRegionTicket(net.minecraft.server.level.TicketType.POST_TELEPORT,
                new net.minecraft.world.level.ChunkPos(BlockPos.containing(at)), 1, player.getId());
        if (player.position().distanceTo(at) > 96) {
            player.teleportTo(level, at.x, at.y - h, at.z, yp[0], yp[1]);
        } else {
            player.moveTo(at.x, at.y - h, at.z, yp[0], yp[1]);
            player.setYHeadRot(yp[0]);
            player.connection.resetPosition();
            level.getChunkSource().move(player);
        }
        LAST.put(player.getUUID(), at);
        player.setDeltaMovement(vel);
        player.resetFallDistance();
        whoosh(level, f, to.frame);
        MagicTrigger.fire(player, "windowway");
    }

    private static void whoosh(ServerLevel level, PortalFrame a, PortalFrame b) {
        for (PortalFrame f : List.of(a, b)) {
            Vec3 c = f.center();
            level.playSound(null, c.x, c.y, c.z, ModSounds.WINDOWWAY.get(), SoundSource.PLAYERS, 0.8f, 1f);
        }
    }

    public static void sync(ServerLevel level) {
        Net.toDimension(level, new Payloads.WindowwaySync(frames(level)));
    }

    public static void syncTo(ServerPlayer player) {
        Net.toPlayer(player, new Payloads.WindowwaySync(frames(player.serverLevel())));
    }

    public static void closeAll(ServerLevel level) {
        Map<Long, Open> open = OPEN.remove(level.dimension());
        if (open == null) return;
        for (Open o : open.values()) closeGaps(level, o);
    }
}
