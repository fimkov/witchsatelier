package com.witchcraft.glyph;

import com.witchcraft.glyph.recognize.GlyphAnalyzer;
import com.witchcraft.glyph.recognize.GlyphReading;
import com.witchcraft.glyph.recognize.Pt;
import com.witchcraft.glyph.recognize.RingFinder;
import com.witchcraft.glyph.storage.GlyphLevelData;
import com.witchcraft.glyph.surface.InkStroke;
import com.witchcraft.glyph.surface.StrokeState;
import com.witchcraft.glyph.surface.SurfacePlane;
import com.witchcraft.glyph.ink.InkSupply;
import com.witchcraft.glyph.ink.InkType;
import com.witchcraft.glyph.ink.LoadedInk;
import com.witchcraft.item.InkBottleItem;
import com.witchcraft.item.InkPenItem;
import net.minecraft.world.item.Items;
import com.witchcraft.network.Payloads;
import com.witchcraft.spell.ActiveSpell;
import com.witchcraft.spell.ElementLook;
import com.witchcraft.spell.SpellCompiler;
import com.witchcraft.spell.SpellProgram;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import com.witchcraft.network.Net;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Server-side heart of the mod: accepts ink, reads glyphs, starts and stops spells. */
public final class GlyphService {
    /** Width of a pen line in blocks (one texture pixel). */
    public static final double INK_WIDTH = 1.0 / 16;
    /** Ink units spent per block of line. */
    public static final double INK_PER_BLOCK = 4;
    public static final double REACH = 7;
    public static final double RAG_RADIUS = 0.24, RAG_MIN = 0.08, RAG_MAX = 0.8, PEN_ERASE_RADIUS = 0.12;
    /** An empty ring smaller than this does nothing instead of bursting. */
    public static final double MIN_BURST_RADIUS = 0.75;
    /** Rings smaller than this never cast: they are loops inside symbols (a sigil's triangle, an eye...). */
    public static final double MIN_GLYPH_RADIUS = 0.4;

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static final Map<ResourceKey<Level>, Map<Long, ActiveSpell>> ACTIVE = new HashMap<>();
    private static long nextSpellId = 1;

    private GlyphService() {}

    // ------------------------------------------------------------------ input from players

