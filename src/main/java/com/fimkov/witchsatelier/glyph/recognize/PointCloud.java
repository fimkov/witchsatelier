package com.fimkov.witchsatelier.glyph.recognize;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class PointCloud {
    public static final int N = 48;
    private static final double DOT_FRACTION = 0.08;

    private PointCloud() {}

    public static final int MAX_GLYPH_POINTS = 3000;

    public static List<List<Pt>> thin(List<List<Pt>> strokes, double inkWidth) {
        double step = inkWidth * 0.2;
        List<List<Pt>> out = strokes;
        for (int round = 0; round < 12; round++) {
            List<List<Pt>> next = new ArrayList<>(strokes.size());
            int total = 0;
            for (List<Pt> s : strokes) {
                List<Pt> kept = thinStroke(s, step);
                next.add(kept);
                total += kept.size();
            }
            out = next;
            if (total <= MAX_GLYPH_POINTS) break;
            step *= 1.5;
        }
        return out;
    }

    private static List<Pt> thinStroke(List<Pt> s, double step) {
        if (s.size() <= 2) return s;
        List<Pt> kept = new ArrayList<>();
        Pt last = s.get(0);
        kept.add(last);
        for (int i = 1; i < s.size() - 1; i++) {
            Pt p = s.get(i);
            if (p.dist(last) >= step) { kept.add(p); last = p; }
        }
        Pt end = s.get(s.size() - 1);
        if (end.dist(last) > 0 || kept.size() == 1) kept.add(end);
        return kept.size() == s.size() ? s : kept;
    }

    public static Pt[] normalize(List<List<Pt>> strokes) {
        List<List<Pt>> expanded = expandDots(strokes);
        Pt[] pts = resample(expanded, N);
        return translateToCentroid(scale(pts));
    }

    public static Pt[] rotate(Pt[] cloud, double angle) {
        Pt[] out = new Pt[cloud.length];
        for (int i = 0; i < cloud.length; i++) out[i] = cloud[i].rotate(angle);
        return out;
    }

    public static double distance(Pt[] a, Pt[] b) {
        return distance(a, b, Double.POSITIVE_INFINITY);
    }

    public static double distance(Pt[] a, Pt[] b, double bound) {
        int n = a.length;
        double[] ax = new double[n], ay = new double[n], bx = new double[n], by = new double[n];
        for (int i = 0; i < n; i++) { ax[i] = a[i].x(); ay[i] = a[i].y(); bx[i] = b[i].x(); by[i] = b[i].y(); }
        boolean[] matched = new boolean[n];
        int step = (int) Math.floor(Math.pow(n, 0.5));
        double min = bound;
        for (int i = 0; i < n; i += step) {
            min = Math.min(min, cloudDistance(ax, ay, bx, by, i, min, matched));
            min = Math.min(min, cloudDistance(bx, by, ax, ay, i, min, matched));
        }
        return min;
    }

    private static double cloudDistance(double[] ax, double[] ay, double[] bx, double[] by, int start, double bound, boolean[] matched) {
        int n = ax.length;
        Arrays.fill(matched, false);
        double sum = 0;
        int i = start;
        do {
            int index = -1;
            double min = Double.POSITIVE_INFINITY;
            double px = ax[i], py = ay[i];
            for (int j = 0; j < n; j++) {
                if (!matched[j]) {
                    double dx = px - bx[j], dy = py - by[j];
                    double d = dx * dx + dy * dy;
                    if (d < min) { min = d; index = j; }
                }
            }
            matched[index] = true;
            double weight = 1 - ((i - start + n) % n) / (double) n;
            sum += weight * Math.sqrt(min);
            if (sum >= bound) return sum;
            i = (i + 1) % n;
        } while (i != start);
        return sum;
    }

    private static List<List<Pt>> expandDots(List<List<Pt>> strokes) {
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
        for (List<Pt> s : strokes) for (Pt p : s) {
            minX = Math.min(minX, p.x()); maxX = Math.max(maxX, p.x());
            minY = Math.min(minY, p.y()); maxY = Math.max(maxY, p.y());
        }
        double size = Math.max(maxX - minX, maxY - minY);
        if (!(size > 0)) size = 1;
        double dotLen = size * DOT_FRACTION;
        List<List<Pt>> out = new ArrayList<>();
        for (List<Pt> s : strokes) {
            if (s.isEmpty()) continue;
            if (pathLength(s) < dotLen) {
                Pt c = centroid(s);
                double r = dotLen / 2;
                List<Pt> circle = new ArrayList<>();
                for (int k = 0; k <= 8; k++) {
                    double a = k * Math.PI * 2 / 8;
                    circle.add(new Pt(c.x() + Math.cos(a) * r, c.y() + Math.sin(a) * r));
                }
                out.add(circle);
            } else {
                out.add(s);
            }
        }
        return out;
    }

    static Pt[] resample(List<List<Pt>> strokes, int n) {
        double total = 0;
        for (List<Pt> s : strokes) total += pathLength(s);
        Pt[] out = new Pt[n];
        int count = 0;
        if (total <= 0) {
            Pt c = strokes.isEmpty() || strokes.get(0).isEmpty() ? new Pt(0, 0) : strokes.get(0).get(0);
            for (int i = 0; i < n; i++) out[i] = c;
            return out;
        }
        double interval = total / (n - 1);
        double acc = 0;
        out[count++] = strokes.get(0).get(0);
        for (List<Pt> stroke : strokes) {
            List<Pt> pts = new ArrayList<>(stroke);
            for (int i = 1; i < pts.size() && count < n; i++) {
                Pt prev = pts.get(i - 1), cur = pts.get(i);
                double d = prev.dist(cur);
                if (acc + d >= interval && d > 0) {
                    double t = (interval - acc) / d;
                    Pt q = new Pt(prev.x() + t * (cur.x() - prev.x()), prev.y() + t * (cur.y() - prev.y()));
                    out[count++] = q;
                    pts.add(i, q);
                    acc = 0;
                } else {
                    acc += d;
                }
            }
        }
        Pt last = lastPoint(strokes);
        while (count < n) out[count++] = last;
        return out;
    }

    private static Pt lastPoint(List<List<Pt>> strokes) {
        for (int i = strokes.size() - 1; i >= 0; i--) {
            List<Pt> s = strokes.get(i);
            if (!s.isEmpty()) return s.get(s.size() - 1);
        }
        return new Pt(0, 0);
    }

    private static Pt[] scale(Pt[] pts) {
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
        for (Pt p : pts) {
            minX = Math.min(minX, p.x()); maxX = Math.max(maxX, p.x());
            minY = Math.min(minY, p.y()); maxY = Math.max(maxY, p.y());
        }
        double size = Math.max(maxX - minX, maxY - minY);
        if (size <= 0) size = 1;
        Pt[] out = new Pt[pts.length];
        for (int i = 0; i < pts.length; i++) out[i] = new Pt((pts[i].x() - minX) / size, (pts[i].y() - minY) / size);
        return out;
    }

    private static Pt[] translateToCentroid(Pt[] pts) {
        double cx = 0, cy = 0;
        for (Pt p : pts) { cx += p.x(); cy += p.y(); }
        cx /= pts.length; cy /= pts.length;
        Pt[] out = new Pt[pts.length];
        for (int i = 0; i < pts.length; i++) out[i] = new Pt(pts[i].x() - cx, pts[i].y() - cy);
        return out;
    }

    public static double pathLength(List<Pt> s) {
        double d = 0;
        for (int i = 1; i < s.size(); i++) d += s.get(i - 1).dist(s.get(i));
        return d;
    }

    public static Pt centroid(List<Pt> s) {
        double x = 0, y = 0;
        for (Pt p : s) { x += p.x(); y += p.y(); }
        return new Pt(x / s.size(), y / s.size());
    }
}
