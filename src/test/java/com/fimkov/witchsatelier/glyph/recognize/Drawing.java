package com.fimkov.witchsatelier.glyph.recognize;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Test helper that "hand-draws" template shapes: places, turns, resamples and adds wobble. */
final class Drawing {
    static final double INK = 1.0 / 16;
    final List<List<Pt>> strokes = new ArrayList<>();
    private final Random rnd;
    private final double noise;

    Drawing(long seed, double noise) {
        this.rnd = new Random(seed);
        this.noise = noise;
    }

    /** Adds template strokes scaled by {@code size/2}, rotated by {@code rot}, centred at {@code at}. */
    Drawing put(List<List<Pt>> template, Pt at, double size, double rot) {
        double wobblePhase = rnd.nextDouble() * 10;
        for (List<Pt> s : template) {
            List<Pt> out = new ArrayList<>();
            for (Pt p : resample(s, 0.04)) {
                Pt q = p.rotate(rot).scale(size / 2).plus(at);
                double w = noise * size;
                double wob = Math.sin(wobblePhase + p.x() * 3 + p.y() * 2) * w * 0.5;
                out.add(new Pt(q.x() + rnd.nextGaussian() * w * 0.3 + wob, q.y() + rnd.nextGaussian() * w * 0.3 - wob));
            }
            strokes.add(out);
        }
        return this;
    }

    /** Ring drawn as one stroke from angle a0 to a1 (degrees). */
    Drawing ring(Pt c, double r, double a0, double a1) {
        List<Pt> s = new ArrayList<>();
        int n = (int) Math.ceil(Math.abs(a1 - a0) / 360 * Math.PI * 2 * r / (INK / 2));
        double phase = rnd.nextDouble() * 6;
        for (int i = 0; i <= n; i++) {
            double a = Math.toRadians(a0 + (a1 - a0) * i / n);
            double rr = r * (1 + noise * 0.4 * Math.sin(a * 3 + phase));
            s.add(new Pt(c.x() + Math.cos(a) * rr, c.y() + Math.sin(a) * rr));
        }
        strokes.add(s);
        return this;
    }

    private static List<Pt> resample(List<Pt> s, double step) {
        if (s.size() < 2) return s;
        List<Pt> out = new ArrayList<>();
        out.add(s.get(0));
        for (int i = 1; i < s.size(); i++) {
            Pt a = s.get(i - 1), b = s.get(i);
            int n = Math.max(1, (int) Math.ceil(a.dist(b) / step));
            for (int k = 1; k <= n; k++) {
                double t = k / (double) n;
                out.add(new Pt(a.x() + (b.x() - a.x()) * t, a.y() + (b.y() - a.y()) * t));
            }
        }
        return out;
    }
}
