package com.witchcraft.glyph.recognize;

import java.util.ArrayList;
import java.util.List;

/**
 * The Imagined Ring, a technique taught in grimoires: the witch circles twice in the air over the page, seeing the
 * ring there, and on the third time round brings the pen down in one quick stroke, and the ring comes out true.
 * In play: once a line has gone right round a circle and sets off round it a second time, it is only imagined (pale,
 * over the surface, no ink, the first lap too); a ring drawn round just once, however it is closed, stays ink, and so
 * do the curves of signs and sigils. When it has gone round twice, a true ring the size of those two laps is laid down at once, from where the hand is, the way
 * it was going, with the small gap every witch leaves so the spell waits until they close it.
 *
 * Coordinates are a flat list x0 y0 x1 y1 ... in the drawing's own units (blocks on a surface, page units on paper).
 */
public final class ImaginedRing {
    private ImaginedRing() {}

    /**
     * How far round (degrees) a line must have gone before it is taken for a ring seen in the air: into its second lap.
     * Rings drawn by hand in one go close with at most about 380 degrees, and no sign or sigil goes round further than
     * one full circle (its spirals do not keep to one circle).
     */
    static final double NOTICE_SWEEP = 420;
    /** Turning back against the way round (degrees, in total) a line may do and still be going round a circle. */
    static final double MAX_BACKTRACK = 45;
    /** Two laps in the air (a little short is near enough), then the ring goes down. */
    static final double LAPS_SWEEP = 700;

    /** True if the line so far is plainly the start of a ring of radius in [minRadius, maxRadius]. */
    public static boolean noticed(double[] xy, int n, double minRadius, double maxRadius) {
        if (n < 10) return false;
        List<Pt> pts = pts(xy, n);
        RingFinder.Circle c = RingFinder.fit(pts);
        if (c == null || c.radius() < minRadius || c.radius() > maxRadius) return false;
        if (c.rms() / c.radius() > 0.12) return false;
        double turn = turned(pts, c.center());
        if (Math.abs(turn) < NOTICE_SWEEP) return false;
        // steadily round, not a scribble to and fro that happens to add up
        return backtracked(pts, c.center(), Math.signum(turn)) <= MAX_BACKTRACK;
    }

    /** True once the line has gone round twice: time to lay the ring down. */
    public static boolean circledTwice(double[] xy, int n) {
        if (n < 10) return false;
        List<Pt> pts = pts(xy, n);
        RingFinder.Circle c = RingFinder.fit(pts);
        return c != null && Math.abs(turned(pts, c.center())) >= LAPS_SWEEP;
    }

    /**
     * The true ring the two laps in the air showed, or null if they were no circle (or not two laps). It starts where
     * the line is now and runs on the way it was going, stopping a small gap short of the start (at least
     * {@code minGap} long); points are about {@code step} apart.
     */
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

    /** Signed total angle (degrees) the line turned through around {@code c}, counting laps past a full turn. */
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

    /** Total angle (degrees) the line turned around {@code c} against the direction {@code dir}. */
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
