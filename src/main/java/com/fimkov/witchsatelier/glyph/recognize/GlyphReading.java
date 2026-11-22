package com.fimkov.witchsatelier.glyph.recognize;

import com.fimkov.witchsatelier.glyph.model.Orientation;
import com.fimkov.witchsatelier.glyph.model.Sigil;
import com.fimkov.witchsatelier.glyph.model.SignType;

import java.util.List;

/**
 * What the analyzer read from a drawing: the outer ring, the centre symbol and the signs around it,
 * plus quality measures that drive power, duration and stability.
 *
 * @param ring           outermost ring, or null if no ring was found
 * @param innerRings     other rings inside it (nested or satellite glyphs)
 * @param sigil          the central element, or null
 * @param centerSign     a sign standing in for the sigil (Repetition, Vision...), or null
 * @param centerScore    match quality of the centre symbol, 0..1
 * @param signs          recognised signs around the centre
 * @param unknown        symbols inside the ring that matched nothing well (noise)
 * @param imbalance      size-weighted mean of the signs' outward unit vectors; length 0 = balanced
 * @param neatness       mean match score of all symbols, 0..1
 * @param usedStrokes    indices of all strokes that belong to this glyph
 * @param core           nested glyph: what is inside the inner ring, read as a glyph of its own (this reading then
 *                       holds only the band between the two rings), or null
 */
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
        GlyphReading core
) {
    public GlyphReading(RingFinder.Ring ring, List<RingFinder.Ring> innerRings, Sigil sigil, SignType centerSign, double centerScore,
                        List<ReadSign> signs, int unknown, Pt imbalance, double neatness, List<Integer> usedStrokes) {
        this(ring, innerRings, sigil, centerSign, centerScore, signs, unknown, imbalance, neatness, usedStrokes, null);
    }

    public boolean nested() {
        return core != null;
    }

    public record ReadSign(SignType type, boolean reversedTemplate, Orientation orientation, double angle,
                           double size, double score, Pt centroid, List<Integer> strokes) {
        /** True when the sign is flipped relative to its normal form and should invert its effect. */
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

    /** Symmetry 0..1 from the sign layout (1 = perfectly balanced). */
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
