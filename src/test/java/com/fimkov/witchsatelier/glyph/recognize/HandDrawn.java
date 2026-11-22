package com.fimkov.witchsatelier.glyph.recognize;

import com.fimkov.witchsatelier.glyph.model.Sigil;
import com.fimkov.witchsatelier.glyph.model.SignType;
import com.fimkov.witchsatelier.glyph.page.GlyphLibrary;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

final class HandDrawn {
    final Random rnd;
    final double sloppiness;
    final List<List<Pt>> strokes = new ArrayList<>();

    double scale, turn;

    HandDrawn(long seed, double sloppiness) {
        this.rnd = new Random(seed);
        this.sloppiness = sloppiness;
    }

    double g(double sd) {
        return rnd.nextGaussian() * sd * sloppiness;
    }

    HandDrawn recipe(GlyphLibrary.Recipe r, double ringRadius, double turn) {
        double radius = 0.85;
        this.scale = ringRadius / radius;
        this.turn = turn;
        ring(radius);
        if (r.band() == null) {
            core(r, radius, 1);
        } else {
            double inner = radius * 0.6;
            ring(inner);
            core(r, inner, inner / radius);
            double mid = (radius + inner) / 2;
            for (GlyphLibrary.Signs s : r.band().signs()) signs(s.type(), s.count(), s.startDeg(), s.size(), mid);
            if (r.band().sigil() != null) symbol(sigil(r.band().sigil()), new Pt(0, mid), 0.2, 0);
        }
        return this;
    }

    HandDrawn sigilOnly(Sigil s, double size, double scale) {
        this.scale = scale;
        this.turn = 0;
        symbol(sigil(s), new Pt(0, 0), size, 0);
        return this;
    }

    private void core(GlyphLibrary.Recipe r, double radius, double k) {
        if (r.sigil() != null) symbol(sigil(r.sigil()), new Pt(0, 0), r.sigilSize() * k, 0);
        else if (r.centerSign() != null) symbol(sign(r.centerSign()), new Pt(0, 0), r.sigilSize() * k, 0);
        for (GlyphLibrary.Signs s : r.signs()) signs(s.type(), s.count(), s.startDeg(), s.size() * k, radius * 0.62);
    }

    private void signs(SignType type, int count, double startDeg, double size, double at) {
        for (int k = 0; k < count; k++) {
            double pos = Math.toRadians(startDeg + k * 360.0 / count);
            symbol(sign(type), new Pt(Math.cos(pos) * at, Math.sin(pos) * at), size, pos - Math.PI / 2);
        }
    }

    private static List<List<Pt>> sigil(Sigil s) {
        return Templates.SIGILS.stream().filter(t -> t.sigil() == s).findFirst().orElseThrow().strokes();
    }

    private static List<List<Pt>> sign(SignType t) {
        return Templates.SIGNS.stream().filter(x -> x.sign() == t && !x.reversed()).findFirst().orElseThrow().strokes();
    }

    private Pt world(Pt p) {
        return p.rotate(turn).scale(scale);
    }

    private void ring(double radius) {
        double ecc = Math.abs(g(0.035)), eccAt = rnd.nextDouble() * Math.PI;
        double w3 = g(0.015), w5 = g(0.01), p3 = rnd.nextDouble() * 6, p5 = rnd.nextDouble() * 6;
        double drift = g(0.025);
        double a0 = rnd.nextDouble() * Math.PI * 2;
        double sweep = Math.PI * 2 + Math.toRadians(5 + Math.abs(g(10)));
        int parts = rnd.nextDouble() < 0.7 ? 1 : 2 + rnd.nextInt(2);
        double step = 0.035 / (radius * scale);
        for (int k = 0; k < parts; k++) {
            double from = a0 + sweep * k / parts - (k > 0 ? Math.toRadians(4 + rnd.nextDouble() * 6) : 0);
            double to = a0 + sweep * (k + 1) / parts;
            List<Pt> s = new ArrayList<>();
            for (double a = from; a <= to; a += step) {
                double t = (a - a0) / (Math.PI * 2);
                double r = radius * (1 + ecc * Math.cos(2 * (a - eccAt)) + w3 * Math.sin(3 * a + p3) + w5 * Math.sin(5 * a + p5) + drift * t);
                s.add(world(new Pt(Math.cos(a) * r, Math.sin(a) * r)));
            }
            strokes.add(jitter(s, 0.004));
        }
    }

