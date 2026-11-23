package com.fimkov.witchsatelier.glyph;

import com.fimkov.witchsatelier.advancement.MagicTrigger;
import com.fimkov.witchsatelier.block.ArcaneLensBlock;
import com.fimkov.witchsatelier.block.Starfall;
import com.fimkov.witchsatelier.compat.Ships;
import com.fimkov.witchsatelier.entity.Taboo;
import com.fimkov.witchsatelier.entity.Witnesses;
import com.fimkov.witchsatelier.glyph.ink.InkSupply;
import com.fimkov.witchsatelier.glyph.ink.InkType;
import com.fimkov.witchsatelier.glyph.model.Sigil;
import com.fimkov.witchsatelier.glyph.model.SignType;
import com.fimkov.witchsatelier.glyph.page.GlyphPages;
import com.fimkov.witchsatelier.glyph.recognize.GlyphAnalyzer;
import com.fimkov.witchsatelier.glyph.recognize.GlyphReading;
import com.fimkov.witchsatelier.glyph.recognize.Pt;
import com.fimkov.witchsatelier.glyph.recognize.RingFinder;
import com.fimkov.witchsatelier.glyph.storage.GlyphLevelData;
import com.fimkov.witchsatelier.glyph.surface.InkStroke;
import com.fimkov.witchsatelier.glyph.surface.StrokeState;
import com.fimkov.witchsatelier.glyph.surface.SurfacePlane;
import com.fimkov.witchsatelier.item.InkPenItem;
import com.fimkov.witchsatelier.item.RagItem;
import com.fimkov.witchsatelier.network.Net;
import com.fimkov.witchsatelier.network.Payloads;
import com.fimkov.witchsatelier.registry.ModBlocks;
import com.fimkov.witchsatelier.registry.ModParticles;
import com.fimkov.witchsatelier.spell.ActiveSpell;
import com.fimkov.witchsatelier.spell.ElementLook;
import com.fimkov.witchsatelier.spell.GlyphFrame;
import com.fimkov.witchsatelier.spell.SpellCompiler;
import com.fimkov.witchsatelier.spell.SpellProgram;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.slf4j.Logger;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

public final class GlyphService {
    public static final double INK_WIDTH = 1.0 / 16;

    public static final double INK_PER_BLOCK = 4;
    public static final double REACH = 7;
    public static final double RAG_RADIUS = 0.24, RAG_MIN = 0.08, RAG_MAX = 0.8, PEN_ERASE_RADIUS = 0.12;

    public static final double MIN_BURST_RADIUS = 0.75;

    public static final double MIN_GLYPH_RADIUS = 0.4;

    public static final int WORN_FX = 1 << 20;

    public static final int WORN_SINKING = 1 << 21;

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<ResourceKey<Level>, Map<Long, ActiveSpell>> ACTIVE = new HashMap<>();
    private static long nextSpellId = 1;

    static final int BATCH_STROKES = 1024, BATCH_POINTS = 30000, BATCH_IDS = 16384, BATCH_WEAR_BYTES = 200_000;
    static final int MAX_PIECES = 8, DRAW_POINTS_PER_SECOND = 16384;

