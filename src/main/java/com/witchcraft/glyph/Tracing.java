package com.witchcraft.glyph;

import com.witchcraft.glyph.page.GlyphPages;
import com.witchcraft.glyph.page.PageKind;
import com.witchcraft.glyph.recognize.GlyphAnalyzer;
import com.witchcraft.glyph.recognize.GlyphReading;
import com.witchcraft.glyph.recognize.Pt;
import com.witchcraft.glyph.surface.InkStroke;
import com.witchcraft.glyph.surface.SurfacePlane;
import com.witchcraft.item.InkPenItem;
import com.witchcraft.registry.ModComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Copying a glyph from a grimoire: a witch pins the page's glyph faintly on a surface (the client's trace guide) and
 * follows it with the pen. A copy made that way is the page's glyph: once its ring is closed, ink that lies along the
 * whole guide and hardly anywhere else is read as what the page shows, however the hand shook. A glyph drawn freehand,
 * or one that strays from the guide, is read as drawn.
 */
public final class Tracing {
    private Tracing() {}

    /** Where a player pinned the guide: page coordinates turned by {@code turn} and scaled, centred at (u, v). */
    public record Pin(SurfacePlane plane, double u, double v, double turn, double scale) {}

    private static final Map<UUID, Pin> PINS = new HashMap<>();

    /** Share of the guide the ink must cover, and the most ink that may lie off it. */
    static final double MIN_COVERAGE = 0.85, MAX_EXCESS = 0.15;

    public static void pin(ServerPlayer p, Pin pin) {
        if (pin == null) PINS.remove(p.getUUID());
        else PINS.put(p.getUUID(), pin);
    }

    public static void forget(UUID player) {
        PINS.remove(player);
    }

    /** The grimoire in the hand that does not hold the pen, or empty (as the client's guide finds it). */
    static ItemStack grimoire(ServerPlayer p) {
        ItemStack main = p.getMainHandItem(), off = p.getOffhandItem();
        if (main.getItem() instanceof InkPenItem && PageKind.of(off) == PageKind.GRIMOIRE) return off;
        if (off.getItem() instanceof InkPenItem && PageKind.of(main) == PageKind.GRIMOIRE) return main;
        return ItemStack.EMPTY;
    }

    /** The pinned guide on {@code plane}, in plane coordinates, or null. */
    static List<List<Pt>> guide(ServerPlayer p, SurfacePlane plane) {
        Pin pin = PINS.get(p.getUUID());
        if (pin == null || !pin.plane().equals(plane)) return null;
        ItemStack book = grimoire(p);
        GlyphPages gp = book.isEmpty() ? null : book.get(ModComponents.GLYPH_PAGES.get());
        if (gp == null || gp.pages().isEmpty()) return null;
        GlyphPages.Page page = gp.page(gp.current());
        if (page.strokes().isEmpty()) return null;
        return layOut(page, pin);
    }

    /** A page laid out exactly as the client shows the guide: page "up" turned to the drawer's up, scaled, centred where pinned. */
    static List<List<Pt>> layOut(GlyphPages.Page page, Pin pin) {
        double rot = pin.turn() - Math.PI / 2, c = Math.cos(rot), s = Math.sin(rot);
        List<List<Pt>> out = new ArrayList<>();
        for (GlyphPages.PageStroke st : page.strokes()) {
            float[] pts = st.pts();
            List<Pt> q = new ArrayList<>(pts.length / 2);
            for (int i = 0; i + 1 < pts.length; i += 2) {
                double x = pts[i] * pin.scale(), y = pts[i + 1] * pin.scale();
                q.add(new Pt(pin.u() + x * c - y * s, pin.v() + x * s + y * c));
            }
            out.add(q);
        }
        return out;
    }

    /**
     * How a closed glyph stands against the guide its drawer pinned there.
     *
     * @param reading the page's reading, if the ink is a faithful copy of it (else null)
     * @param waiting the ink so far follows the guide but does not yet cover it: the copy is not finished, and the
     *                ring (traced first, as it usually is) must not go off empty
     */
    public record Result(GlyphReading reading, boolean waiting) {
        static final Result NONE = new Result(null, false);
    }

