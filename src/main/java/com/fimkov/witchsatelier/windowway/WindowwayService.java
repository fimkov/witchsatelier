package com.fimkov.witchsatelier.windowway;

import com.fimkov.witchsatelier.WitchsAtelier;
import com.fimkov.witchsatelier.advancement.MagicTrigger;
import com.fimkov.witchsatelier.glyph.storage.GlyphLevelData;
import com.fimkov.witchsatelier.glyph.surface.InkStroke;
import com.fimkov.witchsatelier.glyph.surface.StrokeState;
import com.fimkov.witchsatelier.glyph.surface.SurfacePlane;
import com.fimkov.witchsatelier.network.Net;
import com.fimkov.witchsatelier.network.Payloads;
import com.fimkov.witchsatelier.registry.ModBlocks;
import com.fimkov.witchsatelier.registry.ModSounds;
import com.fimkov.witchsatelier.spell.ActiveSpell;
import com.fimkov.witchsatelier.spell.SpellProgram;
import com.fimkov.witchsatelier.windowway.WindowwayNetwork.Role;
import com.fimkov.witchsatelier.windowway.WindowwayNetwork.Station;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Windowways: a Window drawn on a wall opens onto another. A plain Window joins the next plain Window its witch draws,
 * as it always has. A Window named by signs around it joins the network (see {@link WindowwayNetwork}): it opens onto
 * its mark's Sill, wherever and in whatever dimension that is, and a page held up before it turns it, for a little
 * while, toward the Sill of the mark drawn on the page.
 */
public final class WindowwayService {
    private WindowwayService() {}

    /**
     * Why a window is closing. Only ink that is gone makes the network forget the window; a window that is only being
     * read again, its ink untouched ({@code RECAST}), keeps its place, its name and its id.
     */
    public enum Reason { ERASED, PAUSED, SHUTDOWN, RECAST }

    private enum Kind {
        /** No mark: pairs with its witch's next plain window, as Windowways always have. */
        BARE,
        /** Marked and drawn in ink: a lasting place in the network. */
        STATION,
        /** Marked but cast from a page: it only ever visits. */
        PAGE,
        /** Marked, but its witch or the world keeps too many windows already: a quiet rim. */
        INERT
    }

    /** Passed as the expected link when the caller does not know which way the window looked. */
    public static final long ANY_LINK = Long.MIN_VALUE;

    /** Set in the frame id of every window that does not last: plain ones and those cast from pages. */
    public static final long TRANSIENT = 1L << 62;

    public static final int RETUNE_EVERY = 5, SETTLE_TICKS = 10, HOLD_AFTER_CROSS = 60, HOLD_AFTER_LEAVE = 10, BACK_LINK_TICKS = 200;
    public static final double TUNE_NEAR = 0.3, TUNE_FAR = 4, SEEN_RANGE = 6, SEAMLESS = 96, FAR = 1000;
    public static final int SEEN_TICKS = 40;

    public static final TagKey<EntityType<?>> BLOCKED = TagKey.create(Registries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(WitchsAtelier.MODID, "windowway_blocked"));

    /** Registered on the main thread during common setup; read as on when not yet there. */
    public static GameRules.Key<GameRules.BooleanValue> acrossDimensions, discovery;

    public static void register(IEventBus modBus) {
        modBus.addListener((FMLCommonSetupEvent e) -> e.enqueueWork(() -> {
            if (acrossDimensions == null) acrossDimensions = GameRules.register("witchsatelierWindowwaysAcrossDimensions",
                    GameRules.Category.MISC, GameRules.BooleanValue.create(true));
            if (discovery == null) discovery = GameRules.register("witchsatelierWindowwayDiscovery",
                    GameRules.Category.MISC, GameRules.BooleanValue.create(true));
        }));
    }

    private static boolean rule(MinecraftServer server, GameRules.Key<GameRules.BooleanValue> key) {
        return key == null || server.getGameRules().getBoolean(key);
    }

    private static final class Open {
        final ActiveSpell spell;
        final ServerLevel level;
        final Kind kind;
        final UUID owner;
        final UUID wid;
        final String mark;
        PortalFrame frame;
        final Set<BlockPos> gaps = new HashSet<>();

        long standing = -1;
        long linkedAt = Long.MIN_VALUE / 4;
        /** When a witch last went through; things carried across do not count, or a stream of them would hold a window forever. */
        long lastCross = Long.MIN_VALUE / 4;
        /** True while the window is first opening: where it looks then is no turn, and no one waits for it to settle. */
        boolean opening = true;

        UUID tuner;
        String tunedMark;
        long tunedTo = -1, tunerLeftAt = -1;

        long backTo = -1, backUntil;

        final Map<UUID, Integer> near = new HashMap<>();
        UUID refusedWho;
        String refusedMark;
        long refusedAt = Long.MIN_VALUE / 4;

        Open(ActiveSpell spell, ServerLevel level, Kind kind, UUID wid, PortalFrame frame) {
            this.spell = spell;
            this.level = level;
            this.kind = kind;
            this.owner = spell.caster;
            this.wid = wid;
            this.mark = spell.windowMark == null ? "" : spell.windowMark;
            this.frame = frame;
        }

        boolean marked() {
            return kind == Kind.STATION || kind == Kind.PAGE;
        }
    }

    /** Where a link leads: a window that is open now, or a Sill known to the network that is not lit at the moment. */
    public record Dest(ServerLevel level, PortalFrame frame, Station station, boolean live) {}

    private record Back(long to, long until) {}

    private static final Map<ResourceKey<Level>, Map<Long, Open>> OPEN = new HashMap<>();
    private static final Map<Long, Open> BY_SPELL = new HashMap<>();
    private static final Map<Long, Back> PENDING_BACK = new HashMap<>();
    /** Windows whose look changed since their dimension was last told, by dimension. */
    private static final Map<ResourceKey<Level>, Set<Long>> MOVED = new HashMap<>();
    /** Players too far off to be told of a turn as it happened; they hear the whole of their dimension a little later. */
    private static final Set<UUID> OWED = new HashSet<>();
    /** When someone last went through a window; a refusal is no crossing and sets none, so it never lets her by. */
    private static final Map<UUID, Long> COOLDOWN = new HashMap<>();
    /** Where a player's (or a ridden mount's) middle was when a window last saw her, and when that was. */
    private static final Map<UUID, Vec3> LAST = new HashMap<>();
    private static final Map<UUID, Long> LAST_AT = new HashMap<>();
    /** Where each player near a window stood at the end of the last tick, kept for this tick however many windows she is near. */
    private static final Map<UUID, Vec3> STEP = new HashMap<>();

    private static Map<Long, Open> of(ServerLevel level) {
        return OPEN.computeIfAbsent(level.dimension(), k -> new LinkedHashMap<>());
    }

