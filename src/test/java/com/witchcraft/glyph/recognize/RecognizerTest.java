package com.witchcraft.glyph.recognize;

import com.witchcraft.glyph.model.Orientation;
import com.witchcraft.glyph.model.Sigil;
import com.witchcraft.glyph.model.SignType;
import com.witchcraft.glyph.recognize.Templates.Template;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RecognizerTest {

    private static final double NOISE = 0.03;

    @Test
    void sigilsAreRecognisedWhenDrawnByHand() {
        List<String> failures = new ArrayList<>();
        for (Template t : Templates.SIGILS) {
            for (int seed = 0; seed < 6; seed++) {
                Drawing d = new Drawing(seed * 31L + t.id().hashCode(), NOISE)
                        .put(t.strokes(), new Pt(0, 0), 0.6, Math.toRadians((seed - 3) * 3));
                SymbolRecognizer.Match m = SymbolRecognizer.classifyCenter(d.strokes);
                System.out.printf("sigil %-15s seed %d -> %-15s d=%.2f score=%.2f%n", t.id(), seed,
                        m.template() == null ? "-" : m.template().id(), m.distance(), m.score());
                if (m.template() == null || m.template().sigil() != t.sigil()) failures.add(t.id() + "#" + seed + " -> " + (m.template() == null ? "-" : m.template().id()));
            }
        }
        assertTrue(failures.isEmpty(), "misread sigils: " + failures);
    }

    @Test
    void signsAreRecognisedWithTheirOrientation() {
        List<String> failures = new ArrayList<>();
        Orientation[] orients = Orientation.values();
        for (Template t : Templates.SIGNS) {
            for (int seed = 0; seed < 8; seed++) {
                double pos = Math.toRadians(seed * 45 + 10);
                Orientation o = orients[seed % orients.length];
                double rot = switch (o) {
                    case OUTWARD -> pos - Math.PI / 2;
                    case INWARD -> pos + Math.PI / 2;
                    case CCW -> pos;
                    case CW -> pos + Math.PI;
                };
                Pt at = new Pt(Math.cos(pos), Math.sin(pos));
                Drawing d = new Drawing(seed * 17L + t.id().hashCode(), NOISE).put(t.strokes(), at, 0.35, rot);
                SymbolRecognizer.Match m = SymbolRecognizer.classifySign(d.strokes, pos);
                boolean sameSign = m.template() != null && m.template().sign() == t.sign();
                boolean sameMeaning = sameSign && effectiveInversion(m) == effectiveInversion(t.reversed(), o)
                        && sameAxis(m.orientation(), o);
                System.out.printf("sign %-20s %-7s -> %-20s %-7s d=%.2f score=%.2f%s%n", t.id(), o,
                        m.template() == null ? "-" : m.template().id(), m.orientation(), m.distance(), m.score(),
                        sameMeaning ? "" : "   <-- MISMATCH");
                if (!sameMeaning && !symmetricUnderTurn(t.sign(), o, m.orientation())) {
                    failures.add(t.id() + "/" + o + " -> " + (m.template() == null ? "-" : m.template().id() + "/" + m.orientation()));
                }
            }
        }
        System.out.println("sign failures: " + failures.size() + " " + failures);
        assertTrue(failures.size() <= Templates.SIGNS.size() * 8 / 10, "too many misread signs: " + failures);
    }

    /** Shapes that look the same turned 90 or 180 degrees can't reveal their orientation; that is fine. */
    private static boolean symmetricUnderTurn(SignType type, Orientation drawn, Orientation read) {
        return switch (type) {
            case WINDOW, CROSSHAIR, VISION, BILLOWING, DANCING_PUPPET, RAIN, ENLARGE -> true;
            case DIAMOND, COLLECTION, FLOAT, BOLT, EYE, CRUSH -> sameAxis(drawn, read);
            default -> false;
        };
    }

    private static boolean sameAxis(Orientation a, Orientation b) {
        boolean ra = a == Orientation.OUTWARD || a == Orientation.INWARD;
        boolean rb = b == Orientation.OUTWARD || b == Orientation.INWARD;
        return ra == rb;
    }

    private static boolean effectiveInversion(SymbolRecognizer.Match m) {
        return effectiveInversion(m.template().reversed(), m.orientation());
    }

    private static boolean effectiveInversion(boolean reversedTemplate, Orientation o) {
        return reversedTemplate ^ (o == Orientation.INWARD);
    }

    @Test
    void closedRingIsClosed() {
        Drawing d = new Drawing(1, 0.01).ring(new Pt(0, 0), 1.2, 0, 365);
        List<RingFinder.Ring> rings = RingFinder.find(d.strokes, Drawing.INK);
        assertEquals(1, rings.size());
        assertTrue(rings.get(0).closed(), "gap " + rings.get(0).maxGap());
        assertEquals(1.2, rings.get(0).radius(), 0.05);
    }

    @Test
    void ringWithGapIsOpenUntilClosedByAnotherStroke() {
        Drawing d = new Drawing(2, 0.01).ring(new Pt(0, 0), 1.0, 20, 350);
        List<RingFinder.Ring> rings = RingFinder.find(d.strokes, Drawing.INK);
        assertEquals(1, rings.size());
        assertFalse(rings.get(0).closed(), "should be open, gap " + rings.get(0).maxGap());

        d.ring(new Pt(0, 0), 1.0, 345, 385); // short closing stroke over the gap
        rings = RingFinder.find(d.strokes, Drawing.INK);
        assertEquals(1, rings.size());
        assertTrue(rings.get(0).closed(), "should be closed, gap " + rings.get(0).maxGap());
        assertEquals(2, rings.get(0).strokes().size());
    }

    @Test
    void basicFireColumnGlyphReads() {
        Drawing d = new Drawing(3, 0.02);
        d.ring(new Pt(0, 0), 1.5, 0, 362);
        d.put(template(Sigil.FIRE), new Pt(0.02, -0.03), 0.8, 0);
        for (int k = 0; k < 4; k++) {
            double pos = Math.toRadians(45 + k * 90);
            d.put(template(SignType.COLUMN), new Pt(Math.cos(pos) * 0.95, Math.sin(pos) * 0.95), 0.45, pos - Math.PI / 2);
        }
        GlyphReading r = GlyphAnalyzer.analyze(d.strokes, Drawing.INK);
        System.out.println(r.describe());
        assertTrue(r.closed());
        assertEquals(Sigil.FIRE, r.sigil());
        assertEquals(4, r.count(SignType.COLUMN), r.describe());
        assertTrue(r.symmetry() > 0.85, r.describe());
    }

    @Test
    void unbalancedSignsLeanTowardsTheHeavierSide() {
        Drawing d = new Drawing(4, 0.02);
        d.ring(new Pt(0, 0), 1.5, 0, 362);
        d.put(template(Sigil.WATER), new Pt(0, 0), 0.8, 0);
        // three columns on the right half, one on the left
        double[] angles = {-40, 0, 40, 180};
        for (double a : angles) {
            double pos = Math.toRadians(a);
            d.put(template(SignType.COLUMN), new Pt(Math.cos(pos) * 0.95, Math.sin(pos) * 0.95), 0.45, pos - Math.PI / 2);
        }
        GlyphReading r = GlyphAnalyzer.analyze(d.strokes, Drawing.INK);
        System.out.println(r.describe());
        assertEquals(Sigil.WATER, r.sigil());
        assertTrue(r.imbalance().x() > 0.2, "should lean right: " + r.imbalance());
    }

    private static List<List<Pt>> template(Sigil s) {
        return Templates.SIGILS.stream().filter(t -> t.sigil() == s).findFirst().orElseThrow().strokes();
    }

    private static List<List<Pt>> template(SignType s) {
        return Templates.SIGNS.stream().filter(t -> t.sign() == s && !t.reversed()).findFirst().orElseThrow().strokes();
    }

    /** Roughly what was drawn in game: wobbly ring, rounded fire sigil, four wide "T" columns, ring closed later. */
    @Test
    void handDrawnFireGlyphLikeInGame() {
        Drawing d = new Drawing(9, 0.035);
        d.ring(new Pt(0, 0), 1.45, 100, 445);           // the ring, with a gap near the top
        List<List<Pt>> dome = Shapes.of().arc(0, -0.3, 0.8, 5, 175).line(-0.8, -0.3, 0.8, -0.3).line(0.05, 0.5, 0.05, -1.05).build();
        d.put(dome, new Pt(0.05, 0), 0.75, 0.05);
        List<List<Pt>> tee = Shapes.of().line(0, -0.55, 0, 0.55).line(-1, 0.55, 1, 0.55).build();
        for (int k = 0; k < 4; k++) {
            double pos = Math.toRadians(90 + k * 90 + 4);
            d.put(tee, new Pt(Math.cos(pos) * 0.95, Math.sin(pos) * 0.95), 0.5, pos - Math.PI / 2);
        }
        GlyphReading open = GlyphAnalyzer.analyze(d.strokes, Drawing.INK);
        System.out.println("in-game open:  " + open.describe());
        assertFalse(open.closed(), open.describe());
        d.ring(new Pt(0, 0), 1.52, 75, 112);             // closing stroke, drawn a little outside the ring line
        GlyphReading r = GlyphAnalyzer.analyze(d.strokes, Drawing.INK);
        System.out.println("in-game closed: " + r.describe());
        assertTrue(r.closed(), r.describe());
        assertEquals(Sigil.FIRE, r.sigil(), r.describe());
        assertEquals(4, r.count(SignType.COLUMN), r.describe());
    }
}
