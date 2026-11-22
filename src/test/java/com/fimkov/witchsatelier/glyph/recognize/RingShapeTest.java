package com.fimkov.witchsatelier.glyph.recognize;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Rings drawn by turning the camera are wobbly; polygons inside glyphs must not count as rings. */
class RingShapeTest {

    /** Superellipse |x|^n + |y|^n = 1: n=2 circle, n=3 rounded square, n=8 almost a square. */
    private static List<Pt> superellipse(double r, double n, double startDeg, double dent) {
        List<Pt> s = new ArrayList<>();
        for (int i = 0; i <= 400; i++) {
            double t = Math.toRadians(startDeg + 362.0 * i / 400);
            double c = Math.cos(t), sn = Math.sin(t);
            double x = Math.signum(c) * Math.pow(Math.abs(c), 2 / n);
            double y = Math.signum(sn) * Math.pow(Math.abs(sn), 2 / n);
            double k = 1 + 0.04 * Math.sin(5 * t) - dent * Math.exp(-Math.pow((i - 200) / 8.0, 2));
            s.add(new Pt(x * r * k, y * r * k));
        }
        return s;
    }

    private static List<Pt> triangle(double r, double startFraction) {
        Pt[] v = {new Pt(0, r), new Pt(-r * 0.87, -r * 0.5), new Pt(r * 0.87, -r * 0.5)};
        List<Pt> s = new ArrayList<>();
        for (int e = 0; e <= 3; e++) {
            Pt a = v[e % 3], b = v[(e + 1) % 3];
            for (int k = 0; k < 30; k++) {
                double t = k / 30.0;
                if (e == 0 && t < startFraction) continue;
                if (e == 3 && t > startFraction) break;
                s.add(new Pt(a.x() + (b.x() - a.x()) * t, a.y() + (b.y() - a.y()) * t));
            }
        }
        return s;
    }

    private static List<RingFinder.Ring> rings(List<Pt> s) {
        return RingFinder.find(List.of(s), Drawing.INK);
    }

    @Test
    void wobblyRoundedSquareWithDentIsAClosedRing() {
        for (double n : new double[]{2, 2.6, 3.2}) {
            List<Pt> s = superellipse(1.5, n, -90, 0.08);
            System.out.println("n=" + n + " " + RingFinder.diagnose(List.of(s)));
            List<RingFinder.Ring> r = rings(s);
            assertEquals(1, r.size(), "n=" + n + " should be a ring: " + RingFinder.diagnose(List.of(s)));
            assertTrue(r.get(0).closed(), "n=" + n);
        }
    }

    @Test
    void trianglesAreNotRings() {
        for (double start : new double[]{0, 0.5}) {
            List<Pt> s = triangle(0.4, start);
            System.out.println("triangle start=" + start + " " + RingFinder.diagnose(List.of(s)));
            assertTrue(rings(s).isEmpty(), "triangle starting at " + start + " " + RingFinder.diagnose(List.of(s)));
        }
    }

    @Test
    void smallSquareInsideAGlyphIsASymbolNotTheRing() {
        Drawing d = new Drawing(3, 0.01).ring(new Pt(0, 0), 1.5, 0, 365);
        d.strokes.add(superellipse(0.4, 10, 45, 0));
        GlyphReading r = GlyphAnalyzer.analyze(d.strokes, Drawing.INK);
        assertEquals(1.5, r.ring().radius(), 0.1, r.describe());
        assertTrue(r.innerRings().isEmpty(), r.describe());
        assertTrue(r.centerSign() != null || r.sigil() != null || r.unknown() > 0, "the square should be read as the centre symbol: " + r.describe());
    }

    @Test
    void lineEndCurlingInsideDoesNotCloseTheGap() {
        // ring from 30 deg round to 350 deg, then the start of the stroke hooks inwards under the gap (as drawn in game)
        List<Pt> s = new ArrayList<>();
        for (int i = 0; i <= 30; i++) {
            double t = i / 30.0;
            double a = Math.toRadians(-20 + 50 * t);      // sweeps back under the gap ...
            double r = 1.5 * (0.62 + 0.38 * t);            // ... far inside the ring line
            s.add(new Pt(Math.cos(a) * r, Math.sin(a) * r));
        }
        for (int i = 1; i <= 400; i++) {
            double a = Math.toRadians(30 + 320.0 * i / 400);
            s.add(new Pt(Math.cos(a) * 1.5, Math.sin(a) * 1.5));
        }
        List<RingFinder.Ring> r = rings(s);
        assertEquals(1, r.size(), RingFinder.diagnose(List.of(s)));
        assertFalse(r.get(0).closed(), "gap " + r.get(0).maxGap());
    }

    @Test
    void squarishWobblyRingWhoseEndsMeetIsClosed() {
        // like the page in game: a rounded-square ring, drawn in one go, its end running a bit past its start
        List<Pt> s = new ArrayList<>();
        for (int i = 0; i <= 500; i++) {
            double t = Math.toRadians(-100 + 372.0 * i / 500);
            double c = Math.cos(t), sn = Math.sin(t);
            double n = 4.0;
            double x = Math.signum(c) * Math.pow(Math.abs(c), 2 / n), y = Math.signum(sn) * Math.pow(Math.abs(sn), 2 / n);
            double k = 0.72 * (1 + 0.04 * Math.sin(t * 7) + 0.03 * Math.sin(t * 3 + 1));
            s.add(new Pt(x * k, y * k));
        }
        List<RingFinder.Ring> r = RingFinder.find(List.of(s), 0.035);
        assertEquals(1, r.size(), RingFinder.diagnose(List.of(s)));
        assertTrue(r.get(0).closed(), "gap " + r.get(0).maxGap());
    }
}
