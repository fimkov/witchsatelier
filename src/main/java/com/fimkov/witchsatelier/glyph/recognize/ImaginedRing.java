package com.fimkov.witchsatelier.glyph.recognize;

import java.util.ArrayList;
import java.util.List;

public final class ImaginedRing {
    private ImaginedRing() {}

    static final double NOTICE_SWEEP = 420;

    static final double MAX_BACKTRACK = 45;

    static final double LAPS_SWEEP = 700;

    public static boolean noticed(double[] xy, int n, double minRadius, double maxRadius) {
        if (n < 10) return false;
        List<Pt> pts = pts(xy, n);
        RingFinder.Circle c = RingFinder.fit(pts);
        if (c == null || c.radius() < minRadius || c.radius() > maxRadius) return false;
        if (c.rms() / c.radius() > 0.12) return false;
        double turn = turned(pts, c.center());
        if (Math.abs(turn) < NOTICE_SWEEP) return false;

        return backtracked(pts, c.center(), Math.signum(turn)) <= MAX_BACKTRACK;
    }

    public static boolean circledTwice(double[] xy, int n) {
        if (n < 10) return false;
        List<Pt> pts = pts(xy, n);
        RingFinder.Circle c = RingFinder.fit(pts);
        return c != null && Math.abs(turned(pts, c.center())) >= LAPS_SWEEP;
    }

    public static double[] trueRing(double[] xy, int n, double minRadius, double maxRadius, double minGap, double step) {
        if (n < 10) return null;
        List<Pt> pts = pts(xy, n);
        RingFinder.Circle c = RingFinder.fit(pts);
        if (c == null || c.radius() < minRadius || c.radius() > maxRadius) return null;
        if (c.rms() / c.radius() > 0.2) return null;
        double turn = turned(pts, c.center());
        if (Math.abs(turn) < LAPS_SWEEP) return null;
        double r = c.radius();
        double dir = Math.signum(turn);
        double gapAngle = Math.min(Math.toRadians(40), Math.max(minGap, 0.16 * r) / r);
        double span = Math.PI * 2 - gapAngle;
        Pt start = pts.get(pts.size() - 1);
        double a0 = Math.atan2(start.y() - c.center().y(), start.x() - c.center().x());
        int count = Math.max(24, (int) Math.ceil(span * r / step));
        double[] out = new double[(count + 1) * 2];
        for (int i = 0; i <= count; i++) {
            double a = a0 + dir * span * i / count;
            out[i * 2] = c.center().x() + Math.cos(a) * r;
            out[i * 2 + 1] = c.center().y() + Math.sin(a) * r;
        }
        return out;
    }

    static double turned(List<Pt> pts, Pt c) {
        double total = 0;
        double prev = Math.atan2(pts.get(0).y() - c.y(), pts.get(0).x() - c.x());
        for (int i = 1; i < pts.size(); i++) {
            double a = Math.atan2(pts.get(i).y() - c.y(), pts.get(i).x() - c.x());
            double d = a - prev;
            while (d > Math.PI) d -= Math.PI * 2;
            while (d < -Math.PI) d += Math.PI * 2;
            total += d;
            prev = a;
        }
        return Math.toDegrees(total);
    }

    static double backtracked(List<Pt> pts, Pt c, double dir) {
        double back = 0;
        double prev = Math.atan2(pts.get(0).y() - c.y(), pts.get(0).x() - c.x());
        for (int i = 1; i < pts.size(); i++) {
            double a = Math.atan2(pts.get(i).y() - c.y(), pts.get(i).x() - c.x());
            double d = a - prev;
            while (d > Math.PI) d -= Math.PI * 2;
            while (d < -Math.PI) d += Math.PI * 2;
            if (d * dir < 0) back += Math.abs(d);
            prev = a;
        }
        return Math.toDegrees(back);
    }

    private static List<Pt> pts(double[] xy, int n) {
        List<Pt> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) out.add(new Pt(xy[i * 2], xy[i * 2 + 1]));
        return out;
    }
}
