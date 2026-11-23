package com.fimkov.witchsatelier.glyph.recognize;

import com.fimkov.witchsatelier.glyph.model.Sigil;
import com.fimkov.witchsatelier.glyph.model.SignType;
import com.fimkov.witchsatelier.glyph.page.GlyphLibrary;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
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
        int depth = 0;
        for (GlyphLibrary.Band band : r.bands()) {
            depth++;
            if (core.core() == null) return "nest " + depth + " not seen";
            String b = signsMistake(band.signs(), core);
            if (b != null) return "band " + depth + ": " + b;
            if (band.sigil() != core.sigil()) return "band sigil " + band.sigil() + " read as " + core.sigil();
            core = core.core();
        }
        if (core.core() != null) return "nest seen where there is none";
        if (r.sigil() != core.sigil()) return "sigil " + r.sigil() + " read as " + (core.sigil() != null ? core.sigil() : core.centerSign());
        if (r.centerSign() != core.centerSign()) return "centre " + r.centerSign() + " read as " + core.centerSign();
        String signs = signsMistake(r.signs(), core);
        if (signs != null) return signs;
        List<Sigil> want = new ArrayList<>(r.sides()), got = new ArrayList<>(core.sides());
        want.sort(null);
        got.sort(null);
        return want.equals(got) ? null : "side sigils " + want + " read as " + got;
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

    static double ringRadius(GlyphLibrary.Recipe r, HandDrawn h) {
        if (r.bands().size() > 1) return 2.6 + h.rnd.nextDouble() * 1.4;
        if (!r.bands().isEmpty() || !r.lunes().isEmpty()) return 2.0 + h.rnd.nextDouble() * 1.5;
        return 1.2 + h.rnd.nextDouble() * 1.8;
    }

    static GlyphReading read(GlyphLibrary.Recipe r, long seed, double sloppiness) {
        HandDrawn h = new HandDrawn(seed, sloppiness);

        double ringRadius = ringRadius(r, h);
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
    void handDrawnCopiesReadAsTheirPage() {
        Map<String, Integer> mistakes = new TreeMap<>();
        Map<String, double[]> perRecipe = new TreeMap<>();
        double careful = accuracy(0.5, 6, null, perRecipe);
        double ordinary = accuracy(1.0, 6, mistakes, perRecipe);
        System.out.println("careful " + careful + ", ordinary " + ordinary);
        perRecipe.forEach((name, v) -> System.out.printf("  %-20s careful %.2f ordinary %.2f%n", name, v[0], v[1]));
        assertTrue(careful >= 0.94, "careful copies read right only " + careful);
        assertTrue(ordinary >= 0.80, "ordinary copies read right only " + ordinary + ": " + mistakes);
        List<String> unreadable = new ArrayList<>();
        perRecipe.forEach((name, v) -> { if (v[0] == 0) unreadable.add(name); });
        assertTrue(unreadable.isEmpty(), "no careful copy of these reads at all: " + unreadable);
    }
}
