package com.fimkov.witchsatelier.glyph.recognize;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class ImaginedRingTest {
    private static double[] shaky(double turns, int n) {
        double[] xy = new double[n * 2];
        Random rnd = new Random(4);
        for (int i = 0; i < n; i++) {
            double a = Math.toRadians(20 + turns * i / (n - 1));
            double r = 3 * (1 + 0.07 * Math.sin(a * 3) + 0.02 * rnd.nextGaussian());
            xy[i * 2] = 10 + Math.cos(a) * r;
            xy[i * 2 + 1] = 5 + Math.sin(a) * r;
        }
        return xy;
    }

    private static List<List<Pt>> asStrokes(double[]... lines) {
        List<List<Pt>> out = new ArrayList<>();
        for (double[] l : lines) {
            List<Pt> s = new ArrayList<>();
            for (int i = 0; i + 1 < l.length; i += 2) s.add(new Pt(l[i], l[i + 1]));
            out.add(s);
        }
        return out;
    }

    @Test
    void aRingIsOnlyImaginedOnceItGoesRoundASecondTime() {
        assertFalse(noticedAnywhere(shaky(130, 40)));
        assertFalse(noticedAnywhere(shaky(380, 120)));

        assertTrue(ImaginedRing.noticed(shaky(440, 140), 140, 0.45, 12));
        double[] line = new double[80];
        for (int i = 0; i < 40; i++) { line[i * 2] = i * 0.1; line[i * 2 + 1] = 0.02 * Math.sin(i); }
        assertFalse(ImaginedRing.noticed(line, 40, 0.45, 12));
    }

    @Test
    void aScribbleToAndFroRoundACircleIsNoRing() {
        int n = 400;
        double[] xy = new double[n * 2];
        for (int i = 0; i < n; i++) {
            double t = i / 20.0;
            double a = Math.toRadians(30 * t) + Math.toRadians(45) * Math.sin(t * Math.PI);
            xy[i * 2] = 3 * Math.cos(a);
            xy[i * 2 + 1] = 3 * Math.sin(a);
        }
        assertTrue(Math.abs(ImaginedRing.turned(pts(xy), new Pt(0, 0))) > 500, "the scribble does get round");
        assertFalse(noticedAnywhere(xy));
    }

    @Test
    void signsAndSigilsAreNeverTakenForARing() {
        List<Templates.Template> all = new ArrayList<>(Templates.SIGILS);
        all.addAll(Templates.SIGNS);
        for (Templates.Template t : all) {
            for (double size : new double[]{0.5, 1, 2, 4, 8}) {
                List<double[]> lines = new ArrayList<>();
                List<Pt> joined = new ArrayList<>();
                for (List<Pt> s : t.strokes()) {
                    List<Pt> scaled = new ArrayList<>();
                    for (Pt p : s) scaled.add(new Pt(p.x() * size, p.y() * size));
                    List<Pt> fine = resample(scaled, 0.012 * Math.max(1, size / 2));
                    lines.add(flat(fine));
                    joined.addAll(fine);
                }
                lines.add(flat(resample(joined, 0.012 * Math.max(1, size / 2))));
                for (double[] l : lines) assertFalse(noticedAnywhere(l), t.id() + " at size " + size);
            }
        }
    }

    private static boolean noticedAnywhere(double[] xy) {
        int n = xy.length / 2;
        for (int k = 4; k <= n; k += 4) if (ImaginedRing.noticed(xy, k, 0.45, 12) || ImaginedRing.noticed(xy, k, 0.12, 1.3)) return true;
        return false;
    }

    private static List<Pt> pts(double[] xy) {
        List<Pt> out = new ArrayList<>();
        for (int i = 0; i + 1 < xy.length; i += 2) out.add(new Pt(xy[i], xy[i + 1]));
        return out;
    }

    private static double[] flat(List<Pt> pts) {
        double[] out = new double[pts.size() * 2];
        for (int i = 0; i < pts.size(); i++) { out[i * 2] = pts.get(i).x(); out[i * 2 + 1] = pts.get(i).y(); }
        return out;
    }

    private static List<Pt> resample(List<Pt> s, double step) {
        List<Pt> out = new ArrayList<>();
        out.add(s.get(0));
        for (int i = 1; i < s.size(); i++) {
            Pt a = s.get(i - 1), b = s.get(i);
            int k = Math.max(1, (int) Math.ceil(a.dist(b) / step));
            for (int j = 1; j <= k; j++) out.add(new Pt(a.x() + (b.x() - a.x()) * j / k, a.y() + (b.y() - a.y()) * j / k));
        }
        return out;
    }

    @Test
    void twoLapsInTheAirLayDownATrueOpenRing() {
        assertFalse(ImaginedRing.circledTwice(shaky(500, 200), 200));
        assertNull(ImaginedRing.trueRing(shaky(500, 200), 200, 0.45, 12, 0.35, 0.08));
        double[] laps = shaky(720, 300);
        assertTrue(ImaginedRing.circledTwice(laps, 300));
        double[] ring = ImaginedRing.trueRing(laps, 300, 0.45, 12, 0.35, 0.08);
        assertNotNull(ring);

        for (int i = 0; i < ring.length; i += 2) {
            double d = Math.hypot(ring[i] - 10, ring[i + 1] - 5);
            assertEquals(3, d, 0.15);
        }
        GlyphReading r = GlyphAnalyzer.analyze(asStrokes(ring), 1.0 / 16);
        assertNotNull(r.ring(), r.describe());
        assertTrue(r.ring().roundness() > 0.98, r.describe());
        assertFalse(r.closed(), "the ring waits with its gap: " + r.describe());

        int n = ring.length / 2;
        double[] closer = {ring[(n - 1) * 2], ring[(n - 1) * 2 + 1],
                (ring[(n - 1) * 2] + ring[0]) / 2 * 1.0, (ring[(n - 1) * 2 + 1] + ring[1]) / 2, ring[0], ring[1]};
        GlyphReading closed = GlyphAnalyzer.analyze(asStrokes(ring, closer), 1.0 / 16);
        assertTrue(closed.closed(), closed.describe());
    }
}