    public static void handleDraw(ServerPlayer player, SurfacePlane plane, double[] uv) {
        ServerLevel level = player.serverLevel();
        InkSupply supply = InkSupply.of(player);
        if (supply == null || uv.length < 2 || uv.length % 2 != 0 || player.isSpectator()) return;
        InkType ink = supply.ink;
        if (ink == InkType.BLOOD) com.witchcraft.entity.Taboo.add(player, 1);
        int inkLeft = supply.available();
        if (inkLeft <= 0) {
            player.displayClientMessage(Component.translatable("message.witchcraft.pen_dry"), true);
            return;
        }

        // keep only points that lie on a real, exposed face within reach; split where the line leaves the surface
        List<double[]> pieces = new ArrayList<>();
        List<Double> cur = new ArrayList<>();
        Vec3 eye = player.getEyePosition();
        double spent = 0;
        for (int i = 0; i + 1 < uv.length; i += 2) {
            double u = uv[i], v = uv[i + 1];
            if (!Double.isFinite(u) || !Double.isFinite(v)) continue;
            boolean ok = plane.toWorld(u, v, 0).distanceTo(eye) <= REACH && isDrawable(level, plane, u, v);
            double seg = 0;
            if (ok && !cur.isEmpty()) {
                int n = cur.size();
                seg = Math.hypot(u - cur.get(n - 2), v - cur.get(n - 1));
                if (seg > 1.5) ok = false; // a jump, not a line
            }
            if ((spent + seg) * INK_PER_BLOCK > inkLeft) {
                player.displayClientMessage(Component.translatable("message.witchcraft.pen_dry"), true);
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
        if (pieces.isEmpty()) return;

        supply.consume(Math.max(1, (int) Math.ceil(spent * INK_PER_BLOCK)));

        inscribe(level, plane, pieces, ink, player);
    }

    /** Puts already-validated ink on a plane and reacts to it (reads glyphs, starts spells). */
    public static List<InkStroke> inscribe(ServerLevel level, SurfacePlane plane, List<double[]> pieces, InkType ink, ServerPlayer caster) {
        GlyphLevelData data = GlyphLevelData.get(level);
        List<InkStroke> added = new ArrayList<>();
        for (double[] piece : pieces) {
            if (piece.length < 2) continue;
            InkStroke s = InkStroke.of(data.newId(), piece, ink, caster == null ? null : caster.getUUID(), StrokeState.IDLE, 0);
            data.add(plane, s);
            added.add(s);
        }
        Net.toDimension(level, new Payloads.StrokesAdded(plane, added));
        for (InkStroke s : added) evaluate(level, plane, s, caster);
        return added;
    }

    /**
     * Puts ink down without reading it as a glyph (a creature's drawing whose meaning it decides itself, such as the
     * Brimhat Elder's great ring); it is still ink in the world, to be rubbed out, washed off or broken like any.
     */
    public static List<InkStroke> inkWithoutReading(ServerLevel level, SurfacePlane plane, List<double[]> pieces, InkType ink, UUID author) {
        GlyphLevelData data = GlyphLevelData.get(level);
        List<InkStroke> added = new ArrayList<>();
        for (double[] piece : pieces) {
            if (piece.length < 2) continue;
            InkStroke s = InkStroke.of(data.newId(), piece, ink, author, StrokeState.IDLE, 0);
            data.add(plane, s);
            added.add(s);
        }
        Net.toDimension(level, new Payloads.StrokesAdded(plane, added));
        return added;
    }

    /** Removes these strokes (those still there). */
    public static void eraseStrokes(ServerLevel level, Collection<Long> ids) {
        GlyphLevelData data = GlyphLevelData.get(level);
        List<Long> there = new ArrayList<>();
        for (long id : ids) if (data.get(id) != null) there.add(id);
        removeStrokes(level, there);
    }

    public static List<InkStroke> inscribe(ServerLevel level, SurfacePlane plane, List<double[]> pieces, ServerPlayer caster) {
        return inscribe(level, plane, pieces, InkType.CONJURING, caster);
    }

    /** Starts a spell that has no ink in the world (cast from a page in the hand). */
    public static ActiveSpell startSpell(ServerLevel level, SurfacePlane plane, SpellProgram program, ServerPlayer caster, boolean handheld) {
        return startSpell(level, plane, program, caster == null ? null : caster.getUUID(), handheld);
    }

    /** A spell cast by anyone, players or creatures. */
    public static ActiveSpell startSpell(ServerLevel level, SurfacePlane plane, SpellProgram program, UUID who, boolean handheld) {
        ActiveSpell spell = handheld ? ActiveSpell.handheld(nextSpellId++, who, program)
                : new ActiveSpell(nextSpellId++, plane, Set.of(), who, program);
        ACTIVE.computeIfAbsent(level.dimension(), k -> new HashMap<>()).put(spell.id, spell);
        return spell;
    }

    /** The boots spell the player is running, if any. */
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

    /** Rubbing ink out with a rag (wide) or the pen (narrow): only the points under it go. */
    public static void handleErase(ServerPlayer player, SurfacePlane plane, double u, double v, double wanted) {
        ServerLevel level = player.serverLevel();
        boolean rag = player.getMainHandItem().getItem() instanceof com.witchcraft.item.RagItem
                || player.getOffhandItem().getItem() instanceof com.witchcraft.item.RagItem;
        if ((!rag && penHand(player) == null) || plane.toWorld(u, v, 0).distanceTo(player.getEyePosition()) > REACH) return;
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
            level.sendParticles(new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(0.15f, 0.12f, 0.25f), 0.6f),
                    w.x, w.y, w.z, 3, 0.1, 0.1, 0.1, 0);
        }
        applyCuts(level, cuts);
    }

