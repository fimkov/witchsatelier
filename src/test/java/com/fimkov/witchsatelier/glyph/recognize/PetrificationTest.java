package com.fimkov.witchsatelier.glyph.recognize;

import com.fimkov.witchsatelier.glyph.model.SignType;
import com.fimkov.witchsatelier.glyph.page.GlyphLibrary;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PetrificationTest {
    private static boolean puppet(GlyphReading r) {
        for (GlyphReading g = r; g != null; g = g.core()) {
            if (g.centerSign() == SignType.DANCING_PUPPET || g.count(SignType.DANCING_PUPPET) > 0) return true;
        }
        return false;
    }

    @Test
    void aCopyOfPetrificationNeverMakesThingsDance() {
        var recipe = GlyphLibrary.byName("petrification");
        List<String> bad = new ArrayList<>();
        int right = 0, all = 0;
        for (double sloppiness : new double[]{0.5, 1.0, 1.5}) {
            for (int seed = 0; seed < 20; seed++) {
                GlyphReading r = RobustnessTest.read(recipe, 7000L + seed * 31L + Math.round(sloppiness * 10), sloppiness);
                all++;
                if (GlyphLibrary.readsAs(recipe, r)) right++;
                else if (all % 3 == 0) System.out.println("  miss " + sloppiness + ": " + RobustnessTest.mistake(recipe, r) + " | " + r.describe());
                if (puppet(r)) bad.add(sloppiness + "/" + seed + ": " + r.describe());
            }
        }
        System.out.println("petrification copies read right " + right + "/" + all);
        assertTrue(bad.isEmpty(), "petrification read as a dancing puppet: " + bad);
        assertTrue(right >= all / 2, "petrification copies read right only " + right + "/" + all);
    }
}
