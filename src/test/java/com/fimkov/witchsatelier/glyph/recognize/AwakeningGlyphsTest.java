package com.fimkov.witchsatelier.glyph.recognize;

import com.fimkov.witchsatelier.glyph.page.GlyphLibrary;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Every artifact's waking glyph, copied by hand onto the canvas, reads as that glyph. */
class AwakeningGlyphsTest {
    /** The artifacts' glyphs, as Awakening lists them (kept here without the game's registries). */
    static final List<GlyphLibrary.Recipe> GLYPHS = new ArrayList<>();
    static {
        String[][] rows = {
                {"WATER", "REPETITION", "4"}, {"LIGHT", "FLOAT", "4"}, {"-WINDOW", "PULL", "4"}, {"WATER", "COLUMN", "4"},
                {"WIND", "FLOAT", "4"}, {"-DIAMOND", "COLUMN", "4"}, {"LIGHT", "CONVERGENCE", "4"}, {"FIRE", "RADIAL", "4"},
                {"WATER", "CRUSH", "4"}, {"-VISION", "WEAVE", "4"}, {"WIND_UNDERFOOT", null, "0"}, {"EARTH", "LEVITATION", "4"},
                {"LIGHT", "WEAVE", "4"}, {"LIGHT", "PULL", "4"}, {"WATER", "PULL", "4"}};
        for (String[] r : rows) {
            int n = Integer.parseInt(r[2]);
            List<GlyphLibrary.Signs> signs = r[1] == null ? List.of()
                    : List.of(new GlyphLibrary.Signs(com.fimkov.witchsatelier.glyph.model.SignType.valueOf(r[1]), n, n == 2 ? 90 : 45, 0.28));
            if (r[0].startsWith("-")) GLYPHS.add(new GlyphLibrary.Recipe(r[0] + r[1], null, com.fimkov.witchsatelier.glyph.model.SignType.valueOf(r[0].substring(1)), 0.55, signs, null));
            else GLYPHS.add(new GlyphLibrary.Recipe(r[0] + r[1], com.fimkov.witchsatelier.glyph.model.Sigil.valueOf(r[0]), 0.55, signs));
        }
    }

    @Test
    void eachWakingGlyphReadsWhenCopiedOntoTheCanvas() {
        List<String> weak = new ArrayList<>();
        int total = 0, ok = 0;
        for (GlyphLibrary.Recipe g : GLYPHS) {
            int good = 0, n = 30;
            for (long seed = 0; seed < n; seed++) {
                HandDrawn h = new HandDrawn(seed, 0.8);
                h.recipe(g, 0.85, 0); // page units, upright as on the canvas
                GlyphReading r = GlyphAnalyzer.analyze(h.strokes, 0.035);
                if (r.closed() && GlyphLibrary.readsAs(g, r)) good++;
            }
            total += n;
            ok += good;
            if (good < n * 0.75) weak.add(g.name() + " " + good + "/" + n);
        }
        System.out.println("waking glyphs read right " + ok + "/" + total + "; weak: " + weak);
        assertTrue(weak.isEmpty(), "these read poorly: " + weak);
    }
}
