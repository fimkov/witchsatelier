package com.fimkov.witchsatelier.glyph.recognize;

import com.fimkov.witchsatelier.glyph.page.GlyphLibrary;
import com.fimkov.witchsatelier.glyph.page.PageKind;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.junit.jupiter.api.Assertions.*;

class BootsSplitTest {
    @Test
    void halvesOnTheTwoSolesReadAsOneGlyph() {
        GlyphLibrary.Recipe flight = GlyphLibrary.byName("wind_underfoot");
        int ok = 0, base = 0, n = 30;
        for (long seed = 0; seed < n; seed++) {
            HandDrawn h = new HandDrawn(seed, 0.7);
            h.recipe(flight, 0.85, 0);
            List<List<Pt>> onSoles = new ArrayList<>();
            for (List<Pt> s : h.strokes) {
                float[] f = new float[s.size() * 2];
                for (int i = 0; i < s.size(); i++) { f[i * 2] = (float) s.get(i).x(); f[i * 2 + 1] = (float) s.get(i).y(); }
                for (float[] part : PageKind.splitForSoles(f, 0.82f)) {
                    List<Pt> q = new ArrayList<>();
                    for (int i = 0; i + 1 < part.length; i += 2) q.add(new Pt(part[i], part[i + 1]));
                    onSoles.add(q);
                }
            }
            GlyphReading r = GlyphAnalyzer.analyze(PageKind.joinSoles(onSoles), 0.035);
            if (r.closed() && GlyphLibrary.readsAs(flight, r)) ok++;
            List<List<Pt>> whole = new ArrayList<>();
            for (List<Pt> s : h.strokes) { List<Pt> q = new ArrayList<>(); for (Pt p : s) q.add(new Pt(p.x() * 0.82, p.y() * 0.82)); whole.add(q); }
            GlyphReading w = GlyphAnalyzer.analyze(whole, 0.035);
            if (w.closed() && GlyphLibrary.readsAs(flight, w)) base++;
        }
        System.out.println("shoe glyphs read right " + ok + "/" + n + " (drawn whole: " + base + ")");

        assertTrue(ok >= base - 1, ok + "/" + n + " against " + base + " whole");
        assertTrue(ok >= n * 0.6, "shoe glyphs read right only " + ok + "/" + n);
    }
}
