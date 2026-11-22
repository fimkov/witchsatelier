package com.fimkov.witchsatelier.glyph.recognize;

import com.fimkov.witchsatelier.glyph.model.Orientation;
import com.fimkov.witchsatelier.glyph.recognize.Templates.Template;

import java.util.List;

/** Classifies one drawn symbol (one or more strokes) against the templates. */
public final class SymbolRecognizer {
    /** $P distance at which the score reaches 0. Calibrated in tests. */
    static final double ZERO_SCORE_DISTANCE = 3.0;
    private static final double[] JITTER = {0, Math.toRadians(-12), Math.toRadians(12), Math.toRadians(-24), Math.toRadians(24)};

    public record Match(Template template, Orientation orientation, double distance, double score) {
        public static final Match NONE = new Match(null, Orientation.OUTWARD, Double.POSITIVE_INFINITY, 0);
    }

    private SymbolRecognizer() {}

    /**
     * While a glyph is being read, the same marks are compared again and again (the sigil from each side of a floor
     * glyph, a mark with and without its neighbour): within one reading each comparison is made only once.
     */
    private static final ThreadLocal<java.util.Map<Object, Object>> CACHE = new ThreadLocal<>();

    private record Key(char kind, List<List<Pt>> strokes, double angle) {}

    /** Runs {@code work} with comparisons remembered for its duration (nested calls share the outer memory). */
    static <T> T cached(java.util.function.Supplier<T> work) {
        if (CACHE.get() != null) return work.get();
        CACHE.set(new java.util.HashMap<>());
        try {
            return work.get();
        } finally {
            CACHE.remove();
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T memo(char kind, List<List<Pt>> strokes, double angle, java.util.function.Supplier<T> compute) {
        java.util.Map<Object, Object> m = CACHE.get();
        if (m == null) return compute.get();
        Key k = new Key(kind, strokes, angle);
        Object v = m.get(k);
        if (v == null) {
            v = compute.get();
            m.put(new Key(kind, copy(strokes), angle), v);
        }
        return (T) v;
    }

    private static List<List<Pt>> copy(List<List<Pt>> strokes) {
        List<List<Pt>> out = new java.util.ArrayList<>(strokes.size());
        for (List<Pt> s : strokes) out.add(List.copyOf(s));
        return out;
    }

    /** Symbol in the middle of the glyph: compared upright against sigils and centre-capable signs. */
    public static Match classifyCenter(List<List<Pt>> strokes) {
        return memo('c', strokes, 0, () -> classifyCenterNow(strokes));
    }

    private static Match classifyCenterNow(List<List<Pt>> strokes) {
        Pt[][] jittered = turns(PointCloud.normalize(strokes))[0];
        Match[] per = perTemplate(CENTRE, (t, bound) -> matchTurned(jittered, t, Orientation.OUTWARD, bound));
        Match best = Match.NONE;
        for (Match m : per) best = better(best, m);
        return squareOrDiamond(strokes, per, best);
    }

    /**
     * The Window (a square across a cross) and Light (a diamond across one) are the same marks turned by an eighth,
     * and a point cloud can hardly tell them apart. What tells them apart is which way the box's edges run against
     * the arms (see {@link #boxShape}). Whenever the mark reads as either, that decides.
     */
    private static Match squareOrDiamond(List<List<Pt>> strokes, Match[] per, Match best) {
        if (best.template() == null) return best;
        boolean light = best.template().sigil() == com.fimkov.witchsatelier.glyph.model.Sigil.LIGHT;
        boolean window = best.template().sign() == com.fimkov.witchsatelier.glyph.model.SignType.WINDOW;
        if (!light && !window) return best;
        Match other = Match.NONE;
        for (Match m : per) {
            if (m.template() == null) continue;
            boolean isOther = light ? m.template().sign() == com.fimkov.witchsatelier.glyph.model.SignType.WINDOW
                    : m.template().sigil() == com.fimkov.witchsatelier.glyph.model.Sigil.LIGHT;
            if (isOther && m.distance() < other.distance()) other = m;
        }
        // the lines' directions are plain to see where the point cloud only half sees them: when they are, they decide
        if (other.template() == null || other.score() < 0.2) return best;
        int shape = boxShape(strokes);
        if (shape == 0 || (shape < 0) == window) return best;
        return other;
    }

    /**
     * -1 if the box across the cross is a square, +1 a diamond, 0 unsure. A square's edges run the same way as the
     * arms, so all the lines of the mark point one of two ways a quarter turn apart; a diamond's run an eighth turn
     * off the arms, so the lines' directions spread out.
     */
    static int boxShape(List<List<Pt>> strokes) {
        double a = lineAlignment(strokes);
        return a > 0.65 ? -1 : a < 0.45 ? 1 : 0;
    }

    /** How much the lines of a mark run the same way, a quarter turn not counting: 1 all so, 0 every way. */
    static double lineAlignment(List<List<Pt>> strokes) {
        double minX = Double.POSITIVE_INFINITY, minY = minX, maxX = -minX, maxY = -minX;
        for (List<Pt> s : strokes) for (Pt p : s) { minX = Math.min(minX, p.x()); maxX = Math.max(maxX, p.x()); minY = Math.min(minY, p.y()); maxY = Math.max(maxY, p.y()); }
        double size = Math.max(maxX - minX, maxY - minY);
        if (!(size > 0)) return 0.5;
        // chords a tenth of the mark long, so the pen's wobble does not count
        double step = 0.1 * size, sx = 0, sy = 0, len = 0;
        for (List<Pt> s : strokes) {
            if (s.isEmpty()) continue;
            Pt from = s.get(0);
            for (int i = 1; i < s.size(); i++) {
                Pt p = s.get(i);
                double d = p.dist(from);
                if (d < step && i < s.size() - 1) continue;
                if (d > 1e-9) {
                    double ang = 4 * Math.atan2(p.y() - from.y(), p.x() - from.x());
                    sx += d * Math.cos(ang); sy += d * Math.sin(ang); len += d;
                }
                from = p;
            }
        }
        return len > 0 ? Math.hypot(sx, sy) / len : 0.5;
    }

    /**
     * Symbol around the centre. {@code outwardAngle} is the direction from the glyph centre to the symbol;
     * the symbol is turned so that this direction becomes "up" before matching, so the result says whether
     * it points outward, inward or along the ring.
     */
    public static Match classifySign(List<List<Pt>> strokes, double outwardAngle) {
        return memo('s', strokes, outwardAngle, () -> classifySignNow(strokes, outwardAngle));
    }

    private static Match classifySignNow(List<List<Pt>> strokes, double outwardAngle) {
        Match best = Match.NONE;
        for (Match m : signMatches(strokes, outwardAngle)) best = better(best, m);
        return best;
    }

    /** Each sign template's best reading over the four orientations (the far ones only roughly, see {@link #FAR}). */
    private static Match[] signMatches(List<List<Pt>> strokes, double outwardAngle) {
        Pt[][][] turns = turns(PointCloud.normalize(turn(strokes, Math.PI / 2 - outwardAngle)));
        return perTemplate(Templates.SIGNS, (t, bound) -> {
            Match best = Match.NONE;
            for (int o = 0; o < ORIENT.length; o++) best = better(best, matchTurned(turns[o], t, ORIENT[o], Math.min(best.distance(), bound)));
            return best;
        });
    }

    /** Sigils and the signs that may stand in the middle. */
    private static final List<Template> CENTRE = centreTemplates();

    private static List<Template> centreTemplates() {
        List<Template> out = new java.util.ArrayList<>(Templates.SIGILS);
        for (Template t : Templates.SIGNS) if (t.sign().centerCapable) out.add(t);
        return List.copyOf(out);
    }

    /**
     * Matches every template, spread over the processor's cores (a glyph is read on the server thread, so this
     * keeps the world from stalling while a big glyph is read). Each match is cut short once it is {@link #FAR}
     * behind the best found so far by any of them.
     */
    private static Match[] perTemplate(List<Template> templates, java.util.function.BiFunction<Template, Double, Match> match) {
        Match[] out = new Match[templates.size()];
        java.util.concurrent.atomic.AtomicLong best = new java.util.concurrent.atomic.AtomicLong(Double.doubleToLongBits(Double.POSITIVE_INFINITY));
        java.util.stream.IntStream.range(0, out.length).parallel().forEach(i -> {
            Match m = match.apply(templates.get(i), Double.longBitsToDouble(best.get()) + FAR);
            out[i] = m;
            best.accumulateAndGet(Double.doubleToLongBits(m.distance()),
                    (a, b) -> Double.longBitsToDouble(b) < Double.longBitsToDouble(a) ? b : a);
        });
        return out;
    }

    /** Best reading per sign type, best first (for tuning and the debug log). */
    public static List<Match> rankSign(List<List<Pt>> strokes, double outwardAngle) {
        return memo('r', strokes, outwardAngle, () -> rankSignNow(strokes, outwardAngle));
    }

    private static List<Match> rankSignNow(List<List<Pt>> strokes, double outwardAngle) {
        java.util.Map<com.fimkov.witchsatelier.glyph.model.SignType, Match> per = new java.util.EnumMap<>(com.fimkov.witchsatelier.glyph.model.SignType.class);
        for (Match m : signMatches(strokes, outwardAngle)) per.merge(m.template().sign(), m, (a, b) -> a.distance() <= b.distance() ? a : b);
        List<Match> out = new java.util.ArrayList<>(per.values());
        out.sort(java.util.Comparator.comparingDouble(Match::distance));
        return out;
    }

    public static List<Match> rankCenter(List<List<Pt>> strokes) {
        Pt[] cloud = PointCloud.normalize(strokes);
        List<Match> out = new java.util.ArrayList<>();
        for (Template t : Templates.SIGILS) out.add(matchJittered(cloud, 0, t, Orientation.OUTWARD));
        for (Template t : Templates.SIGNS) if (t.sign().centerCapable) out.add(matchJittered(cloud, 0, t, Orientation.OUTWARD));
        out.sort(java.util.Comparator.comparingDouble(Match::distance));
        return out;
    }

    /**
     * Turns the drawing itself before it is normalised: a long sign drawn at a slant would otherwise be scaled by
     * its slanted bounding box and no longer match its upright template.
     */
    static List<List<Pt>> turn(List<List<Pt>> strokes, double angle) {
        if (Math.abs(angle) < 1e-9) return strokes;
        List<List<Pt>> out = new java.util.ArrayList<>(strokes.size());
        for (List<Pt> s : strokes) {
            List<Pt> q = new java.util.ArrayList<>(s.size());
            for (Pt p : s) q.add(p.rotate(angle));
            out.add(q);
        }
        return out;
    }

    /**
     * Readings further than this behind the best one so far are not worked out exactly (they are only known to be
     * at least that far behind): no choice ever turns on a mark that much worse than the best.
     */
    private static final double FAR = 0.6;
    private static final Orientation[] ORIENT = {Orientation.OUTWARD, Orientation.INWARD, Orientation.CCW, Orientation.CW};
    private static final double[] ORIENT_TURN = {0, Math.PI, -Math.PI / 2, Math.PI / 2};

    /** The cloud turned for each orientation and each jitter, once for all templates. */
    private static Pt[][][] turns(Pt[] cloud) {
        Pt[][][] out = new Pt[ORIENT.length][JITTER.length][];
        for (int o = 0; o < ORIENT.length; o++) for (int j = 0; j < JITTER.length; j++) out[o][j] = PointCloud.rotate(cloud, ORIENT_TURN[o] + JITTER[j]);
        return out;
    }

    private static Match matchTurned(Pt[][] jittered, Template t, Orientation o, double bound) {
        double best = bound;
        for (Pt[] c : jittered) best = PointCloud.distance(c, t.cloud(), best);
        double score = Math.max(0, 1 - best / ZERO_SCORE_DISTANCE);
        return new Match(t, o, best, score);
    }

    private static Match matchJittered(Pt[] cloud, double rotation, Template t, Orientation o) {
        double best = Double.POSITIVE_INFINITY;
        for (double j : JITTER) {
            best = PointCloud.distance(PointCloud.rotate(cloud, rotation + j), t.cloud(), best);
        }
        double score = Math.max(0, 1 - best / ZERO_SCORE_DISTANCE);
        return new Match(t, o, best, score);
    }

    /** Lower distance wins; on near-ties prefer the plain OUTWARD reading (symmetric signs). */
    private static Match better(Match a, Match b) {
        if (b.distance() < a.distance() - 1e-6) {
            if (a.template() == b.template() && a.orientation() == Orientation.OUTWARD && b.distance() > a.distance() - 0.05) return a;
            return b;
        }
        return a;
    }
}