    private static Open find(long frameId) {
        for (Map<Long, Open> m : OPEN.values()) {
            Open o = m.get(frameId);
            if (o != null) return o;
        }
        return null;
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

    /** The frame of the window a glyph opened, or null while it is shut. */
    public static PortalFrame frameOf(ActiveSpell spell) {
        Open o = BY_SPELL.get(spell.id);
        return o == null ? null : o.frame;
    }

    /** The network entry of the window a glyph opened, or null for a plain, paged or quiet window. */
    public static Station stationOf(ServerLevel level, ActiveSpell spell) {
        Open o = BY_SPELL.get(spell.id);
        return o == null || o.wid == null ? null : WindowwayNetwork.get(level.getServer()).station(o.wid);
    }

    /** Whether the window a glyph opened is held toward another Sill by a page. */
    public static boolean tuned(ActiveSpell spell) {
        Open o = BY_SPELL.get(spell.id);
        return o != null && o.tuner != null;
    }

    private static Vec3 upFor(ServerLevel level, ActiveSpell spell, Vec3 n) {
        if (Math.abs(n.y) < 0.5) return new Vec3(0, 1, 0);
        Player p = spell.caster == null ? null : level.getPlayerByUUID(spell.caster);
        Vec3 look = p == null ? new Vec3(0, 0, -1) : p.getLookAngle();
        Direction d = Direction.getNearest(look.x, 0, look.z);
        return new Vec3(d.getStepX(), 0, d.getStepZ());
    }

    public static void open(ServerLevel level, ActiveSpell spell) {
        if (spell.plane == null || BY_SPELL.containsKey(spell.id)) return;
        SurfacePlane plane = spell.plane;
        Vec3 n = plane.normal();
        if (com.fimkov.witchsatelier.compat.Ships.onShip(level, BlockPos.containing(spell.program.origin().subtract(n.scale(0.2))))) return;
        Vec3 center = spell.program.origin().subtract(n.scale(0.05));
        double radius = spell.program.radius();
        String mark = spell.windowMark == null ? "" : spell.windowMark;
        long passing = spell.id | TRANSIENT;

        if (mark.isEmpty()) {
            Open me = new Open(spell, level, Kind.BARE, null, PortalFrame.of(passing, center, upFor(level, spell, n), n, radius, -1));
            put(me);
            pair(level, me);
            me.opening = false;
            sync(level);
            return;
        }
        MinecraftServer server = level.getServer();
        if (spell.fromPage || spell.strokes.isEmpty()) {
            Open me = new Open(spell, level, Kind.PAGE, null, PortalFrame.of(passing, center, upFor(level, spell, n), n, radius, -1));
            put(me);
            relink(server, mark);
            me.opening = false;
            sync(level);
            return;
        }

        WindowwayNetwork net = WindowwayNetwork.get(server);
        GlyphLevelData data = GlyphLevelData.get(level);
        prune(server, mark);
        UUID wid = data.windowIdOf(spell.strokes);
        Station st = net.station(wid);
        if (st != null && (!st.mark().equals(mark) || !st.dim().equals(level.dimension()) || find(st.frameId()) != null)) {
            if (find(st.frameId()) == null) {
                net.forget(st.wid());
                relink(server, st.mark());
            }
            st = null;
            wid = null;
        }
        boolean blood = spell.drawnInBlood(level), gated = spell.program.has(SpellProgram.TREAD);
        Vec3 up;
        if (st != null) {
            up = st.up();
            st = net.update(st.wid(), spell.strokes, center, n, radius, blood, gated);
        } else {
            up = upFor(level, spell, n);
            UUID fresh = wid != null && net.station(wid) == null ? wid : UUID.randomUUID();
            st = net.seal(fresh, level.dimension(), spell.strokes, center, up, n, radius, spell.caster, mark, blood, gated);
        }
        if (st == null) {
            put(new Open(spell, level, Kind.INERT, null, PortalFrame.of(passing, center, up, n, radius, -1)));
            sync(level);
            return;
        }
        data.setWindowId(spell.strokes, st.wid());
        Open me = new Open(spell, level, Kind.STATION, st.wid(), PortalFrame.of(st.frameId(), center, up, n, radius, -1));
        put(me);
        Back back = PENDING_BACK.remove(me.frame.id());
        if (back != null && level.getGameTime() < back.until()) {
            me.backTo = back.to();
            me.backUntil = back.until();
        }
        relink(server, mark);
        me.opening = false;
        sync(level);
    }

    private static void put(Open o) {
        of(o.level).put(o.frame.id(), o);
        BY_SPELL.put(o.spell.id, o);
    }

    /** Called when the ink of a lit window changes without changing what it is, so the network keeps up with its strokes. */
    public static void restrung(ServerLevel level, ActiveSpell spell) {
        Open o = BY_SPELL.get(spell.id);
        if (o == null || o.kind != Kind.STATION) return;
        WindowwayNetwork net = WindowwayNetwork.get(level.getServer());
        Station st = net.station(o.wid);
        if (st == null || st.strokes().equals(spell.strokes)) return;
        // ink traced over in blood makes a window private, and plain ink added to a blood one makes it public again
        boolean blood = spell.drawnInBlood(level);
        net.update(o.wid, spell.strokes, st.center(), st.n(), st.radius(), blood, st.gated());
        GlyphLevelData.get(level).setWindowId(spell.strokes, o.wid);
        if (blood != st.blood()) sync(level);
    }

    private static void pair(ServerLevel level, Open me) {
        Open partner = null;
        for (Open other : of(level).values()) {
            if (other != me && other.kind == Kind.BARE && other.frame.link() < 0 && Objects.equals(other.owner, me.owner)) partner = other;
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

    public static void close(ServerLevel level, long spellId, Reason reason) {
        Open me = BY_SPELL.remove(spellId);
        if (me == null) return;
        of(me.level).remove(me.frame.id());
        closeGaps(me.level, me);
        if (me.kind == Kind.BARE) {
            Open partner = me.frame.link() >= 0 ? of(me.level).get(me.frame.link()) : null;
            if (partner != null && partner.kind == Kind.BARE) {
                partner.frame = partner.frame.withLink(-1);
                closeGaps(me.level, partner);
                pair(me.level, partner);
            }
        } else {
            MinecraftServer server = me.level.getServer();
            if (me.kind == Kind.STATION && reason == Reason.ERASED) {
                WindowwayNetwork.get(server).forget(me.wid);
                GlyphLevelData.get(me.level).setWindowId(me.spell.strokes, null);
                PENDING_BACK.remove(me.frame.id());
            }
            relink(server, me.mark);
        }
        sync(me.level);
    }

    /**
     * Brings every open window bearing a mark up to date with the network, and lets go of every window that was
     * looking at a place that is gone.
     */
    private static void relink(MinecraftServer server, String mark) {
        for (Map<Long, Open> m : OPEN.values()) {
            for (Open o : List.copyOf(m.values())) {
                if (!o.marked()) continue;
                if (o.mark.equals(mark)) o.standing = standingOf(server, o);
                if (o.tuner != null && dest(server, o.tunedTo) == null) untune(o);
                if (o.backTo >= 0 && dest(server, o.backTo) == null) o.backTo = -1;
                refresh(o);
            }
        }
    }

    private static long standingOf(MinecraftServer server, Open o) {
        WindowwayNetwork net = WindowwayNetwork.get(server);
        Station sill = net.sill(o.mark);
        if (o.kind == Kind.PAGE) return sill == null || !answers(server, sill, o.owner) ? -1 : sill.frameId();
        Station me = net.station(o.wid);
        if (me == null) return -1;
        if (me.role() == Role.SILL) {
            Station kin = net.kin(o.mark);
            return kin == null ? -1 : kin.frameId();
        }
        if (sill == null) return -1;
        // drawing another witch's mark is not finding her Sill: a stranger's window opens onto it only once she has
        if (me.role() == Role.VISITOR && !sill.owner().equals(me.owner()) && !answers(server, sill, me.owner())) return -1;
        return sill.frameId();
    }

    /**
     * A witch has found a window, by standing by it or coming out of it. Her pages answer to it from then on, and so
     * do the windows she drew bearing its mark.
     */
    public static void discover(MinecraftServer server, UUID who, UUID wid) {
        WindowwayNetwork net = WindowwayNetwork.get(server);
        if (who == null || wid == null || net.knows(who, wid)) return;
        net.discover(who, wid);
        Station st = net.station(wid);
        if (st != null) relink(server, st.mark());
    }

    /** Whether a Sill answers a witch's page: her own always do, others once she has stood by them or come through. */
    private static boolean answers(MinecraftServer server, Station sill, UUID who) {
        if (who != null && sill.owner().equals(who)) return true;
        if (!rule(server, discovery)) return true;
        return who != null && WindowwayNetwork.get(server).knows(who, sill.wid());
    }

    private static long target(Open o) {
        if (o.kind == Kind.INERT) return -1;
        if (o.tuner != null) return o.tunedTo;
        if (o.backTo >= 0) return o.backTo;
        return o.standing;
    }

    private static void refresh(Open o) {
        if (o.backTo >= 0 && o.level.getGameTime() >= o.backUntil) o.backTo = -1;
        long want = target(o);
        if (want != o.frame.link()) setLink(o, want);
    }

    /** Every change of where a marked window looks goes through here; it starts the settling time. */
    private static void setLink(Open o, long link) {
        boolean was = o.frame.link() >= 0;
        o.frame = o.frame.withLink(link);
        // a window opening onto its place has not turned: no one has seen it look anywhere else
        o.linkedAt = o.opening ? o.level.getGameTime() - SETTLE_TICKS : o.level.getGameTime();
        if (link < 0) closeGaps(o.level, o);
        else if (o.gaps.isEmpty()) openGaps(o.level, o);
        Vec3 c = o.frame.center();
        if (link >= 0) o.level.playSound(null, c.x, c.y, c.z, ModSounds.WINDOWWAY_TUNE.get(), SoundSource.BLOCKS, was ? 0.8f : 1f, was ? 1.1f : 0.9f);
        MOVED.computeIfAbsent(o.level.dimension(), k -> new java.util.LinkedHashSet<>()).add(o.frame.id());
    }

    private static void untune(Open o) {
        o.tuner = null;
        o.tunedMark = null;
        o.tunedTo = -1;
        o.tunerLeftAt = -1;
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

    /** Where a link leads, or null when nothing is there any more. */
    public static Dest dest(MinecraftServer server, long frameId) {
        if (frameId < 0) return null;
        Open o = find(frameId);
        Station st = (frameId & TRANSIENT) == 0 ? WindowwayNetwork.get(server).byFrame(frameId) : null;
        if (o != null) return new Dest(o.level, o.frame, st, true);
        if (st == null) return null;
        ServerLevel level = server.getLevel(st.dim());
        if (level == null) return null;
        return new Dest(level, PortalFrame.of(frameId, st.center(), st.up(), st.n(), st.radius(), -1), st, false);
    }

    /**
     * Whether a place will take someone in: all its ink still burns (which the network can tell while its chunk is
     * not loaded), it is not held shut by a Tread, and if it is drawn in blood only its own witch may come.
     */
    public static boolean valid(Dest d, Entity who, ServerLevel from) {
        return valid(d, who, from, false);
    }

    /** As {@link #valid(Dest, Entity, ServerLevel)}; with {@code anyone} set, whether it would take in its own witch. */
    private static boolean valid(Dest d, Entity who, ServerLevel from, boolean anyone) {
        if (d == null) return false;
        if (d.level() != from && !rule(from.getServer(), acrossDimensions)) return false;
        Station st = d.station();
        if (st == null) return d.live();
        Open live = d.live() ? find(d.frame().id()) : null;
        Set<Long> strokes = live != null ? live.spell.strokes : st.strokes();
        if (strokes.isEmpty()) return false;
        GlyphLevelData data = GlyphLevelData.get(d.level());
        for (long id : strokes) {
            InkStroke s = data.get(id);
            if (s == null || s.state() != StrokeState.ACTIVE) return false;
        }
        if (st.gated() && (live == null || live.spell.waiting())) return false;
        return anyone || !st.blood() || (who instanceof Player && st.owner().equals(who.getUUID()));
    }

    public static void tick(ServerLevel level) {
        MinecraftServer server = level.getServer();
        long now = level.getGameTime();
        if (level.dimension() == Level.OVERWORLD && now % 100 == 37) sweep(server);
        STEP.clear();
        Map<Long, Open> open = of(level);
        if (!open.isEmpty()) {
            boolean retune = now % RETUNE_EVERY == 0;
            List<Runnable> later = new ArrayList<>();
            for (Open o : List.copyOf(open.values())) {
                if (open.get(o.frame.id()) != o) continue;
                if (!level.shouldTickBlocksAt(BlockPos.containing(o.frame.center()))) continue;
                if (o.marked()) {
                    if (retune) {
                        restrung(level, o.spell);
                        retune(level, o, now);
                        lookAround(level, o);
                    } else if (o.backTo >= 0 && now >= o.backUntil) {
                        refresh(o);
                    }
                }
                if (o.frame.link() < 0) continue;
                if (o.kind == Kind.BARE) carryBare(level, o, now);
                else carryMarked(level, o, now, later);
            }
            for (Runnable r : later) r.run();
            if (now % 200 == 0) {
                COOLDOWN.values().removeIf(t -> now - t > 100);
                LAST_AT.values().removeIf(t -> now - t > 100);
                LAST.keySet().retainAll(LAST_AT.keySet());
                OWED.removeIf(id -> server.getPlayerList().getPlayer(id) == null);
                PENDING_BACK.values().removeIf(b -> now >= b.until());
            }
        }
        Set<Long> moved = MOVED.remove(level.dimension());
        if (moved != null) tellTurns(level, moved);
        if (now % OWED_EVERY == 0 && !OWED.isEmpty()) {
            for (ServerPlayer p : level.players()) if (OWED.contains(p.getUUID())) syncTo(p);
        }
    }

    /** How near a window a player must be to be told at once that it turned. */
    public static final double TELL_RANGE = 160;
    private static final int OWED_EVERY = 40;

    /** Tells those near each window where it looks now; those further off are owed a whole sync. */
    private static void tellTurns(ServerLevel level, Set<Long> moved) {
        MinecraftServer server = level.getServer();
        Map<Long, Open> here = of(level);
        long now = level.getGameTime();
        for (long id : moved) {
            Open o = here.get(id);
            if (o == null) continue;
            Told told = told(server, o, here);
            int age = (int) Math.max(0, Math.min(Integer.MAX_VALUE, now - o.linkedAt));
            Vec3 c = o.frame.center();
            for (ServerPlayer p : level.players()) {
                if (p.position().distanceToSqr(c) > TELL_RANGE * TELL_RANGE) {
                    OWED.add(p.getUUID());
                    continue;
                }
                UUID me = p.getUUID();
                Net.toPlayer(p, new Payloads.WindowwayLink(id, o.frame.link(), told.frameFor(me).flags(),
                        java.util.Optional.ofNullable(told.farFor(me)), age));
            }
        }
    }

    private static void carryBare(ServerLevel level, Open o, long now) {
        Open to = of(level).get(o.frame.link());
        if (to == null) return;
        PortalFrame f = o.frame;
        AABB box = new AABB(f.center(), f.center()).inflate(f.radius() + 2);
        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, box.inflate(FAST), p -> p.isAlive() && !p.isPassenger() && !p.isSpectator())) {
            Vec3 cur = p.position().add(0, p.getBbHeight() / 2, 0);
            Vec3 prev = stepped(p, cur, now);
            Long cd = COOLDOWN.get(p.getUUID());
            if (prev == null || (cd != null && now - cd < 6) || prev.distanceTo(cur) > FAST || !crossed(f, prev, cur)) continue;
            crossBare(level, p, o, to, prev);
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

    private static void carryMarked(ServerLevel level, Open o, long now, List<Runnable> later) {
        PortalFrame f = o.frame;
        Double back = backOf(o);
        AABB box = new AABB(f.center(), f.center()).inflate(f.radius() + 2);
        // wide enough to have seen, the tick before, whoever comes in falling or flying as fast as anyone can
        AABB wide = box.inflate(FAST);
        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, wide, p -> p.isAlive() && !p.isPassenger() && !p.isSpectator())) {
            Vec3 cur = p.position().add(0, p.getBbHeight() / 2, 0);
            Vec3 prev = stepped(p, cur, now);
            // no cooldown here: the server saw her go in from the front this very tick, so she is either taken or
            // set back out, never left in the wall; a refusal sets no cooldown, and a crossing moves her off this plane
            if (prev == null || prev.distanceTo(cur) > FAST) continue;
            if (crossed(f, prev, cur)) {
                cross(level, p, o, ANY_LINK, false, prev);
                continue;
            }
            // to one it turns away it is a wall to all of her, not to her middle only: she is stopped where the front
            // of her meets the plane, so on a floor window her feet do not sink into the hole before she is held up
            Vec3 lead = f.n().normalize().scale(clear(f, p) - (CLEAR - 0.3));
            if (crossed(f, prev.subtract(lead), cur.subtract(lead)) && !takes(level, o, p, now)) refused(level, o, p, prev);
            // and from behind: the hole the window is drawn over is no way round it, she is stopped at its back as at a wall
            else if (back != null && crossedBack(f, back, prev.add(lead), cur.add(lead)) && !takes(level, o, p, now)) refusedBehind(level, o, p, back, prev);
        }
        // a mount carries no one across, so one that bears someone the window turns away is turned away with her
        for (Entity v : level.getEntities((Entity) null, wide, e -> e.isAlive() && e.isVehicle() && !e.isPassenger()
                && e.getPassengersAndSelf().anyMatch(r -> r instanceof ServerPlayer))) {
            Vec3 cur = v.position().add(0, v.getBbHeight() / 2, 0);
            Vec3 prev = stepped(v, cur, now);
            Vec3 lead = f.n().normalize().scale(clear(f, v) - (CLEAR - 0.3));
            if (prev == null || prev.distanceTo(cur) > FAST) continue;
            boolean behind = !crossed(f, prev.subtract(lead), cur.subtract(lead));
            if (behind && (back == null || !crossedBack(f, back, prev.add(lead), cur.add(lead)))) continue;
            List<ServerPlayer> turned = new ArrayList<>();
            for (Entity rider : v.getIndirectPassengers()) {
                if (rider instanceof ServerPlayer sp && !takes(level, o, sp, now)) turned.add(sp);
            }
            if (turned.isEmpty()) continue;
            if (v instanceof LivingEntity || v instanceof VehicleEntity) {
                bounce(level, o, v, prev, now);
                continue;
            }
            // a seat that something else moves (a contraption, a modded chair) cannot be held back: she is knocked off it
            Vec3 moved = cur.subtract(prev);
            for (ServerPlayer sp : turned) {
                Vec3 mid = sp.position().add(0, sp.getBbHeight() / 2, 0);
                sp.stopRiding();
                if (behind) refusedBehind(level, o, sp, back, mid.subtract(moved));
                else refused(level, o, sp, mid.subtract(moved));
            }
        }
        Station from = o.wid == null ? null : WindowwayNetwork.get(level.getServer()).station(o.wid);
        boolean shut = from != null && from.blood();
        for (Entity e : level.getEntities((Entity) null, box, e -> !(e instanceof Player) && e.isAlive() && !e.isPassenger() && !e.isVehicle())) {
            Long cd = COOLDOWN.get(e.getUUID());
            if (cd != null && now - cd < 4) continue;
            Vec3 prev = new Vec3(e.xo, e.yo + e.getBbHeight() / 2, e.zo);
            Vec3 cur = e.position().add(0, e.getBbHeight() / 2, 0);
            boolean thrown = e instanceof Projectile;
            if (!crossed(f, prev, cur)) {
                // thrown at the back of the hole, it glances off there just as it would off the front
                if (thrown && back != null && crossedBack(f, back, prev, cur) && !carries(level, o, e, shut, now)) bounce(level, o, e, prev, now);
                continue;
            }
            // what is thrown at a window that will not carry it glances off: an ender pearl lands nowhere behind the wall
            if (shut || e.getType().is(BLOCKED)) {
                if (thrown) bounce(level, o, e, prev, now);
                continue;
            }
            Dest d = dest(level.getServer(), f.link());
            if (now - o.linkedAt < SETTLE_TICKS || !valid(d, e, level)
                    || (d.level() != level && !e.canChangeDimensions(level, d.level()))) {
                if (thrown) bounce(level, o, e, prev, now);
                continue;
            }
            double h = e.getBbHeight() / 2;
            Vec3 at = PortalFrame.mapPoint(f, d.frame(), cur);
            arriving(d.level(), at, e.getId());
            Vec3 feet = ExitCheck.find(d.level(), e, at.subtract(0, h, 0), d.frame().n());
            if (feet == null) {
                if (thrown) bounce(level, o, e, prev, now);
                continue;
            }
            COOLDOWN.put(e.getUUID(), now);
            float[] yp = PortalFrame.yawPitch(PortalFrame.mapDir(f, d.frame(), e.getLookAngle()));
            Vec3 vel = PortalFrame.mapDir(f, d.frame(), e.getDeltaMovement());
            if (d.level() == level) {
                place(e, feet, yp, vel);
                whoosh(level, f, d.level(), d.frame());
            } else {
                later.add(() -> {
                    if (!e.isAlive() || e.level() != level) return;
                    Entity moved = e.changeDimension(new DimensionTransition(d.level(), feet, vel, yp[0], yp[1], DimensionTransition.DO_NOTHING));
                    if (moved != null) {
                        COOLDOWN.put(moved.getUUID(), now);
                        whoosh(level, f, d.level(), d.frame());
                    }
                });
            }
        }
    }

    /** Asks for the far end to be loaded before anyone is set down there. */
    private static void arriving(ServerLevel level, Vec3 at, int who) {
        ChunkPos cp = new ChunkPos(BlockPos.containing(at));
        level.getChunkSource().addRegionTicket(TicketType.POST_TELEPORT, cp, 1, who);
        level.getChunk(cp.x, cp.z);
    }

    private static void place(Entity e, Vec3 feet, float[] yp, Vec3 vel) {
        e.teleportTo(feet.x, feet.y, feet.z);
        e.setYRot(yp[0]);
        e.setXRot(yp[1]);
        e.setYHeadRot(yp[0]);
        e.setDeltaMovement(vel);
        e.hurtMarked = true;
        e.setOldPosAndRot();
    }

    /**
     * Turns a marked window toward the Sill of a page held up before it, and back to where it stands once the page is
     * gone. The first witch to hold up a page keeps the window until she has gone and those who follow her are through.
     */
    private static void retune(ServerLevel level, Open o, long now) {
        MinecraftServer server = level.getServer();
        if (o.spell.waiting()) return;
        if (o.tuner != null) {
            ServerPlayer t = server.getPlayerList().getPlayer(o.tuner);
            boolean present = t != null && t.serverLevel() == level && inTuningSpot(o.frame, t) && o.tunedMark.equals(WindowMark.held(t));
            if (present) o.tunerLeftAt = -1;
            else if (o.tunerLeftAt < 0) o.tunerLeftAt = now;
            boolean hold = present || now - o.tunerLeftAt < HOLD_AFTER_LEAVE || now - o.lastCross < HOLD_AFTER_CROSS;
            if (hold && dest(server, o.tunedTo) != null) return;
            untune(o);
            refresh(o);
        }
        PortalFrame f = o.frame;
        ServerPlayer best = null;
        String bestMark = "";
        double bestD = Double.MAX_VALUE;
        AABB box = new AABB(f.center(), f.center()).inflate(f.radius() + TUNE_FAR + 1);
        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, box, p -> p.isAlive() && !p.isSpectator())) {
            if (!inTuningSpot(f, p)) continue;
            String m = WindowMark.held(p);
            if (m.isEmpty()) continue;
            double d = p.position().distanceToSqr(f.center());
            if (d < bestD) { bestD = d; best = p; bestMark = m; }
        }
        if (best == null) return;
        Station sill = WindowwayNetwork.get(server).sill(bestMark);
        if (sill != null && (sill.frameId() == f.id() || sill.frameId() == f.link())) return;
        if (sill == null || !answers(server, sill, best.getUUID()) || !valid(dest(server, sill.frameId()), best, level)) {
            if (best.getUUID().equals(o.refusedWho) && bestMark.equals(o.refusedMark) && now - o.refusedAt < 40) return;
            o.refusedWho = best.getUUID();
            o.refusedMark = bestMark;
            o.refusedAt = now;
            refused(level, o, null, null);
            return;
        }
        o.tuner = best.getUUID();
        o.tunedMark = bestMark;
        o.tunedTo = sill.frameId();
        o.tunerLeftAt = -1;
        o.backTo = -1;
        refresh(o);
    }

