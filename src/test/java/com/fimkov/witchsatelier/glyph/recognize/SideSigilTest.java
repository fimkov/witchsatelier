package com.fimkov.witchsatelier.glyph.recognize;

import com.fimkov.witchsatelier.gametest.GlyphSketch;
import com.fimkov.witchsatelier.glyph.model.Sigil;
import com.fimkov.witchsatelier.glyph.page.GlyphLibrary;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SideSigilTest {
    private static GlyphReading mixed(Sigil middle, Sigil side) {
        double radius = 1.4, off = radius * 0.6;
        GlyphSketch g = new GlyphSketch(new Pt(0, 0), radius).ring(0, 362).sigil(middle, 0.7);
        for (double x : new double[]{off, -off})
            g.strokes.addAll(new GlyphSketch(new Pt(x, 0), 0.3).sigil(side, 0.5).strokes);
        List<List<Pt>> strokes = new ArrayList<>();
        for (double[] f : g.strokes) {
            List<Pt> pts = new ArrayList<>();
            for (int i = 0; i < f.length; i += 2) pts.add(new Pt(f[i], f[i + 1]));
            strokes.add(pts);
        }
        return GlyphAnalyzer.analyze(strokes, RobustnessTest.INK, Math.PI / 2);
    }

    @Test
    void sigilsBesideTheMiddleOneAreReadAsSides() {
        List<String> misread = new ArrayList<>();
        for (Sigil middle : new Sigil[]{Sigil.FIRE, Sigil.WATER, Sigil.WIND, Sigil.EARTH}) {
            for (Sigil side : new Sigil[]{Sigil.WATER, Sigil.FIRE, Sigil.LIGHT}) {
                if (side == middle) continue;
                GlyphReading r = mixed(middle, side);
                if (r.sigil() != middle || r.sides().size() != 2 || r.sides().stream().anyMatch(s -> s != side))
                    misread.add(middle + "+" + side + " -> " + r.sigil() + " " + r.sides());
            }
        }
        assertTrue(misread.isEmpty(), "misread mixed glyphs: " + misread);
    }

    @Test
    void grimoireSpellsGrowNoSideSigils() {
        List<String> grown = new ArrayList<>();
        int all = 0;
        for (int k = 0; k < RobustnessTest.RECIPES.size(); k++) {
            GlyphLibrary.Recipe r = RobustnessTest.RECIPES.get(k);
            for (int i = 0; i < 6; i++) {
                GlyphReading g = RobustnessTest.read(r, 4242L * k + i * 131L, 1.0);
                all++;
                if (!g.sides().isEmpty()) grown.add(r.name() + "#" + i + " " + g.sides());
            }
        }
        System.out.println("side sigils grown in " + grown.size() + " of " + all + ": " + grown);
        assertTrue(grown.size() <= all / 50, "signs read as side sigils: " + grown);
    }
}
