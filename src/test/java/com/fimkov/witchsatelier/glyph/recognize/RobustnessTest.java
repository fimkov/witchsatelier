package com.fimkov.witchsatelier.glyph.recognize;

import com.fimkov.witchsatelier.glyph.model.SignType;
import com.fimkov.witchsatelier.glyph.page.GlyphLibrary;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;

class RobustnessTest {
    static final double INK = 1.0 / 16;
    static final List<GlyphLibrary.Recipe> RECIPES = all();

    private static List<GlyphLibrary.Recipe> all() {
        List<GlyphLibrary.Recipe> out = new ArrayList<>(GlyphLibrary.ALL);
        out.addAll(GlyphLibrary.FORBIDDEN);
        return out;
    }

    static String mistake(GlyphLibrary.Recipe r, GlyphReading g) {
        if (g.ring() == null) return "no ring";
        if (!g.closed()) return "ring open";
        GlyphReading core = g;
        if (r.band() != null) {
            if (g.core() == null) return "nest not seen";
            String band = signsMistake(r.band().signs(), g);
            if (band != null) return "band: " + band;
            if (r.band().sigil() != g.sigil()) return "band sigil " + r.band().sigil() + " read as " + g.sigil();
            core = g.core();
        } else if (g.core() != null) {
            return "nest seen where there is none";
        }
        if (r.sigil() != core.sigil()) return "sigil " + r.sigil() + " read as " + (core.sigil() != null ? core.sigil() : core.centerSign());
        if (r.centerSign() != core.centerSign()) return "centre " + r.centerSign() + " read as " + core.centerSign();
        return signsMistake(r.signs(), core);
    }

    private static String signsMistake(List<GlyphLibrary.Signs> want, GlyphReading g) {
        Map<SignType, Integer> expected = new EnumMap<>(SignType.class);
        for (GlyphLibrary.Signs s : want) expected.merge(s.type(), s.count(), Integer::sum);
        Map<SignType, Integer> got = new EnumMap<>(SignType.class);
        for (GlyphReading.ReadSign s : g.signs()) got.merge(s.type(), 1, Integer::sum);
        if (!expected.equals(got)) {
            List<String> lost = new ArrayList<>(), extra = new ArrayList<>();
            for (var e : expected.entrySet()) if (got.getOrDefault(e.getKey(), 0) < e.getValue()) lost.add(e.getKey().name());
            for (var e : got.entrySet()) if (expected.getOrDefault(e.getKey(), 0) < e.getValue()) extra.add(e.getKey().name());
            return "signs " + lost + " -> " + extra + (g.unknown() > 0 ? " (+" + g.unknown() + " unread)" : "");
        }
        if (g.unknown() > 0) return g.unknown() + " unread marks";
        for (var e : expected.keySet()) {
            int inv = 0, all = 0;
            for (GlyphReading.ReadSign s : g.signs()) if (s.type() == e) { all++; if (s.inverted()) inv++; }
            if (inv * 2 > all) return e.name() + " read as reversed";
        }
        return null;
    }

    static GlyphReading read(GlyphLibrary.Recipe r, long seed, double sloppiness) {
        HandDrawn h = new HandDrawn(seed, sloppiness);

        double ringRadius = r.band() != null ? 2.0 + h.rnd.nextDouble() * 1.5 : 1.2 + h.rnd.nextDouble() * 1.8;
        double turn = h.rnd.nextDouble() * Math.PI * 2;
        h.recipe(r, ringRadius, turn);

        double up = turn + Math.PI / 2 + h.g(0.35);
        return GlyphAnalyzer.analyzeFromAnySide(h.strokes, INK, up);
    }