    public static boolean readOffThread = !Boolean.getBoolean("witchsatelier.syncReading");
    private static final java.util.concurrent.ExecutorService READER = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Witch's Atelier glyph reader");
        t.setDaemon(true);
        return t;
    });
    private static final Map<ResourceKey<Level>, java.util.concurrent.ConcurrentLinkedQueue<java.util.function.Consumer<ServerLevel>>> READ_DONE =
            new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.atomic.AtomicInteger READS_PENDING = new java.util.concurrent.atomic.AtomicInteger();

    private static final Map<ResourceKey<Level>, ArrayDeque<Long>> RELIGHT = new HashMap<>();
    private static final Set<ResourceKey<Level>> RELIGHT_SCANNED = new HashSet<>();
    private static int relightReading;

    private static final Map<UUID, long[]> DRAW_BUDGET = new HashMap<>();

    private GlyphService() {}

    public static boolean readsPending() {
        if (READS_PENDING.get() > 0) return true;
        for (var q : READ_DONE.values()) if (!q.isEmpty()) return true;
        return false;
    }

    private static void send(ServerLevel level, ServerPlayer to, net.minecraft.network.protocol.common.custom.CustomPacketPayload p) {
        if (to == null) Net.toDimension(level, p);
        else Net.toPlayer(to, p);
    }

    private static void sendAdded(ServerLevel level, ServerPlayer to, SurfacePlane plane, List<InkStroke> strokes) {
        List<InkStroke> batch = new ArrayList<>();
        int points = 0;
        for (InkStroke s : strokes) {
            if (!batch.isEmpty() && (batch.size() >= BATCH_STROKES || points + s.size() > BATCH_POINTS)) {
                send(level, to, new Payloads.StrokesAdded(plane, batch));
                batch = new ArrayList<>();
                points = 0;
            }
            batch.add(s);
            points += s.size();
        }
        if (!batch.isEmpty()) send(level, to, new Payloads.StrokesAdded(plane, batch));
    }

    private static void sendRemoved(ServerLevel level, long[] ids) {
        for (int i = 0; i < ids.length; i += BATCH_IDS) {
            Net.toDimension(level, new Payloads.StrokesRemoved(Arrays.copyOfRange(ids, i, Math.min(ids.length, i + BATCH_IDS))));
        }
    }

    private static boolean withinBudget(ServerPlayer player, int points) {
        long now = player.serverLevel().getGameTime();
        long[] b = DRAW_BUDGET.computeIfAbsent(player.getUUID(), k -> new long[]{now, 0});
        if (now - b[0] >= 20 || now < b[0]) { b[0] = now; b[1] = 0; }
        if (b[1] + points > DRAW_POINTS_PER_SECOND) return false;
        b[1] += points;
        return true;
    }

    public static void forgetPlayer(UUID who) {
        DRAW_BUDGET.remove(who);
    }

    public static Vec3 eyeFor(ServerLevel level, SurfacePlane plane, double u, double v, ServerPlayer player) {
        Vec3 eye = player.getEyePosition();
        var toPlot = Ships.toPlot(level, BlockPos.containing(plane.toWorld(u, v, -0.01)), 1f, false);
        return toPlot != null ? toPlot.apply(eye) : eye;
    }

    public static void handleDraw(ServerPlayer player, SurfacePlane plane, double[] uv) {
        ServerLevel level = player.serverLevel();
        InkSupply supply = InkSupply.of(player);
        if (supply == null || uv.length < 2 || uv.length % 2 != 0 || player.isSpectator() || !player.mayBuild()) return;
        if (!withinBudget(player, uv.length / 2)) return;
        InkType ink = supply.ink;
        if (ink == InkType.BLOOD) Taboo.add(player, 1);
        int inkLeft = supply.available();
        if (inkLeft <= 0) {
            player.displayClientMessage(Component.translatable("message.witchsatelier.pen_dry"), true);
            return;
        }

        Witnesses.saw(player);

        List<double[]> pieces = new ArrayList<>();
        List<Double> cur = new ArrayList<>();
        if (!Double.isFinite(uv[0]) || !Double.isFinite(uv[1])) return;
        Vec3 eye = eyeFor(level, plane, uv[0], uv[1], player);
        Map<BlockPos, Boolean> allowed = new HashMap<>();
        double spent = 0;
        for (int i = 0; i + 1 < uv.length; i += 2) {
            double u = uv[i], v = uv[i + 1];
            if (!Double.isFinite(u) || !Double.isFinite(v)) continue;
            boolean ok = plane.toWorld(u, v, 0).distanceTo(eye) <= REACH && isDrawable(level, plane, u, v)
                    && allowed.computeIfAbsent(plane.blockAt(u, v), pos -> level.mayInteract(player, pos));
            double seg = 0;
            if (ok && !cur.isEmpty()) {
                int n = cur.size();
                seg = Math.hypot(u - cur.get(n - 2), v - cur.get(n - 1));
                if (seg > 1.5) ok = false;
                else if (seg < 1e-4) continue;
            }
            if ((spent + seg) * INK_PER_BLOCK > inkLeft) {
                player.displayClientMessage(Component.translatable("message.witchsatelier.pen_dry"), true);
                break;
            }
            if (ok) {
                spent += seg;
                cur.add(u);
                cur.add(v);
            } else if (!cur.isEmpty()) {
                pieces.add(toArray(cur));
                cur.clear();
            }
        }
        if (!cur.isEmpty()) pieces.add(toArray(cur));
        if (pieces.isEmpty() || pieces.size() > MAX_PIECES) return;

        supply.consume(Math.max(pieces.size(), (int) Math.ceil(spent * INK_PER_BLOCK)));

        inscribe(level, plane, pieces, ink, player);
    }

    public static List<InkStroke> inscribe(ServerLevel level, SurfacePlane plane, List<double[]> pieces, InkType ink, ServerPlayer caster) {
        GlyphLevelData data = GlyphLevelData.get(level);
        List<InkStroke> added = new ArrayList<>();
        for (double[] piece : pieces) {
            if (piece.length < 2) continue;
            InkStroke s = InkStroke.of(data.newId(), piece, ink, caster == null ? null : caster.getUUID(), StrokeState.IDLE, 0);
            data.add(plane, s);
            added.add(s);
        }
        sendAdded(level, null, plane, added);
        for (InkStroke s : foci(added)) evaluate(level, plane, s, caster);
        return added;
    }

    static List<InkStroke> foci(List<InkStroke> added) {
        if (added.size() <= MAX_PIECES) return added;
        List<InkStroke> longest = new ArrayList<>(added);
        longest.sort((a, b) -> Double.compare(b.length(), a.length()));
        List<InkStroke> out = new ArrayList<>();
        List<Double> reach = new ArrayList<>();
        for (InkStroke s : longest) {
            Pt c = s.centroid();
            boolean covered = false;
            for (int i = 0; i < out.size() && !covered; i++) covered = out.get(i).centroid().dist(c) < reach.get(i);
            if (covered) continue;
            double r = 0;
            for (Pt p : s.points()) r = Math.max(r, p.dist(c));
            out.add(s);
            reach.add(r * 1.05);
        }
        return out;
    }

    public static List<InkStroke> inkWithoutReading(ServerLevel level, SurfacePlane plane, List<double[]> pieces, InkType ink, UUID author) {
        GlyphLevelData data = GlyphLevelData.get(level);
        List<InkStroke> added = new ArrayList<>();
        for (double[] piece : pieces) {
            if (piece.length < 2) continue;
            InkStroke s = InkStroke.of(data.newId(), piece, ink, author, StrokeState.IDLE, 0);
            data.add(plane, s);
            added.add(s);
        }
        sendAdded(level, null, plane, added);
        return added;
    }

    public static void eraseStrokes(ServerLevel level, Collection<Long> ids) {
        GlyphLevelData data = GlyphLevelData.get(level);
        List<Long> there = new ArrayList<>();
        for (long id : ids) if (data.get(id) != null) there.add(id);
        removeStrokes(level, there);
    }

    public static List<InkStroke> inscribe(ServerLevel level, SurfacePlane plane, List<double[]> pieces, ServerPlayer caster) {
        return inscribe(level, plane, pieces, InkType.CONJURING, caster);
    }

    public static ActiveSpell startSpell(ServerLevel level, SurfacePlane plane, SpellProgram program, ServerPlayer caster, boolean handheld) {
        return startSpell(level, plane, program, caster == null ? null : caster.getUUID(), handheld);
    }

    public static ActiveSpell startSpell(ServerLevel level, SurfacePlane plane, SpellProgram program, UUID who, boolean handheld) {
        ActiveSpell spell = handheld ? ActiveSpell.handheld(nextSpellId++, who, program)
                : new ActiveSpell(nextSpellId++, plane, Set.of(), who, program);
        ACTIVE.computeIfAbsent(level.dimension(), k -> new HashMap<>()).put(spell.id, spell);
        return spell;
    }

    public static ActiveSpell wornSpell(ServerLevel level, UUID caster) {
        Map<Long, ActiveSpell> m = ACTIVE.get(level.dimension());
        if (m == null) return null;
        for (ActiveSpell s : m.values()) if (s.worn && caster.equals(s.caster)) return s;
        return null;
    }

    public static void stop(ServerLevel level, ActiveSpell spell) {
        stopSpell(level, spell, false);
    }

    public static Collection<ActiveSpell> activeSpells(ServerLevel level) {
        Map<Long, ActiveSpell> m = ACTIVE.get(level.dimension());
        return m == null ? List.of() : List.copyOf(m.values());
    }

    public static void handleErase(ServerPlayer player, SurfacePlane plane, double u, double v, double wanted) {
        ServerLevel level = player.serverLevel();
        boolean rag = player.getMainHandItem().getItem() instanceof RagItem
                || player.getOffhandItem().getItem() instanceof RagItem;
        if (!Double.isFinite(u) || !Double.isFinite(v) || !player.mayBuild() || (!rag && penHand(player) == null)) return;
        if (plane.toWorld(u, v, 0).distanceTo(eyeFor(level, plane, u, v, player)) > REACH || !level.mayInteract(player, plane.blockAt(u, v))) return;
        double r = rag ? Math.max(RAG_MIN, Math.min(RAG_MAX, wanted)) : PEN_ERASE_RADIUS;
        GlyphLevelData data = GlyphLevelData.get(level);
        Map<Long, boolean[]> cuts = new HashMap<>();
        for (InkStroke s : data.near(plane, u, v, 1)) {
            boolean[] drop = null;
            for (int i = 0; i < s.size(); i++) {
                double du = s.u(i) - u, dv = s.v(i) - v;
                if (du * du + dv * dv < r * r) {
                    if (drop == null) drop = new boolean[s.size()];
                    drop[i] = true;
                }
            }
            if (drop != null) cuts.put(s.id(), drop);
        }
        if (!cuts.isEmpty() && rag) {
            Vec3 w = plane.toWorld(u, v, 0.05);
            level.sendParticles(new DustParticleOptions(new Vector3f(0.15f, 0.12f, 0.25f), 0.6f),
                    w.x, w.y, w.z, 3, 0.1, 0.1, 0.1, 0);
        }
        applyCuts(level, cuts);
    }

    public static void eraseArea(ServerLevel level, Vec3 center, double radius) {
        GlyphLevelData data = GlyphLevelData.get(level);
        List<Long> gone = new ArrayList<>();
        for (long id : new ArrayList<>(data.ids())) {
            InkStroke s = data.get(id);
            SurfacePlane plane = data.planeOf(id);
            if (s == null || plane == null || s.size() == 0) continue;
            if (plane.toWorld(s.u(0), s.v(0), 0).distanceTo(center) <= radius) gone.add(id);
        }
        removeStrokes(level, gone);
    }

    public static void onBlocksRemoved(ServerLevel level, Collection<BlockPos> positions) {
        if (positions.isEmpty()) return;
        GlyphLevelData data = GlyphLevelData.get(level);
        Map<Long, boolean[]> cuts = new HashMap<>();
        double m = INK_WIDTH / 2;
        for (BlockPos pos : positions) {
            for (Direction d : Direction.values()) {
                SurfacePlane plane = SurfacePlane.of(pos, d);
                Pt c = plane.toUV(Vec3.atCenterOf(pos).add(Vec3.atLowerCornerOf(d.getNormal()).scale(0.5)));
                for (InkStroke s : data.near(plane, c.x(), c.y(), 1)) {
                    for (int i = 0; i < s.size(); i++) {
                        double u = s.u(i), v = s.v(i);
                        if (u >= c.x() - 0.5 - m && u <= c.x() + 0.5 + m && v >= c.y() - 0.5 - m && v <= c.y() + 0.5 + m) {
                            cuts.computeIfAbsent(s.id(), k -> new boolean[s.size()])[i] = true;
                        }
                    }
                }
            }
        }
        applyCuts(level, cuts);
    }

    private static final Set<PistonMovingBlockEntity> CARRIED =
            Collections.newSetFromMap(new WeakHashMap<>());

    private static final Map<ResourceKey<Level>, List<long[]>> REREAD = new HashMap<>();
    private static final int ARRIVAL_TICKS = 3;

    public static void onPistonMoved(ServerLevel level, BlockPos piston) {
        if (GlyphLevelData.get(level).ids().isEmpty()) return;
        Map<Direction, List<BlockPos>> moved = new EnumMap<>(Direction.class);
        ChunkPos c = new ChunkPos(piston);

        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            if (!level.hasChunk(c.x + dx, c.z + dz)) continue;
            for (var be : level.getChunk(c.x + dx, c.z + dz).getBlockEntities().values()) {
                if (be instanceof PistonMovingBlockEntity m && !m.isSourcePiston()
                        && m.getProgress(1) == 0 && CARRIED.add(m)) {
                    Direction d = m.getMovementDirection();
                    moved.computeIfAbsent(d, k -> new ArrayList<>()).add(m.getBlockPos().relative(d.getOpposite()));
                }
            }
        }
        for (var e : moved.entrySet()) onBlocksMoved(level, e.getValue(), e.getKey());
    }

    public static void onBlocksMoved(ServerLevel level, Collection<BlockPos> from, Direction dir) {
        GlyphLevelData data = GlyphLevelData.get(level);
        if (from.isEmpty() || data.ids().isEmpty()) return;
        Set<BlockPos> moving = new HashSet<>(from);
        Map<Long, SurfacePlane> touched = new LinkedHashMap<>();
        for (BlockPos pos : moving) {
            for (Direction d : Direction.values()) {
                SurfacePlane plane = SurfacePlane.of(pos, d);
                Pt c = plane.toUV(Vec3.atCenterOf(pos));
                for (InkStroke s : data.near(plane, c.x(), c.y(), 1)) touched.putIfAbsent(s.id(), plane);
            }
        }
        Vec3 step = Vec3.atLowerCornerOf(dir.getNormal());
        List<Long> gone = new ArrayList<>();
        Map<SurfacePlane, List<InkStroke>> pieces = new LinkedHashMap<>();
        for (Map.Entry<Long, SurfacePlane> e : touched.entrySet()) {
            InkStroke s = data.get(e.getKey());
            SurfacePlane plane = e.getValue();
            if (s == null) continue;
            boolean[] rides = new boolean[s.size()];
            boolean any = false;
            for (int i = 0; i < s.size(); i++) any |= rides[i] = moving.contains(plane.blockAt(s.u(i), s.v(i)));
            if (!any) continue;
            gone.add(s.id());
            SurfacePlane to = new SurfacePlane(plane.face(), plane.layer() + dir.getNormal().get(plane.face().getAxis()));

            StrokeState st = s.state() == StrokeState.SPENT ? StrokeState.SPENT : StrokeState.IDLE;
            List<Double> cur = new ArrayList<>();
            for (int i = 0; i <= s.size(); i++) {
                if ((i == s.size() || (i > 0 && rides[i] != rides[i - 1])) && !cur.isEmpty()) {
                    if (cur.size() >= 4 || s.size() == 1) {
                        InkStroke piece = InkStroke.of(data.newId(), toArray(cur), s.ink(), s.author(), st, 0);
                        pieces.computeIfAbsent(rides[i - 1] ? to : plane, k -> new ArrayList<>()).add(piece);
                    }
                    cur.clear();
                }
                if (i == s.size()) break;
                if (rides[i]) {
                    Pt p = to.toUV(plane.toWorld(s.u(i), s.v(i), 0).add(step));
                    cur.add(p.x());
                    cur.add(p.y());
                } else {
                    cur.add(s.u(i));
                    cur.add(s.v(i));
                }
            }
        }
        if (gone.isEmpty()) return;
        List<long[]> queue = REREAD.computeIfAbsent(level.dimension(), k -> new ArrayList<>());
        long due = level.getGameTime() + ARRIVAL_TICKS;
        for (Map.Entry<SurfacePlane, List<InkStroke>> e : pieces.entrySet()) {
            for (InkStroke piece : e.getValue()) {
                data.add(e.getKey(), piece);
                if (piece.state() == StrokeState.IDLE) queue.add(new long[]{due, piece.id()});
            }
            sendAdded(level, null, e.getKey(), e.getValue());
        }
        removeStrokes(level, gone);
    }

    private static void rereadMoved(ServerLevel level) {
        List<long[]> queue = REREAD.get(level.dimension());
        if (queue == null || queue.isEmpty()) return;
        long now = level.getGameTime();
        List<long[]> due = new ArrayList<>();
        queue.removeIf(e -> e[0] <= now && due.add(e));
        GlyphLevelData data = GlyphLevelData.get(level);
        for (long[] e : due) {
            InkStroke s = data.get(e[1]);
            SurfacePlane plane = data.planeOf(e[1]);

            if (s == null || plane == null || s.state() != StrokeState.IDLE) continue;
            ServerPlayer author = level.getPlayerByUUID(s.author()) instanceof ServerPlayer p ? p : null;
            evaluate(level, plane, s, author);
        }
    }

    public static void applyCuts(ServerLevel level, Map<Long, boolean[]> cuts) {
        if (cuts.isEmpty()) return;
        GlyphLevelData data = GlyphLevelData.get(level);
        List<Long> removed = new ArrayList<>();
        Map<SurfacePlane, List<InkStroke>> pieces = new HashMap<>();

        Map<Long, List<Long>> piecesOf = new HashMap<>();
        Map<Long, byte[]> pieceWear = new HashMap<>();
        for (Map.Entry<Long, boolean[]> e : cuts.entrySet()) {
            InkStroke s = data.get(e.getKey());
            SurfacePlane plane = data.planeOf(e.getKey());
            if (s == null || plane == null) continue;
            boolean[] drop = e.getValue();
            byte[] worn = data.wear(s.id());
            removed.add(s.id());
            List<Double> cur = new ArrayList<>();
            int from = 0;
            for (int i = 0; i <= s.size(); i++) {
                boolean keep = i < s.size() && !drop[i];
                if (keep) {
                    if (cur.isEmpty()) from = i;
                    cur.add(s.u(i));
                    cur.add(s.v(i));
                } else if (!cur.isEmpty()) {
                    if (cur.size() >= 4 || s.size() == 1) {
                        StrokeState st = s.state();
                        InkStroke piece = InkStroke.of(data.newId(), toArray(cur), s.ink(), s.author(), st, st == StrokeState.ACTIVE ? s.color() : 0);
                        pieces.computeIfAbsent(plane, k -> new ArrayList<>()).add(piece);
                        piecesOf.computeIfAbsent(s.id(), k -> new ArrayList<>()).add(piece.id());
                        if (worn != null && worn.length == s.size()) pieceWear.put(piece.id(), Arrays.copyOfRange(worn, from, i));
                    }
                    cur.clear();
                }
            }
        }

        for (Map.Entry<SurfacePlane, List<InkStroke>> e : pieces.entrySet()) {
            for (InkStroke piece : e.getValue()) data.add(e.getKey(), piece);
            sendAdded(level, null, e.getKey(), e.getValue());
        }
        pieceWear.forEach(data::setWear);
        sendWear(level, pieceWear);
        removeStrokes(level, removed, piecesOf);
    }

    private static void sendWear(ServerLevel level, Map<Long, byte[]> wear) {
        sendWear(level, null, wear);
    }

    private static void sendWear(ServerLevel level, ServerPlayer to, Map<Long, byte[]> wear) {
        if (wear.isEmpty()) return;
        List<Long> ids = new ArrayList<>();
        List<byte[]> w = new ArrayList<>();
        int bytes = 0;
        for (Map.Entry<Long, byte[]> e : wear.entrySet()) {
            byte[] b = e.getValue() == null ? new byte[0] : e.getValue();
            if (!ids.isEmpty() && (ids.size() >= 4096 || bytes + b.length > BATCH_WEAR_BYTES)) {
                send(level, to, new Payloads.StrokeWear(ids.stream().mapToLong(Long::longValue).toArray(), w));
                ids = new ArrayList<>();
                w = new ArrayList<>();
                bytes = 0;
            }
            ids.add(e.getKey());
            w.add(b);
            bytes += b.length;
        }
        send(level, to, new Payloads.StrokeWear(ids.stream().mapToLong(Long::longValue).toArray(), w));
    }

    private record Found(GlyphReading reading, List<InkStroke> strokes) {
        Set<Long> usedIds() {
            Set<Long> ids = new HashSet<>();
            for (int i : reading.usedStrokes()) ids.add(strokes.get(i).id());
            return ids;
        }
    }

    private record Snapshot(List<InkStroke> candidates, List<List<Pt>> pts, int focusIdx, Pt focusCentre, double up, boolean flat) {}

    private static Snapshot snapshot(ServerLevel level, SurfacePlane plane, InkStroke focus, ServerPlayer player) {
        GlyphLevelData data = GlyphLevelData.get(level);
        if (focus.size() == 0) return null;
        List<InkStroke> candidates = new ArrayList<>();
        for (InkStroke s : data.near(plane, focus.u(0), focus.v(0), 1)) {
            if (s.state() != StrokeState.SPENT) candidates.add(s);
        }
        List<List<Pt>> pts = new ArrayList<>();
        int focusIdx = -1;
        for (int i = 0; i < candidates.size(); i++) {
            pts.add(candidates.get(i).points());
            if (candidates.get(i).id() == focus.id()) focusIdx = i;
        }
        if (focusIdx < 0) return null;
        return new Snapshot(List.copyOf(candidates), pts, focusIdx, focus.centroid(), upAngle(plane, player), Math.abs(plane.normal().y) >= 0.5);
    }

    private static Found read(Snapshot snap) {
        RingFinder.Ring best = null;
        for (RingFinder.Ring ring : RingFinder.find(snap.pts(), INK_WIDTH)) {
            boolean contains = ring.strokes().contains(snap.focusIdx()) || snap.focusCentre().dist(ring.center()) <= ring.radius() * 1.05;
            if (contains && (best == null || ring.radius() > best.radius())) best = ring;
        }
        if (best == null) return null;
        List<InkStroke> subset = new ArrayList<>();
        List<List<Pt>> subPts = new ArrayList<>();
        for (int i = 0; i < snap.candidates().size(); i++) {
            InkStroke s = snap.candidates().get(i);
            if (best.strokes().contains(i) || s.centroid().dist(best.center()) < best.radius() * 1.02) {
                subset.add(s);
                subPts.add(snap.pts().get(i));
            }
        }
        GlyphReading r = snap.flat() ? GlyphAnalyzer.analyzeFromAnySide(subPts, INK_WIDTH, snap.up()) : GlyphAnalyzer.analyze(subPts, INK_WIDTH, snap.up());
        return new Found(r, subset);
    }

    private static Found find(ServerLevel level, SurfacePlane plane, InkStroke focus, ServerPlayer player) {
        Snapshot snap = snapshot(level, plane, focus, player);
        return snap == null ? null : read(snap);
    }

    private static boolean current(ServerLevel level, Found f) {
        GlyphLevelData data = GlyphLevelData.get(level);
        for (InkStroke s : f.strokes()) {
            InkStroke now = data.get(s.id());
            if (now == null || now.state() == StrokeState.SPENT) return false;
        }
        return true;
    }

    public static GlyphPages.Page glyphAsPage(ServerLevel level, SurfacePlane plane, Pt at, ServerPlayer player) {
        GlyphLevelData data = GlyphLevelData.get(level);
        List<InkStroke> candidates = data.near(plane, at.x(), at.y(), 1);
        if (candidates.isEmpty()) return null;
        List<List<Pt>> pts = new ArrayList<>();
        for (InkStroke s : candidates) pts.add(s.points());
        RingFinder.Ring best = null;
        for (RingFinder.Ring ring : RingFinder.find(pts, INK_WIDTH)) {
            if (at.dist(ring.center()) <= ring.radius() * 1.1 && (best == null || ring.radius() > best.radius())) best = ring;
        }
        if (best == null) return null;
        double k = 0.85 / best.radius(), rot = Math.PI / 2 - upAngle(plane, player), c = Math.cos(rot), sn = Math.sin(rot);
        List<GlyphPages.PageStroke> out = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            InkStroke s = candidates.get(i);
            if (!best.strokes().contains(i) && s.centroid().dist(best.center()) >= best.radius() * 1.02) continue;
            float[] f = new float[s.size() * 2];
            for (int j = 0; j < s.size(); j++) {
                double x = (s.u(j) - best.center().x()) * k, y = (s.v(j) - best.center().y()) * k;
                f[j * 2] = (float) (x * c - y * sn);
                f[j * 2 + 1] = (float) (x * sn + y * c);
            }
            out.add(new GlyphPages.PageStroke(f, s.ink()));
        }
        return out.isEmpty() ? null : new GlyphPages.Page(List.copyOf(out));
    }

    static double upAngle(SurfacePlane plane, ServerPlayer player) {
        Vec3 n = plane.normal();
        Vec3 up;
        if (Math.abs(n.y) < 0.5) up = new Vec3(0, 1, 0);
        else if (player != null) up = new Vec3(player.getLookAngle().x, 0, player.getLookAngle().z);
        else return Math.PI / 2;
        if (up.lengthSqr() < 1e-6) return Math.PI / 2;
        return Math.atan2(up.dot(plane.planeVector(0, 1)), up.dot(plane.planeVector(1, 0)));
    }

    private static void evaluate(ServerLevel level, SurfacePlane plane, InkStroke focus, ServerPlayer player) {
        evaluate(level, plane, focus, player, false, null, 0);
    }

    private static void evaluate(ServerLevel level, SurfacePlane plane, InkStroke focus, ServerPlayer player, boolean quiet,
                                 java.util.function.Consumer<ActiveSpell> then, int retry) {
        Snapshot snap = snapshot(level, plane, focus, player);
        if (snap == null) {
            if (then != null) then.accept(null);
            return;
        }
        if (!readOffThread) {
            Found f = read(snap);
            ActiveSpell r = apply(level, plane, focus, player, f, quiet);
            if (then != null) then.accept(r);
            return;
        }
        ResourceKey<Level> dim = level.dimension();
        UUID who = player == null ? null : player.getUUID();
        READS_PENDING.incrementAndGet();
        READER.execute(() -> {
            Found f;
            try {
                f = read(snap);
            } catch (RuntimeException e) {
                LOGGER.error("[glyph] reading failed", e);
                f = null;
            }
            Found done = f;
            READ_DONE.computeIfAbsent(dim, k -> new java.util.concurrent.ConcurrentLinkedQueue<>()).add(lv -> {
                ServerPlayer p = who != null && lv.getPlayerByUUID(who) instanceof ServerPlayer sp ? sp : null;
                if (done != null && !current(lv, done)) {
                    InkStroke again = GlyphLevelData.get(lv).get(focus.id());
                    if (again != null && retry < 3) evaluate(lv, plane, again, p, quiet, then, retry + 1);
                    else if (then != null) then.accept(null);
                    return;
                }
                ActiveSpell r = apply(lv, plane, focus, p, done, quiet);
                if (then != null) then.accept(r);
            });
            READS_PENDING.decrementAndGet();
        });
    }

    private static void drainReads(ServerLevel level) {
        var q = READ_DONE.get(level.dimension());
        if (q == null) return;
        for (int n = q.size(); n > 0; n--) {
            var job = q.poll();
            if (job == null) break;
            try {
                job.accept(level);
            } catch (RuntimeException e) {
                LOGGER.error("[glyph] applying a reading failed", e);
            }
        }
    }

    private record Ink(double len, double pot, double dur, double mis) {}

    private static Ink inkOf(Found f, Iterable<Integer> idx) {
        double len = 0, pot = 0, dur = 0, mis = 0;
        for (int i : idx) {
            InkStroke st = f.strokes().get(i);
            double l = Math.max(0.05, st.length());
            len += l;
            pot += l * st.ink().potency;
            dur += l * st.ink().duration;
            mis += l * st.ink().misfire;
        }
        return new Ink(len, pot, dur, mis);
    }

    private static void harden(ActiveSpell spell, GlyphReading r, Ink ink) {
        spell.toughness = Math.max(0.3, (ink.len() > 0 ? ink.dur() / ink.len() : 1) * (0.6 + 0.8 * r.neatness()));
        spell.mending = r.core() != null && r.count(SignType.REPETITION) > 0;
    }

    private static UUID authorOf(InkStroke s) {
        return s.author() == null || InkStroke.NOBODY.equals(s.author()) ? null : s.author();
    }

    private static ActiveSpell apply(ServerLevel level, SurfacePlane plane, InkStroke focus, ServerPlayer player, Found f, boolean quiet) {
        if (f == null || f.reading().ring() == null) return null;
        GlyphReading r = f.reading();
        Set<Long> ids = f.usedIds();
        LOGGER.debug("[glyph] {}", r.describe());
        ActiveSpell running = spellUsing(level, ids);

        if (!r.closed()) {
            if (running != null) stopSpell(level, running, false);
            LOGGER.debug("[glyph] {}", describeOpen(r));
            return null;
        }

        List<Integer> inked = r.usedStrokes();
        Tracing.Result copy = Tracing.traced(player, plane, f.strokes(), r, INK_WIDTH);
        if (copy.waiting()) {
            if (running != null) stopSpell(level, running, false);
            return null;
        }
        if (copy.reading() != null) {
            r = copy.reading();
            ids = new HashSet<>();
            inked = new ArrayList<>();
            for (int i = 0; i < f.strokes().size(); i++) {
                ids.add(f.strokes().get(i).id());
                inked.add(i);
            }
            if (running == null) running = spellUsing(level, ids);
            LOGGER.debug("[glyph] traced from the page: {}", r.describe());
        }

        if (player != null && Starfall.among(level, player.position()) && r.ring().radius() >= MIN_GLYPH_RADIUS
                && level.getBlockState(BlockPos.containing(plane.toWorld(r.ring().center(), -0.5))).is(ModBlocks.STAR_BLOCK.get())) {
            setStates(level, ids, StrokeState.ACTIVE, 0xFFE8D8FF, Starfall.RETURN_TICKS);
            Starfall.beginReturn(player, ids);
            return null;
        }

        if (r.sigil() == Sigil.APPLE && r.ring().radius() >= MIN_GLYPH_RADIUS) {
            if (running != null) stopSpell(level, running, false);
            Pt c = r.ring().center();
            Net.toNear(level, plane.toWorld(c, 0.05), 96, new Payloads.BadApple(plane, c.x(), c.y(), (float) r.ring().radius(),
                    (float) upAngle(plane, player), ids.stream().mapToLong(Long::longValue).toArray()));
            eraseStrokes(level, ids);
            return null;
        }

        Ink ink = inkOf(f, inked);
        double len = ink.len(), pot = ink.pot(), dur = ink.dur(), mis = ink.mis();
        SpellProgram program = len > 0
                ? SpellCompiler.compile(r, plane, pot / len, dur / len, mis / len)
                : SpellCompiler.compile(r, plane, 1.0);

        if (ArcaneLensBlock.near(level, program.origin()) || ArcaneLensBlock.near(level, plane.toWorld(r.ring().center(), -0.5))) {
            program = program.amplified(ArcaneLensBlock.AMPLIFY);
            if (player != null) MagicTrigger.fire(player, "cast_amplified");
            level.sendParticles(ParticleTypes.END_ROD, program.origin().x, program.origin().y, program.origin().z, 12, 0.4, 0.4, 0.4, 0.03);
        }

        boolean tooSmallToBurst = program.form() == SpellProgram.Form.BURST && r.ring().radius() < MIN_BURST_RADIUS;
        if (r.ring().radius() < MIN_GLYPH_RADIUS) return null;
        if (!tooSmallToBurst) DrawingDump.save(f.strokes(), upAngle(plane, player), r);
        if (program.form() == SpellProgram.Form.FIZZLE || tooSmallToBurst) {
            if (running != null) stopSpell(level, running, false);
            if (!tooSmallToBurst) fizzle(level, plane.toWorld(r.ring().center(), 0.05), plane, r.ring().radius());
            if (player != null && !tooSmallToBurst) {
                LOGGER.debug("[glyph] {}", describeOpen(r));
            }
            return null;
        }
        if (running != null && (running.program.form() != program.form() || running.program.has(SpellProgram.TREAD) != program.has(SpellProgram.TREAD))) {
            stopSpell(level, running, false);
            running = null;
        }
        if (running != null && running.ringRadius > 0 && r.ring().radius() < running.ringRadius * 0.6) {
            stopSpell(level, running, false);
            return null;
        }
        ActiveSpell result;
        if (running != null) {
            running.ringRadius = Math.max(running.ringRadius, r.ring().radius());
            running.program = program;
            running.strokes.addAll(ids);
            harden(running, r, ink);
            setStates(level, running.strokes, StrokeState.ACTIVE, ElementLook.color(program.element(), program.centerSign()), running.ticksLeft);
            result = running;
        } else {
            UUID who = player != null ? player.getUUID() : authorOf(focus);
            ActiveSpell spell = new ActiveSpell(nextSpellId++, plane, ids, who, program);
            spell.quiet = quiet;
            spell.ringRadius = r.ring().radius();
            harden(spell, r, ink);
            ACTIVE.computeIfAbsent(level.dimension(), k -> new HashMap<>()).put(spell.id, spell);
            if (program.has(SpellProgram.TREAD)) {
                GlyphLevelData data = GlyphLevelData.get(level);
                spell.key = quiet ? data.keyOf(ids) : spell.standingInside(level);
                if (!quiet) data.setKey(ids, spell.key);
            }
            setStates(level, ids, StrokeState.ACTIVE, litColor(program), program.has(SpellProgram.TREAD) ? 0 : spell.ticksLeft);
            if (player != null && program.form() == SpellProgram.Form.PETRIFY && !quiet) Taboo.add(player, 40);
            if (player != null && !quiet) MagicTrigger.cast(player, program, "surface");
            if (player != null && !quiet) com.fimkov.witchsatelier.advancement.Feats.surfaceCast(player, program, r, copy.reading() != null, f.strokes(), inked);
            wakeLinked(level, plane, spell, r, player);
            result = spell;
        }
        LOGGER.debug("[cast] {}", program.summary());
        return result;
    }

    public static final int ARMED = 0x01000000;

    private static int litColor(SpellProgram program) {
        int c = ElementLook.color(program.element(), program.centerSign()) & 0xFFFFFF;
        return program.has(SpellProgram.TREAD) ? c | ARMED : c;
    }

    public static void sprung(ServerLevel level, ActiveSpell spell) {
        setStates(level, spell.strokes, StrokeState.ACTIVE, ElementLook.color(spell.program.element(), spell.program.centerSign()) & 0xFFFFFF,
                spell.program.duration());

        long now = level.getGameTime();
        for (ActiveSpell s : chainOf(level, spell)) {
            if (!s.held) continue;
            s.releaseAt = now + CHAIN_STEP * Math.max(1, Math.abs(s.hop - spell.hop));
        }
    }

    public static final int CHAIN_STEP = 6;

    public static void released(ServerLevel level, ActiveSpell spell) {
        setStates(level, spell.strokes, StrokeState.ACTIVE, ElementLook.color(spell.program.element(), spell.program.centerSign()) & 0xFFFFFF,
                spell.program.duration());
    }

    public static void rearmed(ServerLevel level, ActiveSpell spell) {
        setStates(level, spell.strokes, StrokeState.ACTIVE, litColor(spell.program) | ARMED, 0);
        for (ActiveSpell s : chainOf(level, spell)) {
            if (s.program.has(SpellProgram.TREAD)) continue;
            s.held = true;
            s.releaseAt = 0;
            s.pause(level);
            setStates(level, s.strokes, StrokeState.ACTIVE, litColor(s.program) | ARMED, 0);
        }
    }

    private static List<ActiveSpell> chainOf(ServerLevel level, ActiveSpell spell) {
        Map<Long, ActiveSpell> spells = ACTIVE.get(level.dimension());
        List<ActiveSpell> out = new ArrayList<>();
        if (spells == null || spell.chain == 0) return out;
        for (ActiveSpell s : spells.values()) if (s != spell && s.chain == spell.chain) out.add(s);
        return out;
    }

    private static void wakeLinked(ServerLevel level, SurfacePlane plane, ActiveSpell first, GlyphReading firstReading, ServerPlayer player) {
        List<ActiveSpell> woken = new ArrayList<>();
        Set<ActiveSpell> adopted = new HashSet<>();
        woken.add(first);
        first.chain = first.id;
        first.hop = 0;
        ArrayDeque<Object[]> queue = new ArrayDeque<>();
        queue.add(new Object[]{firstReading.ring(), new HashSet<>(first.strokes), 0});
        Set<Long> claimed = new HashSet<>(first.strokes);
        while (!queue.isEmpty() && woken.size() < 12) {
            Object[] q = queue.poll();
            RingFinder.Ring ring = (RingFinder.Ring) q[0];
            for (Object[] hit : linkedTo(level, plane, ring, claimed, player)) {
                Found next = (Found) hit[0];
                Set<Long> ids = new HashSet<>(next.usedIds());

                ids.add((Long) hit[1]);
                ActiveSpell existing = spellUsing(level, ids);
                if (existing != null) {
                    if (existing.held && existing.chain != first.id && !chainAlive(level, existing.chain)) {
                        existing.chain = first.id;
                        existing.hop = (Integer) q[2] + 1;
                        existing.strokes.addAll(ids);
                        claimed.addAll(existing.strokes);
                        adopted.add(existing);
                        woken.add(existing);
                        queue.add(new Object[]{next.reading().ring(), new HashSet<>(existing.strokes), existing.hop});
                    }
                    continue;
                }
                SpellProgram program = compileFound(level, plane, next);
                if (program == null) continue;
                ActiveSpell spell = new ActiveSpell(nextSpellId++, plane, ids, player != null ? player.getUUID() : first.caster, program);
                harden(spell, next.reading(), inkOf(next, next.reading().usedStrokes()));
                spell.chain = first.id;
                spell.hop = (Integer) q[2] + 1;
                ACTIVE.computeIfAbsent(level.dimension(), k -> new HashMap<>()).put(spell.id, spell);
                setStates(level, ids, StrokeState.ACTIVE, litColor(program), program.has(SpellProgram.TREAD) ? 0 : program.duration());
                claimed.addAll(ids);
                woken.add(spell);
                queue.add(new Object[]{next.reading().ring(), ids, spell.hop});
            }
        }
        if (woken.size() < 2) {
            first.chain = 0;
            return;
        }
        double boost = Math.min(2.0, 1 + 0.2 * (woken.size() - 1));
        for (ActiveSpell s : woken) if (!adopted.contains(s)) s.program = s.program.amplified(boost);

        boolean trigger = woken.stream().anyMatch(s -> s.program.has(SpellProgram.TREAD));
        long now = level.getGameTime();
        for (ActiveSpell s : woken) {
            if (s.program.has(SpellProgram.TREAD)) continue;
            if (trigger) {
                s.held = true;
                setStates(level, s.strokes, StrokeState.ACTIVE, litColor(s.program) | ARMED, 0);
            } else if (s.hop > 0) {
                s.held = true;
                s.releaseAt = now + (long) CHAIN_STEP * s.hop;
                setStates(level, s.strokes, StrokeState.ACTIVE, litColor(s.program) | ARMED, 0);
            }
        }
        if (player != null) MagicTrigger.fire(player, "linked_glyphs");
        if (player != null) com.fimkov.witchsatelier.advancement.Feats.chainWoke(player, woken.size());
        LOGGER.debug("[cast] {} linked glyphs woke together (x{})", woken.size(), boost);
    }

    private static boolean chainAlive(ServerLevel level, long chain) {
        Map<Long, ActiveSpell> spells = ACTIVE.get(level.dimension());
        return chain != 0 && spells != null && spells.containsKey(chain);
    }

    private static List<Object[]> linkedTo(ServerLevel level, SurfacePlane plane, RingFinder.Ring ring, Set<Long> claimed, ServerPlayer player) {
        GlyphLevelData data = GlyphLevelData.get(level);
        Pt c = ring.center();
        double rr = ring.radius(), rim = Math.max(0.25, rr * 0.12);
        List<Object[]> out = new ArrayList<>();
        Set<Long> seen = new HashSet<>(claimed);
        for (InkStroke link : data.near(plane, c.x(), c.y(), 2)) {
            if (seen.contains(link.id()) || link.state() == StrokeState.SPENT || link.size() < 2) continue;
            Pt a = new Pt(link.u(0), link.v(0)), b = new Pt(link.u(link.size() - 1), link.v(link.size() - 1));
            Pt near = Math.abs(a.dist(c) - rr) <= rim ? a : Math.abs(b.dist(c) - rr) <= rim ? b : null;
            if (near == null) continue;
            Pt far = near == a ? b : a;
            if (far.dist(c) < rr + 0.5) continue;

            for (InkStroke t : data.near(plane, far.x(), far.y(), 1)) {
                if (t.id() == link.id() || seen.contains(t.id()) || t.state() == StrokeState.SPENT) continue;
                boolean touches = false;
                for (Pt p : t.points()) if (p.dist(far) <= rim) { touches = true; break; }
                if (!touches) continue;
                Found f = find(level, plane, t, player);
                if (f == null || f.reading().ring() == null) continue;
                RingFinder.Ring other = f.reading().ring();
                if (Math.abs(far.dist(other.center()) - other.radius()) > Math.max(0.25, other.radius() * 0.12)) continue;
                if (other.center().dist(c) < 0.2) continue;
                Set<Long> ids = f.usedIds();
                if (!Collections.disjoint(ids, seen)) continue;
                seen.addAll(ids);
                seen.add(link.id());
                out.add(new Object[]{f, link.id()});
                break;
            }
        }
        return out;
    }

    private static SpellProgram compileFound(ServerLevel level, SurfacePlane plane, Found f) {
        GlyphReading r = f.reading();
        if (r.ring().radius() < MIN_GLYPH_RADIUS) return null;
        double len = 0, pot = 0, dur = 0, mis = 0;
        for (int i : r.usedStrokes()) {
            InkStroke st = f.strokes().get(i);
            double l = Math.max(0.05, st.length());
            len += l;
            pot += l * st.ink().potency;
            dur += l * st.ink().duration;
            mis += l * st.ink().misfire;
        }
        SpellProgram program = len > 0 ? SpellCompiler.compile(r, plane, pot / len, dur / len, mis / len) : SpellCompiler.compile(r, plane, 1.0);
        if (program.form() == SpellProgram.Form.FIZZLE || program.form() == SpellProgram.Form.BURST) return null;
        return program;
    }

    public static void fizzle(ServerLevel level, Vec3 at, GlyphFrame frame, double radius) {
        level.playSound(null, at.x, at.y, at.z, SoundEvents.CANDLE_EXTINGUISH, SoundSource.PLAYERS, 1.2f, 0.7f);
        for (int k = 0; k < 24; k++) {
            double a = k * Math.PI * 2 / 24;
            Vec3 p = at.add(frame.planeVector(Math.cos(a), Math.sin(a)).scale(radius));
            level.sendParticles(ParticleTypes.SMOKE, p.x, p.y, p.z, 1, 0.02, 0.05, 0.02, 0.01);
            if (k % 3 == 0) level.sendParticles(ModParticles.spark(0xB0A0A0A8), p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0.02);
        }
    }

    public static String describeOpen(GlyphReading r) {
        StringBuilder sb = new StringBuilder("ring open");
        if (r.sigil() != null) sb.append(" · ").append(r.sigil().name().toLowerCase());
        else if (r.centerSign() != null) sb.append(" · ").append(r.centerSign().name().toLowerCase());
        Map<String, Integer> counts = new HashMap<>();
        for (GlyphReading.ReadSign s : r.signs()) counts.merge(s.type().name().toLowerCase() + (s.inverted() ? "~" : ""), 1, Integer::sum);
        counts.forEach((k, v) -> sb.append(" · ").append(k).append(v > 1 ? "×" + v : ""));
        if (r.unknown() > 0) sb.append(" · ?×").append(r.unknown());
        return sb.toString();
    }

    private static boolean loadedAt(ServerLevel level, ActiveSpell s) {
        return s.handheld || s.plane == null || level.shouldTickBlocksAt(BlockPos.containing(s.program.origin()));
    }

    public static void tick(ServerLevel level) {
        drainReads(level);
        relight(level);
        if (level.getGameTime() % 20 == 7) washSweep(level);
        wear(level);
        rereadMoved(level);
        Map<Long, ActiveSpell> spells = ACTIVE.get(level.dimension());
        if (spells == null || spells.isEmpty()) return;
        for (Iterator<ActiveSpell> it = new ArrayList<>(spells.values()).iterator(); it.hasNext(); ) {
            ActiveSpell s = it.next();
            if (spells.get(s.id) != s || !loadedAt(level, s)) continue;
            boolean alive;
            try {
                alive = s.tick(level);
            } catch (RuntimeException e) {
                LogUtils.getLogger().error("WitchsAtelier spell {} failed", s.id, e);
                alive = false;
            }
            if (!alive && spells.containsKey(s.id)) stopSpell(level, s, true);
            if (alive && s.waiting()) {
                if (!s.fxOff) sendFx(level, s, 0);
                s.fxOff = true;
                continue;
            }
            s.fxOff = false;
            sendFx(level, s, alive ? s.ticksLeft : 0);
        }
    }

    private static final Map<ResourceKey<Level>, Integer> WASH_CURSOR = new HashMap<>();
    private static final int WASH_BATCH = 256;

    private static void wear(ServerLevel level) {
        Map<Long, ActiveSpell> spells = ACTIVE.get(level.dimension());
        if (spells == null || spells.isEmpty()) return;
        long time = level.getGameTime();
        boolean rainTurn = time % 40 == 11, stepTurn = time % 10 == 3;
        if (!rainTurn && !stepTurn) return;
        GlyphLevelData data = GlyphLevelData.get(level);
        Map<Long, byte[]> worn = new HashMap<>();
        for (ActiveSpell s : spells.values()) {
            if (s.plane == null || s.handheld || s.mending || s.strokes.isEmpty() || !loadedAt(level, s)) continue;
            if (rainTurn) rainWear(level, data, s, worn);
            if (stepTurn && s.plane.face() == Direction.UP) footWear(level, data, s, worn);
        }
        if (worn.isEmpty()) return;

        Map<Long, boolean[]> cuts = new HashMap<>();
        for (Map.Entry<Long, byte[]> e : worn.entrySet()) {
            data.setWear(e.getKey(), e.getValue());
            byte[] w = e.getValue();
            for (int i = 0; i < w.length; i++) {
                if (InkWear.amount(w[i]) >= InkWear.CUT) cuts.computeIfAbsent(e.getKey(), k -> new boolean[w.length])[i] = true;
            }
        }
        sendWear(level, worn);
        applyCuts(level, cuts);
    }

    private static byte[] wearOf(GlyphLevelData data, Map<Long, byte[]> worn, InkStroke st) {
        return worn.computeIfAbsent(st.id(), k -> {
            byte[] w = data.wear(k);
            return w != null && w.length == st.size() ? w.clone() : new byte[st.size()];
        });
    }

    private static void rainWear(ServerLevel level, GlyphLevelData data, ActiveSpell s, Map<Long, byte[]> worn) {
        if (level.random.nextDouble() > 0.6) return;
        List<InkStroke> wet = new ArrayList<>();
        for (long id : s.strokes) {
            InkStroke st = data.get(id);
            if (st == null || st.ink().waterproof || st.size() == 0) continue;
            BlockPos front = s.plane.blockAt(st.u(st.size() / 2), st.v(st.size() / 2)).relative(s.plane.face());
            if (level.isRainingAt(front) || level.getFluidState(front).is(FluidTags.WATER)) wet.add(st);
        }
        if (wet.isEmpty()) return;
        InkStroke st = wet.get(level.random.nextInt(wet.size()));
        byte[] w = wearOf(data, worn, st);
        int at = level.random.nextInt(st.size());
        if (level.random.nextBoolean()) {
            for (int i = 0; i < w.length; i++) if (InkWear.amount(w[i]) > InkWear.amount(w[at])) at = i;
        }
        int spread = 2 + level.random.nextInt(3);
        double by = wearBoost * (10 + level.random.nextInt(9)) / s.toughness;
        for (int k = -spread; k <= spread; k++) {
            int i = at + k;
            if (i < 0 || i >= w.length) continue;
            w[i] = InkWear.add(w[i], by * (1 - Math.abs(k) / (spread + 1.0)), true);
        }
        if (level.random.nextInt(3) == 0) {
            Vec3 p = s.plane.toWorld(st.u(at), st.v(at), 0.05);
            level.sendParticles(ParticleTypes.SPLASH, p.x, p.y, p.z, 4, 0.1, 0.02, 0.1, 0.02);
        }
    }

    public static double wearBoost = 1;

    private static final Map<Entity, Float> WALKED = new WeakHashMap<>();

    private static void footWear(ServerLevel level, GlyphLevelData data, ActiveSpell s, Map<Long, byte[]> worn) {
        Vec3 o = s.program.origin();
        double r = s.program.radius() + 0.4, reach = 0.26;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(o, o).inflate(r, 1.2, r))) {
            if (!e.onGround() || e.isSpectator() || e.isShiftKeyDown()) continue;

            Float before = WALKED.put(e, e.walkDist);
            if (before == null || e.walkDist - before < 0.1 || level.random.nextDouble() > 0.7) continue;
            Pt at = s.plane.toUV(e.position());
            double by = wearBoost * (6 + level.random.nextInt(7)) / s.toughness;
            for (long id : s.strokes) {
                InkStroke st = data.get(id);
                if (st == null) continue;
                byte[] w = null;
                for (int i = 0; i < st.size(); i++) {
                    double d = Math.hypot(st.u(i) - at.x(), st.v(i) - at.y());
                    if (d >= reach) continue;
                    if (w == null) w = wearOf(data, worn, st);
                    w[i] = InkWear.add(w[i], by * (1 - 0.5 * d / reach), false);
                }
                if (w != null && level.random.nextInt(4) == 0) {
                    int c = st.ink().color;
                    var dust = new DustParticleOptions(new Vector3f(((c >> 16) & 0xFF) / 255f, ((c >> 8) & 0xFF) / 255f, (c & 0xFF) / 255f), 0.5f);
                    Vec3 p = s.plane.toWorld(at.x(), at.y(), 0.04);
                    level.sendParticles(dust, p.x, p.y, p.z, 2, 0.12, 0.01, 0.12, 0);
                }
            }
        }
    }

    private static void washSweep(ServerLevel level) {
        GlyphLevelData data = GlyphLevelData.get(level);
        List<Long> ids = new ArrayList<>(data.ids());
        if (ids.isEmpty()) return;
        int start = WASH_CURSOR.getOrDefault(level.dimension(), 0) % ids.size();
        Map<Long, boolean[]> cuts = new HashMap<>();
        for (int k = 0; k < Math.min(WASH_BATCH, ids.size()); k++) {
            long id = ids.get((start + k) % ids.size());
            InkStroke s = data.get(id);
            SurfacePlane plane = data.planeOf(id);
            if (s == null || plane == null) continue;
            boolean[] drop = exposure(level, plane, s);
            if (drop != null) cuts.put(id, drop);
        }
        WASH_CURSOR.put(level.dimension(), start + WASH_BATCH);
        applyCuts(level, cuts);
    }

    private static boolean[] exposure(ServerLevel level, SurfacePlane plane, InkStroke s) {
        Map<BlockPos, Boolean> cache = new HashMap<>();
        boolean[] drop = null;
        for (int i = 0; i < s.size(); i++) {
            BlockPos pos = plane.blockAt(s.u(i), s.v(i));
            Boolean lost = cache.get(pos);
            if (lost == null) {
                if (!level.isLoaded(pos)) return null;
                lost = lost(level, plane, pos, s.ink(), s.state() == StrokeState.ACTIVE);
                cache.put(pos, lost);
            }
            if (lost) {
                if (drop == null) drop = new boolean[s.size()];
                drop[i] = true;
            }
        }
        return drop;
    }

    private static boolean lost(ServerLevel level, SurfacePlane plane, BlockPos pos, InkType ink, boolean awake) {
        boolean waterproof = ink.waterproof;
        BlockState block = level.getBlockState(pos);
        if (block.is(ModBlocks.WINDOWWAY_GAP.get())) return false;
        if (block.is(Blocks.MOVING_PISTON)) return false;
        if (block.isAir() || block.getCollisionShape(level, pos).isEmpty()) return true;
        if (awake) return false;
        BlockPos front = pos.relative(plane.face());
        BlockState f = level.getBlockState(front);
        if (!ink.fireproof && (f.getFluidState().is(FluidTags.LAVA) || f.is(BlockTags.FIRE))) return true;
        if (waterproof) return false;
        return f.getFluidState().is(FluidTags.WATER) || level.isRainingAt(front);
    }

    public static void washArea(ServerLevel level, Vec3 center, double radius, Set<Long> keep) {
        GlyphLevelData data = GlyphLevelData.get(level);
        Map<Long, boolean[]> cuts = new HashMap<>();
        int r = (int) Math.ceil(radius);
        for (Direction d : Direction.values()) {
            int c = (int) Math.floor(d.getAxis().choose(center.x, center.y, center.z));
            for (int layer = c - r - 1; layer <= c + r; layer++) {
                SurfacePlane plane = new SurfacePlane(d, layer);
                Pt uv = plane.toUV(center);
                for (InkStroke s : data.near(plane, uv.x(), uv.y(), 1)) {
                    if (s.ink().waterproof || keep.contains(s.id()) || cuts.containsKey(s.id())) continue;
                    boolean[] drop = null;
                    for (int i = 0; i < s.size(); i++) {
                        if (plane.toWorld(s.u(i), s.v(i), 0).distanceTo(center) <= radius) {
                            if (drop == null) drop = new boolean[s.size()];
                            drop[i] = true;
                        }
                    }
                    if (drop != null) cuts.put(s.id(), drop);
                }
            }
        }
        applyCuts(level, cuts);
    }

    private static byte fxForm(SpellProgram p) {
        return (byte) (p.form().ordinal() | ((p.element() == null ? 0 : p.element().ordinal() + 1) << 4));
    }

    private static void sendFx(ServerLevel level, ActiveSpell s, int ticksLeft) {
        SpellProgram p = s.program;
        int birdColor = ElementLook.color(p.element(), p.centerSign());
        for (ActiveSpell.Bird b : s.birds()) {
            Net.toNear(level, b.pos, 128, new Payloads.BirdFx(s.id, b.index, birdColor, (float) b.pos.x, (float) b.pos.y, (float) b.pos.z,
                    (float) b.vel.x, (float) b.vel.y, (float) b.vel.z, b.age, b.life));
        }
        Vec3 o = p.origin(), n = p.normal(), d = p.direction();

        Vec3 near = s.handheld ? o : Ships.seen(level, o.subtract(n.scale(0.2)));
        int mods = p.mods() | (s.worn ? WORN_FX : 0) | (s.worn && p.inverted() ? WORN_SINKING : 0);
        Net.toNear(level, near, 96, new Payloads.SpellFx(s.id, ElementLook.color(p.element(), p.centerSign()), fxForm(p),
                s.handheld && !s.worn, s.caster == null ? InkStroke.NOBODY : s.caster, o.x, o.y, o.z, (float) n.x, (float) n.y, (float) n.z,
                (float) d.x, (float) d.y, (float) d.z, (float) p.radius(), s.fxLength, s.fxWidth, ticksLeft, p.duration(), mods));

        for (int k = 0; k < p.layers().size(); k++) {
            SpellProgram l = p.layers().get(k);
            float len = k * 2 < s.layerFx.length ? s.layerFx[k * 2] : 0, wid = k * 2 + 1 < s.layerFx.length ? s.layerFx[k * 2 + 1] : 0;
            Vec3 ld = l.direction();
            Net.toNear(level, near, 96, new Payloads.SpellFx(-(s.id * 8 + k + 1), ElementLook.color(l.element(), l.centerSign()), fxForm(l),
                    s.handheld && !s.worn, s.caster == null ? InkStroke.NOBODY : s.caster, o.x, o.y, o.z, (float) n.x, (float) n.y, (float) n.z,
                    (float) ld.x, (float) ld.y, (float) ld.z, (float) l.radius(), len, wid, ticksLeft, p.duration(), 0));
        }
    }

    private static void stopSpell(ServerLevel level, ActiveSpell spell, boolean spent) {
        Map<Long, ActiveSpell> spells = ACTIVE.get(level.dimension());
        if (spells != null && spells.remove(spell.id) != null) sendFx(level, spell, 0);
        spell.stop(level);
        GlyphLevelData data = GlyphLevelData.get(level);
        List<Long> still = new ArrayList<>();
        for (long id : spell.strokes) if (data.get(id) != null) still.add(id);
        setStates(level, still, spent ? StrokeState.SPENT : StrokeState.IDLE, 0);
    }

    private static ActiveSpell spellUsing(ServerLevel level, Collection<Long> ids) {
        Map<Long, ActiveSpell> spells = ACTIVE.get(level.dimension());
        if (spells == null) return null;
        for (ActiveSpell s : spells.values()) {
            for (long id : ids) if (s.strokes.contains(id)) return s;
        }
        return null;
    }

    private static void removeStrokes(ServerLevel level, List<Long> ids) {
        removeStrokes(level, ids, Map.of());
    }

    private static void removeStrokes(ServerLevel level, List<Long> ids, Map<Long, List<Long>> piecesOf) {
        if (ids.isEmpty()) return;
        GlyphLevelData data = GlyphLevelData.get(level);
        for (long id : ids) data.remove(id);
        sendRemoved(level, ids.stream().mapToLong(Long::longValue).toArray());

        Map<Long, ActiveSpell> spells = ACTIVE.get(level.dimension());
        if (spells == null) return;
        for (ActiveSpell s : new ArrayList<>(spells.values())) {
            boolean hit = false;
            for (long id : ids) {
                if (!s.strokes.remove(id)) continue;
                hit = true;
                List<Long> left = piecesOf.get(id);
                if (left != null) s.strokes.addAll(left);
            }
            if (!hit) continue;
            ServerPlayer caster = s.caster != null && level.getPlayerByUUID(s.caster) instanceof ServerPlayer sp ? sp : null;
            InkStroke left = null;
            for (long id : ids) {
                for (long piece : piecesOf.getOrDefault(id, List.of())) {
                    InkStroke st = data.get(piece);
                    if (st != null && s.strokes.contains(piece) && (left == null || st.length() > left.length())) left = st;
                }
            }
            if (left == null) {
                for (long id : s.strokes) {
                    InkStroke st = data.get(id);
                    if (st != null && (left == null || st.length() > left.length())) left = st;
                }
            }
            if (left == null || s.plane == null || s.handheld) {
                stopSpell(level, s, false);
                continue;
            }
            evaluate(level, s.plane, left, caster, false, kept -> {
                Map<Long, ActiveSpell> now = ACTIVE.get(level.dimension());
                if (now != null && now.get(s.id) == s && kept != s) {
                    stopSpell(level, s, false);
                    LOGGER.debug("[glyph] ring broken");
                }
            }, 0);
        }
    }

    private static void setStates(ServerLevel level, Collection<Long> ids, StrokeState state, int color) {
        setStates(level, ids, state, color, 0);
    }

    private static void setStates(ServerLevel level, Collection<Long> ids, StrokeState state, int color, int ticksLeft) {
        if (ids.isEmpty()) return;
        GlyphLevelData data = GlyphLevelData.get(level);
        for (long id : ids) data.setState(id, state, color);
        long[] all = ids.stream().mapToLong(Long::longValue).toArray();
        for (int i = 0; i < all.length; i += BATCH_IDS) {
            Net.toDimension(level, new Payloads.StrokeStates(Arrays.copyOfRange(all, i, Math.min(all.length, i + BATCH_IDS)),
                    (byte) state.ordinal(), color, ticksLeft));
        }
    }

    public static void syncAll(ServerPlayer player) {
        Net.toPlayer(player, new Payloads.ResetGlyphs());
        GlyphLevelData data = GlyphLevelData.get(player.serverLevel());
        for (Map.Entry<SurfacePlane, List<InkStroke>> e : data.allByPlane().entrySet()) {
            sendAdded(player.serverLevel(), player, e.getKey(), e.getValue());
        }
        sendWear(player.serverLevel(), player, data.allWear());
    }

    public static void shutdown(ServerLevel level) {
        Map<Long, ActiveSpell> spells = ACTIVE.remove(level.dimension());
        if (spells == null) return;
        for (ActiveSpell s : spells.values()) {
            try {
                s.stop(level);
            } catch (RuntimeException e) {
                LOGGER.error("[glyph] stopping spell {} failed", s.id, e);
            }
        }
    }

    public static void clearLevel(ServerLevel level) {
        shutdown(level);
        ResourceKey<Level> dim = level.dimension();
        REREAD.remove(dim);
        RELIGHT.remove(dim);
        RELIGHT_SCANNED.remove(dim);
        READ_DONE.remove(dim);
        WASH_CURSOR.remove(dim);
    }

    public static String describeAround(ServerLevel level, SurfacePlane plane, long strokeId) {
        InkStroke s = GlyphLevelData.get(level).get(strokeId);
        if (s == null) return "no stroke " + strokeId;
        Found f = find(level, plane, s, null);
        return f == null ? "no ring" : f.reading().describe() + " used=" + f.usedIds();
    }

    public static void rescanLitGlyphs(ServerLevel level) {
        RELIGHT_SCANNED.remove(level.dimension());
        RELIGHT.remove(level.dimension());
    }

    private static void relight(ServerLevel level) {
        ResourceKey<Level> dim = level.dimension();
        GlyphLevelData data = GlyphLevelData.get(level);
        if (RELIGHT_SCANNED.add(dim)) {
            ArrayDeque<Long> q = new ArrayDeque<>();
            for (long id : data.ids()) {
                InkStroke s = data.get(id);
                if (s != null && s.state() == StrokeState.ACTIVE) q.add(id);
            }
            if (!q.isEmpty()) RELIGHT.put(dim, q);
        }
        ArrayDeque<Long> q = RELIGHT.get(dim);
        if (q == null || q.isEmpty()) return;
        for (int n = Math.min(q.size(), 256); n > 0 && relightReading < 2; n--) {
            long id = q.poll();
            InkStroke s = data.get(id);
            SurfacePlane plane = data.planeOf(id);
            if (s == null || plane == null || s.size() == 0 || s.state() != StrokeState.ACTIVE || spellUsing(level, List.of(id)) != null) continue;
            if (!level.shouldTickBlocksAt(plane.blockAt(s.u(0), s.v(0)))) {
                q.add(id);
                continue;
            }
            relightReading++;
            evaluate(level, plane, s, null, true, lit -> {
                relightReading--;
                InkStroke still = GlyphLevelData.get(level).get(id);
                if (lit == null && still != null && still.state() == StrokeState.ACTIVE && spellUsing(level, List.of(id)) == null) {
                    setStates(level, List.of(id), StrokeState.IDLE, 0);
                }
            }, 0);
        }
    }

    public static boolean isDrawable(Level level, SurfacePlane plane, double u, double v) {
        BlockPos pos = plane.blockAt(u, v);
        BlockState s = level.getBlockState(pos);
        if (s.isAir() || s.getCollisionShape(level, pos).isEmpty()) return false;
        BlockPos front = pos.relative(plane.face());
        BlockState f = level.getBlockState(front);
        return !f.isFaceSturdy(level, front, plane.face().getOpposite());
    }

    private static InteractionHand penHand(ServerPlayer p) {
        if (p.getMainHandItem().getItem() instanceof InkPenItem) return InteractionHand.MAIN_HAND;
        if (p.getOffhandItem().getItem() instanceof InkPenItem) return InteractionHand.OFF_HAND;
        return null;
    }

    private static double[] toArray(List<Double> l) {
        double[] f = new double[l.size()];
        for (int i = 0; i < f.length; i++) f[i] = l.get(i);
        return f;
    }
}
