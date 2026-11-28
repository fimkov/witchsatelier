package com.fimkov.witchsatelier.glyph.recognize;

import com.fimkov.witchsatelier.glyph.model.SignType;
import com.fimkov.witchsatelier.glyph.page.GlyphLibrary;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class WaymarkPageTest {
    @Test
    void handCopiesOfTheWaymarkKeepTheirSigns() {
        var recipe = GlyphLibrary.byName("waymark_window");
        int right = 0, all = 0;
        for (double sloppiness : new double[]{0.5, 1.0}) {
            for (int seed = 0; seed < 20; seed++) {
                GlyphReading r = RobustnessTest.read(recipe, 9100L + seed * 37L + Math.round(sloppiness * 10), sloppiness);
                all++;
                boolean named = r.closed() && r.centerSign() == SignType.WINDOW && r.unknown() == 0
                        && r.count(SignType.BIRD) == 2 && r.count(SignType.EYE) == 1 && r.signs().size() == 3;
                if (named) right++;
                else if (all % 3 == 0) System.out.println("  miss " + sloppiness + ": " + RobustnessTest.mistake(recipe, r) + " | " + r.describe());
            }
        }
        System.out.println("waymark copies keep their signs " + right + "/" + all);
        assertTrue(right * 4 >= all * 3, "waymark copies keep their signs only " + right + "/" + all);
    }
}