    static double accuracy(double sloppiness, int samples, Map<String, Integer> mistakes, Map<String, double[]> perRecipe) {
        int ok = 0, all = 0;
        for (int k = 0; k < RECIPES.size(); k++) {
            GlyphLibrary.Recipe r = RECIPES.get(k);
            int good = 0;
            for (int i = 0; i < samples; i++) {
                String m = mistake(r, read(r, 1000L * k + i * 7919L + Math.round(sloppiness * 100), sloppiness));
                if (m == null) good++;
                else if (mistakes != null) mistakes.merge(r.name() + ": " + m, 1, Integer::sum);
            }
            ok += good;
            all += samples;
            if (perRecipe != null) perRecipe.computeIfAbsent(r.name(), n -> new double[3])[sloppiness < 0.8 ? 0 : sloppiness < 1.3 ? 1 : 2] = (double) good / samples;
        }
        return (double) ok / all;
    }

    @Test
    void inspect() throws Exception {
        String only = System.getenv("ROBUST_ONLY");
        if (only == null) return;
        double level = Double.parseDouble(System.getenv().getOrDefault("ROBUST_LEVEL", "1.0"));
        String dir = System.getenv("ROBUST_DUMP_DIR");
        int k = 0;
        for (; k < RECIPES.size(); k++) if (RECIPES.get(k).name().equals(only)) break;
        GlyphLibrary.Recipe r = RECIPES.get(k);
        int dumped = 0;
        for (int i = 0; i < 12; i++) {
            long seed = 1000L * k + i * 7919L + Math.round(level * 100);
            HandDrawn h = new HandDrawn(seed, level);
            double ringRadius = r.band() != null ? 2.0 + h.rnd.nextDouble() * 1.5 : 1.2 + h.rnd.nextDouble() * 1.8;
            double turn = h.rnd.nextDouble() * Math.PI * 2;
            h.recipe(r, ringRadius, turn);
            double up = turn + Math.PI / 2 + h.g(0.35);
            GlyphReading g = GlyphAnalyzer.analyzeFromAnySide(h.strokes, INK, up);
            String m = mistake(r, g);
            System.out.printf("INSPECT %d %s | %s%n", i, m == null ? "ok" : m, g.describe());
            if (m != null && dir != null && dumped++ < 4) {
                StringBuilder sb = new StringBuilder("#ink " + INK + "\n#up " + Math.toDegrees(up) + "\n");
                for (var st : h.strokes) {
                    for (int j = 0; j < st.size(); j++) sb.append(j > 0 ? " " : "").append(String.format(Locale.ROOT, "%.4f %.4f", st.get(j).x(), st.get(j).y()));
                    sb.append("\n");
                }
                Files.createDirectories(Path.of(dir));
                Files.writeString(Path.of(dir, only + "_" + i + ".txt"), sb.toString());
            }
        }
    }

    @Test
    void handDrawnCopiesReadAsTheirPage() {
        Map<String, Integer> mistakes = new TreeMap<>();
        double careful = accuracy(0.5, 6, null, null);
        double ordinary = accuracy(1.0, 6, mistakes, null);
        assertTrue(careful >= 0.94, "careful copies read right only " + careful);
        assertTrue(ordinary >= 0.80, "ordinary copies read right only " + ordinary + ": " + mistakes);
    }

    @Test
    void report() {
        String n = System.getenv("ROBUST_REPORT");
        if (n == null) return;
        int samples = Integer.parseInt(n);
        Map<String, double[]> per = new TreeMap<>();
        Map<String, Integer> mistakes = new TreeMap<>();
        for (double s : new double[]{0.5, 1.0, 1.6}) {
            double a = accuracy(s, samples, s == 1.0 ? mistakes : null, per);
            System.out.printf("ROBUST sloppiness %.1f: %.1f%% read right%n", s, a * 100);
        }
        for (var e : per.entrySet()) {
            double[] v = e.getValue();
            System.out.printf("ROBUST %-22s careful %3.0f%%  ordinary %3.0f%%  hasty %3.0f%%%n", e.getKey(), v[0] * 100, v[1] * 100, v[2] * 100);
        }
        mistakes.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(40)
                .forEach(e -> System.out.printf("ROBUST mistake x%d  %s%n", e.getValue(), e.getKey()));
    }
}
