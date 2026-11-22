package com.fimkov.witchsatelier.glyph.recognize;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class RingFinder {
    static final double MIN_RADIUS = 0.15;

    static final double MAX_ARC_RESIDUAL = 0.14;

    static final int MAX_CORNERS = 4;
    static final double CORNER_TURN = Math.toRadians(60);
    static final double MIN_ARC_SWEEP = Math.toRadians(50);
    static final double MIN_RING_COVERAGE = 0.55;

    public record Circle(Pt center, double radius, double rms) {}

    public record Ring(Pt center, double radius, double roundness, double coverage, double maxGap, boolean closed,
                       List<Integer> strokes) {}

    private RingFinder() {}

    public static List<Ring> find(List<List<Pt>> strokes, double inkWidth) {
        record Arc(int index, Circle circle, double length) {}
        List<Arc> arcs = new ArrayList<>();
        for (int i = 0; i < strokes.size(); i++) {
            List<Pt> s = strokes.get(i);
            if (s.size() < 6) continue;
            Circle c = fit(s);
            if (c == null || c.radius() < MIN_RADIUS) continue;
            if (c.rms() / c.radius() > MAX_ARC_RESIDUAL) continue;
            if (sweep(s, c.center()) < MIN_ARC_SWEEP) continue;
            if (corners(s, c.radius()) > MAX_CORNERS) continue;
            arcs.add(new Arc(i, c, PointCloud.pathLength(s)));
        }
        arcs.sort(Comparator.comparingDouble(Arc::length).reversed());

        boolean[] used = new boolean[strokes.size()];
        List<Ring> rings = new ArrayList<>();
        for (Arc seed : arcs) {
            if (used[seed.index()]) continue;
            Circle c = seed.circle();
            List<Integer> members = new ArrayList<>();
            members.add(seed.index());
            for (Arc other : arcs) {
                if (other == seed || used[other.index()]) continue;
                if (other.circle().center().dist(c.center()) < 0.2 * c.radius()
                        && Math.abs(other.circle().radius() - c.radius()) < 0.15 * c.radius()) {
                    members.add(other.index());
                }
            }

            boolean more = true;
            while (more) {
                more = false;
                List<Pt> sofar = new ArrayList<>();
                for (int m : members) sofar.addAll(strokes.get(m));
                Circle now = fit(sofar);
                if (now == null) break;
                for (Arc other : arcs) {
                    if (used[other.index()] || members.contains(other.index())) continue;
                    List<Pt> s = strokes.get(other.index());
                    if (other.length() < 0.6 * now.radius()) continue;

                    List<Pt> both = new ArrayList<>(sofar);
                    both.addAll(s);
                    Circle joint = fit(both);
                    if (joint == null || joint.rms() / joint.radius() > 0.07 || Math.abs(joint.radius() - now.radius()) > 0.2 * now.radius()) continue;
                    double reach = Math.max(inkWidth * 2.2, 0.08 * now.radius());
                    if (!touches(s.get(0), strokes, members, reach) && !touches(s.get(s.size() - 1), strokes, members, reach)) continue;
                    members.add(other.index());
                    more = true;
                }
            }
            int arcMembers = members.size();

            double band = band(c.radius(), inkWidth);
            double touch = inkWidth * 2.2;
            boolean grew = true;
            while (grew) {
                grew = false;
                for (int i = 0; i < strokes.size(); i++) {
                    if (used[i] || members.contains(i)) continue;
                    List<Pt> s = strokes.get(i);
                    if (s.isEmpty() || PointCloud.pathLength(s) > c.radius() * 1.5) continue;
                    int on = 0;
                    for (Pt p : s) if (Math.abs(p.dist(c.center()) - c.radius()) <= band) on++;

                    if (on < s.size() * 0.8) continue;
                    if (!tangential(s, c.center())) {
                        if (!bridges(s, strokes, members, touch, c.radius())) continue;
                    } else if (!touches(s.get(0), strokes, members, touch) && !touches(s.get(s.size() - 1), strokes, members, touch)) {
                        continue;
                    } else if (!addsCoverage(strokes, members, i, c, inkWidth)) {
                        continue;
                    }
                    members.add(i);
                    grew = true;
                }
            }
            List<Pt> all = new ArrayList<>();
            for (int m : members) all.addAll(strokes.get(m));
            Circle refit = fit(all);
            if (refit == null) continue;
            double[] cov = coverage(strokes, members, refit, inkWidth);
            if (cov[0] < MIN_RING_COVERAGE) continue;
            for (int m : members) used[m] = true;
            double roundness = Math.max(0, 1 - refit.rms() / refit.radius());

            boolean joined = looseEnds(strokes, members, inkWidth, refit.radius()) == 0;
            if (!joined && members.size() > arcMembers) {
                List<Integer> arcsOnly = new ArrayList<>(members.subList(0, arcMembers));
                double[] c2 = coverage(strokes, arcsOnly, refit, inkWidth);
                if (c2[1] <= inkWidth * 1.6 && looseEnds(strokes, arcsOnly, inkWidth, refit.radius()) == 0) {
                    members = arcsOnly;
                    cov = c2;
                    joined = true;
                }
            }
            boolean closed = cov[1] <= inkWidth * 1.6 && joined;
            if (!joined) cov[1] = Math.max(cov[1], inkWidth * 2);
            rings.add(new Ring(refit.center(), refit.radius(), roundness, cov[0], cov[1], closed, members));
        }
        rings.sort(Comparator.comparingDouble(Ring::radius).reversed());
        return rings;
    }

    static boolean addsCoverage(List<List<Pt>> strokes, List<Integer> members, int i, Circle c, double inkWidth) {
        double before = coverage(strokes, members, c, inkWidth)[0];
        List<Integer> with = new ArrayList<>(members);
        with.add(i);
        double after = coverage(strokes, with, c, inkWidth)[0];
        return (after - before) * Math.PI * 2 * c.radius() >= inkWidth;
    }

    static double[] coverage(List<List<Pt>> strokes, List<Integer> members, Circle c, double inkWidth) {
        double circumference = Math.PI * 2 * c.radius();
        int bins = (int) Math.max(36, Math.min(1440, Math.round(circumference / inkWidth)));
        boolean[] covered = new boolean[bins];
        double step = inkWidth / 3;
        for (int m : members) {
            List<Pt> s = strokes.get(m);
            for (int i = 0; i < s.size(); i++) {
                Pt a = s.get(i);
                Pt b = i + 1 < s.size() ? s.get(i + 1) : a;
                int n = Math.max(1, (int) Math.ceil(a.dist(b) / step));
                for (int k = 0; k <= n; k++) {
                    double t = k / (double) n;
                    Pt p = new Pt(a.x() + (b.x() - a.x()) * t, a.y() + (b.y() - a.y()) * t);
                    double ang = Math.atan2(p.y() - c.center().y(), p.x() - c.center().x());
                    int bin = (int) Math.floor((ang + Math.PI) / (Math.PI * 2) * bins) % bins;
                    covered[bin] = true;
                }
            }
        }
        int count = 0;
        for (boolean b : covered) if (b) count++;
        if (count == bins) return new double[]{1, 0};

        int start = 0;
        while (!covered[start]) start++;
        int run = 0, maxRun = 0;
        for (int k = 1; k <= bins; k++) {
            int idx = (start + k) % bins;
            if (!covered[idx]) { run++; maxRun = Math.max(maxRun, run); } else run = 0;
        }

        double gap = Math.max(0, maxRun - 1) * circumference / bins;
        return new double[]{count / (double) bins, gap};
    }

    public static int corners(List<Pt> s, double radius) {
        double step = radius * 0.06;
        List<Pt> pts = new ArrayList<>();
        pts.add(s.get(0));
        double acc = 0;
        for (int i = 1; i < s.size(); i++) {
            acc += s.get(i).dist(s.get(i - 1));
            if (acc >= step) { pts.add(s.get(i)); acc = 0; }
        }
        int window = 5;
        int skip = 3;
        if (pts.size() < window + 2 * skip + 2) return 0;
        double[] head = new double[pts.size() - 1];
        for (int i = 0; i + 1 < pts.size(); i++) {
            head[i] = Math.atan2(pts.get(i + 1).y() - pts.get(i).y(), pts.get(i + 1).x() - pts.get(i).x());
        }
        int count = 0;
        boolean inCorner = false;
        for (int i = skip; i + window < head.length - skip; i++) {
            double d = head[i + window] - head[i];
            while (d > Math.PI) d -= Math.PI * 2;
            while (d < -Math.PI) d += Math.PI * 2;
            boolean sharp = Math.abs(d) > CORNER_TURN;
            if (sharp && !inCorner) count++;
            inCorner = sharp;
        }
        return count;
    }

    public static String diagnose(List<List<Pt>> strokes) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < strokes.size(); i++) {
            List<Pt> s = strokes.get(i);
            if (s.size() < 6 || PointCloud.pathLength(s) < 1) continue;
            Circle c = fit(s);
            if (c == null) continue;
            sb.append(String.format("#%d r=%.2f rms/r=%.3f sweep=%.0f corners=%d; ", i, c.radius(), c.rms() / c.radius(),
                    Math.toDegrees(sweep(s, c.center())), corners(s, c.radius())));
        }
        return sb.toString();
    }

    public static double[] widestGap(List<List<Pt>> strokes, Ring ring, double inkWidth) {
        Circle c = new Circle(ring.center(), ring.radius(), 0);
        double circumference = Math.PI * 2 * c.radius();
        int bins = (int) Math.max(72, Math.min(1440, Math.round(circumference / (inkWidth / 2))));
        boolean[] covered = new boolean[bins];
        double step = inkWidth / 3;
        for (int m : ring.strokes()) {
            List<Pt> s = strokes.get(m);
            for (int i = 0; i < s.size(); i++) {
                Pt a = s.get(i);
                Pt b = i + 1 < s.size() ? s.get(i + 1) : a;
                int n = Math.max(1, (int) Math.ceil(a.dist(b) / step));
                for (int k = 0; k <= n; k++) {
                    double t = k / (double) n;
                    double px = a.x() + (b.x() - a.x()) * t, py = a.y() + (b.y() - a.y()) * t;
                    if (Math.abs(Math.hypot(px - c.center().x(), py - c.center().y()) - c.radius()) > band(c.radius(), inkWidth)) continue;
                    double ang = Math.atan2(py - c.center().y(), px - c.center().x());
                    covered[(int) Math.floor((ang + Math.PI) / (Math.PI * 2) * bins) % bins] = true;
                }
            }
        }
        int start = -1;
        for (int i = 0; i < bins; i++) if (covered[i]) { start = i; break; }
        if (start < 0) return null;
        int best = 0, bestFrom = -1, run = 0, from = -1;
        for (int k = 1; k <= bins; k++) {
            int idx = (start + k) % bins;
            if (!covered[idx]) {
                if (run == 0) from = idx;
                run++;
                if (run > best) { best = run; bestFrom = from; }
            } else run = 0;
        }
        if (best == 0) return null;
        double a0 = bestFrom * (Math.PI * 2) / bins - Math.PI;
        return new double[]{a0, a0 + best * (Math.PI * 2) / bins};
    }

    static int looseEnds(List<List<Pt>> strokes, List<Integer> members, double inkWidth) {
        return looseEnds(strokes, members, inkWidth, 0);
    }

    static int looseEnds(List<List<Pt>> strokes, List<Integer> members, double inkWidth, double radius) {
        double reach = Math.max(inkWidth * 2.2, 0.1 * radius);
        int loose = 0;
        for (int m : members) {
            List<Pt> s = strokes.get(m);
            if (s.isEmpty()) continue;
            for (int end = 0; end < 2; end++) {
                int ei = end == 0 ? 0 : s.size() - 1;
                Pt e = s.get(ei);
                boolean joined = false;
                for (int o : members) {
                    List<Pt> t = strokes.get(o);
                    double along = 0;
                    for (int k = 0; k < t.size() && !joined; k++) {
                        if (o == m) {
                            int idx = end == 0 ? k : t.size() - 1 - k;
                            if (k > 0) along += t.get(idx).dist(t.get(end == 0 ? idx - 1 : idx + 1));
                            if (along < reach * 3) continue;
                            if (t.get(idx).dist(e) <= reach) joined = true;
                        } else if (t.get(k).dist(e) <= reach) {
                            joined = true;
                        }
                    }
                    if (joined) break;
                }
                if (!joined) loose++;
            }
        }
        return loose;
    }

    private static boolean bridges(List<Pt> s, List<List<Pt>> strokes, List<Integer> members, double touch, double radius) {
        if (PointCloud.pathLength(s) > 0.4 * radius) return false;
        Pt a = s.get(0), b = s.get(s.size() - 1);
        for (int pass = 0; pass < 2; pass++) {
            Pt on = pass == 0 ? a : b, other = pass == 0 ? b : a;
            Pt hitA = nearest(on, strokes, members);
            if (hitA == null || hitA.dist(on) > touch) continue;
            Pt hitB = nearestAwayFrom(other, strokes, members, hitA, 0.12 * radius);
            if (hitB != null && hitB.dist(other) <= 0.12 * radius) return true;
        }
        return false;
    }

    private static Pt nearest(Pt e, List<List<Pt>> strokes, List<Integer> members) {
        Pt best = null;
        for (int m : members) {
            List<Pt> s = strokes.get(m);
            if (s.isEmpty()) continue;
            for (Pt p : new Pt[]{s.get(0), s.get(s.size() - 1)}) if (best == null || p.dist(e) < best.dist(e)) best = p;
        }
        return best;
    }

    private static Pt nearestAwayFrom(Pt e, List<List<Pt>> strokes, List<Integer> members, Pt away, double keep) {
        Pt best = null;
        for (int m : members) {
            List<Pt> s = strokes.get(m);
            if (s.isEmpty()) continue;
            for (Pt p : new Pt[]{s.get(0), s.get(s.size() - 1)}) {
                if (p.dist(away) < keep) continue;
                if (best == null || p.dist(e) < best.dist(e)) best = p;
            }
        }
        return best;
    }

    private static boolean touches(Pt e, List<List<Pt>> strokes, List<Integer> members, double reach) {
        for (int m : members) for (Pt p : strokes.get(m)) if (p.dist(e) <= reach) return true;
        return false;
    }

    static boolean tangential(List<Pt> s, Pt center) {
        if (s.size() < 2) return true;
        Pt a = s.get(0), b = s.get(s.size() - 1);
        double radial = Math.abs(a.dist(center) - b.dist(center));
        return radial <= Math.max(0.6 * a.dist(b), 1e-3) || a.dist(b) < 1e-3;
    }

    static double band(double radius, double inkWidth) {
        return Math.max(inkWidth * 2.5, 0.14 * radius);
    }

    public static Circle fit(List<Pt> pts) {
        int n = pts.size();
        if (n < 3) return null;
        double mx = 0, my = 0;
        for (Pt p : pts) { mx += p.x(); my += p.y(); }
        mx /= n; my /= n;
        double suu = 0, svv = 0, suv = 0, suuu = 0, svvv = 0, suvv = 0, svuu = 0;
        for (Pt p : pts) {
            double u = p.x() - mx, v = p.y() - my;
            suu += u * u; svv += v * v; suv += u * v;
            suuu += u * u * u; svvv += v * v * v;
            suvv += u * v * v; svuu += v * u * u;
        }
        double det = suu * svv - suv * suv;
        if (Math.abs(det) < 1e-12) return null;
        double b1 = 0.5 * (suuu + suvv), b2 = 0.5 * (svvv + svuu);
        double uc = (b1 * svv - b2 * suv) / det;
        double vc = (suu * b2 - suv * b1) / det;
        Pt center = new Pt(uc + mx, vc + my);
        double r = Math.sqrt(uc * uc + vc * vc + (suu + svv) / n);
        double rss = 0;
        for (Pt p : pts) {
            double d = p.dist(center) - r;
            rss += d * d;
        }
        return new Circle(center, r, Math.sqrt(rss / n));
    }

    static double sweep(List<Pt> s, Pt center) {
        double total = 0;
        double prev = Math.atan2(s.get(0).y() - center.y(), s.get(0).x() - center.x());
        for (int i = 1; i < s.size(); i++) {
            double a = Math.atan2(s.get(i).y() - center.y(), s.get(i).x() - center.x());
            double d = a - prev;
            while (d > Math.PI) d -= Math.PI * 2;
            while (d < -Math.PI) d += Math.PI * 2;
            total += d;
            prev = a;
        }
        return Math.abs(total);
    }
}