    /**
     * Compares a closed glyph with the guide {@code player} pinned on {@code plane}. The ring (where the glyph is, how
     * big, that it is closed) stays the one drawn; what is in it comes from the page.
     */
    public static Result traced(ServerPlayer player, SurfacePlane plane, List<InkStroke> drawn, GlyphReading asDrawn, double inkWidth) {
        if (player == null || asDrawn.ring() == null || !asDrawn.closed()) return Result.NONE;
        List<List<Pt>> guide = guide(player, plane);
        if (guide == null) return Result.NONE;
        Pin pin = PINS.get(player.getUUID());
        // what the page means is its recipe, drawn as signs are drawn now; the guide shown is only for the hand to follow
        List<List<Pt>> meaning = guide;
        ItemStack book = grimoire(player);
        GlyphPages gp = book.get(ModComponents.GLYPH_PAGES.get());
        var recipe = gp == null ? null : com.witchcraft.glyph.page.GlyphLibrary.recipeOf(book, gp.current());
        if (recipe != null) meaning = layOut(com.witchcraft.glyph.page.GlyphLibrary.fresh(recipe), pin);
        GlyphReading page = GlyphAnalyzer.analyze(meaning, inkWidth, pin.turn());
        if (page.ring() == null) return Result.NONE;
        double r = page.ring().radius();
        if (asDrawn.ring().center().dist(page.ring().center()) > 0.25 * r || Math.abs(asDrawn.ring().radius() - r) > 0.25 * r) return Result.NONE;
        List<List<Pt>> ink = new ArrayList<>();
        for (InkStroke s : drawn) ink.add(s.points());
        double[] fit = fit(ink, guide, Math.max(0.12, 0.07 * r), page.ring().center(), r);
        if (fit[1] > MAX_EXCESS) return Result.NONE; // not a copy of this page: read as drawn
        if (fit[0] < MIN_COVERAGE) return new Result(null, true);
        return new Result(new GlyphReading(asDrawn.ring(), page.innerRings(), page.sigil(), page.centerSign(), page.centerScore(), page.signs(),
                page.unknown(), page.imbalance(), page.neatness(), page.usedStrokes(), page.core()), false);
    }

    /**
     * {share of the guide the ink covers (within {@code tol}), share of the ink lying far from the guide}; ink well
     * outside the ring (other drawings near by) is left out.
     */
    static double[] fit(List<List<Pt>> ink, List<List<Pt>> guide, double tol, Pt center, double radius) {
        int covered = 0, total = 0;
        for (Pt g : samples(guide, 0.05)) {
            total++;
            if (near(g, ink, tol)) covered++;
        }
        int off = 0, mine = 0;
        for (Pt d : samples(ink, 0.05)) {
            if (d.dist(center) > radius * 1.3) continue;
            mine++;
            if (!near(d, guide, tol * 2)) off++;
        }
        return new double[]{total == 0 ? 0 : (double) covered / total, mine == 0 ? 1 : (double) off / mine};
    }

    private static List<Pt> samples(List<List<Pt>> strokes, double step) {
        List<Pt> out = new ArrayList<>();
        for (List<Pt> s : strokes) {
            if (s.isEmpty()) continue;
            out.add(s.get(0));
            double carry = 0;
            for (int i = 1; i < s.size(); i++) {
                Pt a = s.get(i - 1), b = s.get(i);
                double len = a.dist(b);
                double t = step - carry;
                while (t <= len) {
                    double k = t / len;
                    out.add(new Pt(a.x() + (b.x() - a.x()) * k, a.y() + (b.y() - a.y()) * k));
                    t += step;
                }
                carry = len - (t - step);
            }
        }
        return out;
    }

    private static boolean near(Pt p, List<List<Pt>> strokes, double tol) {
        double tol2 = tol * tol;
        for (List<Pt> s : strokes) {
            if (s.size() == 1 && sq(p, s.get(0)) <= tol2) return true;
            for (int i = 1; i < s.size(); i++) if (segSq(p, s.get(i - 1), s.get(i)) <= tol2) return true;
        }
        return false;
    }

    private static double sq(Pt a, Pt b) {
        double dx = a.x() - b.x(), dy = a.y() - b.y();
        return dx * dx + dy * dy;
    }

    /** Squared distance from p to the segment ab. */
    private static double segSq(Pt p, Pt a, Pt b) {
        double dx = b.x() - a.x(), dy = b.y() - a.y();
        double l2 = dx * dx + dy * dy;
        double t = l2 == 0 ? 0 : Math.max(0, Math.min(1, ((p.x() - a.x()) * dx + (p.y() - a.y()) * dy) / l2));
        double x = a.x() + dx * t - p.x(), y = a.y() + dy * t - p.y();
        return x * x + y * y;
    }
}
