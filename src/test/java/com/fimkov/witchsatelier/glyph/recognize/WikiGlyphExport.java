package com.fimkov.witchsatelier.glyph.recognize;

import com.fimkov.witchsatelier.glyph.model.Sigil;
import com.fimkov.witchsatelier.glyph.page.GlyphLibrary;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

class WikiGlyphExport {
    private static final String[] ARTIFACTS = {"washing_barrel", "glowstone_path", "void_basin", "dragon_teacup", "sealchair",
            "beast_warding_tower", "arcane_lens", "snugstone", "raincleaver", "mirror_mantle", "wingcloak", "tent_staff",
            "capture_pennant", "light_tracer_bangle", "vapor_bubble"};

    @Test
    void export() throws IOException {
        String out = System.getenv("WIKI_OUT");
        Assumptions.assumeTrue(out != null && !out.isBlank(), "set WIKI_OUT to export");
        Path dir = Path.of(out);
        Files.createDirectories(dir.resolve("spells"));
        Files.createDirectories(dir.resolve("artifacts"));
        Files.createDirectories(dir.resolve("sigils"));
        Files.createDirectories(dir.resolve("signs"));
        for (GlyphLibrary.Recipe r : GlyphLibrary.ALL) glyph(dir.resolve("spells/" + r.name() + ".svg"), r);
        for (GlyphLibrary.Recipe r : GlyphLibrary.FORBIDDEN) glyph(dir.resolve("spells/" + r.name() + ".svg"), r);
        for (int i = 0; i < ARTIFACTS.length; i++) glyph(dir.resolve("artifacts/" + ARTIFACTS[i] + ".svg"), AwakeningGlyphsTest.GLYPHS.get(i));
        Set<String> done = new HashSet<>();
        for (Templates.Template t : Templates.SIGILS) {
            if (t.sigil() == Sigil.APPLE || !done.add(t.id())) continue;
            Files.writeString(dir.resolve("sigils/" + t.id() + ".svg"), svg(t.strokes(), 1.15, 0.07));
        }
        for (Templates.Template t : Templates.SIGNS) {
            if (!done.add(t.id())) continue;
            Files.writeString(dir.resolve("signs/" + t.id() + ".svg"), svg(t.strokes(), 1.15, 0.07));
        }
    }

    private static void glyph(Path file, GlyphLibrary.Recipe r) throws IOException {
        HandDrawn h = new HandDrawn(r.name().hashCode(), 0.25).recipe(r, 0.85, 0);
        Files.writeString(file, svg(h.strokes, 0.98, 0.028));
    }

    private static String svg(List<List<Pt>> strokes, double half, double width) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.ROOT, "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"%.3f %.3f %.3f %.3f\">",
                -half, -half, half * 2, half * 2));
        sb.append(String.format(Locale.ROOT, "<g fill=\"none\" stroke=\"#2a1a14\" stroke-width=\"%.3f\" stroke-linecap=\"round\" stroke-linejoin=\"round\">", width));
        for (List<Pt> s : strokes) {
            if (s.isEmpty()) continue;
            if (s.size() == 1) {
                sb.append(String.format(Locale.ROOT, "<circle cx=\"%.4f\" cy=\"%.4f\" r=\"%.4f\" fill=\"#2a1a14\" stroke=\"none\"/>", s.get(0).x(), -s.get(0).y(), width));
                continue;
            }
            sb.append("<polyline points=\"");
            for (Pt p : s) sb.append(String.format(Locale.ROOT, "%.4f,%.4f ", p.x(), -p.y()));
            sb.append("\"/>");
        }
        sb.append("</g></svg>\n");
        return sb.toString();
    }
}
