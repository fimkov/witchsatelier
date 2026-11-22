package com.fimkov.witchsatelier.gametest;

import com.fimkov.witchsatelier.glyph.model.Orientation;
import com.fimkov.witchsatelier.glyph.model.Sigil;
import com.fimkov.witchsatelier.glyph.model.SignType;
import com.fimkov.witchsatelier.glyph.recognize.Pt;
import com.fimkov.witchsatelier.glyph.recognize.Templates;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Builds pen strokes for a glyph the way a player would draw them (for in-game tests). */
public final class GlyphSketch {
    private final Pt center;
    private final double radius;
    private final Random rnd = new Random(42);
    public final List<double[]> strokes = new ArrayList<>();

    public GlyphSketch(Pt center, double radius) {
        this.center = center;
        this.radius = radius;
    }

    /** Ring arc from a0 to a1 degrees, as one stroke. */
    public GlyphSketch ring(double a0, double a1) {
        List<Pt> pts = new ArrayList<>();
        int n = (int) Math.ceil(Math.abs(a1 - a0) / 360 * Math.PI * 2 * radius / 0.03);
        for (int i = 0; i <= n; i++) {
            double a = Math.toRadians(a0 + (a1 - a0) * i / n);
            double r = radius * (1 + 0.01 * Math.sin(a * 5));
            pts.add(new Pt(center.x() + Math.cos(a) * r, center.y() + Math.sin(a) * r));
        }
        strokes.add(flat(pts));
        return this;
    }

    public GlyphSketch sigil(Sigil s, double size) {
        var t = Templates.SIGILS.stream().filter(x -> x.sigil() == s).findFirst().orElseThrow();
        put(t.strokes(), center, size, 0);
        return this;
    }

    /** A sign drawn alone in the middle (instead of a sigil). */
    public GlyphSketch centerSign(SignType type, double size) {
        var t = Templates.SIGNS.stream().filter(x -> x.sign() == type && !x.reversed()).findFirst().orElseThrow();
        put(t.strokes(), center, size, 0);
        return this;
    }

    /** {@code count} copies of a sign evenly around the centre at 0.62 of the radius. */
    public GlyphSketch signs(SignType type, int count, Orientation o, double size, double startDeg) {
        var t = Templates.SIGNS.stream().filter(x -> x.sign() == type && !x.reversed()).findFirst().orElseThrow();
        for (int k = 0; k < count; k++) {
            double pos = Math.toRadians(startDeg + k * 360.0 / count);
            double rot = switch (o) {
                case OUTWARD -> pos - Math.PI / 2;
                case INWARD -> pos + Math.PI / 2;
                case CCW -> pos;
                case CW -> pos + Math.PI;
            };
            Pt at = new Pt(center.x() + Math.cos(pos) * radius * 0.62, center.y() + Math.sin(pos) * radius * 0.62);
            put(t.strokes(), at, size, rot);
        }
        return this;
    }

    private void put(List<List<Pt>> template, Pt at, double size, double rot) {
        for (List<Pt> s : template) {
            List<Pt> pts = new ArrayList<>();
            for (int i = 0; i < s.size(); i++) {
                Pt p = s.get(i).rotate(rot).scale(size / 2).plus(at);
                pts.add(new Pt(p.x() + rnd.nextGaussian() * 0.004, p.y() + rnd.nextGaussian() * 0.004));
            }
            strokes.add(flat(pts));
        }
    }

    private static double[] flat(List<Pt> pts) {
        double[] f = new double[pts.size() * 2];
        for (int i = 0; i < pts.size(); i++) {
            f[i * 2] = pts.get(i).x();
            f[i * 2 + 1] = pts.get(i).y();
        }
        return f;
    }
}
