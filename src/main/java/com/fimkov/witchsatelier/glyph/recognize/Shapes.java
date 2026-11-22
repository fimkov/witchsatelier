package com.fimkov.witchsatelier.glyph.recognize;

import java.util.ArrayList;
import java.util.List;

public final class Shapes {
    private final List<List<Pt>> strokes = new ArrayList<>();

    public static Shapes of() { return new Shapes(); }

    public List<List<Pt>> build() { return strokes; }

    public Shapes poly(double... xy) {
        List<Pt> s = new ArrayList<>();
        for (int i = 0; i + 1 < xy.length; i += 2) s.add(new Pt(xy[i], xy[i + 1]));
        strokes.add(densify(s));
        return this;
    }

    public Shapes line(double x0, double y0, double x1, double y1) {
        return poly(x0, y0, x1, y1);
    }

    public Shapes polygon(double... xy) {
        double[] closed = new double[xy.length + 2];
        System.arraycopy(xy, 0, closed, 0, xy.length);
        closed[xy.length] = xy[0];
        closed[xy.length + 1] = xy[1];
        return poly(closed);
    }

    public Shapes arc(double cx, double cy, double r, double a0, double a1) {
        List<Pt> s = new ArrayList<>();
        int steps = Math.max(8, (int) Math.ceil(Math.abs(a1 - a0) / 10));
        for (int i = 0; i <= steps; i++) {
            double a = Math.toRadians(a0 + (a1 - a0) * i / steps);
            s.add(new Pt(cx + Math.cos(a) * r, cy + Math.sin(a) * r));
        }
        strokes.add(s);
        return this;
    }

    public Shapes circle(double cx, double cy, double r) {
        return arc(cx, cy, r, 0, 360);
    }

    public Shapes dot(double x, double y) {
        List<Pt> s = new ArrayList<>();
        s.add(new Pt(x, y));
        strokes.add(s);
        return this;
    }

    public Shapes wave(double x0, double amp, double y0, double y1) {
        List<Pt> s = new ArrayList<>();
        for (int i = 0; i <= 24; i++) {
            double t = i / 24.0;
            s.add(new Pt(x0 + amp * Math.sin(t * Math.PI * 2), y0 + (y1 - y0) * t));
        }
        strokes.add(s);
        return this;
    }

    public Shapes spiral(double cx, double cy, double r0, double r1, double turns, double startDeg) {
        List<Pt> s = new ArrayList<>();
        int steps = (int) Math.ceil(turns * 36);
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            double a = Math.toRadians(startDeg) + t * turns * Math.PI * 2;
            double r = r0 + (r1 - r0) * t;
            s.add(new Pt(cx + Math.cos(a) * r, cy + Math.sin(a) * r));
        }
        strokes.add(s);
        return this;
    }

    public Shapes drop(double x, double y, double r, double dirDeg) {
        double dir = Math.toRadians(dirDeg);
        Pt tip = new Pt(x + Math.cos(dir) * r * 2.6, y + Math.sin(dir) * r * 2.6);
        List<Pt> s = new ArrayList<>();
        s.add(tip);
        for (int i = 0; i <= 18; i++) {
            double a = dir + Math.toRadians(70 + i * (220.0 / 18));
            s.add(new Pt(x + Math.cos(a) * r, y + Math.sin(a) * r));
        }
        s.add(tip);
        strokes.add(densify(s));
        return this;
    }

    private static List<Pt> densify(List<Pt> s) {
        List<Pt> out = new ArrayList<>();
        for (int i = 0; i < s.size(); i++) {
            if (i > 0) {
                Pt a = s.get(i - 1), b = s.get(i);
                int n = Math.max(1, (int) Math.ceil(a.dist(b) / 0.05));
                for (int k = 1; k < n; k++) {
                    double t = k / (double) n;
                    out.add(new Pt(a.x() + (b.x() - a.x()) * t, a.y() + (b.y() - a.y()) * t));
                }
            }
            out.add(s.get(i));
        }
        return out;
    }
}
