package com.fimkov.witchsatelier.glyph.recognize;

import com.fimkov.witchsatelier.glyph.model.Sigil;
import com.fimkov.witchsatelier.glyph.model.SignType;
import com.fimkov.witchsatelier.glyph.page.GlyphLibrary;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class StrikeTest {
    private static List<Pt> line(Pt from, Pt to) {
        List<Pt> out = new ArrayList<>();
        for (int i = 0; i <= 30; i++) out.add(new Pt(from.x() + (to.x() - from.x()) * i / 30, from.y() + (to.y() - from.y()) * i / 30));
        return out;
    }

    @Test
    void aStruckSignIsLeftOut() {
        var recipe = GlyphLibrary.byName("pillar_of_flame");
        int ok = 0, n = 8;
        StringBuilder seen = new StringBuilder();
        for (int seed = 0; seed < n; seed++) {
            HandDrawn h = new HandDrawn(4000 + seed, 0.4).recipe(recipe, 2.0, 0);
            double k = 2.0 / GlyphLibrary.OUTER_RADIUS, at = GlyphLibrary.OUTER_RADIUS * 0.62 * k, half = 0.28 * k * 0.9;
            double a = Math.toRadians(45);
            Pt mid = new Pt(Math.cos(a) * at, Math.sin(a) * at);
            Pt across = new Pt(-Math.sin(a) * half, Math.cos(a) * half);
            h.strokes.add(line(mid.minus(across), mid.plus(across)));
            GlyphReading r = GlyphAnalyzer.analyze(h.strokes, RobustnessTest.INK, Math.PI / 2);
            boolean right = r.closed() && r.sigil() == Sigil.FIRE && r.count(SignType.COLUMN) == 3 && r.unknown() == 0 && r.signs().size() == 3;
            if (right) ok++;
            else seen.append("\n").append(r.describe());
        }
        assertTrue(ok >= n - 1, "struck columns read right " + ok + "/" + n + seen);
    }

    @Test
    void boltsAreNotStruckColumns() {
        var recipe = GlyphLibrary.byName("fire_bolts");
        int ok = 0, n = 8;
        for (int seed = 0; seed < n; seed++) {
            GlyphReading r = GlyphAnalyzer.analyze(new HandDrawn(5000 + seed, 0.4).recipe(recipe, 2.0, 0).strokes, RobustnessTest.INK, Math.PI / 2);
            if (r.count(SignType.BOLT) == 4) ok++;
        }
        assertTrue(ok >= n - 1, "bolts read right only " + ok + "/" + n);
    }
}
