package com.fimkov.witchsatelier.glyph.recognize;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Reads glyphs dumped from a real game (one stroke per line, "x y x y ..."), if any are given. */
class RealDrawingsTest {
    private static List<List<Pt>> load(String resource) throws Exception {
        List<List<Pt>> strokes = new ArrayList<>();
        for (String line : Files.readAllLines(Path.of(RealDrawingsTest.class.getResource("/drawings/" + resource).toURI()))) {
            if (line.isBlank() || line.startsWith("#")) continue;
            String[] t = line.trim().split(" ");
            List<Pt> s = new ArrayList<>();
            for (int i = 0; i + 1 < t.length; i += 2) s.add(new Pt(Double.parseDouble(t[i]), Double.parseDouble(t[i + 1])));
            strokes.add(s);
        }
        return strokes;
    }

    /** From a real game: Column bars lying just inside the ring must not count as part of the ring. */
    @Test
    void columnBarsNextToTheRingDoNotOpenIt() throws Exception {
        GlyphReading r = GlyphAnalyzer.analyze(load("quire_fire_columns.txt"), 0.035);
        org.junit.jupiter.api.Assertions.assertTrue(r.closed(), r.describe());
        org.junit.jupiter.api.Assertions.assertEquals(com.fimkov.witchsatelier.glyph.model.Sigil.FIRE, r.sigil(), r.describe());
        org.junit.jupiter.api.Assertions.assertEquals(4, r.count(com.fimkov.witchsatelier.glyph.model.SignType.COLUMN), r.describe());
    }

    /** From a real game: shaky arrows drawn as a set are read as the set (three of four, at least). */
    @Test
    void shakyLevitationArrowsAreReadAsASet() throws Exception {
        GlyphReading r = GlyphAnalyzer.analyze(load("ice_wind_levitation.txt"), 1.0 / 16);
        org.junit.jupiter.api.Assertions.assertTrue(r.closed(), r.describe());
        org.junit.jupiter.api.Assertions.assertTrue(r.count(com.fimkov.witchsatelier.glyph.model.SignType.LEVITATION) >= 3, r.describe());
    }

    /**
     * From a real game, drawn on grass: the ring spiralled past its start (one end inside the line, one outside)
     * and was joined with a short stroke across; the witch then walked round to the far side before it closed.
     */
    @Test
    void spiralRingJoinedAcrossClosesAndReadsFromAnySide() throws Exception {
        var strokes = load("floor_spiral_fire_columns.txt");
        // the sigil's up is +u for the witch who drew it; they closed it facing a quarter-turn away
        GlyphReading r = GlyphAnalyzer.analyzeFromAnySide(strokes, 1.0 / 16, Math.PI / 2);
        org.junit.jupiter.api.Assertions.assertTrue(r.closed(), r.describe());
        org.junit.jupiter.api.Assertions.assertEquals(com.fimkov.witchsatelier.glyph.model.Sigil.FIRE, r.sigil(), r.describe());
        org.junit.jupiter.api.Assertions.assertEquals(4, r.count(com.fimkov.witchsatelier.glyph.model.SignType.COLUMN), r.describe());
    }

    /**
     * From a real game, on sand: the earth sigil drawn as a downward triangle on its stem with the dots by its point,
     * and four eyes with a small pupil each. It once read as fire and four diamonds.
     */
    /**
     * A windowway drawn on a floor: a square box across a cross, once with the cross running off to one side of the
     * box. Both read as the Light's diamond, which the point cloud scores nearly alike.
     */
    @Test
    void squareAcrossACrossReadsAsWindowNotLight() throws Exception {
        for (String f : List.of("floor_window_square_cross.txt", "floor_window_offset_cross.txt")) {
            GlyphReading r = GlyphAnalyzer.analyzeFromAnySide(load(f), 1.0 / 16, Math.PI / 2);
            org.junit.jupiter.api.Assertions.assertTrue(r.closed(), f + ": " + r.describe());
            org.junit.jupiter.api.Assertions.assertEquals(com.fimkov.witchsatelier.glyph.model.SignType.WINDOW, r.centerSign(), f + ": " + r.describe());
        }
    }

