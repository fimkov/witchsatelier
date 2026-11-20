package com.witchcraft.windowway;

import com.witchcraft.glyph.surface.SurfacePlane;
import com.witchcraft.network.Net;
import com.witchcraft.network.Payloads;
import com.witchcraft.registry.ModBlocks;
import com.witchcraft.spell.ActiveSpell;
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
import java.util.Set;
import java.util.UUID;

/**
 * Windowways: a ring with the Window sign at its centre, drawn on a surface. Two of them drawn by the same
 * witch join up; while both hold, the wall behind each disc steps aside and whatever passes into one window
 * comes out of the other. Players cross on their own side (so the view never jumps) and tell the server;
 * everything else is carried across here.
 */
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

    /** A windowway glyph came alive: set up its window and join it to its partner if one is waiting. */
    public static void open(ServerLevel level, ActiveSpell spell) {
        if (spell.plane == null || of(level).containsKey(spell.id)) return;
        SurfacePlane plane = spell.plane;
        Vec3 n = plane.normal();
        Vec3 center = spell.program.origin().subtract(n.scale(0.05));
        Vec3 up;
        if (Math.abs(n.y) < 0.5) {
            up = new Vec3(0, 1, 0);
        } else {
            // on a floor or ceiling the window's "up" is the way its maker faced
            Player p = spell.caster == null ? null : level.getPlayerByUUID(spell.caster);
            Vec3 look = p == null ? new Vec3(0, 0, -1) : p.getLookAngle();
            Direction d = Direction.getNearest(look.x, 0, look.z);
            up = new Vec3(d.getStepX(), 0, d.getStepZ());
        }
        Open me = new Open(spell, PortalFrame.of(spell.id, center, up, n, spell.program.radius(), -1));
        of(level).put(spell.id, me);
        // it joins the window its maker drew last that is still waiting
        Open partner = null;
        for (Open other : of(level).values()) {
            if (other != me && other.frame.link() < 0 && java.util.Objects.equals(other.owner, me.owner)) partner = other;
        }
        if (partner != null) link(level, me, partner);
        sync(level);
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

    /** The glyph's spell ended (or its ink was broken): close the window and let its partner wait again. */
    public static void close(ServerLevel level, long spellId) {
        Open me = of(level).remove(spellId);
        if (me == null) return;
        closeGaps(level, me);
        Open partner = me.frame.link() >= 0 ? of(level).get(me.frame.link()) : null;
        if (partner != null) {
            partner.frame = partner.frame.withLink(-1);
            closeGaps(level, partner);
        }
        sync(level);
    }

    public static boolean ownsGap(ServerLevel level, BlockPos pos) {
        for (Open o : of(level).values()) if (o.gaps.contains(pos)) return true;
        return false;
    }

    /** Blocks the disc covers step aside (never unbreakable ones, never ones holding contents). */
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

    /** Carries everything but players across (players cross on their own side, see {@link #handleCross}). */
    public static void tick(ServerLevel level) {
        Map<Long, Open> open = of(level);
        if (open.isEmpty()) return;
        long now = level.getGameTime();
        for (Open o : List.copyOf(open.values())) {
            Open to = o.frame.link() >= 0 ? open.get(o.frame.link()) : null;
            if (to == null) continue;
            PortalFrame f = o.frame;
            AABB box = new AABB(f.center(), f.center()).inflate(f.radius() + 2);
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
        if (now % 200 == 0) COOLDOWN.values().removeIf(t -> now - t > 100);
    }

    /** Went from the window's front to behind it, through the disc. */
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

    /** A player's client saw them step through window {@code id}. */
    public static void handleCross(ServerPlayer player, long id) {
        ServerLevel level = player.serverLevel();
        Open o = of(level).get(id);
        Open to = o == null || o.frame.link() < 0 ? null : of(level).get(o.frame.link());
        if (to == null) return;
        PortalFrame f = o.frame;
        Vec3 c = player.position().add(0, player.getBbHeight() / 2, 0);
        if (Math.abs(f.side(c)) > 2.5 || f.inPlane(c) > f.radius() + 1.5) return; // nowhere near it
        double h = player.getBbHeight() / 2;
        Vec3 at = PortalFrame.mapPoint(f, to.frame, c);
        float[] yp = PortalFrame.yawPitch(PortalFrame.mapDir(f, to.frame, player.getLookAngle()));
        Vec3 vel = PortalFrame.mapDir(f, to.frame, player.getDeltaMovement());
        if (player.position().distanceTo(at) > 96) {
            // too far for the client to have the other side loaded: an ordinary teleport
            player.teleportTo(level, at.x, at.y - h, at.z, yp[0], yp[1]);
        } else {
            player.moveTo(at.x, at.y - h, at.z, yp[0], yp[1]);
            player.setYHeadRot(yp[0]);
            player.connection.resetPosition();
        }
        player.setDeltaMovement(vel);
        player.resetFallDistance();
        whoosh(level, f, to.frame);
        com.witchcraft.advancement.MagicTrigger.fire(player, "windowway");
    }

    private static void whoosh(ServerLevel level, PortalFrame a, PortalFrame b) {
        for (PortalFrame f : List.of(a, b)) {
            Vec3 c = f.center();
            level.playSound(null, c.x, c.y, c.z, com.witchcraft.registry.ModSounds.WINDOWWAY.get(), SoundSource.PLAYERS, 0.8f, 1f);
        }
    }

    public static void sync(ServerLevel level) {
        Net.toDimension(level, new Payloads.WindowwaySync(frames(level)));
    }

    public static void syncTo(ServerPlayer player) {
        Net.toPlayer(player, new Payloads.WindowwaySync(frames(player.serverLevel())));
    }

    /** Server shutting down: put every wall back. */
    public static void closeAll(ServerLevel level) {
        Map<Long, Open> open = OPEN.remove(level.dimension());
        if (open == null) return;
        for (Open o : open.values()) closeGaps(level, o);
    }
}
