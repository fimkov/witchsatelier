package com.fimkov.witchsatelier.glyph.recognize;

import com.fimkov.witchsatelier.glyph.model.Orientation;
import com.fimkov.witchsatelier.glyph.model.Sigil;
import com.fimkov.witchsatelier.glyph.model.SignType;
import com.fimkov.witchsatelier.glyph.recognize.Templates.Template;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.stream.IntStream;

public final class SymbolRecognizer {
    static final double ZERO_SCORE_DISTANCE = 3.0;
    private static final double[] JITTER = {0, Math.toRadians(-12), Math.toRadians(12), Math.toRadians(-24), Math.toRadians(24)};

    public record Match(Template template, Orientation orientation, double distance, double score) {
        public static final Match NONE = new Match(null, Orientation.OUTWARD, Double.POSITIVE_INFINITY, 0);
    }

    private SymbolRecognizer() {}

    private static final ThreadLocal<Map<Object, Object>> CACHE = new ThreadLocal<>();

    private record Key(char kind, List<List<Pt>> strokes, double angle) {}

    static <T> T cached(Supplier<T> work) {
        if (CACHE.get() != null) return work.get();
        CACHE.set(new HashMap<>());
        try {
            return work.get();
        } finally {
            CACHE.remove();
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T memo(char kind, List<List<Pt>> strokes, double angle, Supplier<T> compute) {
        Map<Object, Object> m = CACHE.get();
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
        List<List<Pt>> out = new ArrayList<>(strokes.size());
        for (List<Pt> s : strokes) out.add(List.copyOf(s));
        return out;
    }

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

    private static Match squareOrDiamond(List<List<Pt>> strokes, Match[] per, Match best) {
        if (best.template() == null) return best;
        boolean light = best.template().sigil() == Sigil.LIGHT;
        boolean window = best.template().sign() == SignType.WINDOW;
        if (!light && !window) return best;
        Match other = Match.NONE;
        for (Match m : per) {
            if (m.template() == null) continue;
            boolean isOther = light ? m.template().sign() == SignType.WINDOW
                    : m.template().sigil() == Sigil.LIGHT;
            if (isOther && m.distance() < other.distance()) other = m;
        }

        if (other.template() == null || other.score() < 0.2) return best;
        int shape = boxShape(strokes);
        if (shape == 0 || (shape < 0) == window) return best;
        return other;
    }

    static int boxShape(List<List<Pt>> strokes) {
        double a = lineAlignment(strokes);
        return a > 0.65 ? -1 : a < 0.45 ? 1 : 0;
    }

    static double lineAlignment(List<List<Pt>> strokes) {
        double minX = Double.POSITIVE_INFINITY, minY = minX, maxX = -minX, maxY = -minX;
        for (List<Pt> s : strokes) for (Pt p : s) { minX = Math.min(minX, p.x()); maxX = Math.max(maxX, p.x()); minY = Math.min(minY, p.y()); maxY = Math.max(maxY, p.y()); }
        double size = Math.max(maxX - minX, maxY - minY);
        if (!(size > 0)) return 0.5;

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

    public static Match classifySign(List<List<Pt>> strokes, double outwardAngle) {
        return memo('s', strokes, outwardAngle, () -> classifySignNow(strokes, outwardAngle));
    }

    private static Match classifySignNow(List<List<Pt>> strokes, double outwardAngle) {
        Match best = Match.NONE;
        for (Match m : signMatches(strokes, outwardAngle)) best = better(best, m);
        return best;
    }

    private static Match[] signMatches(List<List<Pt>> strokes, double outwardAngle) {
        Pt[][][] turns = turns(PointCloud.normalize(turn(strokes, Math.PI / 2 - outwardAngle)));
        return perTemplate(Templates.SIGNS, (t, bound) -> {
            Match best = Match.NONE;
            for (int o = 0; o < ORIENT.length; o++) best = better(best, matchTurned(turns[o], t, ORIENT[o], Math.min(best.distance(), bound)));
            return best;
        });
    }

    private static final List<Template> CENTRE = centreTemplates();

    private static List<Template> centreTemplates() {
        List<Template> out = new ArrayList<>(Templates.SIGILS);
        for (Template t : Templates.SIGNS) if (t.sign().centerCapable) out.add(t);
        return List.copyOf(out);
    }

    private static Match[] perTemplate(List<Template> templates, BiFunction<Template, Double, Match> match) {
        Match[] out = new Match[templates.size()];
        AtomicLong best = new AtomicLong(Double.doubleToLongBits(Double.POSITIVE_INFINITY));
        IntStream.range(0, out.length).parallel().forEach(i -> {
            Match m = match.apply(templates.get(i), Double.longBitsToDouble(best.get()) + FAR);
            out[i] = m;
            best.accumulateAndGet(Double.doubleToLongBits(m.distance()),
                    (a, b) -> Double.longBitsToDouble(b) < Double.longBitsToDouble(a) ? b : a);
        });
        return out;
    }

    public static List<Match> rankSign(List<List<Pt>> strokes, double outwardAngle) {
        return memo('r', strokes, outwardAngle, () -> rankSignNow(strokes, outwardAngle));
    }

    private static List<Match> rankSignNow(List<List<Pt>> strokes, double outwardAngle) {
        Map<SignType, Match> per = new EnumMap<>(SignType.class);
        for (Match m : signMatches(strokes, outwardAngle)) per.merge(m.template().sign(), m, (a, b) -> a.distance() <= b.distance() ? a : b);
        List<Match> out = new ArrayList<>(per.values());
        out.sort(Comparator.comparingDouble(Match::distance));
        return out;
    }

    public static List<Match> rankCenter(List<List<Pt>> strokes) {
        Pt[] cloud = PointCloud.normalize(strokes);
        List<Match> out = new ArrayList<>();
        for (Template t : Templates.SIGILS) out.add(matchJittered(cloud, 0, t, Orientation.OUTWARD));
        for (Template t : Templates.SIGNS) if (t.sign().centerCapable) out.add(matchJittered(cloud, 0, t, Orientation.OUTWARD));
        out.sort(Comparator.comparingDouble(Match::distance));
        return out;
    }

    static List<List<Pt>> turn(List<List<Pt>> strokes, double angle) {
        if (Math.abs(angle) < 1e-9) return strokes;
        List<List<Pt>> out = new ArrayList<>(strokes.size());
        for (List<Pt> s : strokes) {
            List<Pt> q = new ArrayList<>(s.size());
            for (Pt p : s) q.add(p.rotate(angle));
            out.add(q);
        }
        return out;
    }

    private static final double FAR = 0.6;
    private static final Orientation[] ORIENT = {Orientation.OUTWARD, Orientation.INWARD, Orientation.CCW, Orientation.CW};
    private static final double[] ORIENT_TURN = {0, Math.PI, -Math.PI / 2, Math.PI / 2};

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

    private static Match better(Match a, Match b) {
        if (b.distance() < a.distance() - 1e-6) {
            if (a.template() == b.template() && a.orientation() == Orientation.OUTWARD && b.distance() > a.distance() - 0.05) return a;
            return b;
        }
        return a;
    }
}
