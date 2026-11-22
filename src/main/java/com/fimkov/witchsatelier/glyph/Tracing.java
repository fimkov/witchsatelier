package com.fimkov.witchsatelier.glyph;

import com.fimkov.witchsatelier.glyph.page.GlyphLibrary;
import com.fimkov.witchsatelier.glyph.page.GlyphPages;
import com.fimkov.witchsatelier.glyph.page.PageKind;
import com.fimkov.witchsatelier.glyph.recognize.GlyphAnalyzer;
import com.fimkov.witchsatelier.glyph.recognize.GlyphReading;
import com.fimkov.witchsatelier.glyph.recognize.Pt;
import com.fimkov.witchsatelier.glyph.surface.InkStroke;
import com.fimkov.witchsatelier.glyph.surface.SurfacePlane;
import com.fimkov.witchsatelier.item.InkPenItem;
import com.fimkov.witchsatelier.registry.ModComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class Tracing {
    private Tracing() {}

    public record Pin(SurfacePlane plane, double u, double v, double turn, double scale) {}

    private static final Map<UUID, Pin> PINS = new HashMap<>();

    static final double MIN_COVERAGE = 0.85, MAX_EXCESS = 0.15;

    public static void pin(ServerPlayer p, Pin pin) {
        if (pin == null) PINS.remove(p.getUUID());
        else PINS.put(p.getUUID(), pin);
    }

    public static void forget(UUID player) {
        PINS.remove(player);
    }

    static ItemStack grimoire(ServerPlayer p) {
        ItemStack main = p.getMainHandItem(), off = p.getOffhandItem();
        if (main.getItem() instanceof InkPenItem && isBook(PageKind.of(off))) return off;
        if (off.getItem() instanceof InkPenItem && isBook(PageKind.of(main))) return main;
        return ItemStack.EMPTY;
    }

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

    public record Result(GlyphReading reading, boolean waiting) {
        static final Result NONE = new Result(null, false);
    }

    public static Result traced(ServerPlayer player, SurfacePlane plane, List<InkStroke> drawn, GlyphReading asDrawn, double inkWidth) {
        if (player == null || asDrawn.ring() == null || !asDrawn.closed()) return Result.NONE;
        List<List<Pt>> guide = guide(player, plane);
        if (guide == null) return Result.NONE;
        Pin pin = PINS.get(player.getUUID());

        List<List<Pt>> meaning = guide;
        ItemStack book = grimoire(player);
        GlyphPages gp = book.get(ModComponents.GLYPH_PAGES.get());
        var recipe = gp == null ? null : GlyphLibrary.recipeOf(book, gp.current());
        if (recipe != null) meaning = layOut(GlyphLibrary.fresh(recipe), pin);
        GlyphReading page = GlyphAnalyzer.analyze(meaning, inkWidth, pin.turn());
        if (page.ring() == null) return Result.NONE;
        double r = page.ring().radius();
        if (asDrawn.ring().center().dist(page.ring().center()) > 0.25 * r || Math.abs(asDrawn.ring().radius() - r) > 0.25 * r) return Result.NONE;
        List<List<Pt>> ink = new ArrayList<>();
        for (InkStroke s : drawn) ink.add(s.points());
        double[] fit = fit(ink, guide, Math.max(0.12, 0.07 * r), page.ring().center(), r);
        if (fit[1] > MAX_EXCESS) return Result.NONE;
        if (fit[0] < MIN_COVERAGE) return new Result(null, true);
        return new Result(new GlyphReading(asDrawn.ring(), page.innerRings(), page.sigil(), page.centerSign(), page.centerScore(), page.signs(),
                page.unknown(), page.imbalance(), page.neatness(), page.usedStrokes(), page.core(), page.sides()), false);
    }

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

    private static double segSq(Pt p, Pt a, Pt b) {
        double dx = b.x() - a.x(), dy = b.y() - a.y();
        double l2 = dx * dx + dy * dy;
        double t = l2 == 0 ? 0 : Math.max(0, Math.min(1, ((p.x() - a.x()) * dx + (p.y() - a.y()) * dy) / l2));
        double x = a.x() + dx * t - p.x(), y = a.y() + dy * t - p.y();
        return x * x + y * y;
    }

    private static boolean isBook(PageKind k) {
        return k != null && k.book();
    }
}