    private static boolean inTuningSpot(PortalFrame f, Player p) {
        Vec3 c = p.position().add(0, p.getBbHeight() / 2, 0);
        double side = f.side(c);
        return side >= TUNE_NEAR && side <= TUNE_FAR && f.inPlane(c) <= f.radius() + 1;
    }

    /** A Sill a witch has stood by for a while answers her pages from then on. */
    private static void lookAround(ServerLevel level, Open o) {
        if (o.kind != Kind.STATION) return;
        WindowwayNetwork net = WindowwayNetwork.get(level.getServer());
        Station st = net.station(o.wid);
        if (st == null || st.role() != Role.SILL) return;
        Vec3 c = o.frame.center();
        Set<UUID> here = new HashSet<>();
        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, new AABB(c, c).inflate(SEEN_RANGE),
                p -> p.isAlive() && !p.isSpectator() && p.position().distanceTo(c) <= SEEN_RANGE)) {
            UUID id = p.getUUID();
            here.add(id);
            if (net.knows(id, o.wid)) continue;
            if (o.near.merge(id, RETUNE_EVERY, Integer::sum) >= SEEN_TICKS) {
                discover(level.getServer(), id, o.wid);
                o.near.remove(id);
            }
        }
        o.near.keySet().retainAll(here);
    }

    /** Forgets windows whose ink has all gone dark while no one was there to see it close. */
    private static void sweep(MinecraftServer server) {
        WindowwayNetwork net = WindowwayNetwork.get(server);
        List<Station> stale = new ArrayList<>();
        for (Station s : net.all()) if (stale(server, s)) stale.add(s);
        for (Station s : stale) {
            net.forget(s.wid());
            relink(server, s.mark());
        }
    }

    private static void prune(MinecraftServer server, String mark) {
        WindowwayNetwork net = WindowwayNetwork.get(server);
        for (Station s : net.withMark(mark)) if (stale(server, s)) net.forget(s.wid());
    }

    private static boolean stale(MinecraftServer server, Station s) {
        if (find(s.frameId()) != null) return false;
        ServerLevel level = server.getLevel(s.dim());
        if (level == null) return false;
        GlyphLevelData data = GlyphLevelData.get(level);
        for (long id : s.strokes()) {
            InkStroke st = data.get(id);
            if (st != null && st.state() == StrokeState.ACTIVE) return false;
        }
        return true;
    }

    public static boolean crossed(PortalFrame f, Vec3 prev, Vec3 cur) {
        double a = f.side(prev), b = f.side(cur);
        if (!(a >= 0 && b < 0)) return false;
        double t = a / (a - b);
        Vec3 hit = prev.add(cur.subtract(prev).scale(t));
        return f.inOpening(hit);
    }

    /**
     * How far behind a window's plane the back of the hole it opened lies, as a side: the far faces of the blocks it
     * opened. Null while it has opened none, when there is no hole to come at from behind.
     */
    private static Double backOf(Open o) {
        if (o.gaps.isEmpty()) return null;
        PortalFrame f = o.frame;
        Vec3 n = f.n();
        double half = (Math.abs(n.x) + Math.abs(n.y) + Math.abs(n.z)) / 2;
        double back = 0;
        for (BlockPos g : o.gaps) back = Math.min(back, f.side(Vec3.atCenterOf(g)) - half);
        return back;
    }

    private static final double FLUSH = 1e-4;

    /** Whether a path from behind a window comes up out of the back of its hole, {@code back} being where that lies. */
    static boolean crossedBack(PortalFrame f, double back, Vec3 prev, Vec3 cur) {
        // one who stood flush against the back of the wall beside the hole is behind it, rounding or no rounding
        double a = f.side(prev) - back, b = f.side(cur) - back;
        if (!(a <= FLUSH && b > FLUSH)) return false;
        Vec3 hit = prev.add(cur.subtract(prev).scale(a / (a - b)));
        return f.inOpening(hit.subtract(f.n().scale(back)));
    }

    /** Whether a marked window would carry a thing across now, as far as what it is and where the window looks go. */
    private static boolean carries(ServerLevel level, Open o, Entity e, boolean shut, long now) {
        if (shut || e.getType().is(BLOCKED) || now - o.linkedAt < SETTLE_TICKS) return false;
        Dest d = dest(level.getServer(), o.frame.link());
        return valid(d, e, level) && (d.level() == level || e.canChangeDimensions(level, d.level()));
    }

    private static void carry(ServerLevel level, Entity e, PortalFrame from, PortalFrame to) {
        double h = e.getBbHeight() / 2;
        Vec3 c = PortalFrame.mapPoint(from, to, e.position().add(0, h, 0));
        float[] yp = PortalFrame.yawPitch(PortalFrame.mapDir(from, to, e.getLookAngle()));
        Vec3 vel = PortalFrame.mapDir(from, to, e.getDeltaMovement());
        place(e, c.subtract(0, h, 0), yp, vel);
        whoosh(level, from, level, to);
    }

    public static void handleCross(ServerPlayer player, long id) {
        handleCross(player, id, ANY_LINK, false);
    }

    public static void handleCross(ServerPlayer player, long id, long expectedLink) {
        handleCross(player, id, expectedLink, false);
    }

    /**
     * A player's client saw her go through a window. {@code expectedLink} is where the window looked on her screen;
     * if it has turned since, or has only just turned, she is not taken. {@code carried} says whether her client
     * already set her down on the far side; if not, or if she is set down somewhere else, she is told where she is.
     */
    public static void handleCross(ServerPlayer player, long id, long expectedLink, boolean carried) {
        ServerLevel level = player.serverLevel();
        Open o = of(level).get(id);
        if (o == null || o.frame.link() < 0) return;
        long now = level.getGameTime();
        Long cd = COOLDOWN.get(player.getUUID());
        if (cd != null && now - cd < 6) return;
        // whatever her client says, she goes in only from the front; from behind the wall there is nothing to cross
        Vec3 was = cameFrom(o.frame, player, now);
        if (was == null) return;
        if (o.kind == Kind.BARE) {
            Open to = of(level).get(o.frame.link());
            if (to != null) crossBare(level, player, o, to, was);
            return;
        }
        cross(level, player, o, expectedLink, carried, was);
    }

    /** How far past a window's plane a client may say she is with nothing else to show she came from the front: a stride. */
    static final double STRIDE = 0.5;
    /** How far past the plane she may be when the server saw her in front of it a tick ago: more than any step, less than the wall. */
    static final double REACH = 1.5;

    /**
     * Where someone who says she went into a window was in front of it, or null if she was not: on the far side of the
     * wall, she stays there. She is in front if her middle is; or no more than a step behind the plane and seen in front
     * of it a moment ago; or, seen nowhere, no more than a stride in, as far as a client can step ahead of the server.
     */
    private static Vec3 cameFrom(PortalFrame f, ServerPlayer player, long now) {
        Vec3 c = player.position().add(0, player.getBbHeight() / 2, 0);
        double side = f.side(c);
        if (side >= 0) return c;
        Vec3 was = LAST.get(player.getUUID());
        Long at = LAST_AT.get(player.getUUID());
        if (was != null && at != null && now - at <= 2 && was.distanceTo(c) <= FAST && f.side(was) >= 0
                && (side >= -REACH || crossed(f, was, c))) return was;
        return side >= -STRIDE ? c.add(f.n().scale(1e-3 - side)) : null;
    }

    /**
     * Where someone stood at the end of the last tick, the same for every window she is near, or null if no window saw
     * her then: a place noted long ago, before she went off and came back by another way, is no step. Notes where she
     * is now.
     */
    private static Vec3 stepped(Entity p, Vec3 cur, long now) {
        UUID id = p.getUUID();
        if (!STEP.containsKey(id)) {
            Long at = LAST_AT.get(id);
            STEP.put(id, at != null && now - at <= 2 ? LAST.get(id) : null);
        }
        seen(p, cur, now);
        return STEP.get(id);
    }

    private static void seen(Entity p, Vec3 mid, long now) {
        LAST.put(p.getUUID(), mid);
        LAST_AT.put(p.getUUID(), now);
    }

    /** How far anyone moves in a tick, at most, falling or flying, for it still to be a step and not a leap elsewhere. */
    static final double FAST = 5;

    /** Whether a window would take her in now, as far as who she is and where it looks go. */
    private static boolean takes(ServerLevel level, Open o, ServerPlayer who, long now) {
        return now - o.linkedAt >= SETTLE_TICKS && valid(dest(level.getServer(), o.frame.link()), who, level);
    }

    /**
     * Sets back something a window would not carry where it was a tick ago, in front, stopped short or, if thrown,
     * glancing off the plane; whoever steers it is told, as her client moves it.
     */
    private static void bounce(ServerLevel level, Open o, Entity e, Vec3 prevMid, long now) {
        PortalFrame f = o.frame;
        Vec3 n = f.n().normalize();
        Vec3 v = e.getDeltaMovement();
        Vec3 feet = prevMid.subtract(0, e.getBbHeight() / 2, 0);
        e.setPos(feet.x, feet.y, feet.z);
        double into = v.dot(n);
        e.setDeltaMovement(e instanceof Projectile ? v.subtract(n.scale(2 * into)).scale(0.5) : v.subtract(n.scale(into)));
        e.hurtMarked = true;
        seen(e, prevMid, now);
        if (e.getControllingPassenger() instanceof ServerPlayer driver) driver.connection.send(new ClientboundMoveVehiclePacket(e));
        thud(level, o, now);
    }

    /** The dull knock of a window turning someone away, no oftener than a few times a second. */
    private static void thud(ServerLevel level, Open o, long now) {
        if (now - o.refusedAt < 6) return;
        o.refusedAt = now;
        Vec3 c = o.frame.center();
        level.playSound(null, c.x, c.y, c.z, ModSounds.WINDOWWAY_REFUSE.get(), SoundSource.BLOCKS, 0.8f, 1f);
        Net.toNear(level, c, 64, new Payloads.WindowwayRefuse(o.frame.id()));
    }

    /**
     * Where a player stands relative to a window she is crossing, or null if she is not in it. {@code was}, where she
     * stood in front of it before, lets one who came in too fast to be seen in the opening be carried from where her
     * path went through it.
     */
    private static Vec3 inWindow(PortalFrame f, ServerPlayer player, Vec3 was) {
        Vec3 c = player.position().add(0, player.getBbHeight() / 2, 0);
        double side = f.side(c);
        if (side < -2.5 && was != null && crossed(f, was, c)) {
            double a = f.side(was);
            Vec3 hit = was.add(c.subtract(was).scale(a / (a - side)));
            return hit.subtract(f.n().scale(0.05));
        }
        if (Math.abs(side) > 2.5 || f.inPlane(c) > f.radius() + 1.5) return null;
        Vec3 onPlane = c.subtract(f.n().scale(side));
        if (!f.inOpening(onPlane)) return null;
        return side > -0.05 ? onPlane.subtract(f.n().scale(0.05)) : c;
    }

    private static void crossBare(ServerLevel level, ServerPlayer player, Open o, Open to, Vec3 was) {
        PortalFrame f = o.frame;
        Vec3 c = inWindow(f, player, was);
        if (c == null) return;
        COOLDOWN.put(player.getUUID(), level.getGameTime());
        double h = player.getBbHeight() / 2;
        Vec3 at = PortalFrame.mapPoint(f, to.frame, c);
        float[] yp = PortalFrame.yawPitch(PortalFrame.mapDir(f, to.frame, player.getLookAngle()));
        Vec3 vel = PortalFrame.mapDir(f, to.frame, player.getDeltaMovement());
        level.getChunkSource().addRegionTicket(TicketType.POST_TELEPORT, new ChunkPos(BlockPos.containing(at)), 1, player.getId());
        if (player.position().distanceTo(at) > SEAMLESS) {
            player.teleportTo(level, at.x, at.y - h, at.z, yp[0], yp[1]);
        } else {
            player.moveTo(at.x, at.y - h, at.z, yp[0], yp[1]);
            player.setYHeadRot(yp[0]);
            player.connection.resetPosition();
            level.getChunkSource().move(player);
        }
        seen(player, at, level.getGameTime());
        player.setDeltaMovement(vel);
        player.resetFallDistance();
        whoosh(level, f, level, to.frame);
        MagicTrigger.fire(player, "windowway");
    }

    /** {@code was} is where she was in front of the window before she went in; she is set back there if it will not take her. */
    private static boolean cross(ServerLevel level, ServerPlayer player, Open o, long expectedLink, boolean carried, Vec3 was) {
        PortalFrame f = o.frame;
        Vec3 c = inWindow(f, player, was);
        if (c == null) {
            // in past the plane, yet nowhere the window can carry her from: it is a wall to her, not a hole
            if (f.side(player.position().add(0, player.getBbHeight() / 2, 0)) < 0) refused(level, o, player, was);
            return false;
        }
        MinecraftServer server = level.getServer();
        long now = level.getGameTime();
        Dest d = dest(server, f.link());
        boolean stale = expectedLink != ANY_LINK && expectedLink != f.link();
        if (stale || now - o.linkedAt < SETTLE_TICKS || !valid(d, player, level)) {
            WitchsAtelier.LOGGER.debug("[windowway] {} turned away at {}: {}", player.getGameProfile().getName(), f.id(),
                    stale ? "stale link" : now - o.linkedAt < SETTLE_TICKS ? "still settling" : d == null ? "nothing there" : "not taking her in");
            refused(level, o, player, was);
            return false;
        }
        double h = player.getBbHeight() / 2;
        Vec3 at = PortalFrame.mapPoint(f, d.frame(), c);
        ServerLevel there = d.level();
        arriving(there, at, player.getId());
        Vec3 feet = ExitCheck.find(there, player, at.subtract(0, h, 0), d.frame().n());
        if (feet == null) {
            WitchsAtelier.LOGGER.debug("[windowway] {} turned away at {}: no room at {}", player.getGameProfile().getName(), f.id(), at);
            refused(level, o, player, was);
            return false;
        }
        COOLDOWN.put(player.getUUID(), now);
        float[] yp = PortalFrame.yawPitch(PortalFrame.mapDir(f, d.frame(), player.getLookAngle()));
        Vec3 vel = PortalFrame.mapDir(f, d.frame(), player.getDeltaMovement());
        if (there != level || player.position().distanceTo(feet) > SEAMLESS) {
            player.teleportTo(there, feet.x, feet.y, feet.z, yp[0], yp[1]);
        } else if (carried && feet.distanceToSqr(at.subtract(0, h, 0)) < 1e-4) {
            // her client stepped through ahead of us, to this very spot: nothing to tell it
            player.moveTo(feet.x, feet.y, feet.z, yp[0], yp[1]);
            player.setYHeadRot(yp[0]);
            player.connection.resetPosition();
            level.getChunkSource().move(player);
        } else {
            // her client left her where she was, or set her down where there was no room: it must hear where she is
            player.connection.teleport(feet.x, feet.y, feet.z, yp[0], yp[1]);
            player.setYHeadRot(yp[0]);
            level.getChunkSource().move(player);
        }
        seen(player, feet.add(0, h, 0), now);
        player.setDeltaMovement(vel);
        player.hurtMarked = true;
        player.resetFallDistance();
        o.lastCross = now;
        whoosh(level, f, there, d.frame());
        if (d.station() != null) discover(server, player.getUUID(), d.station().wid());
        openBack(d, f.id(), now);
        MagicTrigger.fire(player, "windowway");
        if (there != level || f.center().distanceTo(d.frame().center()) > FAR) MagicTrigger.fire(player, "windowway_far");
        return true;
    }

    /** A window that looks nowhere of its own turns back toward where the traveller came from, for a little while. */
    private static void openBack(Dest d, long from, long now) {
        Open t = find(d.frame().id());
        if (t == null) {
            if (d.station() != null) PENDING_BACK.put(d.frame().id(), new Back(from, now + BACK_LINK_TICKS));
            return;
        }
        if (!t.marked() || t.standing >= 0 || t.tuner != null || (t.backTo >= 0 && t.backTo != from)) return;
        t.backTo = from;
        t.backUntil = now + BACK_LINK_TICKS;
        refresh(t);
    }

    /**
     * A window will not take someone: a dull knock, and whoever was halfway in is set back out in front. This is the
     * one place a refusal is made known, so whatever else should show it belongs here. To one it turns away a window is
     * as good as a wall: however often she walks into it, the server sets her back on the near side of its plane, to a
     * spot she fits; {@code was}, where she stood in front of it before, is null for someone who did not come from the
     * front, and she is left where she is.
     */
    private static void refused(ServerLevel level, Open o, ServerPlayer who, Vec3 was) {
        PortalFrame f = o.frame;
        Vec3 c = f.center();
        long now = level.getGameTime();
        if (now - o.refusedAt >= 6 || who == null) {
            level.playSound(null, c.x, c.y, c.z, ModSounds.WINDOWWAY_REFUSE.get(), SoundSource.BLOCKS, 0.8f, 1f);
            Net.toNear(level, c, 64, new Payloads.WindowwayRefuse(f.id()));
        }
        if (who == null) return;
        o.refusedAt = now;
        double h = who.getBbHeight() / 2;
        double side = f.side(who.position().add(0, h, 0));
        if (side < clear(f, who)) {
            // only someone half way in from the front is set back out; whoever is behind the wall stays there
            if (was == null) return;
            Vec3 back = outFront(level, f, who, side, was);
            if (back == null) return;
            // her client may have stepped through ahead and turned her as if she had come out at the far end; the
            // plain teleport keeps whatever way her client faces, so she is told outright which way she faces here
            who.connection.teleport(back.x, back.y, back.z, who.getYRot(), who.getXRot());
            seen(who, back.add(0, h, 0), now);
        }
        who.setDeltaMovement(f.n().scale(0.25));
        who.hurtMarked = true;
    }

    /**
     * A window will not take someone who comes at the back of its hole: the same knock, and she is set back out behind
     * the wall, to a spot she fits, else where she stood a moment ago, {@code was}; never through to the front.
     */
    private static void refusedBehind(ServerLevel level, Open o, ServerPlayer who, double back, Vec3 was) {
        PortalFrame f = o.frame;
        long now = level.getGameTime();
        thud(level, o, now);
        double h = who.getBbHeight() / 2;
        Vec3 n = f.n().normalize();
        double side = f.side(who.position().add(0, h, 0)), clear = clear(f, who);
        if (side > back - clear) {
            Vec3 to = null;
            for (double out : new double[]{clear, clear + 0.25, clear + 0.65, clear + 1.15}) {
                Vec3 at = who.position().add(n.scale(back - out - side));
                if (ExitCheck.free(level, who, at)) { to = at; break; }
                Vec3 up = at.add(0, 0.01, 0);
                if (ExitCheck.free(level, who, up)) { to = up; break; }
            }
            if (to == null && was != null && f.side(was) <= back - clear + 1e-3) {
                Vec3 before = was.subtract(0, h, 0);
                if (level.noCollision(who, who.getDimensions(who.getPose()).makeBoundingBox(before))) to = before;
            }
            if (to != null) {
                who.connection.teleport(to.x, to.y, to.z, who.getYRot(), who.getXRot());
                seen(who, to.add(0, h, 0), now);
            }
        }
        who.setDeltaMovement(n.scale(-0.25));
        who.hurtMarked = true;
    }

    /** How far in front of a window's plane a turned-away traveller is set: the whole of her clear of the opening. */
    static final double CLEAR = 0.35;

    /**
     * How far her middle must stand off a window's plane for all of her to be in front of it: {@link #CLEAR} on a wall,
     * where she is as deep as she is wide; on a floor or a ceiling half her height and a little more, or her feet or
     * her head would be left down or up in the hole.
     */
    static double clear(PortalFrame f, Entity who) {
        Vec3 n = f.n().normalize();
        double half = (Math.abs(n.x) + Math.abs(n.z)) * who.getBbWidth() / 2 + Math.abs(n.y) * who.getBbHeight() / 2;
        return Math.max(CLEAR, half + CLEAR - 0.3);
    }

    /**
     * Where to set someone a window turned away, by her feet: straight back out along the window's front until she fits,
     * else where she stood before she went in. Never behind the plane, never inside a block.
     */
    private static Vec3 outFront(ServerLevel level, PortalFrame f, ServerPlayer who, double side, Vec3 was) {
        Vec3 n = f.n().normalize();
        double clear = clear(f, who);
        for (double out : new double[]{clear, clear + 0.25, clear + 0.65, clear + 1.15}) {
            Vec3 at = who.position().add(n.scale(out - side));
            if (ExitCheck.free(level, who, at)) return at;
            Vec3 up = at.add(0, 0.01, 0);
            if (ExitCheck.free(level, who, up)) return up;
        }
        double h = who.getBbHeight() / 2;
        if (f.side(was) < 0) return null;
        Vec3 before = was.subtract(0, h, 0);
        // she stood there a moment ago, so it held her, water or no water
        return level.noCollision(who, who.getDimensions(who.getPose()).makeBoundingBox(before)) ? before : null;
    }

    private static void whoosh(ServerLevel level, PortalFrame a, ServerLevel otherLevel, PortalFrame b) {
        level.playSound(null, a.center().x, a.center().y, a.center().z, ModSounds.WINDOWWAY.get(), SoundSource.PLAYERS, 0.8f, 1f);
        otherLevel.playSound(null, b.center().x, b.center().y, b.center().z, ModSounds.WINDOWWAY.get(), SoundSource.PLAYERS, 0.8f, 1f);
    }

    /** The colour a window's rim shows: its mark's for a marked window, -1 for a plain one. */
    public static int tint(long frameId) {
        Open o = find(frameId);
        return o == null || o.mark.isEmpty() ? -1 : WindowMark.tint(o.mark);
    }

    public static final int STATION = PortalFrame.STATION, VISITOR = PortalFrame.VISITOR, SHUT = PortalFrame.SHUT, TUNED = PortalFrame.TUNED;

    /**
     * What a client needs to know about a window beyond its frame, as {@link #STATION} and friends. A window onto a
     * blood Sill is not shut for being blood: only its witch may go through, and each client is told so for itself.
     */
    private static int flags(MinecraftServer server, Open o) {
        int f = 0;
        Station st = o.wid == null ? null : WindowwayNetwork.get(server).station(o.wid);
        if (o.kind == Kind.STATION || o.kind == Kind.INERT) f |= STATION;
        if (o.kind == Kind.PAGE || (st != null && st.role() == Role.VISITOR)) f |= VISITOR;
        if (o.tuner != null) f |= TUNED;
        if (o.marked() && o.frame.link() >= 0 && !valid(dest(server, o.frame.link()), null, o.level, true)) f |= SHUT;
        return f;
    }

    /**
     * One window as clients are told of it: its frame, the far end they cannot find among the windows of their own
     * dimension, and, when it looks at a Sill drawn in blood, the one witch who may see where that is. Everyone else
     * sees the window shut and is not told where it leads.
     */
    private record Told(PortalFrame frame, FarFrame far, UUID privateTo) {
        PortalFrame frameFor(UUID viewer) {
            return privateTo == null || privateTo.equals(viewer) ? frame : frame.withLook(frame.tint(), frame.flags() | SHUT);
        }

        FarFrame farFor(UUID viewer) {
            return privateTo == null || privateTo.equals(viewer) ? far : null;
        }
    }

    private static Told told(MinecraftServer server, Open o, Map<Long, Open> here) {
        long link = o.frame.link();
        Dest d = link < 0 ? null : dest(server, link);
        UUID privateTo = o.marked() && d != null && d.station() != null && d.station().blood() ? d.station().owner() : null;
        FarFrame far = d == null || here.containsKey(link) ? null : new FarFrame(o.frame.id(), d.level().dimension(), look(server, d));
        return new Told(look(server, o), far, privateTo);
    }

    /** A window's frame as a client is shown it: with its colour and what kind of window it is. */
    private static PortalFrame look(MinecraftServer server, Open o) {
        return o.frame.withLook(o.mark.isEmpty() ? -1 : WindowMark.tint(o.mark), flags(server, o));
    }

    private static PortalFrame look(MinecraftServer server, Dest d) {
        Open o = d.live() ? find(d.frame().id()) : null;
        if (o != null) return look(server, o);
        Station st = d.station();
        return st == null ? d.frame() : d.frame().withLook(WindowMark.tint(st.mark()), st.role() == Role.VISITOR ? VISITOR : STATION);
    }

    private static List<Told> toldOf(ServerLevel level) {
        MinecraftServer server = level.getServer();
        Map<Long, Open> here = of(level);
        List<Told> out = new ArrayList<>();
        for (Open o : here.values()) out.add(told(server, o, here));
        return out;
    }

    private static Payloads.WindowwaySync syncFor(List<Told> all, UUID viewer) {
        List<PortalFrame> frames = new ArrayList<>(all.size());
        List<FarFrame> far = new ArrayList<>();
        for (Told t : all) {
            frames.add(t.frameFor(viewer));
            FarFrame ff = t.farFor(viewer);
            if (ff != null) far.add(ff);
        }
        return new Payloads.WindowwaySync(frames, far);
    }

    public static void sync(ServerLevel level) {
        MOVED.remove(level.dimension());
        List<Told> all = toldOf(level);
        boolean same = all.stream().allMatch(t -> t.privateTo() == null);
        Payloads.WindowwaySync shared = same ? syncFor(all, null) : null;
        for (ServerPlayer p : level.players()) {
            OWED.remove(p.getUUID());
            Net.toPlayer(p, same ? shared : syncFor(all, p.getUUID()));
        }
    }

    public static void syncTo(ServerPlayer player) {
        OWED.remove(player.getUUID());
        Net.toPlayer(player, syncFor(toldOf(player.serverLevel()), player.getUUID()));
    }

    /** Shuts every window of a level that is going away; the network keeps them all. */
    public static void closeAll(ServerLevel level) {
        Map<Long, Open> open = OPEN.remove(level.dimension());
        MOVED.remove(level.dimension());
        if (open == null) return;
        for (Open o : open.values()) {
            closeGaps(level, o);
            BY_SPELL.remove(o.spell.id);
        }
    }
}