    @Test
    void triangleEarthAndPupilledEyesReadAsPetrification() throws Exception {
        var strokes = load("floor_earth_four_eyes.txt");
        GlyphReading r = GlyphAnalyzer.analyzeFromAnySide(strokes, 1.0 / 16, 0.3);
        org.junit.jupiter.api.Assertions.assertTrue(r.closed(), r.describe());
        org.junit.jupiter.api.Assertions.assertEquals(com.fimkov.witchsatelier.glyph.model.Sigil.EARTH, r.sigil(), r.describe());
        org.junit.jupiter.api.Assertions.assertEquals(4, r.count(com.fimkov.witchsatelier.glyph.model.SignType.EYE), r.describe());
        org.junit.jupiter.api.Assertions.assertEquals(0, r.count(com.fimkov.witchsatelier.glyph.model.SignType.DIAMOND), r.describe());
    }

    /**
     * The same glyph drawn again with care: each eye in two arcs with a round pupil floating in the middle, the
     * earth sigil's dots a little way off. The pupils once read as signs of their own and the dots as an eye.
     */
    @Test
    void floatingPupilsAndSigilDotsAreNotSignsOfTheirOwn() throws Exception {
        var strokes = load("floor_earth_four_round_pupils.txt");
        GlyphReading r = GlyphAnalyzer.analyzeFromAnySide(strokes, 1.0 / 16, 0.3);
        org.junit.jupiter.api.Assertions.assertTrue(r.closed(), r.describe());
        org.junit.jupiter.api.Assertions.assertEquals(com.fimkov.witchsatelier.glyph.model.Sigil.EARTH, r.sigil(), r.describe());
        org.junit.jupiter.api.Assertions.assertEquals(4, r.signs().size(), r.describe());
        org.junit.jupiter.api.Assertions.assertEquals(4, r.count(com.fimkov.witchsatelier.glyph.model.SignType.EYE), r.describe());
        org.junit.jupiter.api.Assertions.assertEquals(0, r.unknown(), r.describe());
    }

    /**
     * From a real palm quire: Pillar of Flame with one Column's bar drawn across the middle of its staff (a little
     * cross that half reads as a Bolt), and the closing gone over on the ring line itself. It once read as fire with
     * three Columns, a Bolt and a Bend.
     */
    @Test
    void aSetOfColumnsKeepsItsOddOneAndRingTouchUpsAreNoSigns() throws Exception {
        var strokes = load("quire_fire_columns_crossed_bar.txt");
        GlyphReading r = GlyphAnalyzer.analyze(strokes, 0.035);
        org.junit.jupiter.api.Assertions.assertTrue(r.closed(), r.describe());
        org.junit.jupiter.api.Assertions.assertEquals(com.fimkov.witchsatelier.glyph.model.Sigil.FIRE, r.sigil(), r.describe());
        org.junit.jupiter.api.Assertions.assertEquals(4, r.signs().size(), r.describe());
        org.junit.jupiter.api.Assertions.assertEquals(4, r.count(com.fimkov.witchsatelier.glyph.model.SignType.COLUMN), r.describe());
    }