    /** Blocks that disappeared take the ink on their faces with them. */
    /** Removes every stroke that starts within {@code radius} of {@code center} (tools and tests). */
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

    // ------------------------------------------------------------------ pistons carry ink

    /** Moving blocks already handled (a piston event can see blocks moved by another piston the same tick). */
    private static final Set<net.minecraft.world.level.block.piston.PistonMovingBlockEntity> CARRIED =
            java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());
    /** Ink a piston moved, to be read again once its blocks have arrived: {due game time, stroke id}. */
    private static final Map<ResourceKey<Level>, List<long[]>> REREAD = new HashMap<>();
    private static final int ARRIVAL_TICKS = 3;

    /** After a piston moved: every block that set off this tick takes the ink on its faces along. */
    public static void onPistonMoved(ServerLevel level, BlockPos piston) {
        if (GlyphLevelData.get(level).ids().isEmpty()) return;
        Map<Direction, List<BlockPos>> moved = new java.util.EnumMap<>(Direction.class);
        net.minecraft.world.level.ChunkPos c = new net.minecraft.world.level.ChunkPos(piston);
        // a piston moves at most 12 blocks, never further than the next chunk over
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            if (!level.hasChunk(c.x + dx, c.z + dz)) continue;
            for (var be : level.getChunk(c.x + dx, c.z + dz).getBlockEntities().values()) {
                if (be instanceof net.minecraft.world.level.block.piston.PistonMovingBlockEntity m && !m.isSourcePiston()
                        && m.getProgress(1) == 0 && CARRIED.add(m)) {
                    Direction d = m.getMovementDirection();
                    moved.computeIfAbsent(d, k -> new ArrayList<>()).add(m.getBlockPos().relative(d.getOpposite()));
                }
            }
        }
        for (var e : moved.entrySet()) onBlocksMoved(level, e.getValue(), e.getKey());
    }

    /**
     * Blocks moved one step {@code dir} carry the ink on their faces: those points go with them, the rest stays (a
     * line half on a moved block tears in two). Ink of a running glyph that moves stops its spell; everything that
     * moved is read again where it arrives, so two halves of a glyph pushed together wake as one.
     */
    public static void onBlocksMoved(ServerLevel level, Collection<BlockPos> from, Direction dir) {
        GlyphLevelData data = GlyphLevelData.get(level);
        if (from.isEmpty() || data.ids().isEmpty()) return;
        Set<BlockPos> moving = new HashSet<>(from);
        Map<Long, SurfacePlane> touched = new java.util.LinkedHashMap<>();
        for (BlockPos pos : moving) {
            for (Direction d : Direction.values()) {
                SurfacePlane plane = SurfacePlane.of(pos, d);
                Pt c = plane.toUV(Vec3.atCenterOf(pos));
                for (InkStroke s : data.near(plane, c.x(), c.y(), 1)) touched.putIfAbsent(s.id(), plane);
            }
        }
        Vec3 step = Vec3.atLowerCornerOf(dir.getNormal());
        List<Long> gone = new ArrayList<>();
        Map<SurfacePlane, List<InkStroke>> pieces = new java.util.LinkedHashMap<>();
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
            // spent ink stays spent; ink of a running glyph is fresh again and gets read where it ends up
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
            Net.toDimension(level, new Payloads.StrokesAdded(e.getKey(), e.getValue()));
        }
        removeStrokes(level, gone);
    }

    /** Reads again the ink pistons moved, once the blocks under it have come to rest. */
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
            // a piece already woken with another of its glyph needs no second reading
            if (s == null || plane == null || s.state() != StrokeState.IDLE) continue;
            ServerPlayer author = level.getPlayerByUUID(s.author()) instanceof ServerPlayer p ? p : null;
            evaluate(level, plane, s, author);
        }
    }

    /**
     * Removes the marked points of each stroke; what is left of a stroke becomes new, shorter strokes.
     * A cut through a ring breaks it, so its spell stops.
     */
    public static void applyCuts(ServerLevel level, Map<Long, boolean[]> cuts) {
        if (cuts.isEmpty()) return;
        GlyphLevelData data = GlyphLevelData.get(level);
        List<Long> removed = new ArrayList<>();
        Map<SurfacePlane, List<InkStroke>> pieces = new HashMap<>();
        for (Map.Entry<Long, boolean[]> e : cuts.entrySet()) {
            InkStroke s = data.get(e.getKey());
            SurfacePlane plane = data.planeOf(e.getKey());
            if (s == null || plane == null) continue;
            boolean[] drop = e.getValue();
            removed.add(s.id());
            List<Double> cur = new ArrayList<>();
            for (int i = 0; i <= s.size(); i++) {
                boolean keep = i < s.size() && !drop[i];
                if (keep) {
                    cur.add(s.u(i));
                    cur.add(s.v(i));
                } else if (!cur.isEmpty()) {
                    // a lone leftover point of a long line is just a smudge
                    if (cur.size() >= 4 || s.size() == 1) {
                        // spent ink stays spent; a cut through a running glyph stops it, the rest is fresh again
                        StrokeState st = s.state() == StrokeState.SPENT ? StrokeState.SPENT : StrokeState.IDLE;
                        InkStroke piece = InkStroke.of(data.newId(), toArray(cur), s.ink(), s.author(), st, 0);
                        pieces.computeIfAbsent(plane, k -> new ArrayList<>()).add(piece);
                    }
                    cur.clear();
                }
            }
        }
        // add what is left first, so a spell that re-reads its glyph after the cut sees the remaining pieces
        for (Map.Entry<SurfacePlane, List<InkStroke>> e : pieces.entrySet()) {
            for (InkStroke piece : e.getValue()) data.add(e.getKey(), piece);
            Net.toDimension(level, new Payloads.StrokesAdded(e.getKey(), e.getValue()));
        }
        removeStrokes(level, removed);
    }

    // ------------------------------------------------------------------ reading glyphs

    private record Found(GlyphReading reading, List<InkStroke> strokes) {
        Set<Long> usedIds() {
            Set<Long> ids = new HashSet<>();
            for (int i : reading.usedStrokes()) ids.add(strokes.get(i).id());
            return ids;
        }
    }

    /** Finds the glyph that {@code focus} belongs to (the largest ring that contains it), if any. */
    private static Found find(ServerLevel level, SurfacePlane plane, InkStroke focus, ServerPlayer player) {
        GlyphLevelData data = GlyphLevelData.get(level);
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
        Pt fc = focus.centroid();
        RingFinder.Ring best = null;
        for (RingFinder.Ring ring : RingFinder.find(pts, INK_WIDTH)) {
            boolean contains = ring.strokes().contains(focusIdx) || fc.dist(ring.center()) <= ring.radius() * 1.05;
            if (contains && (best == null || ring.radius() > best.radius())) best = ring;
        }
        if (best == null) {
            LOGGER.debug("[glyph] no ring around stroke {}: {}", focus.id(), RingFinder.diagnose(pts));
            return null;
        }

        List<InkStroke> subset = new ArrayList<>();
        List<List<Pt>> subPts = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            InkStroke s = candidates.get(i);
            if (best.strokes().contains(i) || s.centroid().dist(best.center()) < best.radius() * 1.02) {
                subset.add(s);
                subPts.add(pts.get(i));
            }
        }
        double up = upAngle(plane, player);
        boolean flat = Math.abs(plane.normal().y) >= 0.5; // floors and ceilings can be read from any side
        return new Found(flat ? GlyphAnalyzer.analyzeFromAnySide(subPts, INK_WIDTH, up) : GlyphAnalyzer.analyze(subPts, INK_WIDTH, up), subset);
    }

    /** Which way was "up" for the drawer, in the plane's coordinates (on floors and ceilings: where they faced). */
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
        Found f = find(level, plane, focus, player);
        if (f == null || f.reading().ring() == null) return;
        GlyphReading r = f.reading();
        Set<Long> ids = f.usedIds();
        LOGGER.debug("[glyph] {}", r.describe());
        ActiveSpell running = spellUsing(level, ids);

        if (!r.closed()) {
            if (running != null) stopSpell(level, running, false);
            LOGGER.debug("[glyph] {}", describeOpen(r));
            return;
        }
        // a copy traced over a pinned page is the page's glyph, all of its ink
        List<Integer> inked = r.usedStrokes();
        Tracing.Result copy = Tracing.traced(player, plane, f.strokes(), r, INK_WIDTH);
        if (copy.waiting()) {
            // a copy of a pinned page still being traced: its ring is closed, but the rest is yet to come
            if (running != null) stopSpell(level, running, false);
            return;
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
        // the ink mix of the glyph, weighted by line length
        double len = 0, pot = 0, dur = 0, mis = 0;
        for (int i : inked) {
            InkStroke st = f.strokes().get(i);
            double l = Math.max(0.05, st.length());
            len += l;
            pot += l * st.ink().potency;
            dur += l * st.ink().duration;
            mis += l * st.ink().misfire;
        }
        SpellProgram program = len > 0
                ? SpellCompiler.compile(r, plane, pot / len, dur / len, mis / len)
                : SpellCompiler.compile(r, plane, 1.0);
        // an Arcane Lense right at the glyph makes far more of it
        if (com.witchcraft.block.ArcaneLensBlock.near(level, program.origin()) || com.witchcraft.block.ArcaneLensBlock.near(level, plane.toWorld(r.ring().center(), -0.5))) {
            program = program.amplified(com.witchcraft.block.ArcaneLensBlock.AMPLIFY);
            if (player != null) com.witchcraft.advancement.MagicTrigger.fire(player, "cast_amplified");
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD, program.origin().x, program.origin().y, program.origin().z, 12, 0.4, 0.4, 0.4, 0.03);
        }
        // nothing readable inside, or a small empty loop (often just a sigil's triangle): wait for more ink
        boolean tooSmallToBurst = program.form() == SpellProgram.Form.BURST && r.ring().radius() < MIN_BURST_RADIUS;
        if (r.ring().radius() < MIN_GLYPH_RADIUS) return; // a loop this small is part of a symbol, not a glyph's ring
        if (!tooSmallToBurst) DrawingDump.save(f.strokes(), upAngle(plane, player), r);
        if (program.form() == SpellProgram.Form.FIZZLE || tooSmallToBurst) {
            if (running != null) stopSpell(level, running, false);
            if (!tooSmallToBurst) fizzle(level, plane.toWorld(r.ring().center(), 0.05), plane, r.ring().radius());
            if (player != null && !tooSmallToBurst) {
                LOGGER.debug("[glyph] {}", describeOpen(r));
            }
            return;
        }
        if (running != null) {
            // the glyph was edited while running: the spell changes with it
            running.program = program;
            running.strokes.addAll(ids);
            setStates(level, running.strokes, StrokeState.ACTIVE, ElementLook.color(program.element(), program.centerSign()), running.ticksLeft);
        } else {
            ActiveSpell spell = new ActiveSpell(nextSpellId++, plane, ids, player != null ? player.getUUID() : null, program);
            ACTIVE.computeIfAbsent(level.dimension(), k -> new HashMap<>()).put(spell.id, spell);
            setStates(level, ids, StrokeState.ACTIVE, litColor(program), program.has(SpellProgram.TREAD) ? 0 : program.duration());
            if (player != null && program.form() == SpellProgram.Form.PETRIFY) com.witchcraft.entity.Taboo.add(player, 40);
            if (player != null) com.witchcraft.advancement.MagicTrigger.cast(player, program, "surface");
            wakeLinked(level, plane, spell, r, player);
        }
        LOGGER.debug("[cast] {}", program.summary());
    }

    /**
     * Glyphs joined by lines of ink are one work: when one wakes, every glyph joined to it wakes too (even with its
     * own ring still open), and each makes the others stronger: many small glyphs joined beat one big one.
     */
    /** Colour bit a client reads as "awake but holding": a snare waiting for a step. */
    public static final int ARMED = 0x01000000;

    private static int litColor(SpellProgram program) {
        int c = ElementLook.color(program.element(), program.centerSign()) & 0xFFFFFF;
        return program.has(SpellProgram.TREAD) ? c | ARMED : c;
    }

    /** A snare was stepped on: its lines catch fire as any glyph's do when it wakes. */
    public static void sprung(ServerLevel level, ActiveSpell spell) {
        setStates(level, spell.strokes, StrokeState.ACTIVE, ElementLook.color(spell.program.element(), spell.program.centerSign()) & 0xFFFFFF,
                spell.program.duration());
    }

    private static void wakeLinked(ServerLevel level, SurfacePlane plane, ActiveSpell first, GlyphReading firstReading, ServerPlayer player) {
        List<ActiveSpell> woken = new ArrayList<>();
        woken.add(first);
        java.util.ArrayDeque<Object[]> queue = new java.util.ArrayDeque<>();
        queue.add(new Object[]{firstReading.ring(), new HashSet<>(first.strokes)});
        Set<Long> claimed = new HashSet<>(first.strokes);
        while (!queue.isEmpty() && woken.size() < 12) {
            Object[] q = queue.poll();
            RingFinder.Ring ring = (RingFinder.Ring) q[0];
            for (Object[] hit : linkedTo(level, plane, ring, claimed, player)) {
                Found next = (Found) hit[0];
                Set<Long> ids = new HashSet<>(next.usedIds());
                // the joining line is part of the work: it lights up with it, the light running along it
                ids.add((Long) hit[1]);
                if (spellUsing(level, ids) != null) continue;
                SpellProgram program = compileFound(level, plane, next);
                if (program == null) continue;
                ActiveSpell spell = new ActiveSpell(nextSpellId++, plane, ids, player != null ? player.getUUID() : null, program);
                ACTIVE.computeIfAbsent(level.dimension(), k -> new HashMap<>()).put(spell.id, spell);
                setStates(level, ids, StrokeState.ACTIVE, litColor(program), program.has(SpellProgram.TREAD) ? 0 : program.duration());
                claimed.addAll(ids);
                woken.add(spell);
                queue.add(new Object[]{next.reading().ring(), ids});
            }
        }
        if (woken.size() < 2) return;
        double boost = Math.min(2.0, 1 + 0.2 * (woken.size() - 1));
        for (ActiveSpell s : woken) s.program = s.program.amplified(boost);
        if (player != null) com.witchcraft.advancement.MagicTrigger.fire(player, "linked_glyphs");
        LOGGER.debug("[cast] {} linked glyphs woke together (x{})", woken.size(), boost);
    }

    /** Other glyphs reached from {@code ring} by a line of ink leaving its rim and ending on theirs. */
    /** Each result is {the glyph found, the id of the line that joins it}. */
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
            if (far.dist(c) < rr + 0.5) continue; // it stays by this ring: a sign, a closer, not a link
            // the far end must lie on another glyph's ring
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
                if (!java.util.Collections.disjoint(ids, seen)) continue;
                seen.addAll(ids);
                seen.add(link.id());
                out.add(new Object[]{f, link.id()});
                break;
            }
        }
        return out;
    }

    /** The spell a glyph found on a surface would cast, or null if it would cast nothing. */
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

    /** A closed ring that reads as nothing: it sputters (smoke and grey sparks around the ring), no ink is spent. */
    public static void fizzle(ServerLevel level, Vec3 at, com.witchcraft.spell.GlyphFrame frame, double radius) {
        level.playSound(null, at.x, at.y, at.z, net.minecraft.sounds.SoundEvents.CANDLE_EXTINGUISH, net.minecraft.sounds.SoundSource.PLAYERS, 1.2f, 0.7f);
        for (int k = 0; k < 24; k++) {
            double a = k * Math.PI * 2 / 24;
            Vec3 p = at.add(frame.planeVector(Math.cos(a), Math.sin(a)).scale(radius));
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE, p.x, p.y, p.z, 1, 0.02, 0.05, 0.02, 0.01);
            if (k % 3 == 0) level.sendParticles(com.witchcraft.registry.ModParticles.spark(0xB0A0A0A8), p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0.02);
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

    // ------------------------------------------------------------------ running spells

    public static void tick(ServerLevel level) {
        if (level.getGameTime() % 20 == 7) washSweep(level);
        rereadMoved(level);
        Map<Long, ActiveSpell> spells = ACTIVE.get(level.dimension());
        if (spells == null || spells.isEmpty()) return;
        for (Iterator<ActiveSpell> it = new ArrayList<>(spells.values()).iterator(); it.hasNext(); ) {
            ActiveSpell s = it.next();
            boolean alive;
            try {
                alive = s.tick(level);
            } catch (RuntimeException e) {
                com.mojang.logging.LogUtils.getLogger().error("WitchCraft spell {} failed", s.id, e);
                alive = false;
            }
            if (!alive && spells.containsKey(s.id)) stopSpell(level, s, true);
            sendFx(level, s, alive ? s.ticksLeft : 0);
        }
    }

    // ------------------------------------------------------------------ water washes ink away

    private static final Map<ResourceKey<Level>, Integer> WASH_CURSOR = new HashMap<>();
    private static final int WASH_BATCH = 256;

    /**
     * Checks a slice of all ink each second. Points under water or out in the rain wash off (unless the ink is
     * waterproof); points touching lava or fire burn away whatever the ink; points whose block has gone
     * (pushed by a piston, replaced) fall off with it.
     */
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

    /** Which points of the stroke are lost to water, fire or a missing block; null if none (or not loaded). */
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

    /**
     * @param awake the stroke belongs to a running glyph: its own magic holds it, so the fire or water it makes
     *              (or any around it) does not take it; only losing the block under it does
     */
    private static boolean lost(ServerLevel level, SurfacePlane plane, BlockPos pos, com.witchcraft.glyph.ink.InkType ink, boolean awake) {
        boolean waterproof = ink.waterproof;
        BlockState block = level.getBlockState(pos);
        if (block.is(com.witchcraft.registry.ModBlocks.WINDOWWAY_GAP.get())) return false; // the wall only stepped aside
        if (block.is(net.minecraft.world.level.block.Blocks.MOVING_PISTON)) return false; // a block still sliding into place
        if (block.isAir() || block.getCollisionShape(level, pos).isEmpty()) return true;
        if (awake) return false;
        BlockPos front = pos.relative(plane.face());
        BlockState f = level.getBlockState(front);
        if (!ink.fireproof && (f.getFluidState().is(FluidTags.LAVA) || f.is(BlockTags.FIRE))) return true;
        if (waterproof) return false;
        return f.getFluidState().is(FluidTags.WATER) || level.isRainingAt(front);
    }

    /** Water magic rinses non-waterproof ink around {@code center} (except the spell's own glyph). */
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

    /** Form in the low four bits, element + 1 in the high four (0: none). */
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
        Net.toNear(level, o, 96, new Payloads.SpellFx(s.id, ElementLook.color(p.element(), p.centerSign()), fxForm(p),
                s.handheld && !s.worn, s.caster == null ? InkStroke.NOBODY : s.caster, o.x, o.y, o.z, (float) n.x, (float) n.y, (float) n.z,
                (float) d.x, (float) d.y, (float) d.z, (float) p.radius(), s.fxLength, s.fxWidth, ticksLeft, p.duration(), p.mods()));
        // each outer layer of a nested glyph shows as its own wider ring and its own beam
        for (int k = 0; k < p.layers().size(); k++) {
            SpellProgram l = p.layers().get(k);
            float len = k * 2 < s.layerFx.length ? s.layerFx[k * 2] : 0, wid = k * 2 + 1 < s.layerFx.length ? s.layerFx[k * 2 + 1] : 0;
            Vec3 ld = l.direction();
            Net.toNear(level, o, 96, new Payloads.SpellFx(-(s.id * 8 + k + 1), ElementLook.color(l.element(), l.centerSign()), fxForm(l),
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
        if (ids.isEmpty()) return;
        GlyphLevelData data = GlyphLevelData.get(level);
        Map<Long, SurfacePlane> planes = new HashMap<>();
        for (long id : ids) {
            SurfacePlane p = data.planeOf(id);
            if (p != null) planes.put(id, p);
            data.remove(id);
        }
        Net.toDimension(level, new Payloads.StrokesRemoved(ids.stream().mapToLong(Long::longValue).toArray()));

        // a spell whose ink was removed stops; if what is left still forms a closed glyph it starts again
        Map<Long, ActiveSpell> spells = ACTIVE.get(level.dimension());
        if (spells == null) return;
        for (ActiveSpell s : new ArrayList<>(spells.values())) {
            boolean hit = false;
            for (long id : ids) if (s.strokes.contains(id)) { hit = true; break; }
            if (!hit) continue;
            ServerPlayer caster = s.caster == null ? null : (ServerPlayer) level.getPlayerByUUID(s.caster);
            stopSpell(level, s, false);
            LOGGER.debug("[glyph] ring broken");
            for (long id : s.strokes) {
                InkStroke left = data.get(id);
                if (left != null) {
                    evaluate(level, s.plane, left, caster);
                    break;
                }
            }
        }
    }

    private static void setStates(ServerLevel level, Collection<Long> ids, StrokeState state, int color) {
        setStates(level, ids, state, color, 0);
    }

    private static void setStates(ServerLevel level, Collection<Long> ids, StrokeState state, int color, int ticksLeft) {
        if (ids.isEmpty()) return;
        GlyphLevelData data = GlyphLevelData.get(level);
        for (long id : ids) data.setState(id, state, color);
        Net.toDimension(level,
                new Payloads.StrokeStates(ids.stream().mapToLong(Long::longValue).toArray(), (byte) state.ordinal(), color, ticksLeft));
    }

    // ------------------------------------------------------------------ sync & helpers

    /** Sends every stroke of the player's current dimension, in packets of bounded size. */
    public static void syncAll(ServerPlayer player) {
        Net.toPlayer(player, new Payloads.ResetGlyphs());
        GlyphLevelData data = GlyphLevelData.get(player.serverLevel());
        for (Map.Entry<SurfacePlane, List<InkStroke>> e : data.allByPlane().entrySet()) {
            List<InkStroke> batch = new ArrayList<>();
            int points = 0;
            for (InkStroke s : e.getValue()) {
                if (points + s.size() > 30000 && !batch.isEmpty()) {
                    Net.toPlayer(player, new Payloads.StrokesAdded(e.getKey(), batch));
                    batch = new ArrayList<>();
                    points = 0;
                }
                batch.add(s);
                points += s.size();
            }
            if (!batch.isEmpty()) Net.toPlayer(player, new Payloads.StrokesAdded(e.getKey(), batch));
        }
    }

    public static void clearLevel(ResourceKey<Level> dim) {
        ACTIVE.remove(dim);
        REREAD.remove(dim);
    }

    /** True if (u, v) lies on a solid face that is not covered by the next block. */
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
