package com.fimkov.witchsatelier.glyph.recognize;

import com.fimkov.witchsatelier.glyph.model.Orientation;
import com.fimkov.witchsatelier.glyph.model.Sigil;
import com.fimkov.witchsatelier.glyph.model.SignType;

import java.util.List;

public record GlyphReading(
        RingFinder.Ring ring,
        List<RingFinder.Ring> innerRings,
        Sigil sigil,
        SignType centerSign,
        double centerScore,
        List<ReadSign> signs,
        int unknown,
        Pt imbalance,
        double neatness,
        List<Integer> usedStrokes,
        GlyphReading core,
        List<Sigil> sides
) {
    public GlyphReading(RingFinder.Ring ring, List<RingFinder.Ring> innerRings, Sigil sigil, SignType centerSign, double centerScore,
                        List<ReadSign> signs, int unknown, Pt imbalance, double neatness, List<Integer> usedStrokes) {
        this(ring, innerRings, sigil, centerSign, centerScore, signs, unknown, imbalance, neatness, usedStrokes, null, List.of());
    }

    public GlyphReading(RingFinder.Ring ring, List<RingFinder.Ring> innerRings, Sigil sigil, SignType centerSign, double centerScore,
                        List<ReadSign> signs, int unknown, Pt imbalance, double neatness, List<Integer> usedStrokes, GlyphReading core) {
        this(ring, innerRings, sigil, centerSign, centerScore, signs, unknown, imbalance, neatness, usedStrokes, core, List.of());
    }

    public boolean nested() {
        return core != null;
    }

    public record ReadSign(SignType type, boolean reversedTemplate, Orientation orientation, double angle,
                           double size, double score, Pt centroid, List<Integer> strokes) {
        public boolean inverted() {
            return reversedTemplate ^ (orientation == Orientation.INWARD);
        }
    }

    public boolean closed() {
        return ring != null && ring.closed();
    }

    public long count(SignType type) {
        return signs.stream().filter(s -> s.type() == type).count();
    }

    public boolean has(SignType type) {
        return count(type) > 0;
    }

    public double symmetry() {
        return signs.isEmpty() ? 1 : Math.max(0, 1 - imbalance.len());
    }

    public String describe() {
        StringBuilder sb = new StringBuilder();
        if (ring == null) return "no ring";
        sb.append(String.format("ring r=%.2f round=%.2f gap=%.3f %s", ring.radius(), ring.roundness(), ring.maxGap(),
                ring.closed() ? "CLOSED" : "open"));
        sb.append(" | centre=").append(sigil != null ? sigil : centerSign != null ? centerSign : "-")
                .append(String.format("(%.2f)", centerScore));
        for (ReadSign s : signs) {
            sb.append(" | ").append(s.type()).append(s.inverted() ? "~" : "").append('/').append(s.orientation())
                    .append(String.format("(%.2f)", s.score()));
        }
        if (unknown > 0) sb.append(" | unknown=").append(unknown);
        sb.append(String.format(" | sym=%.2f neat=%.2f", symmetry(), neatness));
        if (core != null) sb.append(" || inside: ").append(core.describe());
        return sb.toString();
    }
}