    @Test
    void readDumpedDrawings() throws Exception {
        String dir = System.getProperty("witchsatelier.drawings");
        if (dir == null) return;
        try (var files = Files.list(Path.of(dir))) {
            for (Path f : files.filter(p -> p.toString().endsWith(".txt")).sorted().toList()) {
                List<List<Pt>> strokes = new ArrayList<>();
                double ink = Double.parseDouble(System.getProperty("witchsatelier.ink", "0.035"));
                double up = Math.PI / 2;
                for (String line : Files.readAllLines(f)) {
                    if (line.isBlank()) continue;
                    if (line.startsWith("#ink ")) { ink = Double.parseDouble(line.substring(5).trim()); continue; }
                    if (line.startsWith("#up ")) { up = Math.toRadians(Double.parseDouble(line.substring(4).trim())); continue; }
                    if (line.startsWith("#")) continue; // notes, such as what the game read it as
                    String[] t = line.trim().split(" ");
                    List<Pt> s = new ArrayList<>();
                    for (int i = 0; i + 1 < t.length; i += 2) s.add(new Pt(Double.parseDouble(t[i]), Double.parseDouble(t[i + 1])));
                    strokes.add(s);
                }
                GlyphReading r = GlyphAnalyzer.analyze(strokes, ink, up);
                System.out.println("=== " + f.getFileName());
                System.out.println("rings: " + RingFinder.diagnose(strokes));
                for (RingFinder.Ring ring : RingFinder.find(strokes, ink)) {
                    System.out.printf("ring c=%s r=%.3f cov=%.3f gap=%.3f closed=%s members=%s loose=%d%n", ring.center(), ring.radius(),
                            ring.coverage(), ring.maxGap(), ring.closed(), ring.strokes(), RingFinder.looseEnds(strokes, ring.strokes(), ink));
                }
                System.out.println("reading: " + r.describe());
                if (System.getProperty("witchsatelier.ranks") != null) {
                    var ring = RingFinder.find(strokes, ink).get(0);
                    List<Integer> inside = new ArrayList<>();
                    for (int i = 0; i < strokes.size(); i++) if (!ring.strokes().contains(i) && PointCloud.centroid(strokes.get(i)).dist(ring.center()) < ring.radius()) inside.add(i);
                    List<List<Pt>> mid = new ArrayList<>();
                    for (List<Integer> g : GlyphAnalyzer.group(strokes, inside, Math.max(ink * 2.5, 0.07 * ring.radius()))) {
                        List<Pt> all = new ArrayList<>();
                        for (int i : g) all.addAll(strokes.get(i));
                        if (PointCloud.centroid(all).dist(ring.center()) < 0.35 * ring.radius()) for (int i : g) mid.add(strokes.get(i));
                    }
                    if (!mid.isEmpty()) {
                        System.out.printf("  box shape: %.2f%n", SymbolRecognizer.lineAlignment(mid));
                        for (int deg = 0; deg < 360; deg += 45) {
                            List<List<Pt>> rot = new ArrayList<>();
                            for (List<Pt> st : mid) { List<Pt> q = new ArrayList<>(); for (Pt p : st) q.add(p.rotate(Math.toRadians(deg))); rot.add(q); }
                            var rr = SymbolRecognizer.rankCenter(rot);
                            System.out.printf("  rot %d: %s %.2f, %s %.2f%n", deg, rr.get(0).template().id(), rr.get(0).score(), rr.get(1).template().id(), rr.get(1).score());
                        }
                        var cr = SymbolRecognizer.rankCenter(mid);
                        System.out.printf("  whole centre: %s %.2f, %s %.2f, %s %.2f%n", cr.get(0).template().id(), cr.get(0).score(),
                                cr.get(1).template().id(), cr.get(1).score(), cr.get(2).template().id(), cr.get(2).score());
                    }
                    for (List<Integer> g : GlyphAnalyzer.group(strokes, inside, Math.max(ink * 2.5, 0.07 * ring.radius()))) {
                        List<List<Pt>> sym = new ArrayList<>();
                        for (int i : g) sym.add(strokes.get(i));
                        Pt gc = PointCloud.centroid(sym.stream().flatMap(List::stream).toList());
                        double d = gc.dist(ring.center()) / ring.radius();
                        var ranks = d < 0.35 ? SymbolRecognizer.rankCenter(sym) : SymbolRecognizer.rankSign(sym, Math.atan2(gc.y() - ring.center().y(), gc.x() - ring.center().x()));
                        StringBuilder sb = new StringBuilder(String.format("  group %s at %.2fR:", g, d));
                        for (int k = 0; k < Math.min(4, ranks.size()); k++) sb.append(String.format(" %s/%s %.2f", ranks.get(k).template().id(), ranks.get(k).orientation(), ranks.get(k).score()));
                        System.out.println(sb);
                    }
                }
                for (var s : r.signs()) System.out.printf("  sign %s %s at %.0f deg score %.2f%n", s.type(), s.orientation(), Math.toDegrees(s.angle()), s.score());
            }
        }
    }
}