    private void symbol(List<List<Pt>> template, Pt at, double size, double rot) {
        double unit = size / 2;
        Pt c = at.plus(new Pt(g(0.04), g(0.04)));
        double s = unit * Math.exp(g(0.12));
        double sx = Math.exp(g(0.08)), sy = Math.exp(g(0.08));
        double shear = g(0.08);
        double r = rot + g(Math.toRadians(10));
        double wob = 0.035, f1 = 2 + rnd.nextDouble() * 3, f2 = 2 + rnd.nextDouble() * 3, ph1 = rnd.nextDouble() * 6, ph2 = rnd.nextDouble() * 6;
        for (List<Pt> st : template) {
            if (st.size() == 1) {
                Pt d = st.get(0);
                Pt q = c.plus(new Pt((d.x() * sx + d.y() * shear) * s, d.y() * sy * s).rotate(r));
                double rr = (0.025 + rnd.nextDouble() * 0.03) / scale;
                List<Pt> dot = new ArrayList<>();
                int n = 6 + rnd.nextInt(8);
                for (int i = 0; i <= n; i++) {
                    double a = i * Math.PI * 2 * 1.2 / n;
                    dot.add(world(q.plus(new Pt(Math.cos(a) * rr, Math.sin(a) * rr))));
                }
                strokes.add(jitter(dot, 0.003));
                continue;
            }
            List<Pt> line = resample(st, 0.02);
            line = trimEnds(line);
            List<Pt> out = new ArrayList<>();
            for (int i = 0; i < line.size(); i++) {
                Pt p = line.get(i);
                double t = (double) i / Math.max(1, line.size() - 1);
                double wx = wob * sloppiness * (Math.sin(f1 * t * Math.PI * 2 + ph1)), wy = wob * sloppiness * Math.sin(f2 * t * Math.PI * 2 + ph2);
                Pt local = new Pt(((p.x() + wx) * sx + (p.y() + wy) * shear) * s, (p.y() + wy) * sy * s);
                out.add(world(c.plus(local.rotate(r))));
            }
            out = resampleWorld(out, 0.035);

            if (out.size() > 12 && rnd.nextDouble() < 0.12 * sloppiness) {
                int cut = 4 + rnd.nextInt(out.size() - 8);
                strokes.add(jitter(new ArrayList<>(out.subList(0, cut)), 0.003));
                strokes.add(jitter(new ArrayList<>(out.subList(cut + 1, out.size())), 0.003));
            } else {
                strokes.add(jitter(out, 0.003));
            }
        }
    }

    private List<Pt> trimEnds(List<Pt> line) {
        if (line.size() < 4) return line;
        List<Pt> out = new ArrayList<>(line);
        for (int end = 0; end < 2; end++) {
            double d = g(0.08);
            int n = out.size();
            Pt a = end == 0 ? out.get(0) : out.get(n - 1), b = end == 0 ? out.get(1) : out.get(n - 2);
            if (d > 0) {
                Pt dir = a.minus(b);
                double len = dir.len();
                if (len < 1e-9) continue;
                Pt ext = a.plus(dir.scale(d / len));
                if (end == 0) out.add(0, ext); else out.add(ext);
            } else {
                int drop = (int) Math.min(n / 4, Math.round(-d / 0.02));
                for (int i = 0; i < drop && out.size() > 3; i++) out.remove(end == 0 ? 0 : out.size() - 1);
            }
        }
        return out;
    }

    private List<Pt> jitter(List<Pt> s, double sd) {
        List<Pt> out = new ArrayList<>(s.size());
        for (Pt p : s) out.add(new Pt(p.x() + rnd.nextGaussian() * sd, p.y() + rnd.nextGaussian() * sd));
        return out;
    }

    private static List<Pt> resample(List<Pt> s, double step) {
        List<Pt> out = new ArrayList<>();
        out.add(s.get(0));
        for (int i = 1; i < s.size(); i++) {
            Pt a = s.get(i - 1), b = s.get(i);
            double len = a.dist(b);
            int n = Math.max(1, (int) Math.ceil(len / step));
            for (int k = 1; k <= n; k++) out.add(new Pt(a.x() + (b.x() - a.x()) * k / n, a.y() + (b.y() - a.y()) * k / n));
        }
        return out;
    }

    private static List<Pt> resampleWorld(List<Pt> s, double step) {
        List<Pt> out = new ArrayList<>();
        out.add(s.get(0));
        Pt last = s.get(0);
        for (int i = 1; i < s.size(); i++) {
            if (s.get(i).dist(last) >= step || i == s.size() - 1) {
                out.add(s.get(i));
                last = s.get(i);
            }
        }
        return out;
    }
}
