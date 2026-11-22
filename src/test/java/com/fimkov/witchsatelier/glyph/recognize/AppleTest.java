package com.fimkov.witchsatelier.glyph.recognize;

import com.fimkov.witchsatelier.glyph.model.Sigil;
import com.fimkov.witchsatelier.glyph.page.GlyphLibrary;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** The apple no book shows: drawn in a ring by hand it reads as an apple, and no recipe reads as one. */
class AppleTest {
    static final GlyphLibrary.Recipe APPLE = new GlyphLibrary.Recipe("apple", Sigil.APPLE, 0.6, List.of());

    static GlyphReading draw(GlyphLibrary.Recipe r, long seed, double sloppiness) {
        HandDrawn h = new HandDrawn(seed, sloppiness);
        java.util.Random rnd = new java.util.Random(seed * 31 + 7);
        double turn = rnd.nextDouble() * Math.PI * 2;
        h.recipe(r, 1.4 + rnd.nextDouble() * 1.6, turn);
        double up = turn + Math.PI / 2 + rnd.nextGaussian() * 0.3;
        return GlyphAnalyzer.analyzeFromAnySide(h.strokes, RobustnessTest.INK, up);
    }

    @Test
    void aHandDrawnAppleReadsAsAnApple() {
        int ok = 0, n = 0;
        List<String> wrong = new ArrayList<>();
        for (double sloppiness : new double[]{0.5, 1.0}) {
            for (long seed = 0; seed < 30; seed++) {
                GlyphReading g = draw(APPLE, seed, sloppiness);
                n++;
                if (g.closed() && g.sigil() == Sigil.APPLE && g.core() == null) ok++;
                else wrong.add(g.describe());
            }
        }
        System.out.println("apple read right " + ok + "/" + n);
        assertTrue(ok >= n * 0.9, ok + "/" + n + ": " + wrong);
    }

    @Test
    void noRecipeReadsAsAnApple() {
        List<String> wrong = new ArrayList<>();
        for (GlyphLibrary.Recipe r : RobustnessTest.RECIPES) {
            for (long seed = 0; seed < 24; seed++) {
                GlyphReading g = seed % 2 == 0 ? draw(r, seed, 1.0) : RobustnessTest.read(r, seed, 1.3);
                GlyphReading core = g.core() != null ? g.core() : g;
                if (g.sigil() == Sigil.APPLE || core.sigil() == Sigil.APPLE) wrong.add(r.name() + " #" + seed);
            }
        }
        assertTrue(wrong.isEmpty(), "read as an apple: " + wrong);
    }
}
