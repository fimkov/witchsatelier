package com.fimkov.witchsatelier.glyph.recognize;

import com.fimkov.witchsatelier.gametest.GlyphSketch;
import com.fimkov.witchsatelier.glyph.model.Orientation;
import com.fimkov.witchsatelier.glyph.model.Sigil;
import com.fimkov.witchsatelier.glyph.model.SignType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

class ForbiddenTest {
    @Test
    void petrificationReads() /* signs at 45 degrees used to be scaled by their slanted box */ {
        GlyphSketch g = new GlyphSketch(new Pt(0, 0), 1.4).ring(0, 362).sigil(Sigil.EARTH, 0.8).signs(SignType.EYE, 4, Orientation.OUTWARD, 0.4, 45);
        List<List<Pt>> strokes = new ArrayList<>();
        for (double[] d : g.strokes) { List<Pt> s = new ArrayList<>(); for (int i = 0; i + 1 < d.length; i += 2) s.add(new Pt(d[i], d[i + 1])); strokes.add(s); }
        GlyphReading r = GlyphAnalyzer.analyze(strokes, 1.0 / 16);
        org.junit.jupiter.api.Assertions.assertEquals(Sigil.EARTH, r.sigil(), r.describe());
        org.junit.jupiter.api.Assertions.assertTrue(r.count(SignType.EYE) >= 2, r.describe());
    }
}
